import SwiftUI

/// 收藏页：收藏的频道 + 最近播放。数据全部来自频道 id，刷新播放列表不会丢收藏。
struct FavoritesView: View {
    @EnvironmentObject private var playlists: PlaylistStore
    @EnvironmentObject private var favorites: FavoritesStore
    @EnvironmentObject private var epg: EPGStore
    @EnvironmentObject private var player: PlaybackEngine

    @State private var showPlayer = false

    var body: some View {
        NavigationStack {
            Group {
                if favoriteChannels.isEmpty {
                    emptyState
                } else {
                    List {
                        Section {
                            ForEach(favoriteChannels) { channel in
                                ChannelRow(
                                    channel: channel,
                                    isFavorite: true,
                                    isPlaying: player.current?.id == channel.id,
                                    nowPlayingTitle: epg.now(on: channel)?.title,
                                    onTap: { play(channel) },
                                    onToggleFavorite: { favorites.toggle(channel) }
                                )
                            }
                            .onDelete(perform: removeFavorites)
                        } header: {
                            Text("\(favoriteChannels.count) 个收藏频道")
                        }
                    }
                    .listStyle(.plain)
                }
            }
            .navigationTitle("收藏")
            .toolbar {
                if !favoriteChannels.isEmpty {
                    ToolbarItem(placement: .navigationBarTrailing) {
                        EditButton()
                    }
                }
            }
            .fullScreenCover(isPresented: $showPlayer) {
                PlayerView()
            }
        }
    }

    private var favoriteChannels: [Channel] {
        playlists.allChannels.filter { favorites.contains($0) }
    }

    private var emptyState: some View {
        VStack(spacing: 14) {
            Image(systemName: "star")
                .font(.system(size: 44, weight: .light))
                .foregroundStyle(.secondary)
            Text("还没有收藏频道")
                .font(.title3.weight(.semibold))
            Text("在频道列表里点右侧的星标，就能把常看的频道留在这里，CarPlay 也会优先显示。")
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 32)
        }
        .padding()
    }

    private func play(_ channel: Channel) {
        player.play(channel)
        showPlayer = true
    }

    private func removeFavorites(at offsets: IndexSet) {
        let channels = favoriteChannels
        for index in offsets where index < channels.count {
            if favorites.contains(channels[index]) {
                favorites.toggle(channels[index])
            }
        }
    }
}
