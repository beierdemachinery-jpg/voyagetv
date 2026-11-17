import AVFoundation
import Foundation

// 真机（macOS）AVFoundation 探针：用真实的 HLSResourceLoader 逻辑去播一个地址，
// 把 item.status / item.error / errorLog 打出来，用来判断"播不了"到底卡在哪一步。
//
// 用法：
//   PROBE_URL=<地址> PROBE_MODE=loader|direct ./avprobe
//   退出码：0 播放成功，2 item 失败，3 超时（一直没播起来）

let env = ProcessInfo.processInfo.environment
guard let urlString = env["PROBE_URL"], let url = URL(string: urlString) else {
    print("missing PROBE_URL")
    exit(64)
}
let mode = env["PROBE_MODE"] ?? "loader"
print("=== probe start mode=\(mode) url=\(urlString) ===")

var asset: AVURLAsset
var loader: HLSResourceLoader?
var observed = false

if mode == "direct" {
    asset = AVURLAsset(url: url)
} else {
    let l = HLSResourceLoader(originalURL: url, headers: [:])
    l.onDebug = { print("[loader] \($0)") }
    loader = l
    asset = HLSResourceLoader.makeAsset(for: url, loader: l)
}

let item = AVPlayerItem(asset: asset)
let player = AVPlayer(playerItem: item)
var readyLogged = false

let statusObservation = item.observe(\.status, options: [.initial, .new]) { item, _ in
    guard !observed || item.status != .unknown else { return }
    observed = true
    print("item.status=\(item.status.rawValue)")
    switch item.status {
    case .readyToPlay:
        if !readyLogged {
            readyLogged = true
            print("item.duration=\(item.duration.seconds) tracks=\(asset.tracks.map { $0.mediaType.rawValue })")
            player.play()
        }
    case .failed:
        print("item.error=\(String(describing: item.error))")
        if let error = item.error as NSError? {
            print("item.error.code=\(error.code) domain=\(error.domain)")
            print("item.error.userInfo=\(error.userInfo)")
        }
        print("errorLog=\(String(describing: item.errorLog()))")
        exit(2)
    default:
        break
    }
}
_ = statusObservation

player.addPeriodicTimeObserver(forInterval: CMTime(seconds: 1, preferredTimescale: 600), queue: .main) { time in
    print("played=\(time.seconds)")
    if time.seconds >= 3 {
        print("RESULT: PLAYING")
        exit(0)
    }
}

DispatchQueue.main.asyncAfter(deadline: .now() + 45) {
    print("RESULT: TIMEOUT（45 秒没有播放到 3 秒）")
    print("item.status=\(item.status.rawValue)")
    print("errorLog=\(String(describing: item.errorLog()))")
    // 让 loader 有机会被释放
    loader?.stop()
    exit(3)
}

RunLoop.main.run()
