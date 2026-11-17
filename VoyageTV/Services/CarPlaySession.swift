import Foundation

/// CarPlay 连接状态。由 CarPlaySceneDelegate 维护，UI 与播放引擎据此切换"仅音频"。
@MainActor
final class CarPlaySession: ObservableObject {
    @Published var isConnected: Bool = false
    /// 最近一次连接成功的时间，仅用于设置页展示。
    @Published var lastConnectedAt: Date?
}
