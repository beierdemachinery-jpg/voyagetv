import Foundation

/// 由内容派生的稳定 ID（FNV-1a 64 位）。
/// 用途：同一频道在重复导入、刷新播放列表后仍保持同一个 id，
/// 因此收藏、最近播放等状态不会因为列表更新而丢失。
enum StableID {
    static func channel(_ url: URL, name: String) -> String {
        hash(url.absoluteString + "|" + name)
    }

    static func playlist(source: String, name: String) -> String {
        hash(source + "|" + name)
    }

    static func hash(_ seed: String) -> String {
        var value: UInt64 = 14_695_981_039_346_656_037
        for byte in seed.utf8 {
            value ^= UInt64(byte)
            value = value &* 1_099_511_628_211
        }
        return String(value, radix: 16)
    }
}
