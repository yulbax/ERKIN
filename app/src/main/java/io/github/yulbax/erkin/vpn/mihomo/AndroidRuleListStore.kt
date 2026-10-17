package io.github.yulbax.erkin.vpn.mihomo

import android.content.Context
import io.github.yulbax.erkin.rulelist.InstalledSet
import io.github.yulbax.erkin.rulelist.RuleListStore
import io.github.yulbax.erkin.vpn.core.RuleLists
import io.github.yulbax.erkin.vpn.core.RuleSetFormat
import io.github.yulbax.erkin.vpn.core.RuleSetKind
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mihomocore.Mihomocore

class AndroidRuleListStore(private val context: Context) : RuleListStore {
    private val directory: File get() = File(MihomoRuntime.workDir(context), RuleLists.DIRECTORY)

    override fun installed(fileName: String): File = File(directory, fileName)

    override fun stagingDir(): File = File(context.cacheDir, RuleLists.DIRECTORY)

    override suspend fun install(key: String, source: File, format: RuleSetFormat, kind: RuleSetKind?): InstalledSet =
        withContext(Dispatchers.IO) {
            val set = Mihomocore.installRuleSet(source.absolutePath, directory.absolutePath, key, format.wire, kind?.behavior.orEmpty())
            val installed = RuleSetKind.fromBehavior(set.behavior) ?: throw IOException("Unknown rule list kind")
            InstalledSet(installed, set.count)
        }

    override suspend fun remove(fileName: String) {
        withContext(Dispatchers.IO) { File(directory, fileName).delete() }
    }
}
