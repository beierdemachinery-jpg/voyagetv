import Foundation

/// 最近观看。只存频道 id + 时间，列表刷新后依然能对应上。
struct RecentEntry: Codable, Hashable {
    var channelID: String
    var playedAt: Date
}

@MainActor
final class RecentsStore: ObservableObject {
    @Published private(set) var entries: [RecentEntry] = []

    private let defaults: UserDefaults
    private let storageKey = "recents.channels"
    private let maxEntries = 20

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        if let data = defaults.data(forKey: storageKey),
           let decoded = try? JSONDecoder().decode([RecentEntry].self, from: data) {
            entries = decoded
        }
    }

    func record(_ channel: Channel) {
        entries.removeAll { $0.channelID == channel.id }
        entries.insert(RecentEntry(channelID: channel.id, playedAt: Date()), at: 0)
        if entries.count > maxEntries {
            entries = Array(entries.prefix(maxEntries))
        }
        persist()
    }

    func channelIDs(limit: Int = 8) -> [String] {
        Array(entries.prefix(limit)).map { $0.channelID }
    }

    func clearAll() {
        entries.removeAll()
        persist()
    }

    private func persist() {
        guard let data = try? JSONEncoder().encode(entries) else { return }
        defaults.set(data, forKey: storageKey)
    }
}
