package io.github.yulbax.erkin.vpn

import io.github.yulbax.erkin.geoip.GeoIpDatabase
import io.github.yulbax.erkin.rulelist.RuleListDatabase
import io.github.yulbax.erkin.util.AppLog
import io.github.yulbax.erkin.vpn.core.ByeDpiProcess
import io.github.yulbax.erkin.vpn.core.VpnEngine
import kotlinx.coroutines.CoroutineScope

class VpnSessionFactory(
    private val store: VpnSessionStore,
    private val stateRepository: VpnStateRepository,
    private val geoIp: GeoIpDatabase,
    private val ruleLists: RuleListDatabase,
    private val log: AppLog
) {
    fun create(
        controller: VpnController,
        scope: CoroutineScope,
        engine: VpnEngine,
        byeDpi: ByeDpiProcess,
        byeDpiHealthProbeUrl: String,
        ownPackage: String
    ): VpnSession = VpnSession(
        engine = engine,
        byeDpi = byeDpi,
        store = store,
        stateRepository = stateRepository,
        health = HealthMonitor(stateRepository, log, SocksHealthProbe(byeDpiHealthProbeUrl)),
        quality = SocksSiteProbe,
        log = log,
        ownPackage = ownPackage,
        scope = scope,
        requestWork = controller::requestWork,
        onByeDpiExit = controller::onByeDpiExit,
        geoIpReady = { geoIp.isReady },
        ruleLists = ruleLists::installed
    )
}
