package com.lucas.mobilemanager.domain.update

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

data class AppRelease(val version: String, val url: String, val size: Long, val sha256: String)

/** Valide les métadonnées avant de proposer un téléchargement. Refuse les publications incompatibles. */
object GitHubRelease {
    const val REPOSITORY = "Por-Tra/mobile-manager"
    const val MAX_APK_SIZE = 200L * 1024 * 1024
    private val json = Json { ignoreUnknownKeys = true }

    fun available(payload: String, installedVersion: String): AppRelease? {
        val release = json.decodeFromString<Release>(payload)
        require(!release.draft && !release.prerelease)
        val version = requireNotNull(ReleaseVersion.parse(release.tag))
        val installed = requireNotNull(ReleaseVersion.parse(installedVersion))
        if (version <= installed) return null
        val asset = release.assets.filter { it.name == "app-release.apk" && it.state == "uploaded" }.single()
        require(asset.size in 1..MAX_APK_SIZE)
        require(asset.url.startsWith("https://github.com/$REPOSITORY/releases/download/"))
        require(Regex("sha256:[a-fA-F0-9]{64}").matches(asset.digest))
        return AppRelease(release.tag, asset.url, asset.size, asset.digest.substringAfter(":").lowercase())
    }

    @Serializable
    private data class Release(
        @SerialName("tag_name") val tag: String,
        val draft: Boolean,
        val prerelease: Boolean,
        val assets: List<Asset>,
    )

    @Serializable
    private data class Asset(
        val name: String,
        val state: String,
        val size: Long,
        @SerialName("browser_download_url") val url: String,
        val digest: String = "",
    )
}
