package com.lucas.mobilemanager.domain.update

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class GitHubReleaseTest {
    private val digest = "a".repeat(64)
    private fun asset(url: String = "https://github.com/Por-Tra/mobile-manager/releases/download/v0.2.1/app-release.apk", size: Long = 1024, hash: String = "sha256:$digest") =
        """{"name":"app-release.apk","state":"uploaded","size":$size,"browser_download_url":"$url","digest":"$hash"}"""
    private fun release(assets: String = asset(), tag: String = "v0.2.1", draft: Boolean = false, prerelease: Boolean = false) =
        """{"tag_name":"$tag","draft":$draft,"prerelease":$prerelease,"assets":[$assets],"body":"Ignored release notes"}"""

    @Test fun `offers a newer stable APK and normalizes its digest`() {
        val result = GitHubRelease.available(release(assets = asset(hash = "sha256:${digest.uppercase()}")), "0.2.0")!!
        assertEquals("v0.2.1", result.version)
        assertEquals(1024, result.size)
        assertEquals(digest, result.sha256)
    }
    @Test fun `does not offer current or older versions`() {
        assertNull(GitHubRelease.available(release(tag = "v0.2.0"), "0.2.0"))
        assertNull(GitHubRelease.available(release(tag = "v0.1.9"), "0.2.0"))
    }
    @Test fun `rejects drafts prereleases and invalid tags`() {
        listOf(release(draft = true), release(prerelease = true), release(tag = "v0.2.1-rc1"), release(tag = "latest")).forEach {
            assertThrows(IllegalArgumentException::class.java) { GitHubRelease.available(it, "0.2.0") }
        }
    }
    @Test fun `rejects absent or ambiguous APK assets`() {
        assertThrows(NoSuchElementException::class.java) { GitHubRelease.available(release(assets = ""), "0.2.0") }
        assertThrows(IllegalArgumentException::class.java) { GitHubRelease.available(release(assets = "${asset()},${asset()}"), "0.2.0") }
    }
    @Test fun `rejects untrusted URLs missing hashes and invalid sizes`() {
        listOf(
            asset(url = "http://github.com/Por-Tra/mobile-manager/releases/download/v0.2.1/app-release.apk"),
            asset(url = "https://github.com/attacker/app/releases/download/v0.2.1/app-release.apk"),
            asset(url = "https://github.com.evil.test/Por-Tra/mobile-manager/releases/download/a.apk"),
            asset(hash = ""), asset(hash = "sha256:1234"), asset(size = 0), asset(size = GitHubRelease.MAX_APK_SIZE + 1),
        ).forEach {
            assertThrows(IllegalArgumentException::class.java) { GitHubRelease.available(release(assets = it), "0.2.0") }
        }
    }
}
