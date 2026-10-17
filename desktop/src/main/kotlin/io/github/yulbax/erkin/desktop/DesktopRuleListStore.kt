package io.github.yulbax.erkin.desktop

import io.github.yulbax.erkin.rulelist.RuleListStore
import io.github.yulbax.erkin.vpn.core.RuleLists
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DesktopRuleListStore(private val service: DesktopService) : RuleListStore {

    override fun installed(fileName: String): File? =
        service.peek()?.workDir?.let { File(File(it, RuleLists.DIRECTORY), fileName) }

    override fun stagingDir(): File = File(DesktopPaths.dataDir, RuleLists.DIRECTORY)

    override suspend fun install(files: Map<String, File?>) = withContext(Dispatchers.IO) {
        val client = service.connect()
        files.forEach { (name, file) -> client.importRuleList(name, file?.absolutePath) }
    }
}
