import Foundation
import SwiftUI

/// 播放列表资料库：内存状态 + Application Support 下的 JSON 落盘。
@MainActor
final class PlaylistStore: ObservableObject {
    @Published private(set) var playlists: [Playlist] = []

    private let storageURL: URL

    init(storageURL: URL? = nil) {
        self.storageURL = storageURL ?? Self.defaultStorageURL()
        loadFromDisk()
    }

    // MARK: - 读

    /// 所有频道的扁平列表，按导入顺序、并按 id 去重。
    var allChannels: [Channel] {
        var ordered: [Channel] = []
        var seen = Set<String>()
        for playlist in playlists {
            for channel in playlist.channels where seen.insert(channel.id).inserted {
                ordered.append(channel)
            }
        }
        return ordered
    }

    /// 全部分类，按频道数量从多到少。
    var groups: [String] {
        var counts: [String: Int] = [:]
        for channel in allChannels {
            counts[channel.displayGroup, default: 0] += 1
        }
        let names = counts.keys.filter { $0 != "未分类" }.sorted { lhs, rhs in
            let left = counts[lhs] ?? 0
            let right = counts[rhs] ?? 0
            if left != right { return left > right }
            return lhs.localizedStandardCompare(rhs) == .orderedAscending
        }
        return counts["未分类"] == nil ? names : names + ["未分类"]
    }

    func channels(inGroup group: String) -> [Channel] {
        allChannels.filter { $0.displayGroup == group }
    }

    func channel(id: String) -> Channel? {
        allChannels.first { $0.id == id }
    }

    func channels(ids: [String]) -> [Channel] {
        let index = Dictionary(uniqueKeysWithValues: allChannels.map { ($0.id, $0) })
        return ids.compactMap { index[$0] }
    }

    /// 搜索：先按分类过滤，再按关键词匹配，最后按频道号 / 名称排序。
    func search(_ query: String, group: String? = nil, limit: Int = 0) -> [Channel] {
        var result = allChannels
        if let group, !group.isEmpty {
            result = result.filter { $0.displayGroup == group }
        }
        let key = query.searchKey
        if !key.isEmpty {
            result = result.filter { $0.matches(query) }
        }
        result.sort(by: Self.channelSort)
        if limit > 0, result.count > limit {
            result = Array(result.prefix(limit))
        }
        return result
    }

    static func channelSort(_ lhs: Channel, _ rhs: Channel) -> Bool {
        let leftNumber = Int(lhs.number ?? "") ?? Int.max
        let rightNumber = Int(rhs.number ?? "") ?? Int.max
        if leftNumber != rightNumber { return leftNumber < rightNumber }
        return lhs.name.localizedStandardCompare(rhs.name) == .orderedAscending
    }

    // MARK: - 写

    /// 导入或刷新一个播放列表。相同 source 视为刷新，保留原 id 与位置。
    @discardableResult
    func upsert(
        name: String,
        source: String,
        channels: [Channel],
        kind: Playlist.Kind = .m3uURL,
        credentials: XtreamCredentials? = nil
    ) -> Int {
        if let index = playlists.firstIndex(where: { $0.source == source }) {
            playlists[index].name = name
            playlists[index].kind = kind
            playlists[index].credentials = credentials
            playlists[index].channels = channels
            playlists[index].importedAt = Date()
        } else {
            playlists.append(
                Playlist(name: name, source: source, kind: kind, credentials: credentials, channels: channels)
            )
        }
        persist()
        return channels.count
    }

    func playlist(id: UUID) -> Playlist? {
        playlists.first { $0.id == id }
    }

    func remove(_ playlist: Playlist) {
        playlists.removeAll { $0.id == playlist.id }
        persist()
    }

    func remove(atOffsets offsets: IndexSet) {
        playlists.remove(atOffsets: offsets)
        persist()
    }

    func clearAll() {
        playlists.removeAll()
        persist()
    }

    // MARK: - 落盘

    private static func defaultStorageURL() -> URL {
        let manager = FileManager.default
        let base = (try? manager.url(for: .applicationSupportDirectory, in: .userDomainMask, appropriateFor: nil, create: true))
            ?? manager.temporaryDirectory
        let directory = base.appendingPathComponent("VoyageTV", isDirectory: true)
        try? manager.createDirectory(at: directory, withIntermediateDirectories: true)
        return directory.appendingPathComponent("playlists.json")
    }

    private func loadFromDisk() {
        guard let data = try? Data(contentsOf: storageURL) else { return }
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        guard let decoded = try? decoder.decode([Playlist].self, from: data) else { return }
        playlists = decoded
    }

    private func persist() {
        let encoder = JSONEncoder()
        encoder.dateEncodingStrategy = .iso8601
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
        guard let data = try? encoder.encode(playlists) else { return }
        try? data.write(to: storageURL, options: [.atomic])
    }
}
