import Foundation

/// 收藏：只存频道 id 的有序集合，列表刷新后依然有效。
@MainActor
final class FavoritesStore: ObservableObject {
    @Published private(set) var ids: Set<String> = []

    private let defaults: UserDefaults
    private let storageKey = "favorites.channelIDs"

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        if let stored = defaults.array(forKey: storageKey) as? [String] {
            ids = Set(stored)
        }
    }

    func contains(_ channel: Channel) -> Bool {
        ids.contains(channel.id)
    }

    func toggle(_ channel: Channel) {
        if ids.contains(channel.id) {
            ids.remove(channel.id)
        } else {
            ids.insert(channel.id)
        }
        persist()
    }

    func clearAll() {
        ids.removeAll()
        persist()
    }

    private func persist() {
        defaults.set(Array(ids), forKey: storageKey)
    }
}
