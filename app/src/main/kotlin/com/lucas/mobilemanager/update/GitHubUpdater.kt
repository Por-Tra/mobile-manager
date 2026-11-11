package com.lucas.mobilemanager.update

import android.content.Context
import android.content.pm.PackageManager
import com.lucas.mobilemanager.BuildConfig
import com.lucas.mobilemanager.domain.update.AppRelease
import com.lucas.mobilemanager.domain.update.GitHubRelease
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Requêtes explicites uniquement. Aucune donnée d'usage, aucun identifiant ni secret transmis. */
class GitHubUpdater @Inject constructor(@ApplicationContext private val context: Context) {
    suspend fun latest(): AppRelease? = withContext(Dispatchers.IO) {
        val connection = connect("https://api.github.com/repos/$REPOSITORY/releases/latest")
        try {
            if (connection.responseCode == 404) throw NoReleaseException()
            check(connection.responseCode == 200) { "GitHub HTTP ${connection.responseCode}" }
            val payload = connection.inputStream.use { stream ->
                val bytes = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = stream.read(buffer)
                    if (count < 0) break
                    check(bytes.size() + count <= 1_048_576)
                    bytes.write(buffer, 0, count)
                }
                bytes.toString("UTF-8")
            }
            try {
                GitHubRelease.available(payload, BuildConfig.VERSION_NAME)
            } catch (error: IllegalArgumentException) {
                throw IncompatibleReleaseException()
            } catch (error: NoSuchElementException) {
                throw IncompatibleReleaseException()
            }
        } finally { connection.disconnect() }
    }

    suspend fun download(release: AppRelease, progress: (Int) -> Unit): File = withContext(Dispatchers.IO) {
        val directory = File(context.cacheDir, "updates").apply { mkdirs() }
        val partial = File(directory, "update.part")
        val apk = File(directory, "update.apk")
        apk.delete()
        try {
            val connection = connect(release.url)
            try {
                check(connection.responseCode == 200)
                val digest = MessageDigest.getInstance("SHA-256")
                var total = 0L
                connection.inputStream.use { input ->
                    partial.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var lastProgress = -1
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            total += count
                            check(total <= release.size && total <= MAX_APK_SIZE)
                            digest.update(buffer, 0, count)
                            output.write(buffer, 0, count)
                            val percent = (total * 100 / release.size).toInt()
                            if (percent != lastProgress) { progress(percent); lastProgress = percent }
                        }
                    }
                }
                check(total == release.size)
                check(digest.digest().joinToString("") { "%02x".format(it) } == release.sha256)
            } finally { connection.disconnect() }
            validateApk(partial)
            check(partial.renameTo(apk))
            apk
        } finally { partial.delete() }
    }

    @Suppress("DEPRECATION")
    fun validateApk(file: File) {
        val pm = context.packageManager
        val candidate = pm.getPackageArchiveInfo(file.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES)
            ?: error("Invalid APK")
        val installed = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        check(candidate.packageName == context.packageName && candidate.longVersionCode > installed.longVersionCode)
        val expected = installed.signingInfo?.apkContentsSigners?.map { it.toCharsString() }?.toSet()
        val actual = candidate.signingInfo?.apkContentsSigners?.map { it.toCharsString() }?.toSet()
        check(!expected.isNullOrEmpty() && expected == actual) { "Signing certificate mismatch" }
    }

    private fun connect(address: String): HttpURLConnection {
        var url = URL(address)
        repeat(6) {
            check(url.protocol == "https" && url.port in listOf(-1, 443))
            check(url.host in setOf("api.github.com", "github.com", "release-assets.githubusercontent.com", "objects.githubusercontent.com"))
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                instanceFollowRedirects = false
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "MobileManager/${BuildConfig.VERSION_NAME}")
            }
            try {
                if (connection.responseCode !in listOf(301, 302, 303, 307, 308)) return connection
                url = URL(url, connection.getHeaderField("Location") ?: error("Missing redirect"))
            } catch (error: Exception) {
                connection.disconnect()
                throw error
            }
            connection.disconnect()
        }
        error("Too many redirects")
    }

    companion object {
        const val REPOSITORY = GitHubRelease.REPOSITORY
        private const val MAX_APK_SIZE = GitHubRelease.MAX_APK_SIZE
    }
}

class NoReleaseException : Exception()
class IncompatibleReleaseException : Exception()
