import AVFoundation
import Foundation
import MediaPlayer
import UIKit

/// 播放引擎：AVPlayer 封装 + 音频会话 + 锁屏/CarPlay 遥控 + 画面闸门。
///
/// 关于"行驶中只播音频"的实现原则：
/// 本 App 不做任何车辆状态推断，也不尝试绕开系统限制。
/// 规则只有一条 —— **只要 CarPlay 会话处于连接状态，就永不渲染视频画面**，
/// 播放器容器视图整体不创建，AVPlayer 只输出音频。
/// 因此不存在"行驶中仍显示视频"的代码路径。
@MainActor
final class PlaybackEngine: NSObject, ObservableObject {
    @Published private(set) var current: Channel?
    @Published private(set) var isPlaying = false
    @Published private(set) var isBuffering = false
    @Published private(set) var errorMessage: String?
    /// 是否允许把画面渲染出来。false 时 UI 只呈现音频播放页。
    @Published private(set) var isVideoAllowed = true
    @Published private(set) var isCarPlayConnected = false

    let player = AVPlayer()

    /// 用于"上一台 / 下一台"的频道序列，由 AppContainer 注入。
    var channelSequenceProvider: (() -> [Channel])?
    /// 开始播放某个频道时的回调（用于记录最近观看）。
    var onChannelStarted: ((Channel) -> Void)?

    private var itemStatusObservation: NSKeyValueObservation?
    private var timeControlObservation: NSKeyValueObservation?
    private var failureObserver: NSObjectProtocol?
    private var forceAudioOnly: Bool
    private var artworkTask: Task<Void, Never>?

    private static let forceAudioOnlyKey = "settings.forceAudioOnly"

    override init() {
        forceAudioOnly = UserDefaults.standard.bool(forKey: Self.forceAudioOnlyKey)
        super.init()
        player.actionAtItemEnd = .pause
        player.automaticallyWaitsToMinimizeStalling = true
        configureAudioSession()
        registerRemoteCommands()
        refreshVideoGate()
    }

    deinit {
        if let failureObserver {
            NotificationCenter.default.removeObserver(failureObserver)
        }
    }

    // MARK: - 对外操作

    func play(_ channel: Channel) {
        if current?.id == channel.id, player.currentItem != nil {
            resume()
            return
        }

        current = channel
        errorMessage = nil
        isBuffering = true
        onChannelStarted?(channel)

        let options: [String: Any] = channel.httpHeaders.isEmpty
            ? [:]
            : ["AVURLAssetHTTPHeaderFieldsKey": channel.httpHeaders]
        let asset = AVURLAsset(url: channel.streamURL, options: options)
        let item = AVPlayerItem(asset: asset)
        item.preferredForwardBufferDuration = 5
        observe(item: item)

        player.replaceCurrentItem(with: item)
        activateAudioSession()
        player.play()
        updateNowPlaying()
    }

    func togglePlayPause() {
        if isPlaying {
            pause()
        } else {
            resume()
        }
    }

    func pause() {
        player.pause()
        isPlaying = false
        updateNowPlaying()
    }

    func resume() {
        guard player.currentItem != nil else { return }
        activateAudioSession()
        player.play()
        isPlaying = true
        updateNowPlaying()
    }

    func stop() {
        player.pause()
        player.replaceCurrentItem(with: nil)
        current = nil
        isPlaying = false
        isBuffering = false
        errorMessage = nil
        artworkTask?.cancel()
        MPNowPlayingInfoCenter.default().nowPlayingInfo = nil
    }

    func skip(by seconds: Double) {
        guard player.currentItem != nil else { return }
        let target = CMTimeAdd(player.currentTime(), CMTime(seconds: seconds, preferredTimescale: 1000))
        player.seek(to: target, toleranceBefore: .zero, toleranceAfter: .zero)
    }

    // MARK: - 切台

    func playNextChannel() {
        stepChannel(by: 1)
    }

    func playPreviousChannel() {
        stepChannel(by: -1)
    }

    private func stepChannel(by offset: Int) {
        guard let channelSequenceProvider else { return }
        let sequence = channelSequenceProvider()
        guard !sequence.isEmpty else { return }

        var index = 0
        if let current, let found = sequence.firstIndex(where: { $0.id == current.id }) {
            index = found
        }
        let count = sequence.count
        let target = ((index + offset) % count + count) % count
        play(sequence[target])
    }

    // MARK: - 画面闸门

    func setCarPlayConnected(_ connected: Bool) {
        isCarPlayConnected = connected
        refreshVideoGate()
    }

    func setForceAudioOnly(_ value: Bool) {
        forceAudioOnly = value
        UserDefaults.standard.set(value, forKey: Self.forceAudioOnlyKey)
        refreshVideoGate()
    }

    var forceAudioOnlyEnabled: Bool { forceAudioOnly }

    private func refreshVideoGate() {
        isVideoAllowed = !forceAudioOnly && !isCarPlayConnected
    }

    // MARK: - 音频会话

    private func configureAudioSession() {
        let session = AVAudioSession.sharedInstance()
        try? session.setCategory(.playback, mode: .default, options: [.allowAirPlay, .allowBluetoothA2DP])
    }

    private func activateAudioSession() {
        try? AVAudioSession.sharedInstance().setActive(true)
    }

    // MARK: - 观察

    private func observe(item: AVPlayerItem) {
        itemStatusObservation = item.observe(\.status, options: [.initial, .new]) { [weak self] item, _ in
            Task { @MainActor in
                guard let self else { return }
                switch item.status {
                case .readyToPlay:
                    self.isBuffering = false
                case .failed:
                    self.isBuffering = false
                    self.isPlaying = false
                    self.errorMessage = item.error?.localizedDescription ?? "这个频道当前无法播放。"
                default:
                    self.isBuffering = true
                }
                self.updateNowPlaying()
            }
        }

        timeControlObservation = player.observe(\.timeControlStatus, options: [.new]) { [weak self] player, _ in
            Task { @MainActor in
                guard let self else { return }
                switch player.timeControlStatus {
                case .playing:
                    self.isPlaying = true
                    self.isBuffering = false
                    self.errorMessage = nil
                case .waitingToPlayAtSpecifiedRate:
                    self.isBuffering = true
                case .paused:
                    self.isPlaying = false
                @unknown default:
                    break
                }
                self.updateNowPlaying()
            }
        }

        if let failureObserver {
            NotificationCenter.default.removeObserver(failureObserver)
        }
        failureObserver = NotificationCenter.default.addObserver(
            forName: .AVPlayerItemFailedToPlayToEndTime,
            object: item,
            queue: .main
        ) { [weak self] notification in
            let underlying = notification.userInfo?[AVPlayerItemFailedToPlayToEndTimeErrorKey] as? Error
            Task { @MainActor in
                self?.isBuffering = false
                self?.isPlaying = false
                self?.errorMessage = underlying?.localizedDescription ?? "播放中断，可能是网络或节目源问题。"
            }
        }
    }

    // MARK: - 锁屏 / CarPlay 遥控

    private func registerRemoteCommands() {
        let center = MPRemoteCommandCenter.shared()

        center.playCommand.isEnabled = true
        center.playCommand.addTarget { [weak self] _ in
            Task { @MainActor in self?.resume() }
            return .success
        }

        center.pauseCommand.isEnabled = true
        center.pauseCommand.addTarget { [weak self] _ in
            Task { @MainActor in self?.pause() }
            return .success
        }

        center.togglePlayPauseCommand.isEnabled = true
        center.togglePlayPauseCommand.addTarget { [weak self] _ in
            Task { @MainActor in self?.togglePlayPause() }
            return .success
        }

        center.changePlaybackPositionCommand.isEnabled = true
        center.changePlaybackPositionCommand.addTarget { [weak self] event in
            guard let positionEvent = event as? MPChangePlaybackPositionCommandEvent else {
                return .commandFailed
            }
            Task { @MainActor in
                self?.player.seek(to: CMTime(seconds: positionEvent.positionTime, preferredTimescale: 1000))
            }
            return .success
        }

        // 直播没有上下集，但支持"上一台 / 下一台"，方向盘左右键可用。
        center.nextTrackCommand.isEnabled = true
        center.nextTrackCommand.addTarget { [weak self] _ in
            Task { @MainActor in self?.playNextChannel() }
            return .success
        }

        center.previousTrackCommand.isEnabled = true
        center.previousTrackCommand.addTarget { [weak self] _ in
            Task { @MainActor in self?.playPreviousChannel() }
            return .success
        }
    }

    private func updateNowPlaying() {
        guard let channel = current else {
            MPNowPlayingInfoCenter.default().nowPlayingInfo = nil
            return
        }

        var info: [String: Any] = [
            MPMediaItemPropertyTitle: channel.name,
            MPMediaItemPropertyArtist: channel.displayGroup,
            MPMediaItemPropertyAlbumTitle: "VoyageTV",
            MPNowPlayingInfoPropertyIsLiveStream: true,
            MPNowPlayingInfoPropertyPlaybackRate: isPlaying ? 1.0 : 0.0,
            MPNowPlayingInfoPropertyMediaType: MPNowPlayingInfoMediaType.audio.rawValue,
            MPNowPlayingInfoPropertyExternalContentIdentifier: channel.id
        ]

        let elapsed = player.currentTime().seconds
        if elapsed.isFinite, elapsed > 0 {
            info[MPNowPlayingInfoPropertyElapsedPlaybackTime] = elapsed
        }
        if let duration = player.currentItem?.duration.seconds, duration.isFinite, duration > 0 {
            info[MPMediaItemPropertyPlaybackDuration] = duration
        }

        MPNowPlayingInfoCenter.default().nowPlayingInfo = info
        loadArtworkIfNeeded(for: channel)
    }

    private func loadArtworkIfNeeded(for channel: Channel) {
        guard MPNowPlayingInfoCenter.default().nowPlayingInfo?[MPMediaItemPropertyArtwork] == nil,
              channel.logoURL != nil else { return }

        artworkTask?.cancel()
        artworkTask = Task { [weak self] in
            guard let image = await ArtworkLoader.shared.image(for: channel.logoURL) else { return }
            guard !Task.isCancelled else { return }
            await MainActor.run {
                guard let self, self.current?.id == channel.id else { return }
                var info = MPNowPlayingInfoCenter.default().nowPlayingInfo ?? [:]
                info[MPMediaItemPropertyArtwork] = MPMediaItemArtwork(boundsSize: image.size) { _ in image }
                MPNowPlayingInfoCenter.default().nowPlayingInfo = info
            }
        }
    }
}
