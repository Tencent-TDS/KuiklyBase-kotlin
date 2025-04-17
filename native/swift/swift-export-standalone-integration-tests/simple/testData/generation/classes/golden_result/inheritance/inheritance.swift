@_implementationOnly import KotlinBridges_inheritance
import KotlinRuntime
import KotlinRuntimeSupport

public final class INHERITANCE_SINGLE_CLASS: inheritance.OPEN_CLASS {
    public var value: Swift.Int32 {
        get {
            return INHERITANCE_SINGLE_CLASS_value_get(self.__externalRCRef())
        }
        set {
            return INHERITANCE_SINGLE_CLASS_value_set__TypesOfArguments__Swift_Int32__(self.__externalRCRef(), newValue)
        }
    }
    public init(
        value: Swift.Int32
    ) {
        precondition(Self.self == inheritance.INHERITANCE_SINGLE_CLASS.self, "Inheritance from exported Kotlin classes is not supported yet: \(String(reflecting: Self.self)) inherits from inheritance.INHERITANCE_SINGLE_CLASS ")
        let __kt = __root___INHERITANCE_SINGLE_CLASS_init_allocate()
        super.init(__externalRCRefUnsafe: __kt, cache: true, substitute: false)
        __root___INHERITANCE_SINGLE_CLASS_init_initialize__TypesOfArguments__Swift_UnsafeMutableRawPointer_Swift_Int32__(__kt, value)
    }
    package override init(
        __externalRCRefUnsafe: Swift.UnsafeMutableRawPointer?,
        cache: Swift.Bool,
        substitute: Swift.Bool
    ) {
        super.init(__externalRCRefUnsafe: __externalRCRefUnsafe, cache: cache, substitute: substitute)
    }
}
public final class OBJECT_WITH_CLASS_INHERITANCE: inheritance.OPEN_CLASS {
    public static var shared: inheritance.OBJECT_WITH_CLASS_INHERITANCE {
        get {
            return inheritance.OBJECT_WITH_CLASS_INHERITANCE.__create(externalRCRef: __root___OBJECT_WITH_CLASS_INHERITANCE_get())
        }
    }
    private override init() {
        fatalError()
    }
    package override init(
        __externalRCRefUnsafe: Swift.UnsafeMutableRawPointer?,
        cache: Swift.Bool,
        substitute: Swift.Bool
    ) {
        super.init(__externalRCRefUnsafe: __externalRCRefUnsafe, cache: cache, substitute: substitute)
    }
}
open class OPEN_CLASS: KotlinRuntime.KotlinBase, KotlinRuntimeSupport._KotlinBridged {
    public init() {
        precondition(Self.self == inheritance.OPEN_CLASS.self, "Inheritance from exported Kotlin classes is not supported yet: \(String(reflecting: Self.self)) inherits from inheritance.OPEN_CLASS ")
        let __kt = __root___OPEN_CLASS_init_allocate()
        super.init(__externalRCRefUnsafe: __kt, cache: true, substitute: false)
        __root___OPEN_CLASS_init_initialize__TypesOfArguments__Swift_UnsafeMutableRawPointer__(__kt)
    }
    package override init(
        __externalRCRefUnsafe: Swift.UnsafeMutableRawPointer?,
        cache: Swift.Bool,
        substitute: Swift.Bool
    ) {
        super.init(__externalRCRefUnsafe: __externalRCRefUnsafe, cache: cache, substitute: substitute)
    }
}
