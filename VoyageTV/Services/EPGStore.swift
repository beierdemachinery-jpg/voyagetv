import Foundation

/// 节目单：一个 XMLTV 源，按频道 id 建索引。
@MainActor
final class EPGStore: ObservableObject {
    @Published private(set) var programs: [String: [EPGProgram]] = [:]
    @Published private(set) var displayNames: [String: String] = [:]
    @Published private(set) var sourceURL: String = ""
    @Published private(set) var lastUpdated: Date?
    @Published private(set) var isRefreshing = false
    @Published var errorMessage: String?

    private let storageURL: URL
    private let defaults: UserDefaults
    private let refreshInterval: TimeInterval = 12 * 60 * 60

    private enum Keys {
        static let source = "epg.sourceURL"
        static let updated = "epg.lastUpdated"
    }

    init(storageURL: URL? = nil, defaults: UserDefaults = .standard) {
        self.defaults = defaults
        self.storageURL = storageURL ?? Self.defaultStorageURL()
        self.sourceURL = defaults.string(forKey: Keys.source) ?? ""
        if let stamp = defaults.object(forKey: Keys.updated) as? Date {
            self.lastUpdated = stamp
        }
        loadPrograms()
    }

    var hasSource: Bool { !sourceURL.trimmed.isEmpty }

    // MARK: - 查询

    func now(on channel: Channel, at date: Date = Date()) -> EPGProgram? {
        for key in lookupKeys(for: channel) {
            guard let list = programs[key] else { continue }
            if let hit = list.first(where: { $0.start <= date && $0.stop > date }) { return hit }
        }
        return nil
    }

    func next(on channel: Channel, at date: Date = Date()) -> EPGProgram? {
        for key in lookupKeys(for: channel) {
            guard let list = programs[key] else { continue }
            if let hit = list.first(where: { $0.start > date }) { return hit }
        }
        return nil
    }

    func programs(on channel: Channel, from date: Date = Date(), limit: Int = 6) -> [EPGProgram] {
        for key in lookupKeys(for: channel) {
            guard let list = programs[key] else { continue }
            let upcoming = list.filter { $0.stop > date }
            return Array(upcoming.prefix(limit))
        }
        return []
    }

    private func lookupKeys(for channel: Channel) -> [String] {
        var keys: [String] = []
        if let tvgID = channel.tvgID?.trimmed.lowercased(), !tvgID.isEmpty {
            keys.append(tvgID)
        }
        // tvg-id 缺失时，用频道的英文/原始名去匹配 XMLTV 的 display-name
        let name = channel.name.trimmed.lowercased()
        if !name.isEmpty, programs[name] == nil {
            if let matchedID = displayNames.first(where: { $0.value.lowercased() == name })?.key {
                keys.append(matchedID)
            }
        }
        if programs[name] != nil { keys.append(name) }
        return keys
    }

    // MARK: - 刷新

    /// 设置新的节目单地址并立即拉取。
    func setSource(_ rawURL: String) async throws {
        let trimmed = rawURL.trimmed
        guard let url = URL(string: trimmed),
              let scheme = url.scheme?.lowercased(),
              ["http", "https"].contains(scheme) else {
            throw EPGError.invalidURL
        }
        isRefreshing = true
        defer { isRefreshing = false }
        do {
            let result = try await download(url)
            apply(result)
            sourceURL = trimmed
            defaults.set(trimmed, forKey: Keys.source)
            errorMessage = nil
        } catch {
            errorMessage = error.localizedDescription
            throw error
        }
    }

    /// 已有源时重新拉取。
    func refresh() async throws {
        guard hasSource, let url = URL(string: sourceURL) else { throw EPGError.noSource }
        isRefreshing = true
        defer { isRefreshing = false }
        do {
            let result = try await download(url)
            apply(result)
            errorMessage = nil
        } catch {
            errorMessage = error.localizedDescription
            throw error
        }
    }

    /// 启动时调用：超过 12 小时或有源但没数据才真正请求。
    func refreshIfNeeded() async {
        guard hasSource, !isRefreshing else { return }
        if let lastUpdated, Date().timeIntervalSince(lastUpdated) < refreshInterval, !programs.isEmpty {
            return
        }
        try? await refresh()
    }

    func clear() {
        programs = [:]
        displayNames = [:]
        sourceURL = ""
        lastUpdated = nil
        defaults.removeObject(forKey: Keys.source)
        defaults.removeObject(forKey: Keys.updated)
        try? FileManager.default.removeItem(at: storageURL)
    }

    private func apply(_ result: XMLTVParser.Result) {
        // 丢掉已经结束超过 1 小时的节目，控制体积
        let cutoff = Date().addingTimeInterval(-3600)
        var pruned: [String: [EPGProgram]] = [:]
        for (key, list) in result.programs {
            let kept = list.filter { $0.stop > cutoff }.sorted { $0.start < $1.start }
            if !kept.isEmpty { pruned[key] = kept }
        }
        programs = pruned
        displayNames = result.displayNames
        lastUpdated = Date()
        defaults.set(lastUpdated, forKey: Keys.updated)
        savePrograms()
    }

    private func download(_ url: URL) async throws -> XMLTVParser.Result {
        var request = URLRequest(url: url)
        request.timeoutInterval = 45
        request.setValue("VoyageTV/1.0 (iOS)", forHTTPHeaderField: "User-Agent")
        request.setValue("*/*", forHTTPHeaderField: "Accept")

        let data: Data
        do {
            (data, _) = try await URLSession.shared.data(for: request)
        } catch {
            throw EPGError.network(error.localizedDescription)
        }
        guard data.count > 1024 else { throw EPGError.empty }
        // XMLTV 文件可能几 MB，解析放到后台线程
        return await Task.detached(priority: .utility) {
            XMLTVParser.parse(data)
        }.value
    }

    // MARK: - 落盘

    private static func defaultStorageURL() -> URL {
        let manager = FileManager.default
        let base = (try? manager.url(for: .applicationSupportDirectory, in: .userDomainMask, appropriateFor: nil, create: true))
            ?? manager.temporaryDirectory
        let directory = base.appendingPathComponent("VoyageTV", isDirectory: true)
        try? manager.createDirectory(at: directory, withIntermediateDirectories: true)
        return directory.appendingPathComponent("epg.json")
    }

    private struct Persisted: Codable {
        var programs: [String: [EPGProgram]]
        var displayNames: [String: String]
    }

    private func savePrograms() {
        let snapshot = Persisted(programs: programs, displayNames: displayNames)
        let encoder = JSONEncoder()
        encoder.dateEncodingStrategy = .iso8601
        guard let data = try? encoder.encode(snapshot) else { return }
        try? data.write(to: storageURL, options: [.atomic])
    }

    private func loadPrograms() {
        guard let data = try? Data(contentsOf: storageURL) else { return }
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        guard let snapshot = try? decoder.decode(Persisted.self, from: data) else { return }
        let cutoff = Date().addingTimeInterval(-3600)
        programs = snapshot.programs.mapValues { list in
            list.filter { $0.stop > cutoff }
        }
        displayNames = snapshot.displayNames
    }

    enum EPGError: LocalizedError {
        case invalidURL
        case noSource
        case network(String)
        case empty

        var errorDescription: String? {
            switch self {
            case .invalidURL: return "节目单地址不正确，请填写 http(s) 链接。"
            case .noSource: return "还没有设置节目单地址。"
            case .network(let message): return "下载节目单失败：\(message)"
            case .empty: return "节目单内容为空或格式不是 XMLTV。"
            }
        }
    }
}
