package io.github.yulbax.erkin.proxy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ShareLinkTest {

    private fun parse(input: String): ShareLink = requireNotNull(ShareLink.parse(input)) { input }

    @Test
    fun theNameKeepsSpacesQuotesEmojiAndFurtherHashes() {
        assertEquals("""My "best" server #2 🚀""", parse("""vless://id@h.example:443#My "best" server #2 🚀""").name)
    }

    @Test
    fun theNameIsPercentDecodedAndPlusStaysPlus() {
        assertEquals("a b+c", parse("trojan://p@h:443#a%20b+c").name)
    }

    @Test
    fun theNameEndsAtTheLineBreak() {
        val link = parse("trojan://p@h:443#Name\r\ntrojan://q@g:443#Other")
        assertEquals("Name", link.name)
    }

    @Test
    fun anEmptyNameIsAbsent() {
        assertNull(parse("trojan://p@h:443#").name)
        assertNull(parse("trojan://p@h:443").name)
    }

    @Test
    fun htmlEscapedAmpersandsSeparateQueryItems() {
        val link = parse("vless://id@h:443?security=tls&amp;sni=s.example&amp;type=ws#n")
        assertEquals(mapOf("security" to "tls", "sni" to "s.example", "type" to "ws"), link.query)
    }

    @Test
    fun anUnencodedAtInThePasswordBelongsToThePassword() {
        val link = parse("socks5://alice:p@ss@203.0.113.7:1080#n")
        assertEquals("alice", link.user)
        assertEquals("p@ss", link.password)
        assertEquals("203.0.113.7", link.host)
        assertEquals(1080, link.port)
    }

    @Test
    fun aBrokenPercentSequenceStaysLiteral() {
        assertEquals("a%zzb", parse("vless://id@h:443?x=a%zzb").query["x"])
        assertEquals("100%", parse("vless://id@h:443?x=100%").query["x"])
    }

    @Test
    fun aPlusInAQueryValueIsASpaceButNotInUserInfo() {
        val link = parse("vless://a+b@h:443?x=a+b")
        assertEquals("a b", link.query["x"])
        assertEquals("a+b", link.user)
    }

    @Test
    fun anIpv6LiteralWithAndWithoutAPort() {
        val withPort = parse("trojan://p@[2001:db8::1]:8443#n")
        assertEquals("2001:db8::1", withPort.host)
        assertEquals(8443, withPort.port)
        val withoutPort = parse("trojan://p@[2001:db8::1]#n")
        assertEquals("2001:db8::1", withoutPort.host)
        assertNull(withoutPort.port)
    }

    @Test
    fun aPortSpecIsKeptVerbatim() {
        val link = parse("hysteria2://p@h.example:443,20000-30000/?sni=x")
        assertEquals("h.example", link.host)
        assertEquals("443,20000-30000", link.portSpec)
        assertEquals(443, link.port)
        assertEquals("20000-30000", parse("hysteria2://p@h:20000-30000").portSpec)
    }

    @Test
    fun aMissingPortLeavesTheWholeAuthorityAsHost() {
        val link = parse("trojan://p@h.example")
        assertEquals("h.example", link.host)
        assertNull(link.portSpec)
        assertNull(link.port)
    }

    @Test
    fun theEmptyPathDiffersFromTheRoot() {
        assertEquals("", parse("anytls://p@h:443?sni=x").path)
        assertEquals("/", parse("anytls://p@h:443/?sni=x").path)
        assertEquals("/a/b%20c", parse("anytls://p@h:443/a/b%20c?sni=x").path)
    }

    @Test
    fun theFirstDuplicateQueryKeyWins() {
        assertEquals("1", parse("vless://id@h:443?a=1&a=2&A=3").query["a"])
        assertEquals("3", parse("vless://id@h:443?a=1&a=2&A=3").query["A"])
    }

    @Test
    fun emptyQueryKeysAreSkipped() {
        assertEquals(mapOf("a" to "1"), parse("vless://id@h:443?&=x&a=1&").query)
    }

    @Test
    fun theBodyIsTheTextBeforeTheFirstHash() {
        assertEquals("eyJ2IjoyfQ==", parse("vmess://eyJ2IjoyfQ==#Name").body)
    }

    @Test
    fun textWithoutASchemeSeparatorIsNotALink() {
        assertNull(ShareLink.parse("just text"))
        assertNull(ShareLink.parse("://host"))
        assertNull(ShareLink.parse("bad scheme://host"))
        assertNull(ShareLink.parse("line one\nvless://id@h:443"))
    }

    @Test
    fun theSchemeIsLowercased() {
        assertEquals("vless", parse("VLESS://id@h:443").scheme)
    }

    @Test
    fun formatRoundTripsEveryField() {
        listOf(
            "vless://0b3f1a2c@vl.example.com:443?security=tls&sni=vl.example.com&type=ws&path=%2Fws%3Fed%3D2048#Amsterdam",
            """trojan://p@ss word@h.example:443?x=a b&y=%zz#My "best" #2 🚀""",
            "socks5://alice:p@ss@203.0.113.7:1080#Vilnius",
            "hysteria2://secret@hy.example.net:443,20000-30000/?sni=hy.example.net&insecure=1#Helsinki",
            "trojan://p@[2001:db8::1]:8443#V6",
            "trojan://p@[2001:db8::1]#V6",
            "anytls://p@h.example?sni=x",
            "tuic://0b3f:s%3Acret@tu.example.net:443?alpn=h3&congestion_control=bbr#Tallinn",
            "ss://YWVzLTI1Ni1nY206c2VjcmV0@203.0.113.5:8388/?plugin=obfs-local%3Bobfs%3Dhttp#Oslo",
            "wireguard://a%3D@wg.example.com:51820?address=10.9.0.2%2F32&mtu=1280#Warsaw",
            "vless://id@h:443/a/b?e=%E2%9C%93#Ünï cödé",
            "trojan://@h:443#empty user"
        ).forEach { input ->
            val first = parse(input)
            val canonical = first.format()
            val second = parse(canonical)
            assertEquals(input, first.copy(body = second.body), second)
            assertEquals(input, canonical, second.format())
        }
    }

    @Test
    fun formatKeepsABodyOnlyLinkAsItIs() {
        val link = parse("vmess://eyJ2IjoyfQ==#My name")
        assertEquals("vmess://eyJ2IjoyfQ==#My%20name", link.copy(host = "").format())
        assertNotNull(ShareLink.parse(link.copy(host = "").format()))
    }

    @Test
    fun formatEncodesEverythingButUnreservedCharacters() {
        val link = ShareLink("x", "", "u s", "p:w", "h", "1", "", mapOf("k e" to "v&=~"), "n#")
        assertEquals("x://u%20s:p%3Aw@h:1?k%20e=v%26%3D~#n%23", link.format())
    }
}
