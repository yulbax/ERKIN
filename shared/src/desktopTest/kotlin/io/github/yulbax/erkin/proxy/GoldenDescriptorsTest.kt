package io.github.yulbax.erkin.proxy

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class GoldenDescriptorsTest {

    @Test
    fun descriptorsMatchTheGoldenFileTheCoreIsChecked() {
        val actual = pretty.encodeToString(JsonArray.serializer(), descriptors()) + "\n"
        if (System.getenv(UPDATE_VARIABLE) == "1") {
            GOLDEN.parentFile.mkdirs()
            GOLDEN.writeText(actual)
        }
        assertEquals(GOLDEN.readText(), actual)
    }

    private fun descriptors(): JsonArray = JsonArray(
        ProtocolContractTest.FORMS.entries.sortedBy { it.key.wire }
            .flatMap { (protocol, forms) -> forms.map { protocol to it } }
            .map { (protocol, form) ->
                JsonObject(
                    mapOf(
                        "wire" to JsonPrimitive(protocol.wire),
                        "proxy" to requireNotNull(LinkParser.parse(form)) { protocol.wire }.proxy
                    )
                )
            }
    )

    private companion object {
        const val UPDATE_VARIABLE = "ERKIN_UPDATE_GOLDEN"
        val GOLDEN = File("src/desktopTest/resources/core-descriptors.json")
        val pretty = Json { prettyPrint = true }
    }
}
