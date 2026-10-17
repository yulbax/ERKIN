package io.github.yulbax.erkin.vpn.mihomo

import android.content.Context
import io.github.yulbax.erkin.rulelist.RuleListStore
import io.github.yulbax.erkin.vpn.core.RuleLists
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidRuleListStore(private val context: Context) : RuleListStore {
    private val directory: File get() = File(MihomoRuntime.workDir(context), RuleLists.DIRECTORY)

    override fun installed(fileName: String): File = File(directory, fileName)

    override fun stagingDir(): File = File(context.cacheDir, RuleLists.DIRECTORY)

    override suspend fun install(files: Map<String, File?>) = withContext(Dispatchers.IO) {
        directory.mkdirs()
        files.forEach { (name, file) ->
            val target = File(directory, name)
            if (file == null) {
                target.delete()
            } else {
                file.copyTo(target, overwrite = true)
            }
        }
    }
}
