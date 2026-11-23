package com.crosspoint.launcher.util

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.URL

/** Downloads the latest Aurora Store from F-Droid and verifies F-Droid's signature before installing. */
object AuroraInstaller {
    const val PACKAGE = "com.aurora.store"
    // SHA-256 of the certificate F-Droid signs its Aurora Store builds with.
    private const val FDROID_CERT_SHA256 = "5c83c7672b929955dc0a1db89a5e6ae4389e2eae7ec939956041694e5815f532"

    suspend fun download(context: Context, onProgress: (Int) -> Unit): File {
        val code = withContext(Dispatchers.IO) {
            JSONObject(URL("https://f-droid.org/api/v1/packages/$PACKAGE").readText()).getInt("suggestedVersionCode")
        }
        val file = File(context.filesDir, "aurora-store.apk")
        ApkInstall.download("https://f-droid.org/repo/${PACKAGE}_$code.apk", file, onProgress)
        if (!isSignedByFdroid(context, file)) {
            file.delete()
            error("Download failed verification")
        }
        return file
    }

    internal fun isSignedByFdroid(context: Context, file: File): Boolean {
        val signers = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val info = context.packageManager.getPackageArchiveInfo(file.path, PackageManager.GET_SIGNING_CERTIFICATES)
                ?: return false
            if (info.packageName != PACKAGE) return false
            info.signingInfo?.apkContentsSigners ?: return false
        } else {
            // SigningInfo needs Android 9; older versions report the same certificates as signatures.
            @Suppress("DEPRECATION")
            val info = context.packageManager.getPackageArchiveInfo(file.path, PackageManager.GET_SIGNATURES)
                ?: return false
            if (info.packageName != PACKAGE) return false
            @Suppress("DEPRECATION")
            info.signatures ?: return false
        }
        return signers.any { ApkInstall.sha256(it.toByteArray()) == FDROID_CERT_SHA256 }
    }
}
