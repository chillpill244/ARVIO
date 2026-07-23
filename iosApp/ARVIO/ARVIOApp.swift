import SwiftUI

@main
struct ARVIOApp: App {
    @StateObject private var appState = AppState()

    var body: some Scene {
        WindowGroup {
            ComposeView()
                .ignoresSafeArea()
        }
    }
}
