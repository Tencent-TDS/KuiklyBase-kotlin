type Nullable<T> = T | null | undefined
declare function KtSingleton<T>(): T & (abstract new() => any);
export declare const value: { get(): number; };
export declare const variable: { get(): number; set(value: number): void; };
export declare class C {
    constructor(x: number);
    get x(): number;
    doubleX(): number;
}
/** @deprecated $metadata$ is used for internal purposes, please don't use it in your code, because we can remove it in any moment */
export declare namespace C.$metadata$ {
    const constructor: abstract new () => C;
}
export declare const O: {
    getInstance(): typeof __NonExistentO;
};
export declare abstract class __NonExistentO extends KtSingleton<__NonExistentO.$metadata$.constructor>() {
    private constructor();
}
/** @deprecated $metadata$ is used for internal purposes, please don't use it in your code, because we can remove it in any moment */
export declare namespace __NonExistentO.$metadata$ {
    abstract class constructor {
        get value(): number;
        private constructor();
    }
}
export declare const Parent: {
    getInstance(): typeof __NonExistentParent;
};
export declare abstract class __NonExistentParent extends KtSingleton<__NonExistentParent.$metadata$.constructor>() {
    private constructor();
}
export declare namespace __NonExistentParent {
    class Nested {
        constructor();
        get value(): number;
    }
    /** @deprecated $metadata$ is used for internal purposes, please don't use it in your code, because we can remove it in any moment */
    namespace Nested.$metadata$ {
        const constructor: abstract new () => Nested;
    }
}
/** @deprecated $metadata$ is used for internal purposes, please don't use it in your code, because we can remove it in any moment */
export declare namespace __NonExistentParent.$metadata$ {
    abstract class constructor {
        get value(): number;
        private constructor();
    }
}
export declare function box(): string;
