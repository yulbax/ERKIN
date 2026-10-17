package io.github.yulbax.erkin.desktop

import io.github.yulbax.erkin.rulelist.InstalledSet
import io.github.yulbax.erkin.rulelist.RuleListStore
import io.github.yulbax.erkin.vpn.core.RuleLists
import io.github.yulbax.erkin.vpn.core.RuleSetFormat
import io.github.yulbax.erkin.vpn.core.RuleSetKind
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DesktopRuleListStore(private val service: DesktopService) : RuleListStore {

    override fun installed(fileName: String): File? =
        service.peek()?.workDir?.let { File(File(it, RuleLists.DIRECTORY), fileName) }

    override fun stagingDir(): File = File(DesktopPaths.dataDir, RuleLists.DIRECTORY)

    override suspend fun install(key: String, source: File, format: RuleSetFormat, kind: RuleSetKind?): InstalledSet =
        withContext(Dispatchers.IO) { service.connect().importRuleList(key, source.absolutePath, format, kind) }

    override suspend fun remove(fileName: String) = withContext(Dispatchers.IO) {
        service.connect().removeRuleList(fileName)
    }
}
