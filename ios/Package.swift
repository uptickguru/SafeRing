// swift-tools-version: 5.7
// SafeRing iOS — SPM dependencies
// No external dependency manager; only Apple frameworks used.

import PackageDescription

let package = Package(
    name: "SafeRing",
    defaultLocalization: "en",
    platforms: [
        .iOS(.v16)
    ],
    products: [
        .library(
            name: "SafeRing",
            targets: ["SafeRing"]
        ),
        .library(
            name: "CallDirectoryHandler",
            targets: ["CallDirectoryHandler"]
        )
    ],
    dependencies: [
        // No external dependencies needed.
        // All integration uses native Apple frameworks:
        // - SwiftUI, SwiftData (First-party)
        // - CallKit, CoreML, NaturalLanguage (First-party)
        // - BackgroundTasks, Network (First-party)
    ],
    targets: [
        .target(
            name: "SafeRing",
            dependencies: [],
            path: "SafeRing",
            resources: [
                .process("Assets.xcassets")
            ]
        ),
        .target(
            name: "CallDirectoryHandler",
            dependencies: [],
            path: "CallDirectoryHandler"
        )
    ]
)
