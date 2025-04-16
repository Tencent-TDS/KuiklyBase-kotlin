@_implementationOnly import KotlinBridges_anotherFeature
import KotlinRuntime
import KotlinRuntimeSupport
import state

public final class FeatureC: KotlinRuntime.KotlinBase, KotlinRuntimeSupport._KotlinBridged {
    public var state: ExportedKotlinPackages.oh.my.state.State {
        get {
            return ExportedKotlinPackages.oh.my.state.State.__create(externalRCRef: FeatureC_state_get(self.__externalRCRef()))
        }
    }
    public init() {
        precondition(Self.self == anotherFeature.FeatureC.self, "Inheritance from exported kotlin classes is not supported yet")
        let __kt = __root___FeatureC_init_allocate()
        super.init(__externalRCRefUnsafe: __kt, cache: true, substitute: false)
        __root___FeatureC_init_initialize__TypesOfArguments__Swift_UnsafeMutableRawPointer__(__kt)
    }
    package override init(
        __externalRCRefUnsafe: Swift.UnsafeMutableRawPointer?,
        cache: Swift.Bool,
        substitute: Swift.Bool
    ) {
        super.init(__externalRCRefUnsafe: __externalRCRefUnsafe, cache: cache, substitute: substitute)
    }
}
