import Foundation
import UIKit

/// 频道台标加载（用于锁屏 / CarPlay Now Playing 封面）。
/// 做一个小小的内存 + 磁盘无关缓存，避免每次切台都重新下载。
actor ArtworkLoader {
    static let shared = ArtworkLoader()

    private var cache: [String: UIImage] = [:]
    private var inFlight: [String: Task<UIImage?, Never>] = [:]

    func image(for url: URL?) async -> UIImage? {
        guard let url else { return nil }
        let key = url.absoluteString
        if let cached = cache[key] { return cached }
        if let task = inFlight[key] { return await task.value }

        let task = Task<UIImage?, Never> { [url] in
            var request = URLRequest(url: url)
            request.timeoutInterval = 12
            guard let (data, response) = try? await URLSession.shared.data(for: request),
                  let http = response as? HTTPURLResponse,
                  (200..<300).contains(http.statusCode),
                  data.count > 0,
                  let image = UIImage(data: data) else {
                return nil
            }
            return image
        }
        inFlight[key] = task
        let result = await task.value
        inFlight[key] = nil
        if let result { cache[key] = result }
        return result
    }
}
