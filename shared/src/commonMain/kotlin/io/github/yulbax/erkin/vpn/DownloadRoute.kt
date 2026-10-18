package io.github.yulbax.erkin.vpn

import java.io.EOFException
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.URI
import java.net.UnknownHostException
import javax.net.ssl.SSLException

class DownloadRoute {
    @Volatile var tunnel: SocksEndpoint? = null

    fun <T> fetch(url: String, read: (HttpURLConnection) -> T): T {
        val endpoint = tunnel ?: return read(direct(url))
        return try {
            read(SocksHttp.open(url, endpoint))
        } catch (viaTunnel: IOException) {
            if (!viaTunnel.isNetworkFailure()) throw viaTunnel
            try {
                read(direct(url))
            } catch (direct: IOException) {
                direct.addSuppressed(viaTunnel)
                throw direct
            }
        }
    }

    private fun direct(url: String): HttpURLConnection = URI(url).toURL().openConnection() as HttpURLConnection

    private fun IOException.isNetworkFailure(): Boolean =
        this is SocketException || this is SocketTimeoutException || this is UnknownHostException ||
            this is SSLException || this is EOFException
}
