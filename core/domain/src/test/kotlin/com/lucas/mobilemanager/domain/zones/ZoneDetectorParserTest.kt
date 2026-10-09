package com.lucas.mobilemanager.domain.zones

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ZoneDetectorParserTest {

    private fun zoneJson(anyOf: String = """[{"viewId":"x"}]""", exit: String = """[{"back":true}]""", version: Int = 1) = """
        {"version": $version, "apps": [{"package": "pkg", "zones": [
          {"id": "z", "label": "Z", "anyOf": $anyOf, "exit": $exit}
        ]}]}
    """.trimIndent()

    private fun invalid(text: String) = ZoneDetectorParser.parse(text) as ZoneDetectorParser.Result.Invalid

    @Test
    fun `valid file is parsed`() {
        val file = (ZoneDetectorParser.parse(zoneJson()) as ZoneDetectorParser.Result.Valid).file
        assertEquals(setOf("pkg"), file.packages)
        assertEquals(listOf(NodeSignal(viewId = "x")), file.zonesFor("pkg").single().anyOf)
    }

    @Test
    fun `unknown keys are tolerated`() {
        val text = zoneJson().replace("\"label\": \"Z\"", "\"label\": \"Z\", \"comment\": \"à revoir\"")
        assertTrue(ZoneDetectorParser.parse(text) is ZoneDetectorParser.Result.Valid)
    }

    @Test
    fun `broken json gives a readable message`() =
        assertTrue(invalid("{ \"version\": 1, ").message.startsWith("JSON invalide"))

    @Test
    fun `unsupported version is refused`() =
        assertTrue(invalid(zoneJson(version = 2)).message.contains("Version 2"))

    @Test
    fun `hide signals and colors are read`() {
        val text = zoneJson().replace("\"label\": \"Z\"", "\"label\": \"Z\", \"hide\": [{\"viewId\": \"tab\"}], \"hideColor\": {\"dark\": \"#0C1014\"}")
        val zone = (ZoneDetectorParser.parse(text) as ZoneDetectorParser.Result.Valid).file.zonesFor("pkg").single()
        assertEquals(listOf(NodeSignal(viewId = "tab")), zone.hide)
        assertEquals(MaskColor(light = "#FFFFFF", dark = "#0C1014"), zone.hideColor)
    }

    @Test
    fun `badly formatted mask color is refused`() {
        val text = zoneJson().replace("\"label\": \"Z\"", "\"label\": \"Z\", \"hideColor\": {\"dark\": \"noir\"}")
        assertTrue(invalid(text).message.contains("hideColor"))
    }

    @Test
    fun `zone without signals is refused`() =
        assertTrue(invalid(zoneJson(anyOf = "[]")).message.contains("anyOf"))

    @Test
    fun `empty signal is refused`() =
        assertTrue(invalid(zoneJson(anyOf = "[{}]")).message.contains("aucune condition"))

    @Test
    fun `exit with two actions is refused`() =
        assertTrue(invalid(zoneJson(exit = """[{"back":true,"clickDesc":["Home"]}]""")).message.contains("une seule action"))

    @Test
    fun `encode then parse gives the same file`() {
        val file = (ZoneDetectorParser.parse(zoneJson()) as ZoneDetectorParser.Result.Valid).file
        assertEquals(ZoneDetectorParser.Result.Valid(file), ZoneDetectorParser.parse(ZoneDetectorParser.encode(file)))
    }
}
