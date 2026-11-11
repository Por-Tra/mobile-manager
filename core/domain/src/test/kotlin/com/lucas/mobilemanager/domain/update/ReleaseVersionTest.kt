package com.lucas.mobilemanager.domain.update

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ReleaseVersionTest {
    @Test fun `compares numeric components rather than strings`() {
        assertTrue(ReleaseVersion.parse("v1.10.0")!! > ReleaseVersion.parse("1.9.9")!!)
        assertTrue(ReleaseVersion.parse("2.0.0")!! > ReleaseVersion.parse("1.99.99")!!)
        assertEquals(ReleaseVersion.parse("1.2.3"), ReleaseVersion.parse("v1.2.3+build.12"))
    }
    @Test fun `rejects prereleases malformed tags and overflow`() {
        listOf("1.0.0-beta", "latest", "1.2", "01.2.3", "999999999999.0.0", "1.0.0\n").forEach {
            assertNull(ReleaseVersion.parse(it), it)
        }
    }
}
