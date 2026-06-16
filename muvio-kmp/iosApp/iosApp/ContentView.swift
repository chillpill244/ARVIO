import UIKit
import SwiftUI
import Shared

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        // Register MPV player bridge before Compose initialises
        MuvioPlayerRegistration.register()

        let controller = MainViewControllerKt.MainViewController()
        controller.view.backgroundColor = UIColor.black
        return RootComposeViewController(contentController: controller)
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    var body: some View {
        ComposeView()
            .ignoresSafeArea()
    }
}
