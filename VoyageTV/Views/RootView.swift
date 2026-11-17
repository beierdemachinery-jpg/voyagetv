import SwiftUI

struct RootView: View {
    var body: some View {
        TabView {
            ChannelListView()
                .tabItem { Label("频道", systemImage: "tv") }

            FavoritesView()
                .tabItem { Label("收藏", systemImage: "star") }

            SettingsView()
                .tabItem { Label("设置", systemImage: "gearshape") }
        }
    }
}

struct RootView_Previews: PreviewProvider {
    static var previews: some View {
        RootView()
            .environmentObject(AppContainer.shared.playlists)
            .environmentObject(AppContainer.shared.favorites)
            .environmentObject(AppContainer.shared.recents)
            .environmentObject(AppContainer.shared.epg)
            .environmentObject(AppContainer.shared.player)
            .environmentObject(AppContainer.shared.carPlaySession)
    }
}
