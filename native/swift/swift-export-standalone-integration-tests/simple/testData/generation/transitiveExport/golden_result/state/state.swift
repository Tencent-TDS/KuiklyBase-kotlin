@_exported import ExportedKotlinPackages
@_implementationOnly import KotlinBridges_state
import KotlinRuntime
import KotlinRuntimeSupport
import KotlinStdlib

public extension ExportedKotlinPackages.oh.my.state {
    public final class State: KotlinRuntime.KotlinBase, KotlinRuntimeSupport._KotlinBridged {
        public var bytes: ExportedKotlinPackages.kotlin.ByteArray? {
            get {
                return { switch oh_my_state_State_bytes_get(self.__externalRCRef()) { case nil: .none; case let res: ExportedKotlinPackages.kotlin.ByteArray.__create(externalRCRef: res); } }()
            }
        }
        public init(
            bytes: ExportedKotlinPackages.kotlin.ByteArray?
        ) {
            precondition(Self.self == ExportedKotlinPackages.oh.my.state.State.self, "Inheritance from exported kotlin classes is not supported yet")
            let __kt = oh_my_state_State_init_allocate()
            super.init(__externalRCRefUnsafe: __kt, cache: true, substitute: false)
            oh_my_state_State_init_initialize__TypesOfArguments__Swift_UnsafeMutableRawPointer_Swift_Optional_ExportedKotlinPackages_kotlin_ByteArray___(__kt, bytes.map { it in it.__externalRCRef() } ?? nil)
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
