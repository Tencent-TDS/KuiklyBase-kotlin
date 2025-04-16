@_exported import ExportedKotlinPackages
@_implementationOnly import KotlinBridges_feature
import KotlinRuntime
import KotlinRuntimeSupport

public extension ExportedKotlinPackages.oh.my.kotlin {
    public final class FeatureA: KotlinRuntime.KotlinBase, KotlinRuntimeSupport._KotlinBridged {
        public init() {
            precondition(Self.self == ExportedKotlinPackages.oh.my.kotlin.FeatureA.self, "Inheritance from exported kotlin classes is not supported yet")
            let __kt = oh_my_kotlin_FeatureA_init_allocate()
            super.init(__externalRCRefUnsafe: __kt, cache: true, substitute: false)
            oh_my_kotlin_FeatureA_init_initialize__TypesOfArguments__Swift_UnsafeMutableRawPointer__(__kt)
        }
        package override init(
            __externalRCRefUnsafe: Swift.UnsafeMutableRawPointer?,
            cache: Swift.Bool,
            substitute: Swift.Bool
        ) {
            super.init(__externalRCRefUnsafe: __externalRCRefUnsafe, cache: cache, substitute: substitute)
        }
    }
    public final class FeatureB: KotlinRuntime.KotlinBase, KotlinRuntimeSupport._KotlinBridged {
        public init() {
            precondition(Self.self == ExportedKotlinPackages.oh.my.kotlin.FeatureB.self, "Inheritance from exported kotlin classes is not supported yet")
            let __kt = oh_my_kotlin_FeatureB_init_allocate()
            super.init(__externalRCRefUnsafe: __kt, cache: true, substitute: false)
            oh_my_kotlin_FeatureB_init_initialize__TypesOfArguments__Swift_UnsafeMutableRawPointer__(__kt)
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
