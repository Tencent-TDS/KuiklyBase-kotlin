#import <Foundation/NSArray.h>
#import <Foundation/NSDictionary.h>
#import <Foundation/NSError.h>
#import <Foundation/NSObject.h>
#import <Foundation/NSSet.h>
#import <Foundation/NSString.h>
#import <Foundation/NSValue.h>

@class Foo;

NS_ASSUME_NONNULL_BEGIN
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wunknown-warning-option"
#pragma clang diagnostic ignored "-Wincompatible-property-type"
#pragma clang diagnostic ignored "-Wnullability"

#pragma push_macro("_Nullable_result")
#if !__has_feature(nullability_nullable_result)
#undef _Nullable_result
#define _Nullable_result _Nullable
#endif

__attribute__((objc_subclassing_restricted))
@interface Foo : Base
- (instancetype)init __attribute__((swift_name("init()"))) __attribute__((objc_designated_initializer));
+ (instancetype)new __attribute__((availability(swift, unavailable, message="use object initializers instead")));
- (void)alloc __attribute__((swift_name("alloc()"))) __attribute__((objc_method_family(none)));
- (void)allocWithX:(int32_t)x __attribute__((swift_name("allocWith(x:)"))) __attribute__((objc_method_family(none)));
- (void)allocation __attribute__((swift_name("allocation()")));
- (Foo *)copy __attribute__((swift_name("copy()"))) __attribute__((objc_method_family(none)));
- (Foo *)copyWithX:(int32_t)x __attribute__((swift_name("copyWith(x:)"))) __attribute__((objc_method_family(none)));
- (Foo *)copymachine __attribute__((swift_name("copymachine()")));
- (Foo *)doInit __attribute__((swift_name("doInit()")));
- (Foo *)initOtherX:(int32_t)x __attribute__((swift_name("initOther(x:)"))) __attribute__((objc_method_family(none)));
- (Foo *)doInitWithX:(int32_t)x __attribute__((swift_name("doInitWith(x:)")));
- (Foo *)initializer __attribute__((swift_name("initializer()")));
- (Foo *)mutableCopy __attribute__((swift_name("mutableCopy()"))) __attribute__((objc_method_family(none)));
- (Foo *)mutableCopyWithX:(int32_t)x __attribute__((swift_name("mutableCopyWith(x:)"))) __attribute__((objc_method_family(none)));
- (Foo *)mutableCopymachine __attribute__((swift_name("mutableCopymachine()")));
- (Foo *)new __attribute__((swift_name("new()"))) __attribute__((objc_method_family(none)));
- (Foo *)newWithX:(int32_t)x __attribute__((swift_name("newWith(x:)"))) __attribute__((objc_method_family(none)));
- (Foo *)newer __attribute__((swift_name("newer()")));
@end

#pragma pop_macro("_Nullable_result")
#pragma clang diagnostic pop
NS_ASSUME_NONNULL_END
