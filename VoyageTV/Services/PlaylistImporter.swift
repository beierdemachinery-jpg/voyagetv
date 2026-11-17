import Foundation

/// 播放列表导入：远端 URL、本地文件、粘贴文本三种入口。
@MainActor
enum PlaylistImporter {
    enum ImportError: LocalizedError {
        case invalidURL
        case http(String)
        case emptyContent
        case noChannels
        case readFailed(String)
        case notRefreshable

        var errorDescription: String? {
            switch self {
            case .invalidURL:
                return "链接格式不正确，请填写以 http:// 或 https:// 开头的地址。"
            case .http(let message):
                return "下载失败：\(message)"
            case .emptyContent:
                return "这个文件是空的，没有可解析的内容。"
            case .noChannels:
                return "没有在这份内容里找到任何频道。请确认它是 M3U 频道表（含 #EXTINF 与流地址）。"
            case .readFailed(let message):
                return "读取文件失败：\(message)"
            case .notRefreshable:
                return "这个来源无法在线刷新（粘贴文本或本地文件），请重新导入。"
            }
        }
    }

    /// 从 http(s) 链接导入，返回新增频道数。
    @discardableResult
    static func importFromURLString(_ raw: String, name: String? = nil, into store: PlaylistStore) async throws -> Int {
        let trimmed = raw.trimmed
        guard let url = URL(string: trimmed),
              let scheme = url.scheme?.lowercased(),
              ["http", "https"].contains(scheme) else {
            throw ImportError.invalidURL
        }

        var request = URLRequest(url: url)
        request.timeoutInterval = 25
        request.setValue("VoyageTV/1.0 (iOS)", forHTTPHeaderField: "User-Agent")
        request.setValue("*/*", forHTTPHeaderField: "Accept")

        let data: Data
        let response: URLResponse
        do {
            (data, response) = try await URLSession.shared.data(for: request)
        } catch {
            throw ImportError.http(error.localizedDescription)
        }

        if let http = response as? HTTPURLResponse, !(200..<300).contains(http.statusCode) {
            throw ImportError.http("服务器返回状态码 \(http.statusCode)")
        }
        guard data.count > 0 else { throw ImportError.emptyContent }

        let text = decode(data)
        return try importText(
            text,
            name: name ?? displayName(for: url),
            source: url.absoluteString,
            kind: .m3uURL,
            fallbackURL: url,
            into: store
        )
    }

    /// 从"文件"App / iCloud 选中的文件导入。
    @discardableResult
    static func importFromFile(_ fileURL: URL, into store: PlaylistStore) throws -> Int {
        let scoped = fileURL.startAccessingSecurityScopedResource()
        defer { if scoped { fileURL.stopAccessingSecurityScopedResource() } }

        let data: Data
        do {
            data = try Data(contentsOf: fileURL)
        } catch {
            throw ImportError.readFailed(error.localizedDescription)
        }
        guard data.count > 0 else { throw ImportError.emptyContent }

        let text = decode(data)
        let name = fileURL.deletingPathExtension().lastPathComponent
        return try importText(
            text,
            name: name.isEmpty ? "本地播放列表" : name,
            source: "file://\(fileURL.lastPathComponent)",
            kind: .file,
            fallbackURL: fileURL,
            into: store
        )
    }

    /// 从粘贴的文本导入。
    @discardableResult
    static func importFromText(_ text: String, name: String = "粘贴的播放列表", into store: PlaylistStore) throws -> Int {
        try importText(text, name: name, source: "pasted", kind: .pasted, fallbackURL: nil, into: store)
    }

    /// Xtream Codes 登录并导入。
    @discardableResult
    static func importXtream(
        server: String,
        username: String,
        password: String,
        name: String,
        into store: PlaylistStore
    ) async throws -> Int {
        let credentials = XtreamCredentials(
            baseURL: server.trimmed,
            username: username.trimmed,
            password: password
        )
        guard !credentials.baseURL.isEmpty, !credentials.username.isEmpty else {
            throw ImportError.invalidURL
        }

        let payload = try await XtreamClient.fetch(credentials, fallbackName: name)
        let displayName = name.trimmed.isEmpty
            ? (URL(string: credentials.normalizedBase)?.host ?? "Xtream 源")
            : name.trimmed

        return store.upsert(
            name: displayName,
            source: "xtream://\(credentials.normalizedBase)",
            channels: payload.channels,
            kind: .xtream,
            credentials: credentials
        )
    }

    /// 刷新已有订阅（M3U 链接 / Xtream）。
    @discardableResult
    static func refresh(_ playlist: Playlist, into store: PlaylistStore) async throws -> Int {
        switch playlist.kind {
        case .xtream:
            guard let credentials = playlist.credentials else {
                throw ImportError.notRefreshable
            }
            let payload = try await XtreamClient.fetch(credentials, fallbackName: playlist.name)
            return store.upsert(
                name: playlist.name,
                source: playlist.source,
                channels: payload.channels,
                kind: .xtream,
                credentials: credentials
            )
        case .m3uURL:
            return try await importFromURLString(playlist.source, name: playlist.name, into: store)
        case .pasted, .file:
            throw ImportError.notRefreshable
        }
    }

    // MARK: - 内部

    private static func importText(
        _ text: String,
        name: String,
        source: String,
        kind: Playlist.Kind = .m3uURL,
        fallbackURL: URL?,
        into store: PlaylistStore
    ) throws -> Int {
        let trimmed = text.trimmed
        guard !trimmed.isEmpty else { throw ImportError.emptyContent }

        let parsed = M3UParser.parse(trimmed, baseURL: fallbackURL)
        var channels = parsed.channels

        // 用户直接贴了一个 .m3u8 分片清单：当成单流频道处理，而不是报"没有频道"。
        if channels.isEmpty, parsed.isHLSMediaPlaylist, let fallbackURL {
            let id = StableID.channel(fallbackURL, name: name)
            channels = [
                Channel(id: id, name: name, streamURL: fallbackURL, group: "HLS 单流")
            ]
        }

        guard !channels.isEmpty else { throw ImportError.noChannels }
        return store.upsert(name: name, source: source, channels: channels, kind: kind)
    }

    private static func displayName(for url: URL) -> String {
        let last = url.deletingPathExtension().lastPathComponent
        if !last.isEmpty, last != "/" { return last }
        return url.host ?? "播放列表"
    }

    /// 优先 UTF-8，再退回 GB18030（中文 IPTV 列表常见），最后 latin1 兜底。
    private static func decode(_ data: Data) -> String {
        if let text = String(data: data, encoding: .utf8) { return text }

        let gbEncoding = CFStringConvertEncodingToNSStringEncoding(
            CFStringEncoding(CFStringEncodings.GB_18030_2000.rawValue)
        )
        if let text = String(data: data, encoding: String.Encoding(rawValue: gbEncoding)) { return text }
        if let text = String(data: data, encoding: .isoLatin1) { return text }
        return ""
    }
}
