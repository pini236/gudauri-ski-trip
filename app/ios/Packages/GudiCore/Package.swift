// swift-tools-version:6.0
// The iPhone app's logic, with no UIKit or SwiftUI (docs/ARCHITECTURE.md, section 11): Data (the packed site data)
// and Domain (the calculations that must match the site and Android). Built and tested on its own, also on Linux.
import PackageDescription

let package = Package(
    name: "GudiCore",
    platforms: [.iOS(.v17), .macOS(.v14)],
    products: [
        .library(name: "GudiData", targets: ["GudiData"]),
        .library(name: "GudiDomain", targets: ["GudiDomain"]),
    ],
    targets: [
        .target(name: "GudiData"),
        .target(name: "GudiDomain", dependencies: ["GudiData"]),
        .testTarget(name: "GudiDataTests", dependencies: ["GudiData"]),
        .testTarget(name: "GudiDomainTests", dependencies: ["GudiDomain"]),
    ]
)
