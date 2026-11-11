// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "LastLockMobileSdk",
    platforms: [
        .iOS(.v16)
    ],
    products: [
        .library(
            name: "LastLockMobileSdk",
            targets: ["LastLockMobileSdk", "LastLockMobileSdkTypes", "LastLockMobileSdkCore"]
        ),
        .library(
            name: "LastLockMobileSdkTypes",
            targets: ["LastLockMobileSdkTypes"]
        )
    ],
    dependencies: [
        .package(url: "https://github.com/grpc/grpc-swift.git", from: "1.24.2"),
        .package(url: "https://github.com/apple/swift-protobuf", from: "1.28.2"),
        .package(url: "https://github.com/apple/swift-nio.git", from: "2.60.0"),
        .package(url: "https://github.com/apple/swift-nio-http2.git", from: "1.27.0"),
        .package(url: "https://github.com/apple/swift-atomics.git", from: "1.0.0")
    ],
    targets: [
        .binaryTarget(
            name: "LastLockMobileSdk",
            url: "https://api.github.com/repos/last-lock/link-mobile-sdk-prod/releases/assets/737229454.zip",
            checksum: "ca2cd973cbd1a770cc10bba19118025bd5db87f829c97b0c61b1eb3c9b7b3dbc"
        ),
        .binaryTarget(
            name: "LastLockMobileSdkCore",
            url: "https://api.github.com/repos/last-lock/link-mobile-sdk-prod/releases/assets/737229451.zip",
            checksum: "dc752ebadac46ce2b050c97ec6ee05da782b65143c1faf8d6fc51b65d49d0c71"
        ),
        .target(
            name: "LastLockMobileSdkTypes",
            dependencies: [
                .product(name: "GRPC", package: "grpc-swift"),
                .product(name: "SwiftProtobuf", package: "swift-protobuf"),
                .product(name: "NIO", package: "swift-nio"),
                .product(name: "NIOCore", package: "swift-nio"),
                .product(name: "NIOConcurrencyHelpers", package: "swift-nio"),
                .product(name: "NIOHTTP2", package: "swift-nio-http2"),
                .product(name: "Atomics", package: "swift-atomics")
            ],
            path: "Sources/LastLockMobileSdkTypes"
        )
    ]
)
