#!/usr/bin/python

##
# Copyright 2010-2023 JetBrains s.r.o.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

#
# (lldb) command script import llvmDebugInfoC/src/scripts/konan_lldb.py
# (lldb) p kotlin_variable
#

import lldb
import struct
import re
import sys
import os
import time
import io
import traceback
import datetime
from enum import Enum

NULL = 'null'
logging=True
exe_logging=True# if (os.getenv('GLOG_log_dir') != None) else False # Same as in LLDBFrontend
bench_logging=False
f = None

class CachedDictType(Enum):
    READ_STRING = 1
    LOADED_ADDRESS = 2
    TYPE_INFO_EXPR = 3
    STRING_SYNTHETIC_READ_CSTRING = 4

def bench(start, msg):
    if bench_logging:
        print("{}: {}".format(msg(), time.monotonic() - start))

def evaluate(expr):
    target = lldb.debugger.GetSelectedTarget()
    result = target.EvaluateExpression(expr)
    return result


class DebuggerException(Exception):
    pass


_OUTPUT_MAX_CHILDREN = re.compile(r"target.max-children-count \(int\) = (.*)\n")
def _max_children_count():
    result = lldb.SBCommandReturnObject()
    lldb.debugger.GetCommandInterpreter().HandleCommand("settings show target.max-children-count", result, False)
    if not result.Succeeded():
        raise DebuggerException()
    v = _OUTPUT_MAX_CHILDREN.search(result.GetOutput()).group(1)
    return int(v)


def _symbol_loaded_address(name, debugger = lldb.debugger):
    loaded_address = get_value_from_cached_dict(CachedDictType.LOADED_ADDRESS,name)
    if loaded_address is not None:
        return loaded_address

    target = debugger.GetSelectedTarget()
    frame = target.GetProcess().GetSelectedThread().GetSelectedFrame()
    candidates = frame.module.symbol[name]
    # take first
    for candidate in candidates:
        address = candidate.GetStartAddress().GetLoadAddress(target)
        add_to_cached_dict(CachedDictType.LOADED_ADDRESS,name,address)
        return address

    return 0

def _type_info_by_address(address, debugger = lldb.debugger):
    target = debugger.GetSelectedTarget()
    frame = target.GetProcess().GetSelectedThread().GetSelectedFrame()
    candidates = list(filter(lambda x: x.GetStartAddress().GetLoadAddress(target) == address, frame.module.symbols))
    return candidates

def is_instance_of(addr, typeinfo):
    return evaluate("(bool)Konan_DebugIsInstance({:#x}, {:#x})".format(addr, typeinfo)).GetValue() == "true"

def type_info(value):
    """This method checks self-referencing of pointer of first member of TypeInfo including case when object has an
    meta-object pointed by TypeInfo. Two lower bits are reserved for memory management needs see runtime/src/main/cpp/Memory.h."""
    if value.GetTypeName() != "ObjHeader *":
        return None
    expr = "*(void **)((uintptr_t)(*(void**){0:#x}) & ~0x3) == **(void***)((uintptr_t)(*(void**){0:#x}) & ~0x3) " \
           "? *(void **)((uintptr_t)(*(void**){0:#x}) & ~0x3) : (void *)0".format(value.unsigned)
    result = get_value_from_cached_dict(CachedDictType.TYPE_INFO_EXPR,expr)
    if result is None:
        result = evaluate(expr)
        add_to_cached_dict(CachedDictType.TYPE_INFO_EXPR,expr,result)

    return result.unsigned if result.IsValid() and result.unsigned != 0 else None


__FACTORY = {}


# Cache type info pointer to [ChildMetaInfo]
SYNTHETIC_OBJECT_LAYOUT_CACHE = {}
TO_STRING_DEPTH = 2
ARRAY_TO_STRING_LIMIT = 10
TOTAL_MEMBERS_LIMIT = 50

_TYPE_CONVERSION = [
     lambda obj, value, address, name: value.CreateValueFromExpression(name, "(void *){:#x}".format(address)),
     lambda obj, value, address, name: value.CreateValueFromAddress(name, address, value.type),
     lambda obj, value, address, name: value.CreateValueFromExpression(name, "(int8_t *){:#x}".format(address)),
     lambda obj, value, address, name: value.CreateValueFromExpression(name, "(int16_t *){:#x}".format(address)),
     lambda obj, value, address, name: value.CreateValueFromExpression(name, "(int32_t *){:#x}".format(address)),
     lambda obj, value, address, name: value.CreateValueFromExpression(name, "(int64_t *){:#x}".format(address)),
     lambda obj, value, address, name: value.CreateValueFromExpression(name, "(float *){:#x}".format(address)),
     lambda obj, value, address, name: value.CreateValueFromExpression(name, "(double *){:#x}".format(address)),
     lambda obj, value, address, name: value.CreateValueFromExpression(name, "(void **){:#x}".format(address)),
     lambda obj, value, address, name: value.CreateValueFromExpression(name, "(bool *){:#x}".format(address)),
     lambda obj, value, address, name: None]

_TYPES = [
      lambda x: x.GetType().GetBasicType(lldb.eBasicTypeVoid).GetPointerType(),
      lambda x: x.GetType(),
      lambda x: x.GetType().GetBasicType(lldb.eBasicTypeChar),
      lambda x: x.GetType().GetBasicType(lldb.eBasicTypeShort),
      lambda x: x.GetType().GetBasicType(lldb.eBasicTypeInt),
      lambda x: x.GetType().GetBasicType(lldb.eBasicTypeLongLong),
      lambda x: x.GetType().GetBasicType(lldb.eBasicTypeFloat),
      lambda x: x.GetType().GetBasicType(lldb.eBasicTypeDouble),
      lambda x: x.GetType().GetBasicType(lldb.eBasicTypeVoid).GetPointerType(),
      lambda x: x.GetType().GetBasicType(lldb.eBasicTypeBool)
]

def kotlin_object_type_summary(lldb_val, internal_dict = {}):
    """Hook that is run by lldb to display a Kotlin object."""
    start = time.monotonic()

    if lldb_val.GetTypeName() != "ObjHeader *":
        if lldb_val.GetValue() is None:
            bench(start, lambda: "kotlin_object_type_summary:({:#x}) = NULL".format(lldb_val.unsigned))
            return NULL
        bench(start, lambda: "kotlin_object_type_summary:({:#x}) = {}".format(lldb_val.unsigned, lldb_val.signed))
        return lldb_val.value

    if lldb_val.unsigned == 0:
            bench(start, lambda: "kotlin_object_type_summary:({:#x}) = NULL".format(lldb_val.unsigned))
            return NULL
    tip = internal_dict["type_info"] if "type_info" in internal_dict.keys() else type_info(lldb_val)

    if not tip:
        bench(start, lambda: "kotlin_object_type_summary:({0:#x}) = falback:{0:#x}".format(lldb_val.unsigned))
        fallback = lldb_val.GetValue()
        return fallback

    value = select_provider(lldb_val, tip, internal_dict)

    bench(start, lambda: "kotlin_object_type_summary:({:#x}) = value:{:#x}".format(lldb_val.unsigned, value._valobj.unsigned))
    start = time.monotonic()
    str0 = value.to_short_string()

    bench(start, lambda: "kotlin_object_type_summary:({:#x}) = str:'{}...'".format(lldb_val.unsigned, str0[:3]))
    return str0


def select_provider(lldb_val, tip, internal_dict):
    start = time.monotonic()
    error = lldb.SBError()
    flow_string = _read_string("(char *)Konan_DebugCompleteTypeInitFlow({0:#x}, {1:#x})".
                               format(lldb_val.unsigned, _symbol_loaded_address('kclass:kotlin.String')),
                              error)
    if not error.Success():
        raise DebuggerException()

    flow_steps_string = flow_string.split("|")
    if flow_steps_string[0] == '1':
        buff_addr = int(flow_steps_string[1])
        buff_len = int(flow_steps_string[2])
        return __FACTORY['string'](lldb_val, buff_addr, buff_len)
    elif flow_steps_string[0] == '2':
        children_count = int(flow_steps_string[1])
        return __FACTORY['array'](lldb_val, internal_dict, children_count)
    else:
        children_count = int(flow_steps_string[1])
        children = flow_steps_string[2].split(",")
        children_type_element = flow_steps_string[3]
        children_type = children_type_element.split(",")
        children_addr_element = flow_steps_string[4]
        children_addr = children_addr_element.split(",")

        return __FACTORY['object'](lldb_val, internal_dict, children_count, children, children_type, children_addr)

class KonanHelperProvider(lldb.SBSyntheticValueProvider):
    def __init__(self, valobj, amString, type_name, internal_dict = {}):
        self._target = lldb.debugger.GetSelectedTarget()
        self._process = self._target.GetProcess()
        self._valobj = valobj
        self._internal_dict = internal_dict.copy()
        self._type_name = type_name
        if amString:
            return
        if self._children_count == 0:
            children_count = evaluate("(int)Konan_DebugGetFieldCount({:#x})".format(self._valobj.unsigned)).signed
            self._children_count = children_count

    def _read_string(self, expr, error):
        return _read_string(expr, error)

    def _read_value(self, index):
        value_type = self._field_type(index)
        address = self._field_address(index)
        return _TYPE_CONVERSION[int(value_type)](self, self._valobj, address, str(self._field_name(index)))

    def _read_type(self, index):
        type = _TYPES[self._field_type(index)](self._valobj)
        return type

    def _deref_or_obj_summary(self, index, internal_dict = {}):
        value = self._read_value(index)
        if not value:
            return None
        return value.value if type_info(value) else value.deref.value

    def _field_address(self, index):
        if not hasattr(self, '_children_addr') or not self._children_addr:
            return evaluate("(void *)Konan_DebugGetFieldAddress({:#x}, {})".format(self._valobj.unsigned, index)).unsigned
        else:
            return int(self._children_addr[index])

    def _field_type(self, index):
        if not hasattr(self, '_children_type') or not self._children_type:
            return evaluate("(int)Konan_DebugGetFieldType({:#x}, {})".format(self._valobj.unsigned, index)).unsigned
        else:
            return self._children_type[index]

    def to_string_by_fields_name_list(self):
        max_children_count=_max_children_count()
        limit = min(self._children_count, max_children_count)
        namelist = self._fields_name_list(limit)
        words = namelist.split('|')
        replaced_words = [word + ": ..." for word in words]
        replaced_string = ', '.join(replaced_words)
        return replaced_string

    def to_string(self, representation):
        writer = io.StringIO()
        max_children_count=_max_children_count()
        limit = min(self._children_count, max_children_count)
        for i in range(limit):
            writer.write(representation(i))
            if (i != limit - 1):
                writer.write(", ")
        if max_children_count < self._children_count:
            writer.write(', ...')

        return "[{}]".format(writer.getvalue())


class KonanStringSyntheticProvider(KonanHelperProvider):
    def __init__(self, valobj, buff_addr, buff_len):
        self._children_count = 0
        super(KonanStringSyntheticProvider, self).__init__(valobj, True, "StringProvider")
        fallback = valobj.GetValue()
        if not buff_len:
            self._representation = fallback
            return

        key = "{:#x}_{}_{:#x}".format(valobj.unsigned, valobj.name, buff_addr, buff_len)
        s = get_value_from_cached_dict(CachedDictType.STRING_SYNTHETIC_READ_CSTRING, key)
        if s is None:
            error = lldb.SBError()
            s = self._process.ReadCStringFromMemory(buff_addr, buff_len, error)
            if not error.Success():
                raise DebuggerException()
            else:
                add_to_cached_dict(CachedDictType.STRING_SYNTHETIC_READ_CSTRING, key, s)
            self._representation = s if error.Success() else fallback
        else:
            self._representation = s
        self._logger = lldb.formatters.Logger.Logger()

    def update(self):
        pass

    def num_children(self):
        return 0

    def has_children(self):
        return False

    def get_child_index(self, _):
        return None

    def get_child_at_index(self, _):
        return None

    def to_short_string(self):
        return self._representation

    def to_string(self):
        return self._representation

g_object_num_children_handle_recorded = []

class KonanObjectSyntheticProvider(KonanHelperProvider):
    def __init__(self, valobj, internal_dict, children_count, children, children_type, children_addr):
        # Save an extra call into the process
        self._children_count = children_count
        self._children_type = children_type
        self._children_addr = children_addr
        super(KonanObjectSyntheticProvider, self).__init__(valobj, False, "ObjectProvider", internal_dict)
        self._children = children

    def _field_name(self, index):
        name = self._children[index]
        return name

    def _fields_name_list(self, child_count):
        return "|".join(self._children)

    def num_children(self):
        # 因发现在二次访问同一变量的一子变量时，即使其父子内存等参数不变，获取的值却会变，且暂未找到如析构时的较好时机去清理工作链缓存，固结合实际情况，先在这
        # 里做一个处理：第二次访问此处时，清理相关缓存
        combined = [self._valobj.unsigned,self._children_count]
        global g_object_num_children_handle_recorded
        if combined in g_object_num_children_handle_recorded:
            clean_cached_dict(CachedDictType.READ_STRING)

        g_object_num_children_handle_recorded.append(combined)

        return self._children_count

    def has_children(self):
        return self._children_count > 0

    def get_child_index(self, name):
        index = self._children.index(name)
        return index

    def get_child_at_index(self, index):
        return self._read_value(index)

    def to_short_string(self):
        return super().to_string_by_fields_name_list()

    def to_string(self):
        return super().to_string(lambda index: "{}: {}".format(self._field_name(index),
                                                               self._deref_or_obj_summary(index)))

class KonanArraySyntheticProvider(KonanHelperProvider):
    def __init__(self, valobj, internal_dict, _children_count):
        self._children_count = _children_count
        super(KonanArraySyntheticProvider, self).__init__(valobj, False, "ArrayProvider", internal_dict)
        if self._valobj is None:
            return
        valobj.SetSyntheticChildrenGenerated(True)

    def num_children(self):
        return self._children_count

    def has_children(self):
        return self._children_count > 0

    def get_child_index(self, name):
        index = int(name)
        return index if (0 <= index < self._children_count) else -1

    def get_child_at_index(self, index):
        if not hasattr(self, '_children_type') or not self._children_type:
            # 先前未拉取过子元素的信息，则在此预拉取并做缓存以便后用（这表明调用直接来自lldb server等，此处并不可见，无法在既定埋点处处理）
            error = lldb.SBError()
            expr = "(char *)Konan_DebugGetFieldsTypeAndAddress({0:#x}, {1:#x})".format(self._valobj.unsigned, self._children_count)
            fieldsTypeAndAddress_string = _read_string(expr, error)
            if not error.Success():
                raise DebuggerException()

            fieldsTypeAndAddress_steps_string = fieldsTypeAndAddress_string.split("|")
            children_type_element = fieldsTypeAndAddress_steps_string[0]
            self._children_type = children_type_element.split(",")

            if not hasattr(self, '_children_addr') or not self._children_addr:
                children_addr_element = fieldsTypeAndAddress_steps_string[1]
                self._children_addr = children_addr_element.split(",")
            else:
                raise DebuggerException()


        value = self._read_value(index)
        return value

    def _field_name(self, index):
        return str(index)

    def to_short_string(self):
        return super().to_string(lambda index: "...")

    def to_string(self):
        return super().to_string(lambda index: "{}".format(self._deref_or_obj_summary(index)))

class KonanZerroSyntheticProvider(lldb.SBSyntheticValueProvider):
    def num_children(self):
        return 0

    def has_children(self):
        return False

    def get_child_index(self, name):
        return 0

    def get_child_at_index(self, index):
        return None

    def to_string(self):
        return NULL

    def to_short_string(self):
        return NULL

    def __getattr__(self, item):
        pass

class KonanNullSyntheticProvider(KonanZerroSyntheticProvider):
    def __init__(self, valobj):
        super(KonanNullSyntheticProvider, self).__init__(valobj)

class KonanNotInitializedObjectSyntheticProvider(KonanZerroSyntheticProvider):
    def __init__(self, valobj):
        super(KonanNotInitializedObjectSyntheticProvider, self).__init__(valobj)


class KonanProxyTypeProvider:
    def __init__(self, valobj, internal_dict):
        start = time.monotonic()
        if valobj.unsigned == 0:
           bench(start, lambda: "KonanProxyTypeProvider({:#x})".format(valobj.unsigned))
           self._proxy = KonanNullSyntheticProvider(valobj)
           return

        tip = type_info(valobj)
        if not tip:
           bench(start, lambda: "KonanProxyTypeProvider({:#x})".format(valobj.unsigned))
           self._proxy = KonanNotInitializedObjectSyntheticProvider(valobj)
           return
        self._proxy = select_provider(valobj, tip, internal_dict)
        bench(start, lambda: "KonanProxyTypeProvider({:#x})".format(valobj.unsigned))

    def __getattr__(self, item):
       return getattr(self._proxy, item)

def strip_quotes(name):
    return "" if (name == None) else name.strip('"')

def get_runtime_type(variable):
    return strip_quotes(evaluate("(char *)Konan_DebugGetTypeName({:#x})".format(variable.unsigned)).summary)

def field_type_command(debugger, field_address, exe_ctx, result, internal_dict):
    """
    Returns runtime type of foo.bar.baz field in the form "(foo.bar.baz <TYPE_NAME>)".
    If requested field could not be traced, then "<NO_FIELD_FOUND>" plug is used for type name.
    """
    fields = field_address.split('.')

    variable = exe_ctx.GetFrame().FindVariable(fields[0])
    provider = None

    for field_name in fields[1:]:
        if (variable != None):
            provider = KonanProxyTypeProvider(variable, internal_dict)
            field_index = provider.get_child_index(field_name)
            variable = provider.get_child_at_index(field_index)
        else:
            break

    desc = "<NO_FIELD_FOUND>"

    if (variable != None):
        rt = get_runtime_type(variable)
        if (len(rt) > 0):
            desc = rt

    result.write("{}".format(desc))


def _read_string(expr, error):
    if not error.Success():
        raise DebuggerException()
    address = get_value_from_cached_dict(CachedDictType.READ_STRING,expr)
    if address is None:
        address = evaluate(expr).unsigned
        add_to_cached_dict(CachedDictType.READ_STRING,expr,address)
    return lldb.debugger.GetSelectedTarget().GetProcess().ReadCStringFromMemory(address, 0x1000, error)

__KONAN_VARIABLE = re.compile('kvar:(.*)#internal')
__KONAN_VARIABLE_TYPE = re.compile('^kfun:<get-(.*)>\\(\\)(.*)$')
__TYPES_KONAN_TO_C = {
   'kotlin.Byte': ('int8_t', lambda v: v.signed),
   'kotlin.Short': ('short', lambda v: v.signed),
   'kotlin.Int': ('int', lambda v: v.signed),
   'kotlin.Long': ('long', lambda v: v.signed),
   'kotlin.UByte': ('int8_t', lambda v: v.unsigned),
   'kotlin.UShort': ('short', lambda v: v.unsigned),
   'kotlin.UInt': ('int', lambda v: v.unsigned),
   'kotlin.ULong': ('long', lambda v: v.unsigned),
   'kotlin.Char': ('short', lambda v: v.signed),
   'kotlin.Boolean': ('bool', lambda v: v.signed),
   'kotlin.Float': ('float', lambda v: v.value),
   'kotlin.Double': ('double', lambda v: v.value)
}


def type_by_address_command(debugger, command, result, internal_dict):
    result.AppendMessage("DEBUG: {}".format(command))
    tokens = command.split()
    target = debugger.GetSelectedTarget()
    types = _type_info_by_address(tokens[0])
    result.AppendMessage("DEBUG: {}".format(types))
    for t in types:
        result.AppendMessage("{}: {:#x}".format(t.name, t.GetStartAddress().GetLoadAddress(target)))


def symbol_by_name_command(debugger, command, result, internal_dict):
    target = debugger.GetSelectedTarget()
    frame = target.GetProcess().GetSelectedThread().GetSelectedFrame()
    tokens = command.split()
    mask = re.compile(tokens[0])
    symbols = list(filter(lambda v: mask.match(v.name), frame.GetModule().symbols))
    visited = list()
    for symbol in symbols:
       name = symbol.name
       if name in visited:
           continue
       visited.append(name)
       result.AppendMessage("{}: {:#x}".format(name, symbol.GetStartAddress().GetLoadAddress(target)))


def konan_globals_command(debugger, command, result, internal_dict):
    target = debugger.GetSelectedTarget()
    frame = target.GetProcess().GetSelectedThread().GetSelectedFrame()
    konan_variable_symbols = list(filter(lambda v: __KONAN_VARIABLE.match(v.name), frame.GetModule().symbols))
    visited = list()
    for symbol in konan_variable_symbols:
       name = __KONAN_VARIABLE.search(symbol.name).group(1)

       if name in visited:
           continue
       visited.append(name)

       getters = list(filter(lambda v: re.match('^kfun:<get-{}>\\(\\).*$'.format(name), v.name), frame.module.symbols))
       if not getters:
           result.AppendMessage("storage not found for name:{}".format(name))
           continue

       getter_functions = frame.module.FindFunctions(getters[0].name)
       if not getter_functions:
           continue

       address = getter_functions[0].function.GetStartAddress().GetLoadAddress(target)
       type = __KONAN_VARIABLE_TYPE.search(getters[0].name).group(2)
       (c_type, extractor) = __TYPES_KONAN_TO_C[type] if type in __TYPES_KONAN_TO_C.keys() else ('ObjHeader *', lambda v: kotlin_object_type_summary(v))
       value = evaluate('(({0} (*)()){1:#x})()'.format(c_type, address))
       str_value = extractor(value)
       result.AppendMessage('{} {}: {}'.format(type, name, str_value))

g_read_string_dict = {}
g_loaded_address_dict = {}
g_type_info_expr_dict = {}
g_string_synthetic_read_cstring = {}

def add_to_cached_dict(type: CachedDictType, key, value):
    if type is CachedDictType.READ_STRING:
        g_read_string_dict[key] = value
    elif type is CachedDictType.LOADED_ADDRESS:
        g_loaded_address_dict[key] = value
    elif type is CachedDictType.TYPE_INFO_EXPR:
        g_type_info_expr_dict[key] = value
    elif type is CachedDictType.STRING_SYNTHETIC_READ_CSTRING:
        g_string_synthetic_read_cstring[key] = value
    else:
        raise DebuggerException()


def clean_cached_dict(type: CachedDictType):
    if type is CachedDictType.READ_STRING:
        global g_read_string_dict
        g_read_string_dict = {}
    else:
        raise DebuggerException()

def get_value_from_cached_dict(type: CachedDictType, key):
    # 如果键不存在，返回None
    if type is CachedDictType.READ_STRING:
        return g_read_string_dict.get(key, None)
    if type is CachedDictType.LOADED_ADDRESS:
        return g_loaded_address_dict.get(key, None)
    if type is CachedDictType.TYPE_INFO_EXPR:
        return g_type_info_expr_dict.get(key, None)
    if type is CachedDictType.STRING_SYNTHETIC_READ_CSTRING:
        return g_string_synthetic_read_cstring.get(key, None)
    raise DebuggerException()

class KonanStep(object):
    def __init__(self, thread_plan):
        self.thread_plan = thread_plan
        self.step_thread_plan = self.queue_thread_plan()

        debugger = thread_plan.GetThread().GetProcess().GetTarget().GetDebugger()
        self.avoid_no_debug = debugger.GetInternalVariableValue('target.process.thread.step-in-avoid-nodebug',
                                                                debugger.GetInstanceName()).GetStringAtIndex(0)

    def explains_stop(self, event):
        return True

    def should_stop(self, event):
        frame = self.thread_plan.GetThread().GetFrameAtIndex(0)
        source_file = frame.GetLineEntry().GetFileSpec().GetFilename()

        if self.avoid_no_debug == 'true' and source_file in [None, '<compiler-generated>']:
            self.step_thread_plan = self.queue_thread_plan()
            return False

        self.thread_plan.SetPlanComplete(True)
        return True

    def should_step(self):
        return True

    def queue_thread_plan(self):
        address = self.thread_plan.GetThread().GetFrameAtIndex(0).GetPCAddress()
        line_entry = self.thread_plan.GetThread().GetFrameAtIndex(0).GetLineEntry()
        begin_address = line_entry.GetStartAddress().GetFileAddress()
        end_address = line_entry.GetEndAddress().GetFileAddress()
        return self.do_queue_thread_plan(address, end_address - begin_address)


class KonanStepIn(KonanStep):
    def __init__(self, thread_plan, dict, *args):
        KonanStep.__init__(self, thread_plan)

    def do_queue_thread_plan(self, address, offset):
        return self.thread_plan.QueueThreadPlanForStepInRange(address, offset)


class KonanStepOver(KonanStep):
    def __init__(self, thread_plan, dict, *args):
        KonanStep.__init__(self, thread_plan)

    def do_queue_thread_plan(self, address, offset):
        return self.thread_plan.QueueThreadPlanForStepOverRange(address, offset)


class KonanStepOut(KonanStep):
    def __init__(self, thread_plan, dict, *args):
        KonanStep.__init__(self, thread_plan)

    def do_queue_thread_plan(self, address, offset):
      return self.thread_plan.QueueThreadPlanForStepOut(0)


KONAN_LLDB_DONT_SKIP_BRIDGING_FUNCTIONS = 'KONAN_LLDB_DONT_SKIP_BRIDGING_FUNCTIONS'
MAX_SIZE_FOR_STOP_REASON = 20
PLAN_FROM_STOP_REASON = {
    'step in' : KonanStepIn.__name__,
    'step out' : KonanStepOut.__name__,
    'step over' : KonanStepOver.__name__,
}

class KonanHook:
    def __init__(self, target, extra_args, _):
        pass

    def handle_stop(self, execution_context, stream) -> bool:
        is_bridging_functions_skip_enabled = not execution_context.target.GetEnvironment().Get(KONAN_LLDB_DONT_SKIP_BRIDGING_FUNCTIONS)

        def is_kotlin_bridging_function() -> bool:
            addr = execution_context.frame.addr
            function_name = addr.function.name
            if function_name is None:
                return False
            file_name = addr.line_entry.file.basename
            if file_name is None:
                return False
            return function_name.startswith('objc2kotlin_') and file_name == '<compiler-generated>'

        if is_bridging_functions_skip_enabled and is_kotlin_bridging_function():
            stop_reason = execution_context.frame.thread.GetStopDescription(MAX_SIZE_FOR_STOP_REASON)
            plan = PLAN_FROM_STOP_REASON.get(stop_reason)
            if plan is not None:
                execution_context.thread.StepUsingScriptedThreadPlan('{}.{}'.format(__name__, plan), False)
                return False
        return True


def __lldb_init_module(debugger, _):
    if exe_logging:
        file_path = os.getenv('HOME', '') + "/konan_lldb_log.txt"
        if os.path.exists(file_path):
            os.remove(file_path)

        global f
        if f is None or f.closed:
            f = open(file_path, "a")
            # close暂不处理

    __FACTORY['object'] = lambda x, y, z, a, b, c: KonanObjectSyntheticProvider(x, y, z, a, b, c)
    __FACTORY['array'] = lambda x, y, z: KonanArraySyntheticProvider(x, y, z)
    __FACTORY['string'] = lambda x, y, z: KonanStringSyntheticProvider(x, y, z)
    debugger.HandleCommand('\
        type summary add \
        --no-value \
        --expand \
        --python-function konan_lldb.kotlin_object_type_summary \
        "ObjHeader *" \
        --category Kotlin\
    ')
    debugger.HandleCommand('\
        type synthetic add \
        --python-class konan_lldb.KonanProxyTypeProvider \
        "ObjHeader *" \
        --category Kotlin\
    ')
    debugger.HandleCommand('type category enable Kotlin')
    debugger.HandleCommand('command script add -f {}.field_type_command field_type'.format(__name__))
    debugger.HandleCommand('command script add -f {}.type_by_address_command type_by_address'.format(__name__))
    debugger.HandleCommand('command script add -f {}.symbol_by_name_command symbol_by_name'.format(__name__))
    # Avoid Kotlin/Native runtime
    debugger.HandleCommand('settings set target.process.thread.step-avoid-regexp ^::Kotlin_')
    debugger.HandleCommand('target stop-hook add -P {}.KonanHook'.format(__name__))
