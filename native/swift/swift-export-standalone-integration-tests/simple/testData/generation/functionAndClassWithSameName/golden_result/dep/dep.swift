@_exported import ExportedKotlinPackages
@_implementationOnly import KotlinBridges_dep
import KotlinRuntime
import KotlinRuntimeSupport

public extension ExportedKotlinPackages.test.factory.modules {
    public final class ClassFromDependency: KotlinRuntime.KotlinBase, KotlinRuntimeSupport._KotlinBridged {
        public init() {
            precondition(Self.self == ExportedKotlinPackages.test.factory.modules.ClassFromDependency.self, "Inheritance from exported Kotlin classes is not supported yet: \(String(reflecting: Self.self)) inherits from ExportedKotlinPackages.test.factory.modules.ClassFromDependency ")
            let __kt = test_factory_modules_ClassFromDependency_init_allocate()
            super.init(__externalRCRefUnsafe: __kt, cache: true, substitute: false)
            test_factory_modules_ClassFromDependency_init_initialize__TypesOfArguments__Swift_UnsafeMutableRawPointer__(__kt)
        }
        package override init(
            __externalRCRefUnsafe: Swift.UnsafeMutableRawPointer?,
            cache: Swift.Bool,
            substitute: Swift.Bool
        ) {
            super.init(__externalRCRefUnsafe: __externalRCRefUnsafe, cache: cache, substitute: substitute)
        }
    }
}
