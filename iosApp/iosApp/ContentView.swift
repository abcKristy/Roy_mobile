import UIKit
import SwiftUI
import Shared

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Self.Context) -> UIViewController {
        #if DEBUG
        let origin = ProcessInfo.processInfo.environment["ROY_API_ORIGIN"] ?? "http://127.0.0.1:18080"
        return MainViewControllerKt.MainViewController(apiOrigin: origin)
        #else
        return MainViewControllerKt.MainViewController(apiOrigin: nil)
        #endif
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Self.Context) {}
}

struct ContentView: View {
    var body: some View {
        ComposeView()
            .ignoresSafeArea()
    }
}
