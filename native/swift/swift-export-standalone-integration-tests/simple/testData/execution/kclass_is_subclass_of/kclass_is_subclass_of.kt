// KIND: STANDALONE
// WITH_PLATFORM_LIBS

// MODULE: fooKitInterop

// FILE: fooKitInterop.def
language = Objective-C
modules = FooKit
package = foo

// FILE: Foo.h
#import <Foundation/Foundation.h>

@protocol Bar
- (int)barValue;
@end

@interface Foo : NSObject <Bar>
@property (nonatomic, assign) int payload;
- (instancetype)initWithPayload:(int)payload;
- (int)doubled;
@end

// FILE: Foo.m
#import "Foo.h"

@implementation Foo

- (instancetype)initWithPayload:(int)payload {
    if (self = [super init]) {
        _payload = payload;
    }
    return self;
}

- (int)doubled {
    return self.payload * 2;
}

- (int)barValue {
    return self.payload;
}

@end

// FILE: module.modulemap
module FooKit {
    header "Foo.h"
    export *
}

// MODULE: Main(fooKitInterop)
// FILE: main.kt
@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

import foo.BarProtocol
import foo.Foo
import platform.Foundation.NSCopyingProtocol
import platform.Foundation.NSDate
import platform.Foundation.NSLock
import platform.Foundation.NSLockingProtocol
import platform.Foundation.NSString
import platform.darwin.NSObject
import platform.darwin.NSObjectProtocol

open class Base {
    open fun greet(): String = "Hello from Kotlin"
}

interface Speaker {
    fun speak(): String
    fun volume(): Int
}

interface Reader {
    fun read(): String
}

interface Writer {
    fun write(s: String): Int
}

interface Animal {
    fun name(): String
}

interface Dog : Animal {
    fun bark(): String
}

open class DogBase : Dog {
    override fun name(): String = "kotlin-dog"
    override fun bark(): String = "kotlin-woof"
}

abstract class AbstractBase {
    abstract fun compute(): Int
}

open class ConcreteAbstractBase : AbstractBase(), Reader {
    override fun compute(): Int = 42
    override fun read(): String = "concrete-abstract-reader"
}

class FinalChild : Base()

private class PrivateChild : Base(), Speaker {
    override fun speak(): String = "private-speak"
    override fun volume(): Int = 1
}

fun createPrivateChildAsBase(): Base = PrivateChild()
fun createPrivateChildAsSpeaker(): Speaker = PrivateChild()

object SingletonChild : Base()

enum class ExportedEnum : Animal {
    ONE { override fun name(): String = "one" },
    TWO { override fun name(): String = "two" }
}

fun isSubclassOfByInstances(sub: Any, sup: Any): Boolean = sub::class.isSubclassOf(sup::class)
fun isInstanceByInstances(receiver: Any, value: Any?): Boolean = receiver::class.isInstance(value)
fun isSameKClass(a: Any, b: Any): Boolean = a::class == b::class

fun isSubclassOfBase(obj: Any): Boolean = obj::class.isSubclassOf(Base::class)
fun isBaseSubclassOfObj(obj: Any): Boolean = Base::class.isSubclassOf(obj::class)
fun isBaseInstance(obj: Any?): Boolean = Base::class.isInstance(obj)
fun isSubclassOfAbstractBase(obj: Any): Boolean = obj::class.isSubclassOf(AbstractBase::class)
fun isSubclassOfSpeaker(obj: Any): Boolean = obj::class.isSubclassOf(Speaker::class)
fun isSpeakerInstance(obj: Any?): Boolean = Speaker::class.isInstance(obj)
fun isSubclassOfReader(obj: Any): Boolean = obj::class.isSubclassOf(Reader::class)
fun isSubclassOfWriter(obj: Any): Boolean = obj::class.isSubclassOf(Writer::class)
fun isSubclassOfAnimal(obj: Any): Boolean = obj::class.isSubclassOf(Animal::class)
fun isSubclassOfDog(obj: Any): Boolean = obj::class.isSubclassOf(Dog::class)
fun isSubclassOfAny(obj: Any): Boolean = obj::class.isSubclassOf(Any::class)
fun isSubclassOfCharSequence(obj: Any): Boolean = obj::class.isSubclassOf(CharSequence::class)
fun isSubclassOfList(obj: Any): Boolean = obj::class.isSubclassOf(List::class)
fun isSubclassOfSet(obj: Any): Boolean = obj::class.isSubclassOf(Set::class)
fun isSubclassOfMap(obj: Any): Boolean = obj::class.isSubclassOf(Map::class)
fun isSubclassOfNumber(obj: Any): Boolean = obj::class.isSubclassOf(Number::class)

fun verifyPureKotlinKClassHierarchy(): Boolean {
    return Base::class.isSubclassOf(Any::class) &&
        FinalChild::class.isSubclassOf(Base::class) &&
        !Base::class.isSubclassOf(FinalChild::class) &&
        PrivateChild::class.isSubclassOf(Base::class) &&
        PrivateChild::class.isSubclassOf(Speaker::class) &&
        SingletonChild::class.isSubclassOf(Base::class) &&
        ExportedEnum::class.isSubclassOf(Enum::class) &&
        ExportedEnum::class.isSubclassOf(Animal::class) &&
        Dog::class.isSubclassOf(Animal::class) &&
        !Animal::class.isSubclassOf(Dog::class) &&
        DogBase::class.isSubclassOf(Dog::class) &&
        DogBase::class.isSubclassOf(Animal::class)
}

private class KotlinNSObjectSub : NSObject(), NSCopyingProtocol {
    override fun copyWithZone(zone: kotlinx.cinterop.CPointer<cnames.structs._NSZone>?): Any = this
}

fun checkKotlinAndSwiftSubclassNotNSObject(obj: Any): Boolean {
    return !Any::class.isSubclassOf(NSObject::class) &&
        !Any()::class.isSubclassOf(NSObject::class) &&
        !Base::class.isSubclassOf(NSObject::class) &&
        !obj::class.isSubclassOf(NSObject::class) &&
        !obj::class.isSubclassOf(NSObjectProtocol::class) &&
        !NSObject::class.isInstance(obj) &&
        !NSObjectProtocol::class.isInstance(obj) &&
        (obj::class != NSObject::class) &&
        obj::class.isSubclassOf(Any::class)
}

fun checkExternalObjCAndSwiftNSObjectHierarchy(
    nsDate: NSDate,
    swiftObj1: NSLock,
    swiftObj2: NSLock,
): Boolean {
    val nsLock = NSLock()
    val kotlinNSObj = KotlinNSObjectSub()
    return nsDate::class.isSubclassOf(NSDate::class) &&
        nsDate::class.isSubclassOf(NSObject::class) &&
        nsDate::class.isSubclassOf(NSCopyingProtocol::class) &&
        nsDate::class.isSubclassOf(Any::class) &&
        !nsDate::class.isSubclassOf(NSString::class) &&
        // NSDate() is a class cluster (__NSTaggedDate : NSDate), so NSDate is a superclass of nsDate::class
        !NSDate::class.isSubclassOf(nsDate::class) &&
        NSDate::class.isSubclassOf(NSDate::class) &&
        NSDate::class.isSubclassOf(NSObject()::class) &&
        NSObject::class.isSubclassOf(NSObject()::class) &&
        !NSObject::class.isSubclassOf(nsDate::class) &&
        // NSLock() is a concrete ObjC class whose runtime class is NSLock itself
        (nsLock::class == NSLock::class) &&
        (NSLock::class == nsLock::class) &&
        NSLock::class.isSubclassOf(nsLock::class) &&
        nsLock::class.isSubclassOf(NSLock::class) &&
        NSDate::class.isInstance(nsDate) &&
        NSObject::class.isInstance(nsDate) &&
        NSCopyingProtocol::class.isInstance(nsDate) &&
        swiftObj1::class.isSubclassOf(NSObject::class) &&
        swiftObj1::class.isSubclassOf(NSLock::class) &&
        swiftObj1::class.isSubclassOf(NSLockingProtocol::class) &&
        swiftObj1::class.isSubclassOf(NSCopyingProtocol::class) &&
        swiftObj2::class.isSubclassOf(swiftObj1::class) &&
        !swiftObj1::class.isSubclassOf(swiftObj2::class) &&
        swiftObj1::class.isInstance(swiftObj2) &&
        !swiftObj2::class.isInstance(swiftObj1) &&
        swiftObj2::class.isSubclassOf(NSObject::class) &&
        swiftObj2::class.isSubclassOf(NSLock::class) &&
        swiftObj2::class.isSubclassOf(NSLockingProtocol::class) &&
        swiftObj2::class.isSubclassOf(NSCopyingProtocol::class) &&
        !swiftObj2::class.isSubclassOf(Base::class) &&
        KotlinNSObjectSub::class.isInstance(kotlinNSObj) &&
        KotlinNSObjectSub::class.isSubclassOf(NSObject::class) &&
        KotlinNSObjectSub::class.isSubclassOf(NSCopyingProtocol::class) &&
        KotlinNSObjectSub::class.isSubclassOf(kotlinNSObj::class) &&
        kotlinNSObj::class.isSubclassOf(KotlinNSObjectSub::class) &&
        kotlinNSObj::class.isSubclassOf(NSObject::class) &&
        kotlinNSObj::class.isSubclassOf(NSCopyingProtocol::class)
}

fun checkReexportedCinteropIsSubclassOf(foo: Foo, swiftFoo1: Foo, swiftFoo2: Foo, barOnly: BarProtocol): Boolean {
    return Foo::class.isSubclassOf(BarProtocol::class) &&
        Foo::class.isSubclassOf(foo::class) &&
        foo::class.isSubclassOf(Foo::class) &&
        foo::class.isSubclassOf(BarProtocol::class) &&
        Foo::class.isInstance(foo) &&
        BarProtocol::class.isInstance(foo) &&
        swiftFoo1::class.isSubclassOf(Foo::class) &&
        swiftFoo1::class.isSubclassOf(BarProtocol::class) &&
        !Foo::class.isSubclassOf(swiftFoo1::class) &&
        swiftFoo2::class.isSubclassOf(swiftFoo1::class) &&
        swiftFoo2::class.isSubclassOf(Foo::class) &&
        swiftFoo2::class.isSubclassOf(BarProtocol::class) &&
        !swiftFoo1::class.isSubclassOf(swiftFoo2::class) &&
        swiftFoo1::class.isInstance(swiftFoo2) &&
        !swiftFoo2::class.isInstance(swiftFoo1) &&
        barOnly::class.isSubclassOf(BarProtocol::class) &&
        !barOnly::class.isSubclassOf(Foo::class) &&
        BarProtocol::class.isInstance(barOnly) &&
        !Foo::class.isInstance(barOnly)
}
