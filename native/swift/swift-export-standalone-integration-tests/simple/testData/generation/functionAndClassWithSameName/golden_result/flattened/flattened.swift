@_exported import ExportedKotlinPackages
@_implementationOnly import KotlinBridges_flattened
import KotlinRuntime
import KotlinRuntimeSupport

public typealias FlattenedPackageClass = ExportedKotlinPackages.flattenedPackage.FlattenedPackageClass
public func flattenedPackageClass(
    i: Swift.Int32
) -> ExportedKotlinPackages.flattenedPackage.FlattenedPackageClass {
    ExportedKotlinPackages.flattenedPackage.flattenedPackageClass(i: i)
}
public extension ExportedKotlinPackages.flattenedPackage {
    public final class FlattenedPackageClass: KotlinRuntime.KotlinBase, KotlinRuntimeSupport._KotlinBridged {
        public init() {
            precondition(Self.self == ExportedKotlinPackages.flattenedPackage.FlattenedPackageClass.self, "Inheritance from exported kotlin classes is not supported yet")
            let __kt = flattenedPackage_FlattenedPackageClass_init_allocate()
            super.init(__externalRCRefUnsafe: __kt, cache: true, substitute: false)
            flattenedPackage_FlattenedPackageClass_init_initialize__TypesOfArguments__Swift_UnsafeMutableRawPointer__(__kt)
        }
        package override init(
            __externalRCRefUnsafe: Swift.UnsafeMutableRawPointer?,
            cache: Swift.Bool,
            substitute: Swift.Bool
        ) {
            super.init(__externalRCRefUnsafe: __externalRCRefUnsafe, cache: cache, substitute: substitute)
        }
    }
    public static func flattenedPackageClass(
        i: Swift.Int32
    ) -> ExportedKotlinPackages.flattenedPackage.FlattenedPackageClass {
        return ExportedKotlinPackages.flattenedPackage.FlattenedPackageClass.__create(externalRCRef: flattenedPackage_FlattenedPackageClass__TypesOfArguments__Swift_Int32__(i))
    }
}
