package io.github.yulbax.erkin.proxy

import io.github.yulbax.erkin.proxy.protocol.decodeBase64Lenient

interface FormatReader {
    fun detects(text: String): Boolean

    fun read(text: String): List<ParsedProfile>
}

private object DocumentReader : FormatReader {
    override fun detects(text: String): Boolean = LinkParser.documentOwner(text.trim()) != null

    override fun read(text: String): List<ParsedProfile> = listOfNotNull(LinkParser.parse(text))
}

private object LinksReader : FormatReader {
    override fun detects(text: String): Boolean = true

    override fun read(text: String): List<ParsedProfile> =
        text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.mapNotNull { LinkParser.parse(it) }.toList()
}

private object Base64LinksReader : FormatReader {
    override fun detects(text: String): Boolean = "://" !in text && decodeBase64Lenient(text)?.contains("://") == true

    override fun read(text: String): List<ParsedProfile> = LinksReader.read(decodeBase64Lenient(text).orEmpty())
}

enum class SubscriptionFormat(val reader: FormatReader) {
    SING_BOX_JSON(SingBoxJsonReader),
    XRAY_JSON(XrayJsonReader),
    SIP008_JSON(Sip008JsonReader),
    CLASH_YAML(ClashYamlReader),
    DOCUMENT(DocumentReader),
    BASE64_LINKS(Base64LinksReader),
    LINKS(LinksReader);

    companion object {
        fun detect(text: String): SubscriptionFormat = entries.firstOrNull { it.reader.detects(text) } ?: LINKS
    }
}

object SubscriptionParser {
    fun parseBody(text: String): List<ParsedProfile> = SubscriptionFormat.detect(text).reader.read(text)
}
