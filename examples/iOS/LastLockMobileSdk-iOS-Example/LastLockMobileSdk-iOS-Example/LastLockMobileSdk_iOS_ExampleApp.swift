//
//  LastLockMobileSdk_iOS_ExampleApp.swift
//  LastLockMobileSdk-iOS-Example
//
//  Created by Nihir Singh on 10/27/25.
//

import SwiftUI

@main
struct LastLockMobileSdk_iOS_ExampleApp: App {
    let persistenceController = PersistenceController.shared

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environment(\.managedObjectContext, persistenceController.container.viewContext)
        }
    }
}
