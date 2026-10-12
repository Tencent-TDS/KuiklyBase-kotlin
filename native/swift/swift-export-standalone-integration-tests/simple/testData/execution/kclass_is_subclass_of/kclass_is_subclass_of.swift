@_spi(kotlinx$cinterop$ExperimentalForeignApi) import Main
import FooKit
import Foundation
import KotlinRuntime
import Testing

@Test
func kClassIsSubclassOfForPureKotlinAndExistentials() throws {
    #expect(verifyPureKotlinKClassHierarchy())

    let base = Base()
    let finalChild = FinalChild()
    let singleton = SingletonChild.shared
    let enumVal = ExportedEnum.ONE
    let privAsBase = createPrivateChildAsBase()
    let privAsSpeaker: KotlinBase = createPrivateChildAsSpeaker()

    #expect(isSubclassOfBase(obj: base))
    #expect(isSubclassOfBase(obj: finalChild))
    #expect(isSubclassOfBase(obj: singleton))
    #expect(isSubclassOfBase(obj: privAsBase))
    #expect(isSubclassOfBase(obj: privAsSpeaker))
    #expect(isSubclassOfSpeaker(obj: privAsBase))
    #expect(isSubclassOfSpeaker(obj: privAsSpeaker))
    #expect(isSameKClass(a: privAsBase, b: privAsSpeaker))

    #expect(isSubclassOfAnimal(obj: enumVal))
    #expect(!isSubclassOfDog(obj: enumVal))
    #expect(isSubclassOfByInstances(sub: finalChild, sup: base))
    #expect(!isSubclassOfByInstances(sub: base, sup: finalChild))

    // Bridged stdlib types passed as Any (_KotlinBridgeable)
    #expect(isSubclassOfCharSequence(obj: "hello"))
    #expect(isSubclassOfNumber(obj: Int32(42)))
    #expect(isSubclassOfList(obj: [Int32(1), Int32(2)]))
    #expect(isSubclassOfSet(obj: Set([Int32(1), Int32(2)])))
    #expect(isSubclassOfMap(obj: ["a": Int32(1)]))
}

@Test
func kClassIsSubclassOfForDirectSwiftSubclass() throws {
    class SwiftSub: Base, Speaker {
        func speak() -> String { "swift-sub" }
        func volume() -> Int32 { 3 }
    }
    class SwiftSibling: Base {}
    class SwiftConcreteAbstractSub: ConcreteAbstractBase {
        override func compute() -> Int32 { 99 }
    }

    let s1 = SwiftSub()
    let s2 = SwiftSub()
    let sibling = SwiftSibling()
    let absImpl = SwiftConcreteAbstractSub()

    #expect(isSubclassOfBase(obj: s1))
    #expect(isSubclassOfSpeaker(obj: s1))
    #expect(isSubclassOfAny(obj: s1))
    #expect(!isSubclassOfReader(obj: s1))
    #expect(!isBaseSubclassOfObj(obj: s1))

    #expect(isBaseInstance(obj: s1))
    #expect(isSpeakerInstance(obj: s1))
    #expect(!isBaseInstance(obj: nil))

    #expect(isSameKClass(a: s1, b: s2))
    #expect(isSubclassOfByInstances(sub: s1, sup: s2))
    #expect(!isSameKClass(a: s1, b: sibling))
    #expect(!isSubclassOfByInstances(sub: s1, sup: sibling))

    #expect(isSubclassOfAbstractBase(obj: absImpl))
    #expect(isSubclassOfReader(obj: absImpl))
    #expect(!isSubclassOfSpeaker(obj: absImpl))
}

@Test
func kClassIsSubclassOfForMultiLevelSwiftSubclass() throws {
    class SwiftLevel1: Base, Speaker {
        override func greet() -> String { "greet-1" }
        func speak() -> String { "speak-1" }
        func volume() -> Int32 { 10 }
    }
    class SwiftLevel2: SwiftLevel1, Reader {
        override func greet() -> String { "greet-2" }
        override func speak() -> String { "speak-2" }
        func read() -> String { "read-2" }
    }
    class SwiftLevel3: SwiftLevel2, Writer {
        override func speak() -> String { "speak-3" }
        func write(s: String) -> Int32 { Int32(s.count) }
    }

    let l3 = SwiftLevel3()
    let l2 = SwiftLevel2()
    let l1 = SwiftLevel1()
    let base = Base()

    // Class hierarchy checks
    #expect(isSubclassOfByInstances(sub: l1, sup: base))
    #expect(isSubclassOfByInstances(sub: l2, sup: base))
    #expect(isSubclassOfByInstances(sub: l3, sup: base))
    #expect(isSubclassOfByInstances(sub: l2, sup: l1))
    #expect(isSubclassOfByInstances(sub: l3, sup: l2))
    #expect(isSubclassOfByInstances(sub: l3, sup: l1))

    #expect(!isSubclassOfByInstances(sub: l1, sup: l2))
    #expect(!isSubclassOfByInstances(sub: l2, sup: l3))
    #expect(!isSubclassOfByInstances(sub: l1, sup: l3))

    // isInstance checks across multi-level Swift subclasses
    #expect(isInstanceByInstances(receiver: l1, value: l2))
    #expect(isInstanceByInstances(receiver: l1, value: l3))
    #expect(isInstanceByInstances(receiver: l2, value: l3))
    #expect(!isInstanceByInstances(receiver: l2, value: l1))
    #expect(!isInstanceByInstances(receiver: l3, value: l2))

    // Interface inheritance across multi-level Swift subclasses
    #expect(isSubclassOfSpeaker(obj: l1))
    #expect(isSubclassOfSpeaker(obj: l2))
    #expect(isSubclassOfSpeaker(obj: l3))
    #expect(isSpeakerInstance(obj: l2))
    #expect(isSpeakerInstance(obj: l3))

    #expect(!isSubclassOfReader(obj: l1))
    #expect(isSubclassOfReader(obj: l2))
    #expect(isSubclassOfReader(obj: l3))

    #expect(!isSubclassOfWriter(obj: l1))
    #expect(!isSubclassOfWriter(obj: l2))
    #expect(isSubclassOfWriter(obj: l3))
}

@Test
func kClassIsSubclassOfWithExternalFoundationTypes() throws {
    class SwiftSubOfKotlin: Base {}

    class SwiftLock1: NSLock, NSCopying, @unchecked Sendable {
        func copy(with zone: NSZone? = nil) -> Any { self }
    }
    class SwiftLock2: SwiftLock1, @unchecked Sendable {}

    // Trigger existential wrapper creation first (populates objCClass on theAnyTypeInfo and PrivateChild)
    let privIface: KotlinBase = createPrivateChildAsSpeaker()
    let exportedKotlin = Base()
    let swiftSubKotlin = SwiftSubOfKotlin()

    try #require(checkKotlinAndSwiftSubclassNotNSObject(obj: privIface))
    try #require(checkKotlinAndSwiftSubclassNotNSObject(obj: exportedKotlin))
    try #require(checkKotlinAndSwiftSubclassNotNSObject(obj: swiftSubKotlin))

    let date = NSDate()
    let obj1 = SwiftLock1()
    let obj2 = SwiftLock2()
    try #require(checkExternalObjCAndSwiftNSObjectHierarchy(nsDate: date, swiftObj1: obj1, swiftObj2: obj2))
}

@Test
func kClassIsSubclassOfWithReexportedCinteropTypes() {
    class SwiftFoo1: Foo {}
    class SwiftFoo2: SwiftFoo1 {}
    class SwiftBarOnly: NSObject, Bar {
        func barValue() -> Int32 { 99 }
    }

    let foo = Foo(payload: 7)!
    let sf1 = SwiftFoo1(payload: 10)!
    let sf2 = SwiftFoo2(payload: 20)!
    let barOnly = SwiftBarOnly()
    #expect(checkReexportedCinteropIsSubclassOf(foo: foo, swiftFoo1: sf1, swiftFoo2: sf2, barOnly: barOnly))
}
