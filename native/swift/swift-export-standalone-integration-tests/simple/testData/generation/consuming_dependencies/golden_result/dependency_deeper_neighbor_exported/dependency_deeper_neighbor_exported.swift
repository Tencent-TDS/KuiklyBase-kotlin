@_exported import ExportedKotlinPackages
@_implementationOnly import KotlinBridges_dependency_deeper_neighbor_exported
import KotlinRuntime
import KotlinRuntimeSupport

public extension ExportedKotlinPackages.dependency.four {
    public final class AnotherBar: KotlinRuntime.KotlinBase, KotlinRuntimeSupport._KotlinBridged {
        public init() {
            precondition(Self.self == ExportedKotlinPackages.dependency.four.AnotherBar.self, "Inheritance from exported Kotlin classes is not supported yet: \(String(reflecting: Self.self)) inherits from ExportedKotlinPackages.dependency.four.AnotherBar ")
            let __kt = dependency_four_AnotherBar_init_allocate()
            super.init(__externalRCRefUnsafe: __kt, cache: true, substitute: false)
            dependency_four_AnotherBar_init_initialize__TypesOfArguments__Swift_UnsafeMutableRawPointer__(__kt)
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
