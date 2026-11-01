// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "LinkMobileiOS",
    platforms: [
        .iOS(.v16)
    ],
    products: [
        .library(
            name: "LinkMobile",
            targets: ["LinkMobile", "LinkMobileTypes", "bridge"]
        ),
        .library(
            name: "LinkMobileTypes",
            targets: ["LinkMobileTypes"]
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
            name: "LinkMobile",
            url: "https://api.github.com/repos/last-lock/link-mobile-sdk-prod/releases/assets/698918906.zip",
            checksum: "b33abb1e202a271718b8b23451dc40c4f4c1244e24c66baa792f939bc123e5a4"
        ),
        .binaryTarget(
            name: "bridge",
            url: "https://api.github.com/repos/last-lock/link-mobile-sdk-prod/releases/assets/698918905.zip",
            checksum: "0c68b921bb04813f47cd5dde2057acb8a2b87f9c95c48c214516115946c128b3"
        ),
        .target(
            name: "LinkMobileTypes",
            dependencies: [
                .product(name: "GRPC", package: "grpc-swift"),
                .product(name: "SwiftProtobuf", package: "swift-protobuf"),
                .product(name: "NIO", package: "swift-nio"),
                .product(name: "NIOCore", package: "swift-nio"),
                .product(name: "NIOConcurrencyHelpers", package: "swift-nio"),
                .product(name: "NIOHTTP2", package: "swift-nio-http2"),
                .product(name: "Atomics", package: "swift-atomics")
            ],
            path: "Sources/LinkMobileTypes"
        )
    ]
)
