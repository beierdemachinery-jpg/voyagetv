import AVFoundation
import Foundation

/// 修 HLS 源的两类"识别问题"：
///
/// 1. URL 没有 `.m3u8` 后缀（例如 `ysp.php?id=cctv1`）；
/// 2. 响应头 Content-Type 不是 `application/vnd.apple.mpegurl`（很多 PHP 代理返回 `text/html`）。
///
/// 两种情况下 AVPlayer 会直接判定"不可播放"。做法是：用一个自定义 scheme 包一层，
/// 让 AVPlayer 把所有请求交给 `AVAssetResourceLoaderDelegate`；我们自己去下载，
/// 确认内容是 HLS 后按正确的 content type 回给播放器。
///
/// 只对"看起来不像 HLS 文件"的地址启用，正常 `.m3u8` 仍走原生路径，避免不必要的绕行。
final class HLSResourceLoader: NSObject, AVAssetResourceLoaderDelegate {

    static let customScheme = "voyagetv-hls"
    /// HLS 播放列表的 MIME，AVFoundation 认这个字符串。
    private static let playlistContentType = "application/vnd.apple.mpegurl"

    private let originalURL: URL
    private let headers: [String: String]
    private let session: URLSession
    /// AVAssetResourceLoader 的 delegate 队列必须是串行 DispatchQueue。
    private let queue = DispatchQueue(label: "VoyageTV.HLSResourceLoader")
    private var runningTasks: [URLSessionDataTask] = []

    init(originalURL: URL, headers: [String: String]) {
        self.originalURL = originalURL
        var merged = headers
        if merged["User-Agent"] == nil {
            merged["User-Agent"] = "VoyageTV/1.0 (iOS)"
        }
        self.headers = merged

        let config = URLSessionConfiguration.ephemeral
        config.timeoutIntervalForRequest = 20
        config.timeoutIntervalForResource = 40
        self.session = URLSession(configuration: config)
        super.init()
    }

    /// 这个地址是否需要交给资源加载器处理。
    /// 规则：http(s) 且路径后缀不是 `.m3u8`。
    static func needsLoader(for url: URL) -> Bool {
        guard let scheme = url.scheme?.lowercased(), scheme == "http" || scheme == "https" else { return false }
        return url.pathExtension.lowercased() != "m3u8"
    }

    /// 造一个挂着自定义 scheme 的 asset，并把 delegate 接上。
    static func makeAsset(for url: URL, loader: HLSResourceLoader) -> AVURLAsset {
        var components = URLComponents(url: url, resolvingAgainstBaseURL: false)
        components?.scheme = customScheme
        let proxied = components?.url ?? url
        let asset = AVURLAsset(url: proxied)
        asset.resourceLoader.setDelegate(loader, queue: loader.queue)
        return asset
    }

    func stop() {
        runningTasks.forEach { $0.cancel() }
        runningTasks.removeAll()
        session.invalidateAndCancel()
    }

    // MARK: - AVAssetResourceLoaderDelegate

    func resourceLoader(
        _ resourceLoader: AVAssetResourceLoader,
        shouldWaitForLoadingOfRequestedResource loadingRequest: AVAssetResourceLoadingRequest
    ) -> Bool {
        guard let target = resolve(loadingRequest.request.url) else { return false }

        var request = URLRequest(url: target)
        request.timeoutInterval = 20
        for (field, value) in headers {
            request.setValue(value, forHTTPHeaderField: field)
        }

        let task = session.dataTask(with: request) { [weak self] data, response, error in
            guard let self else { return }
            // 错误优先回报
            if let error {
                loadingRequest.finishLoading(with: error)
                return
            }
            guard let http = response as? HTTPURLResponse else {
                loadingRequest.finishLoading(with: URLError(.badServerResponse))
                return
            }
            guard (200..<300).contains(http.statusCode), let data, !data.isEmpty else {
                loadingRequest.finishLoading(
                    with: NSError(
                        domain: "VoyageTV.HLS",
                        code: http.statusCode,
                        userInfo: [NSLocalizedDescriptionKey: "节目源返回 \(http.statusCode)"]
                    )
                )
                return
            }

            let isPlaylist = Self.looksLikePlaylist(data)

            if let info = loadingRequest.contentInformationRequest {
                info.contentType = isPlaylist ? Self.playlistContentType : "video/mp2t"
                info.contentLength = Int64(data.count)
                info.isByteRangeAccessSupported = false
            }

            if isPlaylist, let rewritten = Self.rewriteRelativeURIs(data, base: target) {
                loadingRequest.dataRequest?.respond(with: rewritten)
            } else {
                loadingRequest.dataRequest?.respond(with: data)
            }
            loadingRequest.finishLoading()
        }

        runningTasks.append(task)
        task.resume()
        return true
    }

    func resourceLoader(
        _ resourceLoader: AVAssetResourceLoader,
        didCancel loadingRequest: AVAssetResourceLoadingRequest
    ) {
        runningTasks.forEach { $0.cancel() }
        runningTasks.removeAll()
    }

    // MARK: - 内部

    /// 把自定义 scheme 的请求还原成真实的 http(s) 地址。
    private func resolve(_ url: URL?) -> URL? {
        guard let url, var components = URLComponents(url: url, resolvingAgainstBaseURL: false) else { return nil }
        components.scheme = originalURL.scheme
        components.host = originalURL.host
        components.port = originalURL.port
        // 相对路径的请求会丢失原始路径，用原始路径兜底
        if components.path.isEmpty || components.path == "/" {
            components.path = originalURL.path
        }
        return components.url
    }

    private static func looksLikePlaylist(_ data: Data) -> Bool {
        guard let head = String(data: data.prefix(1024), encoding: .utf8) else { return false }
        return head.contains("#EXTM3U") || head.contains("#EXT-X-")
    }

    /// HLS 里的相对地址（分片、子清单）按真实地址补成绝对地址，
    /// 这样播放器后续直接请求原站，不必每片都绕经我们。
    private static func rewriteRelativeURIs(_ data: Data, base: URL) -> Data? {
        guard let text = String(data: data, encoding: .utf8) else { return data }
        var lines: [String] = []
        for raw in text.components(separatedBy: "\n") {
            let line = raw.trimmingCharacters(in: .whitespaces)
            if !line.isEmpty, !line.hasPrefix("#"), let resolved = URL(string: line, relativeTo: base)?.absoluteURL {
                lines.append(resolved.absoluteString)
            } else {
                lines.append(raw)
            }
        }
        let joined = lines.joined(separator: "\n")
        return joined.data(using: .utf8)
    }
}
