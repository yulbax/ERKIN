package io.github.yulbax.erkin.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InstalledAppsRepositoryTest {
    @Test
    fun directRoutingUsesTheSharedPackageSegmentPolicy() {
        assertTrue(InstalledAppsRepository.usesDirectRouting("ru.example.app"))
        assertTrue(InstalledAppsRepository.usesDirectRouting("com.yandex.browser"))
        assertTrue(InstalledAppsRepository.usesDirectRouting("com.vkontakte.app"))
        assertTrue(InstalledAppsRepository.usesDirectRouting("com.vk.app"))
    }

    @Test
    fun directRoutingMatchesCaseInsensitivelyButNotPartialSegments() {
        assertFalse(InstalledAppsRepository.usesDirectRouting("com.example.vkclient"))
        assertFalse(InstalledAppsRepository.usesDirectRouting("com.rustore.app"))
        assertTrue(InstalledAppsRepository.usesDirectRouting("COM.YANDEX.BROWSER"))
    }
}
