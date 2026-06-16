import UIKit

private let lockPlayerToLandscapeNotification = Notification.Name("MuvioPlayerLockLandscape")
private let unlockPlayerOrientationNotification = Notification.Name("MuvioPlayerUnlockOrientation")

final class OrientationLockAppDelegate: NSObject, UIApplicationDelegate {
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        OrientationLockCoordinator.shared.start()
        return true
    }

    func application(
        _ application: UIApplication,
        supportedInterfaceOrientationsFor window: UIWindow?
    ) -> UIInterfaceOrientationMask {
        OrientationLockCoordinator.shared.supportedOrientations
    }
}

final class OrientationLockCoordinator {
    static let shared = OrientationLockCoordinator()

    private(set) var supportedOrientations: UIInterfaceOrientationMask = .allButUpsideDown
    private var observers: [NSObjectProtocol] = []

    private init() {}

    func start() {
        guard observers.isEmpty else { return }
        let center = NotificationCenter.default
        observers.append(
            center.addObserver(forName: lockPlayerToLandscapeNotification, object: nil, queue: .main) { [weak self] _ in
                self?.setLandscapeLock(enabled: true)
            }
        )
        observers.append(
            center.addObserver(forName: unlockPlayerOrientationNotification, object: nil, queue: .main) { [weak self] _ in
                self?.setLandscapeLock(enabled: false)
            }
        )
    }

    private func setLandscapeLock(enabled: Bool) {
        supportedOrientations = enabled ? .landscape : .allButUpsideDown
        requestOrientationUpdate(forceRotate: enabled)
    }

    private func requestOrientationUpdate(forceRotate: Bool) {
        if #available(iOS 16.0, *) {
            let mask = supportedOrientations
            let preferences = UIWindowScene.GeometryPreferences.iOS(interfaceOrientations: mask)
            UIApplication.shared.connectedScenes
                .compactMap { $0 as? UIWindowScene }
                .forEach { scene in
                    scene.requestGeometryUpdate(preferences) { _ in }
                }
            UIApplication.shared.connectedScenes
                .compactMap { $0 as? UIWindowScene }
                .flatMap(\.windows)
                .forEach { $0.rootViewController?.setNeedsUpdateOfSupportedInterfaceOrientations() }
        } else {
            if forceRotate {
                UIDevice.current.setValue(UIInterfaceOrientation.landscapeRight.rawValue, forKey: "orientation")
            }
            UIViewController.attemptRotationToDeviceOrientation()
        }
    }
}

final class RootComposeViewController: UIViewController {
    private let contentController: UIViewController

    init(contentController: UIViewController) {
        self.contentController = contentController
        super.init(nibName: nil, bundle: nil)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) { fatalError() }

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .black

        addChild(contentController)
        contentController.view.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(contentController.view)
        NSLayoutConstraint.activate([
            contentController.view.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            contentController.view.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            contentController.view.topAnchor.constraint(equalTo: view.topAnchor),
            contentController.view.bottomAnchor.constraint(equalTo: view.bottomAnchor),
        ])
        contentController.didMove(toParent: self)
    }

    override var childForHomeIndicatorAutoHidden: UIViewController? { contentController }
    override var childForScreenEdgesDeferringSystemGestures: UIViewController? { contentController }
    override var childForStatusBarHidden: UIViewController? { contentController }

    func refreshImmersiveSystemUI() {
        setNeedsUpdateOfHomeIndicatorAutoHidden()
        setNeedsUpdateOfScreenEdgesDeferringSystemGestures()
        setNeedsStatusBarAppearanceUpdate()
    }
}
