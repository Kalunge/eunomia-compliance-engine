package io.kalunge.eunomia.extraction

import org.apache.tika.metadata.Metadata
import org.apache.tika.metadata.TikaCoreProperties
import org.apache.tika.parser.AutoDetectParser
import org.apache.tika.parser.ParseContext
import org.apache.tika.sax.BodyContentHandler
import org.springframework.stereotype.Service
import java.io.InputStream

/**
 * Apache Tika-backed implementation. Tika's AutoDetectParser sniffs the
 * byte stream and selects the right parser (PDFBox for PDFs, POI for
 * Office docs, plain readers for txt/md, etc.) so we don't have to
 * branch on MIME type ourselves.
 *
 * `BodyContentHandler(-1)` disables Tika's default 100 KB output cap —
 * a 40-page CBK circular easily exceeds that, and silently truncating
 * the text would produce silently-incomplete obligations later.
 */
@Service
class TikaDocumentParser : DocumentParser {

    override fun parse(input: InputStream, filenameHint: String?): ParsedDocument {
        val parser = AutoDetectParser()
        val handler = BodyContentHandler(-1)
        val metadata = Metadata().apply {
            filenameHint?.let { set(TikaCoreProperties.RESOURCE_NAME_KEY, it) }
        }
        val context = ParseContext()

        parser.parse(input, handler, metadata, context)

        val text = handler.toString()
        return ParsedDocument(
            text = text,
            detectedMimeType = metadata.get(Metadata.CONTENT_TYPE),
            charactersExtracted = text.length,
        )
    }
}
