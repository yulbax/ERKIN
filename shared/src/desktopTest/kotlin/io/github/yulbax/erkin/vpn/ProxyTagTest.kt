package io.github.yulbax.erkin.vpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProxyTagTest {

    @Test
    fun everyTagTheBuilderWritesIsReadBack() {
        assertEquals(ProxyRef.Profile(5), ProxyTag.parse(ProxyTag.of(5)))
        assertEquals(ProxyRef.Hop(9, 1), ProxyTag.parse(ProxyTag.hop(9, 1)))
        assertEquals(5L, ProxyTag.profileId(ProxyTag.of(5)))
        assertNull(ProxyTag.profileId(ProxyTag.hop(9, 1)))
    }

    @Test
    fun otherNamesAreNotServers() {
        assertNull(ProxyTag.parse("DIRECT"))
        assertNull(ProxyTag.parse("probe-in"))
        assertNull(ProxyTag.parse("p9hx"))
    }
}
