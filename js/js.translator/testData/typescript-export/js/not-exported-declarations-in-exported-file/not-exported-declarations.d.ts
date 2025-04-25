declare namespace JS_TESTS {
    type Nullable<T> = T | null | undefined
    function KtSingleton<T>(): T & (abstract new() => any);
    namespace foo {
        interface ExportedInterface {
            readonly __doNotUseOrImplementIt: {
                readonly "foo.ExportedInterface": unique symbol;
            };
        }
        class OnlyFooParamExported implements foo.ExportedInterface {
            constructor(foo: string);
            get foo(): string;
            readonly __doNotUseOrImplementIt: foo.ExportedInterface["__doNotUseOrImplementIt"];
        }
        /** @deprecated $metadata$ is used for internal purposes, please don't use it in your code, because we can remove it in any moment */
        namespace OnlyFooParamExported.$metadata$ {
            const constructor: abstract new () => OnlyFooParamExported;
        }
    }
}
