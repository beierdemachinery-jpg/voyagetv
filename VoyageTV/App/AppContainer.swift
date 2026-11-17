import Foundation

/// 极简依赖容器。CarPlay 的 SceneDelegate 无法访问 SwiftUI 环境，
/// 因此用一个进程级单例持有同一份资料库、节目单与播放引擎，
/// 保证车机与手机是同一状态。
@MainActor
final class AppContainer {
    static let shared = AppContainer()

    let playlists: PlaylistStore
    let favorites: FavoritesStore
    let recents: RecentsStore
    let epg: EPGStore
    let player: PlaybackEngine
    let carPlaySession: CarPlaySession

    private init() {
        let playlists = PlaylistStore()
        let recents = RecentsStore()
        let player = PlaybackEngine()

        self.playlists = playlists
        self.recents = recents
        self.player = player
        self.favorites = FavoritesStore()
        self.epg = EPGStore()
        self.carPlaySession = CarPlaySession()

        // 上下切台按"全部分类、按频道号/名称排序"的顺序走。
        player.channelSequenceProvider = { [weak playlists] in
            playlists?.search("", group: nil) ?? []
        }
        player.onChannelStarted = { [weak recents] channel in
            recents?.record(channel)
        }
    }
}
