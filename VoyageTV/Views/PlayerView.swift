import SwiftUI

/// 播放页。画面是否出现完全由 PlaybackEngine.isVideoAllowed 决定：
/// 连接 CarPlay 或用户开启"始终仅音频"时，这里只会渲染音频播放页。
struct PlayerView: View {
    @EnvironmentObject private var player: PlaybackEngine
    @EnvironmentObject private var favorites: FavoritesStore
    @EnvironmentObject private var epg: EPGStore
    @Environment(\.dismiss) private var dismiss

    @State private var controlsVisible = true

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()

            if let channel = player.current {
                stage(for: channel)
                controls(for: channel)
            } else {
                Text("没有正在播放的频道")
                    .foregroundStyle(.white.opacity(0.7))
            }
        }
        .statusBarHidden(true)
        .onTapGesture {
            withAnimation(.easeInOut(duration: 0.2)) {
                controlsVisible.toggle()
            }
        }
    }

    // MARK: - 画面 / 音频舞台

    @ViewBuilder
    private func stage(for channel: Channel) -> some View {
        if player.isVideoAllowed {
            VideoLayerView(player: player.player)
                .ignoresSafeArea()
        } else {
            audioStage(for: channel)
        }
    }

    private func audioStage(for channel: Channel) -> some View {
        VStack(spacing: 14) {
            Spacer()
            logoView(for: channel, size: 132)
            Text(channel.name)
                .font(.title2.weight(.semibold))
                .foregroundStyle(.white)
                .multilineTextAlignment(.center)
            Text(channel.displayGroup)
                .font(.subheadline)
                .foregroundStyle(.white.opacity(0.6))

            if let now = epg.now(on: channel) {
                VStack(spacing: 4) {
                    Text(now.title)
                        .font(.subheadline.weight(.medium))
                        .foregroundStyle(.white.opacity(0.9))
                        .lineLimit(2)
                        .multilineTextAlignment(.center)
                    Text(now.timeRangeText)
                        .font(.caption.monospacedDigit())
                        .foregroundStyle(.white.opacity(0.55))
                }
                .padding(.top, 4)
            }

            Label("音频模式 · 不显示画面", systemImage: "speaker.wave.2.fill")
                .font(.caption.weight(.medium))
                .foregroundStyle(.white.opacity(0.75))
                .padding(.horizontal, 10)
                .padding(.vertical, 5)
                .background(Color.white.opacity(0.12), in: Capsule())
            Spacer()
        }
        .padding(.horizontal, 24)
    }

    private func logoView(for channel: Channel, size: CGFloat) -> some View {
        Group {
            if let url = channel.logoURL {
                AsyncImage(url: url) { phase in
                    if case .success(let image) = phase {
                        image.resizable().scaledToFit()
                    } else {
                        logoPlaceholder(size: size)
                    }
                }
            } else {
                logoPlaceholder(size: size)
            }
        }
        .frame(width: size, height: size)
        .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
    }

    private func logoPlaceholder(size: CGFloat) -> some View {
        ZStack {
            Color.white.opacity(0.08)
            Image(systemName: "tv")
                .font(.system(size: size * 0.3))
                .foregroundStyle(.white.opacity(0.5))
        }
    }

    // MARK: - 控件层

    @ViewBuilder
    private func controls(for channel: Channel) -> some View {
        if controlsVisible {
            VStack {
                HStack {
                    Button {
                        dismiss()
                    } label: {
                        Image(systemName: "xmark")
                            .font(.system(size: 16, weight: .semibold))
                            .foregroundStyle(.white)
                            .frame(width: 40, height: 40)
                            .background(.ultraThinMaterial, in: Circle())
                    }
                    Spacer()
                    Button {
                        favorites.toggle(channel)
                    } label: {
                        Image(systemName: favorites.contains(channel) ? "star.fill" : "star")
                            .font(.system(size: 16, weight: .semibold))
                            .foregroundStyle(favorites.contains(channel) ? Color.yellow : Color.white)
                            .frame(width: 40, height: 40)
                            .background(.ultraThinMaterial, in: Circle())
                    }
                }
                .padding(.horizontal, 20)
                .padding(.top, 12)

                Spacer()

                if let error = player.errorMessage {
                    Text(error)
                        .font(.footnote)
                        .foregroundStyle(.white)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 16)
                        .padding(.vertical, 10)
                        .background(Color.red.opacity(0.55), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                        .padding(.horizontal, 24)
                }

                if let next = epg.next(on: channel) {
                    VStack(spacing: 2) {
                        Text("接下来")
                            .font(.caption2.weight(.semibold))
                            .foregroundStyle(.white.opacity(0.5))
                        Text("\(next.timeRangeText) · \(next.title)")
                            .font(.caption)
                            .foregroundStyle(.white.opacity(0.8))
                            .lineLimit(1)
                    }
                    .padding(.bottom, 8)
                }

                if player.isCarPlayConnected {
                    Label("已连接 CarPlay · 驾驶中仅播放音频", systemImage: "car.fill")
                        .font(.caption.weight(.medium))
                        .foregroundStyle(.white.opacity(0.8))
                        .padding(.bottom, 6)
                }

                HStack(spacing: 34) {
                    Button {
                        player.playPreviousChannel()
                    } label: {
                        Image(systemName: "backward.end.fill")
                            .font(.system(size: 22))
                            .foregroundStyle(.white)
                            .frame(width: 44, height: 44)
                    }
                    .accessibilityLabel("上一台")

                    Button {
                        player.togglePlayPause()
                    } label: {
                        ZStack {
                            Circle().fill(.ultraThinMaterial).frame(width: 72, height: 72)
                            if player.isBuffering {
                                ProgressView().tint(.white)
                            } else {
                                Image(systemName: player.isPlaying ? "pause.fill" : "play.fill")
                                    .font(.system(size: 28, weight: .semibold))
                                    .foregroundStyle(.white)
                            }
                        }
                    }

                    Button {
                        player.playNextChannel()
                    } label: {
                        Image(systemName: "forward.end.fill")
                            .font(.system(size: 22))
                            .foregroundStyle(.white)
                            .frame(width: 44, height: 44)
                    }
                    .accessibilityLabel("下一台")
                }
                .padding(.bottom, 34)
            }
            .transition(.opacity)
        }
    }
}
