import XCTest
@testable import VoyageTV

final class M3UParserTests: XCTestCase {

    private let base = URL(string: "https://example.com/iptv/list.m3u")!

    func testParsesAttributesAndTitleWithCommaInGroup() {
        let text = """
        #EXTM3U
        #EXTINF:-1 tvg-id="cctv1" tvg-name="CCTV-1" tvg-logo="https://example.com/1.png" group-title="News, World",CCTV-1 综合
        https://example.com/live/cctv1.m3u8
        """
        let output = M3UParser.parse(text, baseURL: base)
        XCTAssertEqual(output.channels.count, 1)
        let channel = output.channels[0]
        XCTAssertEqual(channel.name, "CCTV-1 综合")
        XCTAssertEqual(channel.displayGroup, "News, World")
        XCTAssertEqual(channel.tvgID, "cctv1")
        XCTAssertEqual(channel.tvgName, "CCTV-1")
        XCTAssertEqual(channel.logoURL?.absoluteString, "https://example.com/1.png")
        XCTAssertEqual(channel.streamURL.absoluteString, "https://example.com/live/cctv1.m3u8")
    }

    func testExtGrpOverridesGroup() {
        let text = """
        #EXTM3U
        #EXTINF:-1,CCTV-2
        #EXTGRP:央视
        https://example.com/live/cctv2.m3u8
        """
        let output = M3UParser.parse(text, baseURL: base)
        XCTAssertEqual(output.channels.first?.displayGroup, "央视")
    }

    func testResolvesRelativeURL() {
        let text = """
        #EXTM3U
        #EXTINF:-1,本地频道
        streams/local.m3u8
        """
        let output = M3UParser.parse(text, baseURL: base)
        XCTAssertEqual(output.channels.first?.streamURL.absoluteString, "https://example.com/iptv/streams/local.m3u8")
    }

    func testVLCHeadersMappedToHTTPHeaders() {
        let text = """
        #EXTM3U
        #EXTINF:-1,带防盗链的源
        #EXTVLCOPT:http-user-agent=VLC/3.0
        #EXTVLCOPT:http-referrer=https://example.com/
        https://example.com/live/auth.m3u8
        """
        let output = M3UParser.parse(text, baseURL: base)
        let headers = output.channels.first?.httpHeaders ?? [:]
        XCTAssertEqual(headers["User-Agent"], "VLC/3.0")
        XCTAssertEqual(headers["Referer"], "https://example.com/")
    }

    func testGlobalUserAgentAppliesToAllChannels() {
        let text = """
        #EXTM3U http-user-agent="PlayerUA/1.0"
        #EXTINF:-1,频道A
        https://example.com/a.m3u8
        #EXTINF:-1,频道B
        https://example.com/b.m3u8
        """
        let output = M3UParser.parse(text, baseURL: base)
        XCTAssertEqual(output.channels.count, 2)
        for channel in output.channels {
            XCTAssertEqual(channel.httpHeaders["User-Agent"], "PlayerUA/1.0")
        }
    }

    func testDuplicateStreamDropped() {
        let text = """
        #EXTM3U
        #EXTINF:-1,频道A
        https://example.com/a.m3u8
        #EXTINF:-1,频道A
        https://example.com/a.m3u8
        """
        let output = M3UParser.parse(text, baseURL: base)
        XCTAssertEqual(output.channels.count, 1)
    }

    func testHLSMediaPlaylistIsDetectedNotParsedAsChannels() {
        let text = """
        #EXTM3U
        #EXT-X-VERSION:3
        #EXT-X-TARGETDURATION:6
        #EXTINF:6.0,
        segment0.ts
        """
        let output = M3UParser.parse(text, baseURL: base)
        XCTAssertTrue(output.isHLSMediaPlaylist)
        XCTAssertTrue(output.channels.isEmpty)
    }

    func testBOMAndCRLFHandled() {
        let text = "\u{FEFF}#EXTM3U\r\n#EXTINF:-1,频道\r\nhttps://example.com/a.m3u8\r\n"
        let output = M3UParser.parse(text, baseURL: nil)
        XCTAssertEqual(output.channels.count, 1)
        XCTAssertEqual(output.channels.first?.name, "频道")
    }

    func testMissingNameFallsBackToFileName() {
        let text = """
        #EXTM3U
        #EXTINF:-1,
        https://example.com/live/channel-42.m3u8
        """
        let output = M3UParser.parse(text, baseURL: base)
        XCTAssertEqual(output.channels.first?.name, "channel-42.m3u8")
    }

    func testStableIDKeepsFavoritesAcrossReimport() {
        let first = M3UParser.parse("#EXTM3U\n#EXTINF:-1,频道\nhttps://example.com/a.m3u8", baseURL: base)
        let second = M3UParser.parse("#EXTM3U\n#EXTINF:-1 tvg-id=\"x\",频道\nhttps://example.com/a.m3u8", baseURL: base)
        XCTAssertEqual(first.channels.first?.id, second.channels.first?.id)
    }

    func testAttributeParserHandlesSpacesInsideQuotes() {
        let attributes = M3UParser.parseAttributes("-1 tvg-id=\"a b\" group-title=\"Kids & Family\" tvg-chno=12")
        XCTAssertEqual(attributes["tvg-id"], "a b")
        XCTAssertEqual(attributes["group-title"], "Kids & Family")
        XCTAssertEqual(attributes["tvg-chno"], "12")
    }
}
