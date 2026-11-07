package io.github.yulbax.erkin.vpn.connections

import io.github.yulbax.erkin.data.App
import io.github.yulbax.erkin.data.ConnectionType
import io.github.yulbax.erkin.data.RoutedApps
import io.github.yulbax.erkin.vpn.ProxyRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionLedgerTest {

    @Test
    fun hostsAreGroupedByTheirSiteAndAddressesStayAsTheyAre() {
        assertEquals("googlevideo.com", ConnectionHosts.group("rr3---sn-4g5e.googlevideo.com", "1.1.1.1"))
        assertEquals("bbc.co.uk", ConnectionHosts.group("www.bbc.co.uk", ""))
        assertEquals("github.com", ConnectionHosts.group("github.com.", ""))
        assertEquals("1.2.3.4", ConnectionHosts.group("", "1.2.3.4"))
    }

    @Test
    fun theExitOfTheConnectionTellsTheChannel() {
        assertEquals(ConnectionRoute(ConnectionType.VPN, ProxyRef.Profile(7)), ConnectionRoute.of("p7"))
        assertEquals(ConnectionRoute(ConnectionType.VPN, ProxyRef.Hop(9, 1)), ConnectionRoute.of("p9h1"))
        assertEquals(ConnectionRoute(ConnectionType.BYEDPI), ConnectionRoute.of("byedpi"))
        assertEquals(ConnectionRoute(ConnectionType.DIRECT), ConnectionRoute.of("DIRECT"))
        assertEquals(ConnectionRoute.BLOCKED, ConnectionRoute.of("REJECT"))
        assertEquals(RouteOrigin.APP_CHANNEL, RouteOrigin.of("ProcessName"))
        assertEquals(RouteOrigin.RULE, RouteOrigin.of("RuleSet"))
        assertEquals(RouteOrigin.APP_RULE, RouteOrigin.of("AND"))
    }

    @Test
    fun theCoreTotalsOfOneSiteAreAddedUpForTheApp() {
        val table = ConnectionTable.parse(
            """{"rows":[
                {"process":"$YOUTUBE","host":"rr3---sn-a.googlevideo.com","inbound":"Tun","route":"byedpi","rule":"ProcessName","up":10,"down":300,"active":1,"connections":3,"lastSeen":5},
                {"process":"$YOUTUBE","host":"rr4---sn-b.googlevideo.com","inbound":"Tun","route":"byedpi","rule":"ProcessName","up":5,"down":50,"active":0,"connections":1,"lastSeen":9},
                {"process":"$YOUTUBE","host":"ads.doubleclick.net","inbound":"Tun","route":"REJECT","connections":4,"lastSeen":7}
            ]}"""
        )
        val app = ConnectionLedger.activity(table).single()

        assertEquals(YOUTUBE, app.app)
        assertEquals(350, app.down)
        assertEquals(1, app.active)
        assertEquals(mapOf(ConnectionType.BYEDPI to 365L, ConnectionType.BLOCK to 0L), app.traffic)
        val video = app.hosts.first { it.host == "googlevideo.com" }
        assertEquals(4, video.connections)
        assertEquals(9, video.lastSeen)
        assertEquals(listOf("rr3---sn-a.googlevideo.com", "rr4---sn-b.googlevideo.com"), video.addresses.map { it.name })
        assertEquals(listOf(300L, 50L), video.addresses.map { it.down })
        assertEquals("ads.doubleclick.net", app.hosts.first { it.blocked }.single?.name)
        val ads = app.hosts.first { it.blocked }
        assertEquals("doubleclick.net", ads.host)
        assertEquals(4, ads.attempts)
    }

    @Test
    fun erkinsOwnConnectionsAndProbesAreShownAsErkinAfterTheApps() {
        val table = ConnectionTable.parse(
            """{"rows":[
                {"host":"1.1.1.1","inbound":"Inner","route":"p1","down":900,"connections":1},
                {"host":"www.cloudflare.com","inbound":"Socks5","inName":"probe-in","rule":"InName","payload":"probe-in","route":"p1","connections":1},
                {"process":"$YOUTUBE","host":"www.youtube.com","inbound":"Tun","route":"byedpi","down":10,"connections":1}
            ]}"""
        )
        val apps = ConnectionLedger.activity(table)

        assertEquals(listOf(YOUTUBE, ConnectionLedger.SERVICE_APP), apps.map { it.app })
        val service = apps.last()
        assertTrue(service.isService)
        assertEquals(setOf(ServicePurpose.CORE, ServicePurpose.REQUEST), service.hosts.map { it.purpose }.toSet())
        assertEquals(setOf(RouteOrigin.APP_CHANNEL), service.hosts.map { it.origin }.toSet())
    }

    @Test
    fun directAppsAreTunneledAndBypassAppsAreNot() {
        val apps = listOf(App("org.direct", "Direct", false, ConnectionType.DIRECT), App("org.bank", "Bank", false, ConnectionType.BYPASS))
        val routed = RoutedApps.from(apps)

        assertEquals(listOf("org.direct"), routed.directPackages)
        assertEquals(listOf("org.direct"), routed.tunneledPackages)
    }

    private companion object {
        const val YOUTUBE = "com.google.android.youtube"
    }
}
