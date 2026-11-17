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

    /// 订阅头里带 x-tvg-url（真实源站：nas.vipgoodmedical.com）时，节目单地址要被采集到。
    func testReadsEPGURLFromHeader() {
        let text = """
        #EXTM3U x-tvg-url="https://epg.catvod.com/epg.xml"
        #EXTINF:-1 tvg-name="CCTV1" group-title="未分组",CCTV1
        http://nas.example.com:1122/ysp.php?id=cctv1
        """
        let output = M3UParser.parse(text, baseURL: base)
        XCTAssertEqual(output.epgURL?.absoluteString, "https://epg.catvod.com/epg.xml")
        XCTAssertEqual(output.channels.count, 1)
        // 没有 .m3u8 后缀的转发接口也要原样保留
        XCTAssertEqual(output.channels[0].streamURL.absoluteString, "http://nas.example.com:1122/ysp.php?id=cctv1")
    }

    /// 多个节目单地址（逗号分隔）时取第一个。
    func testReadsFirstOfMultipleEPGURLs() {
        let text = """
        #EXTM3U url-tvg="https://a.example.com/1.xml,https://b.example.com/2.xml"
        #EXTINF:-1,频道
        https://example.com/a.m3u8
        """
        let output = M3UParser.parse(text, baseURL: base)
        XCTAssertEqual(output.epgURL?.absoluteString, "https://a.example.com/1.xml")
    }

    /// 无后缀 / 非 .m3u8 的 http 地址要走资源加载器（修 Content-Type），
    /// 正常 .m3u8 与本地文件不走，避免无谓绕行。
    func testOnlyNonM3U8RemoteURLsNeedResourceLoader() {
        XCTAssertTrue(HLSResourceLoader.needsLoader(for: URL(string: "http://nas.example.com:1122/ysp.php?id=cctv1")!))
        XCTAssertFalse(HLSResourceLoader.needsLoader(for: URL(string: "https://example.com/live/a.m3u8")!))
        XCTAssertFalse(HLSResourceLoader.needsLoader(for: URL(string: "file:///tmp/a.m3u")!))
    }

    /// 实测源站（ysp.php）的清单带私有标签，AVFoundation 会判成不可播（-12642）；
    /// 规范化后必须只留下标准标签，同时把相对地址补成绝对。
    func testPlaylistNormalizationDropsPrivateTags() {
        let text = """
        #EXTM3U
        #EXT-X-REFRESH:21
        #EXT-X-VERSION:3
        #EXT-X-ALLOW-CACHE:NO
        #EXT-X-TARGETDURATION:7
        #EXT-X-MEDIA-SEQUENCE:1786664719
        #EXT-QQHLS-MACHINEID:11111
        #EXT-SID:1786664719
        #EXT-X-PROGRAM-DATE-TIME:2026-11-17T12:46:12+08:00
        #EXTINF:5.000,
        http://hlsliveali-cdn.ysp.cctv.cn/abc/2024078202-1786664719.ts
        """
        let data = text.data(using: .utf8)!
        let base = URL(string: "http://nas.example.com:1122/ysp.php?id=cctv1")!
        let out = HLSResourceLoader.rewritePlaylist(data, base: base)!
        let result = String(data: out, encoding: .utf8)!

        XCTAssertTrue(result.hasPrefix("#EXTM3U"))
        XCTAssertTrue(result.contains("#EXT-X-TARGETDURATION:7"))
        XCTAssertTrue(result.contains("#EXT-X-MEDIA-SEQUENCE:1786664719"))
        XCTAssertTrue(result.contains("#EXTINF:5.000,"))
        XCTAssertTrue(result.contains("http://hlsliveali-cdn.ysp.cctv.cn/abc/2024078202-1786664719.ts"))
        XCTAssertFalse(result.contains("EXT-X-REFRESH"))
        XCTAssertFalse(result.contains("EXT-QQHLS"))
        XCTAssertFalse(result.contains("EXT-SID"))
        XCTAssertFalse(result.contains("PROGRAM-DATE-TIME"))
        XCTAssertFalse(result.contains("ALLOW-CACHE"))
    }

    /// 相对地址的分片要按清单地址补成绝对。
    func testPlaylistNormalizationResolvesRelativeSegment() {
        let text = """
        #EXTM3U
        #EXT-X-TARGETDURATION:6
        #EXTINF:5.000,
        seg/1.ts
        """
        let data = text.data(using: .utf8)!
        let base = URL(string: "http://nas.example.com:1122/live/index.m3u8")!
        let out = HLSResourceLoader.rewritePlaylist(data, base: base)!
        let result = String(data: out, encoding: .utf8)!
        XCTAssertTrue(result.contains("http://nas.example.com:1122/live/seg/1.ts"))
    }

    /// 主清单（含 #EXT-X-STREAM-INF）不能被裁剪，只做地址补全。
    func testMasterPlaylistIsNotStripped() {
        let text = """
        #EXTM3U
        #EXT-X-STREAM-INF:BANDWIDTH=1280000,RESOLUTION=640x360
        low/index.m3u8
        """
        let data = text.data(using: .utf8)!
        let base = URL(string: "http://nas.example.com:1122/master.m3u8")!
        let out = HLSResourceLoader.rewritePlaylist(data, base: base)!
        let result = String(data: out, encoding: .utf8)!
        XCTAssertTrue(result.contains("#EXT-X-STREAM-INF:BANDWIDTH=1280000,RESOLUTION=640x360"))
        XCTAssertTrue(result.contains("http://nas.example.com:1122/low/index.m3u8"))
    }
}
