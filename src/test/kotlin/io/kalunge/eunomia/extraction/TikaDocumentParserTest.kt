package io.kalunge.eunomia.extraction

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Tika is a JVM library — no containers, no network. Plain unit tests
 * against canned bytes are enough.
 */
class TikaDocumentParserTest {

    private val parser = TikaDocumentParser()

    @Test
    fun `parses plain text and reports the right content type`() {
        val source = "Section 3.1 Customer Identification at Agent Outlets\n" +
            "Agents shall verify identity for any transaction above KES 70,000."
        val parsed = parser.parse(source.byteInputStream(), filenameHint = "rule.txt")

        assertTrue(parsed.text.contains("Customer Identification"), "Body content should pass through")
        assertTrue(parsed.text.contains("70,000"), "Numeric thresholds should pass through")
        // Tika sometimes appends a trailing newline. Allow a small delta.
        assertTrue(
            parsed.charactersExtracted >= source.length,
            "Expected at least ${source.length} chars, got ${parsed.charactersExtracted}",
        )
        // Tika reports "text/plain" with a charset suffix; substring check is the stable assertion.
        assertNotNull(parsed.detectedMimeType)
        assertTrue(
            parsed.detectedMimeType!!.startsWith("text/plain"),
            "Expected text/plain, got ${parsed.detectedMimeType}",
        )
    }

    @Test
    fun `parses markdown and strips formatting characters where Tika does so`() {
        val source = """
            # Banking Circular 12/2026

            ## Section 3.1 — Customer Identification

            Agents **shall** verify identity for any transaction above
            *KES 70,000*. See `Section 4.2` for record-retention rules.
        """.trimIndent()

        val parsed = parser.parse(source.byteInputStream(), filenameHint = "rule.md")

        // The body text always passes through regardless of how Tika
        // classifies markdown. Some Tika builds tag .md as text/x-web-markdown
        // and pass headings through verbatim; others (older) tag as text/plain.
        // Either way the substantive content lands in parsed.text.
        assertTrue(parsed.text.contains("Section 3.1"), "Heading text should survive parsing")
        assertTrue(parsed.text.contains("KES 70,000"), "Body should survive parsing")
        assertTrue(parsed.charactersExtracted > 0)
    }

    @Test
    fun `does not silently truncate large documents`() {
        // Build a doc bigger than Tika's default 100 KB BodyContentHandler cap
        // so we'd catch a regression that re-introduces the limit.
        val bigDoc = buildString {
            repeat(20_000) { append("This is line ").append(it).append('\n') }
        }
        val parsed = parser.parse(bigDoc.byteInputStream(), filenameHint = "huge.txt")

        // If the cap were back, charactersExtracted would top out near 100k.
        assertTrue(
            parsed.charactersExtracted > 200_000,
            "Expected the full ~${bigDoc.length}-char document, got ${parsed.charactersExtracted}",
        )
    }
}
