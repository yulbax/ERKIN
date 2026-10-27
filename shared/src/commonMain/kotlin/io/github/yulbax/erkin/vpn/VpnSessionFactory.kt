package io.github.yulbax.erkin.vpn

import io.github.yulbax.erkin.rulelist.RuleListDatabase
import io.github.yulbax.erkin.util.AppLog
import io.github.yulbax.erkin.vpn.core.ByeDpiProcess
import io.github.yulbax.erkin.vpn.core.ByeDpiQualityCheck
import io.github.yulbax.erkin.vpn.core.VpnEngine
import kotlinx.coroutines.CoroutineScope

class VpnSessionFactory(
    private val store: VpnSessionStore,
    private val stateRepository: VpnStateRepository,
    private val ruleLists: RuleListDatabase,
    private val downloadRoute: DownloadRoute,
    private val quality: ByeDpiQualityCheck,
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
        quality = quality,
        log = log,
        ownPackage = ownPackage,
        scope = scope,
        requestWork = controller::requestWork,
        onByeDpiExit = controller::onByeDpiExit,
        ruleLists = ruleLists::installed,
        downloadRoute = downloadRoute
    )
}
