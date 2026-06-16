import Foundation
import UIKit
import Libmpv
import Shared

// MARK: - Bridge implementation (conforms to Kotlin MuvioPlayerBridge protocol)

final class MPVPlayerBridgeImpl: NSObject, MuvioPlayerBridge {

    private var playerVC: MPVPlayerViewController?

    func createPlayerViewController() -> UIViewController {
        let vc = MPVPlayerViewController()
        self.playerVC = vc
        return vc
    }

    func loadFileWithAudio(videoUrl: String, audioUrl: String?, headersJson: String?) {
        playerVC?.loadFile(
            videoUrl,
            audioUrl: audioUrl,
            requestHeaders: parseRequestHeaders(headersJson)
        )
    }

    func play() { playerVC?.playPlayback() }
    func pause() { playerVC?.pausePlayback() }
    func seekTo(positionMs: Int64) { playerVC?.seekToMs(positionMs) }

    func getIsLoading() -> Bool { playerVC?.refreshPlaybackState(); return playerVC?.isPlayerLoading ?? true }
    func getIsPlaying() -> Bool { playerVC?.isPlayerPlaying ?? false }
    func getIsEnded() -> Bool { playerVC?.isPlayerEnded ?? false }
    func getDurationMs() -> Int64 { playerVC?.durationMs ?? 0 }
    func getPositionMs() -> Int64 { playerVC?.positionMs ?? 0 }
    func getBufferedMs() -> Int64 { playerVC?.bufferedMs ?? 0 }
    func getErrorMessage() -> String { playerVC?.currentErrorMessage ?? "" }

    func destroy() {
        playerVC?.destroyPlayer()
        playerVC = nil
    }

    private func parseRequestHeaders(_ headersJson: String?) -> [String: String] {
        guard
            let headersJson,
            !headersJson.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
            let data = headersJson.data(using: .utf8),
            let raw = try? JSONSerialization.jsonObject(with: data) as? [String: Any]
        else { return [:] }

        var headers: [String: String] = [:]
        raw.forEach { key, value in
            if let v = value as? String { headers[key] = v }
        }
        return headers
    }
}

// MARK: - Track info

struct TrackInfo {
    let index: Int
    let id: Int
    let type: String
    let title: String
    let lang: String
    let selected: Bool
}

private struct PendingLoadRequest {
    let urlString: String
    let audioUrl: String?
    let requestHeaders: [String: String]
    let queuedAtUptime: TimeInterval
}

// MARK: - MPV player view controller

final class MPVPlayerViewController: UIViewController {

    private static let defaultAudioOutput = "avfoundation,audiounit,"

    private let errorStateLock = NSLock()
    private var metalLayer = MetalLayer()
    private var lastAppliedDrawableSize: CGSize = .zero
    private var pendingLoadRequest: PendingLoadRequest?
    private var pendingLoadRetryWorkItem: DispatchWorkItem?
    private var mpv: OpaquePointer?
    private lazy var eventQueue = DispatchQueue(label: "mpv-events", qos: .userInitiated)
    private var recentPlaybackLogs: [String] = []
    private var activeRequestHeaders: [String: String] = [:]

    var audioTracks: [TrackInfo] = []
    var subtitleTracks: [TrackInfo] = []

    var isPlayerLoading: Bool = true
    var isPlayerPlaying: Bool = false
    var isPlayerEnded: Bool = false
    var durationMs: Int64 = 0
    var positionMs: Int64 = 0
    var bufferedMs: Int64 = 0
    var currentSpeed: Float = 1.0
    var currentErrorMessage: String {
        errorStateLock.lock()
        defer { errorStateLock.unlock() }
        return _currentErrorMessage ?? ""
    }
    private var _currentErrorMessage: String?

    override var prefersHomeIndicatorAutoHidden: Bool { true }
    override var preferredScreenEdgesDeferringSystemGestures: UIRectEdge { [.bottom, .left, .right] }
    override var prefersStatusBarHidden: Bool { true }
    override var preferredStatusBarUpdateAnimation: UIStatusBarAnimation { .fade }

    // MARK: Lifecycle

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .black
        view.layer.masksToBounds = true

        metalLayer.contentsGravity = .resize
        metalLayer.contentsScale = view.window?.screen.nativeScale ?? UIScreen.main.nativeScale
        metalLayer.framebufferOnly = true
        metalLayer.backgroundColor = UIColor.black.cgColor
        metalLayer.wantsExtendedDynamicRangeContent = true
        view.layer.addSublayer(metalLayer)
        layoutMetalLayer()

        setupMpv()
        setupNotifications()
        refreshImmersiveSystemUI()
    }

    override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)
        refreshImmersiveSystemUI()
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        layoutMetalLayer()
        attemptStartPendingLoad()
    }

    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        refreshImmersiveSystemUI()
        attemptStartPendingLoad()
    }

    override func viewSafeAreaInsetsDidChange() {
        super.viewSafeAreaInsetsDidChange()
        layoutMetalLayer()
        refreshImmersiveSystemUI()
        attemptStartPendingLoad()
    }

    private func layoutMetalLayer() {
        let bounds = view.bounds
        guard bounds.width > 1, bounds.height > 1 else { return }

        let scale = view.window?.screen.nativeScale ?? UIScreen.main.nativeScale
        let drawableSize = CGSize(
            width: (bounds.width * scale).rounded(.toNearestOrAwayFromZero),
            height: (bounds.height * scale).rounded(.toNearestOrAwayFromZero)
        )

        CATransaction.begin()
        CATransaction.setDisableActions(true)
        metalLayer.contentsScale = scale
        metalLayer.frame = CGRect(origin: .zero, size: bounds.size)
        if drawableSize != lastAppliedDrawableSize {
            metalLayer.drawableSize = drawableSize
            lastAppliedDrawableSize = drawableSize
        }
        CATransaction.commit()
    }

    // MARK: MPV setup

    private func setupMpv() {
        mpv = mpv_create()
        guard mpv != nil else { return }

        checkError(mpv_request_log_messages(mpv, "warn"))
        checkError(mpv_set_option(mpv, "wid", MPV_FORMAT_INT64, &metalLayer))
        checkError(mpv_set_option_string(mpv, "vo", "gpu-next"))
        checkError(mpv_set_option_string(mpv, "gpu-api", "vulkan"))
        checkError(mpv_set_option_string(mpv, "gpu-context", "moltenvk"))
        checkError(mpv_set_option_string(mpv, "hwdec", "videotoolbox"))
        checkError(mpv_set_option_string(mpv, "ao", Self.defaultAudioOutput))
        checkError(mpv_set_option_string(mpv, "audio-channels", "auto"))
        checkError(mpv_set_option_string(mpv, "audio-fallback-to-null", "yes"))
        checkError(mpv_set_option_string(mpv, "vulkan-swap-mode", "fifo"))
        checkError(mpv_set_option_string(mpv, "vulkan-queue-count", "1"))
        checkError(mpv_set_option_string(mpv, "vulkan-async-compute", "no"))
        checkError(mpv_set_option_string(mpv, "vulkan-async-transfer", "no"))
        checkError(mpv_set_option_string(mpv, "vulkan-disable-interop", "yes"))
        checkError(mpv_set_option_string(mpv, "video-rotate", "no"))
        checkError(mpv_set_option_string(mpv, "subs-match-os-language", "yes"))
        checkError(mpv_set_option_string(mpv, "subs-fallback", "yes"))
        checkError(mpv_set_option_string(mpv, "keep-open", "yes"))
        checkError(mpv_set_option_string(mpv, "target-colorspace-hint", "yes"))
        checkError(mpv_set_option_string(mpv, "tone-mapping", "auto"))
        checkError(mpv_set_option_string(mpv, "hdr-compute-peak", "yes"))
        checkError(mpv_initialize(mpv))

        mpv_observe_property(mpv, 0, "pause", MPV_FORMAT_FLAG)
        mpv_observe_property(mpv, 0, "paused-for-cache", MPV_FORMAT_FLAG)
        mpv_observe_property(mpv, 0, "core-idle", MPV_FORMAT_FLAG)
        mpv_observe_property(mpv, 0, "eof-reached", MPV_FORMAT_FLAG)
        mpv_observe_property(mpv, 0, "seeking", MPV_FORMAT_FLAG)
        mpv_observe_property(mpv, 0, "track-list/count", MPV_FORMAT_INT64)

        mpv_set_wakeup_callback(mpv, { ctx in
            let vc = unsafeBitCast(ctx, to: MPVPlayerViewController.self)
            vc.readEvents()
        }, UnsafeMutableRawPointer(Unmanaged.passUnretained(self).toOpaque()))
    }

    private func setupNotifications() {
        NotificationCenter.default.addObserver(self, selector: #selector(enterBackground),
                                               name: UIApplication.didEnterBackgroundNotification, object: nil)
        NotificationCenter.default.addObserver(self, selector: #selector(enterForeground),
                                               name: UIApplication.willEnterForegroundNotification, object: nil)
    }

    @objc private func enterBackground() {
        guard mpv != nil else { return }
        pausePlayback()
        setStringProperty("vid", "no")
    }

    @objc private func enterForeground() {
        guard mpv != nil else { return }
        setStringProperty("vid", "auto")
        playPlayback()
    }

    // MARK: Playback API

    func loadFile(_ urlString: String, audioUrl: String? = nil, requestHeaders: [String: String] = [:]) {
        let request = PendingLoadRequest(
            urlString: urlString,
            audioUrl: audioUrl,
            requestHeaders: requestHeaders,
            queuedAtUptime: ProcessInfo.processInfo.systemUptime
        )
        if Thread.isMainThread {
            queueLoad(request)
        } else {
            DispatchQueue.main.async { [weak self] in self?.queueLoad(request) }
        }
    }

    private func queueLoad(_ request: PendingLoadRequest) {
        pendingLoadRequest = request
        attemptStartPendingLoad()
    }

    func attemptStartPendingLoad() {
        guard let request = pendingLoadRequest, mpv != nil else { return }
        layoutMetalLayer()
        guard isViewportReadyForPlayback(queuedAtUptime: request.queuedAtUptime) else {
            schedulePendingLoadRetry()
            return
        }
        pendingLoadRequest = nil
        pendingLoadRetryWorkItem?.cancel()
        pendingLoadRetryWorkItem = nil
        startLoad(request)
    }

    private func startLoad(_ request: PendingLoadRequest) {
        guard mpv != nil else { return }
        layoutMetalLayer()
        clearPlaybackError()
        let sanitized = sanitizeRequestHeaders(request.requestHeaders)
        activeRequestHeaders = sanitized
        applyRequestHeaders(sanitized)
        isPlayerLoading = true
        isPlayerEnded = false
        command("loadfile", args: [request.urlString, "replace"])
        if let audioUrl = request.audioUrl, !audioUrl.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.2) { [weak self] in
                self?.command("audio-add", args: [audioUrl, "select"], checkForErrors: false)
            }
        }
    }

    private func isViewportReadyForPlayback(queuedAtUptime: TimeInterval) -> Bool {
        guard isViewLoaded, view.window != nil else { return false }
        let bounds = view.bounds
        guard bounds.width > 1, bounds.height > 1 else { return false }
        if bounds.width >= bounds.height { return true }
        return ProcessInfo.processInfo.systemUptime - queuedAtUptime >= 0.9
    }

    private func schedulePendingLoadRetry() {
        guard pendingLoadRetryWorkItem == nil else { return }
        let workItem = DispatchWorkItem { [weak self] in
            self?.pendingLoadRetryWorkItem = nil
            self?.attemptStartPendingLoad()
        }
        pendingLoadRetryWorkItem = workItem
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.05, execute: workItem)
    }

    func playPlayback() {
        guard mpv != nil else { return }
        setFlag("pause", false)
    }

    func pausePlayback() {
        guard mpv != nil else { return }
        setFlag("pause", true)
    }

    func seekToMs(_ ms: Int64) {
        guard mpv != nil else { return }
        let seconds = Double(ms) / 1000.0
        command("seek", args: [String(format: "%.3f", seconds), "absolute"])
    }

    func destroyPlayer() {
        NotificationCenter.default.removeObserver(self)
        pendingLoadRetryWorkItem?.cancel()
        pendingLoadRetryWorkItem = nil
        pendingLoadRequest = nil
        clearPlaybackError()
        guard let ctx = mpv else { return }
        mpv = nil
        mpv_terminate_destroy(ctx)
    }

    // MARK: State

    func refreshPlaybackState() {
        guard mpv != nil else { return }
        let duration = getDouble("duration")
        let position = getDouble("time-pos")
        let cached = getDouble("demuxer-cache-time")
        let paused = getFlag("pause")
        let eofReached = getFlag("eof-reached")
        let idle = getFlag("core-idle")
        let seeking = getFlag("seeking")
        let bufferingCache = getFlag("paused-for-cache")

        isPlayerLoading = (idle && !paused && !eofReached) || seeking || bufferingCache
        isPlayerPlaying = !paused && !idle && !eofReached
        isPlayerEnded = eofReached
        durationMs = Int64(duration * 1000)
        positionMs = Int64(max(position, 0) * 1000)
        bufferedMs = Int64(max(position + cached, 0) * 1000)
        currentSpeed = Float(getDouble("speed").isNormal ? getDouble("speed") : 1.0)
    }

    // MARK: Event loop

    private func readEvents() {
        eventQueue.async { [weak self] in
            guard let self, let mpv = self.mpv else { return }
            while true {
                let event = mpv_wait_event(mpv, 0)
                guard let eventPtr = event else { break }
                if eventPtr.pointee.event_id == MPV_EVENT_NONE { break }

                switch eventPtr.pointee.event_id {
                case MPV_EVENT_PROPERTY_CHANGE:
                    DispatchQueue.main.async { self.refreshPlaybackState() }
                case MPV_EVENT_FILE_LOADED:
                    DispatchQueue.main.async {
                        self.clearPlaybackError()
                        self.isPlayerLoading = false
                        self.refreshPlaybackState()
                    }
                case MPV_EVENT_END_FILE:
                    if let data = eventPtr.pointee.data {
                        let endFile = UnsafePointer<mpv_event_end_file>(OpaquePointer(data)).pointee
                        if endFile.reason == MPV_END_FILE_REASON_ERROR {
                            let errorText = String(cString: mpv_error_string(endFile.error))
                            self.setPlaybackError("[mpv] \(errorText)")
                        }
                    }
                case MPV_EVENT_SHUTDOWN:
                    return
                case MPV_EVENT_LOG_MESSAGE:
                    if let msg = UnsafeMutablePointer<mpv_event_log_message>(OpaquePointer(eventPtr.pointee.data)) {
                        let prefix = String(cString: msg.pointee.prefix!)
                        let level = String(cString: msg.pointee.level!)
                        let text = String(cString: msg.pointee.text!)
                        self.appendPlaybackLog(prefix: prefix, level: level, text: text)
                    }
                default:
                    break
                }
            }
        }
    }

    // MARK: Error management

    private func clearPlaybackError() {
        errorStateLock.lock()
        recentPlaybackLogs.removeAll(keepingCapacity: true)
        _currentErrorMessage = nil
        errorStateLock.unlock()
    }

    private func appendPlaybackLog(prefix: String, level: String, text: String) {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty, level == "warn" || level == "error" || level == "fatal" else { return }
        errorStateLock.lock()
        recentPlaybackLogs.append("[\(prefix)] \(trimmed)")
        if recentPlaybackLogs.count > 4 { recentPlaybackLogs.removeFirst(recentPlaybackLogs.count - 4) }
        errorStateLock.unlock()
    }

    private func setPlaybackError(_ fallback: String) {
        errorStateLock.lock()
        var parts = recentPlaybackLogs.suffix(3)
        if !fallback.isEmpty && !parts.contains(fallback) { parts.append(fallback) }
        _currentErrorMessage = parts.isEmpty ? "Unable to play this stream." : parts.joined(separator: "\n")
        errorStateLock.unlock()
    }

    // MARK: MPV helpers

    private func command(_ command: String, args: [String?] = [], checkForErrors: Bool = true) {
        guard mpv != nil else { return }
        var cargs = makeCArgs(command, args).map { $0.flatMap { UnsafePointer<CChar>(strdup($0)) } }
        defer { for ptr in cargs where ptr != nil { free(UnsafeMutablePointer(mutating: ptr!)) } }
        let ret = mpv_command(mpv, &cargs)
        if checkForErrors { checkError(ret) }
    }

    private func makeCArgs(_ command: String, _ args: [String?]) -> [String?] {
        var strArgs = args; strArgs.insert(command, at: 0); strArgs.append(nil); return strArgs
    }

    private func getDouble(_ name: String) -> Double {
        guard mpv != nil else { return 0.0 }
        var data = Double(); mpv_get_property(mpv, name, MPV_FORMAT_DOUBLE, &data); return data
    }

    private func getFlag(_ name: String) -> Bool {
        guard mpv != nil else { return false }
        var data = Int64(); mpv_get_property(mpv, name, MPV_FORMAT_FLAG, &data); return data > 0
    }

    private func setFlag(_ name: String, _ flag: Bool) {
        guard mpv != nil else { return }
        var data: Int = flag ? 1 : 0; mpv_set_property(mpv, name, MPV_FORMAT_FLAG, &data)
    }

    private func setStringProperty(_ name: String, _ value: String) {
        guard mpv != nil else { return }
        checkError(mpv_set_property_string(mpv, name, value))
    }

    private func checkError(_ status: CInt) {
        if status < 0 { print("[MPV] API error: \(String(cString: mpv_error_string(status)))") }
    }

    private func sanitizeRequestHeaders(_ headers: [String: String]) -> [String: String] {
        var sanitized: [String: String] = [:]
        headers.forEach { key, value in
            let k = key.trimmingCharacters(in: .whitespacesAndNewlines)
            let v = value.trimmingCharacters(in: .whitespacesAndNewlines)
            guard !k.isEmpty, !v.isEmpty,
                  k.caseInsensitiveCompare("Range") != .orderedSame else { return }
            sanitized[k] = v
        }
        return sanitized
    }

    private func applyRequestHeaders(_ headers: [String: String]) {
        guard mpv != nil else { return }
        if headers.isEmpty {
            checkError(mpv_set_property_string(mpv, "http-header-fields", ""))
            return
        }
        let serialized = headers
            .sorted { $0.key.localizedCaseInsensitiveCompare($1.key) == .orderedAscending }
            .map { key, value in
                let escaped = value.replacingOccurrences(of: "\\", with: "\\\\")
                                   .replacingOccurrences(of: ",", with: "\\,")
                return "\(key): \(escaped)"
            }
            .joined(separator: ",")
        checkError(mpv_set_property_string(mpv, "http-header-fields", serialized))
    }

    private func refreshImmersiveSystemUI() {
        setNeedsUpdateOfHomeIndicatorAutoHidden()
        setNeedsUpdateOfScreenEdgesDeferringSystemGestures()
        setNeedsStatusBarAppearanceUpdate()
        var currentParent = parent
        while let controller = currentParent {
            controller.setNeedsUpdateOfHomeIndicatorAutoHidden()
            controller.setNeedsUpdateOfScreenEdgesDeferringSystemGestures()
            controller.setNeedsStatusBarAppearanceUpdate()
            if let root = controller as? RootComposeViewController {
                root.refreshImmersiveSystemUI()
            }
            currentParent = controller.parent
        }
    }
}

// MARK: - Factory + Registration

final class MPVPlayerBridgeCreatorImpl: NSObject, MuvioPlayerBridgeCreator {
    func createBridge() -> any MuvioPlayerBridge {
        return MPVPlayerBridgeImpl()
    }
}

enum MuvioPlayerRegistration {
    static func register() {
        MuvioPlayerBridgeFactory.shared.registerCreator(creator: MPVPlayerBridgeCreatorImpl())
    }
}
