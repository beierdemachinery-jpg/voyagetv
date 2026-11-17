import AVFoundation
import SwiftUI
import UIKit

/// AVPlayer 的画面承载视图。只有 isVideoAllowed 为 true 时才会被创建。
final class PlayerLayerContainerView: UIView {
    override static var layerClass: AnyClass { AVPlayerLayer.self }

    /// layerClass 已保证底层就是 AVPlayerLayer。
    var playerLayer: AVPlayerLayer {
        layer as! AVPlayerLayer
    }
}

struct VideoLayerView: UIViewRepresentable {
    let player: AVPlayer

    func makeUIView(context: Context) -> PlayerLayerContainerView {
        let view = PlayerLayerContainerView()
        view.backgroundColor = .black
        view.playerLayer.player = player
        view.playerLayer.videoGravity = .resizeAspect
        return view
    }

    func updateUIView(_ uiView: PlayerLayerContainerView, context: Context) {
        if uiView.playerLayer.player !== player {
            uiView.playerLayer.player = player
        }
    }
}
