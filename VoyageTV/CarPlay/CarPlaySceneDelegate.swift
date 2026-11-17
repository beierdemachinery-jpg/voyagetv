import CarPlay
import UIKit

/// CarPlay 场景入口。
///
/// 生命周期由系统管理：iPhone 接入支持 CarPlay 的车机时，若 App 拥有
/// `com.apple.developer.carplay-audio` 权限，系统会创建这个场景。
/// 连接期间本 App 一律以音频模式播放，手机端同步隐藏视频画面。
@MainActor
final class CarPlaySceneDelegate: UIResponder, CPTemplateApplicationSceneDelegate {
    private var coordinator: CarPlayCoordinator?

    func templateApplicationScene(
        _ templateApplicationScene: CPTemplateApplicationScene,
        didConnect interfaceController: CPInterfaceController
    ) {
        let container = AppContainer.shared

        container.carPlaySession.isConnected = true
        container.carPlaySession.lastConnectedAt = Date()
        container.player.setCarPlayConnected(true)

        let coordinator = CarPlayCoordinator(
            interfaceController: interfaceController,
            playlists: container.playlists,
            favorites: container.favorites,
            recents: container.recents,
            epg: container.epg,
            player: container.player
        )
        coordinator.start()
        self.coordinator = coordinator
    }

    func templateApplicationScene(
        _ templateApplicationScene: CPTemplateApplicationScene,
        didDisconnect interfaceController: CPInterfaceController
    ) {
        let container = AppContainer.shared

        container.carPlaySession.isConnected = false
        container.player.setCarPlayConnected(false)

        coordinator?.stop()
        coordinator = nil
    }
}
