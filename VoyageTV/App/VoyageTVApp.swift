import SwiftUI

@main
struct VoyageTVApp: App {
    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(AppContainer.shared.playlists)
                .environmentObject(AppContainer.shared.favorites)
                .environmentObject(AppContainer.shared.recents)
                .environmentObject(AppContainer.shared.epg)
                .environmentObject(AppContainer.shared.player)
                .environmentObject(AppContainer.shared.carPlaySession)
                .task {
                    await AppContainer.shared.epg.adoptSourceFromPlaylists(AppContainer.shared.playlists)
                    await AppContainer.shared.epg.refreshIfNeeded()
                }
        }
    }
}
