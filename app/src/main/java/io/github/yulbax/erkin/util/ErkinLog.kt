package io.github.yulbax.erkin.util

import android.content.Context
import android.util.Log
import org.koin.core.annotation.Single
import java.io.File
import kotlinx.coroutines.flow.StateFlow

@Single(binds = [AppLog::class])
class ErkinLog(context: Context) : AppLog, LogFeed {
    private val file = FileAppLog(File(context.applicationContext.filesDir, "erkin.log")) { level, tag, text ->
        when (level) {
            LogLevel.INFO -> Log.i(tag, text)
            LogLevel.WARN -> Log.w(tag, text)
            LogLevel.ERROR -> Log.e(tag, text)
        }
    }

    override fun i(tag: String, message: String) = file.i(tag, message)

    override fun w(tag: String, message: String, t: Throwable?) = file.w(tag, message, t)

    override fun e(tag: String, message: String, t: Throwable?) = file.e(tag, message, t)

    override fun of(source: LogSource): AppLog = file.of(source)

    override val entries: StateFlow<List<String>> get() = file.entries

    fun dump(): String = file.dump()

    fun clear() = file.clear()
}
