package com.crosspoint.launcher.util

import android.content.Context
import android.os.Build
import com.crosspoint.launcher.R
import java.security.KeyStore
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * Android before 7.1.1 does not trust ISRG Root X1, the Let's Encrypt root behind the sync server.
 * There, HTTPS falls back to the bundled root when the system store rejects a chain.
 */
object LegacyTls {
    fun install(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) return
        runCatching {
            val root = context.resources.openRawResource(R.raw.isrg_root_x1).use {
                CertificateFactory.getInstance("X.509").generateCertificate(it)
            }
            val bundled = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
                load(null, null)
                setCertificateEntry("isrg_root_x1", root)
            }
            val system = trustManager(null)
            val extra = trustManager(bundled)
            val combined = object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) =
                    system.checkClientTrusted(chain, authType)

                override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
                    try {
                        system.checkServerTrusted(chain, authType)
                    } catch (e: CertificateException) {
                        extra.checkServerTrusted(chain, authType)
                    }
                }

                override fun getAcceptedIssuers(): Array<X509Certificate> =
                    system.acceptedIssuers + extra.acceptedIssuers
            }
            val tls = SSLContext.getInstance("TLS").apply { init(null, arrayOf(combined), null) }
            HttpsURLConnection.setDefaultSSLSocketFactory(tls.socketFactory)
        }
    }

    private fun trustManager(store: KeyStore?): X509TrustManager =
        TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            .apply { init(store) }
            .trustManagers.filterIsInstance<X509TrustManager>().first()
}
