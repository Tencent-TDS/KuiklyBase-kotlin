@_exported import ExportedKotlinPackages
@_implementationOnly import KotlinBridges_KotlinStdlib
import KotlinRuntime
import KotlinRuntimeSupport

public extension ExportedKotlinPackages.kotlin {
    public final class ByteArray: KotlinRuntime.KotlinBase, KotlinRuntimeSupport._KotlinBridged {
        public var size: Swift.Int32 {
            get {
                return kotlin_ByteArray_size_get(self.__externalRCRef())
            }
        }
        public init(
            size: Swift.Int32
        ) {
            fatalError()
        }
        public init(
            size: Swift.Int32,
            `init`: @escaping (Swift.Int32) -> Swift.Int8
        ) {
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
}
