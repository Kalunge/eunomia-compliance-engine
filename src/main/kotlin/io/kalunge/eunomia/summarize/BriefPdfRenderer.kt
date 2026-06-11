package io.kalunge.eunomia.summarize

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer
import org.springframework.stereotype.Component
import java.io.ByteArrayOutputStream

/**
 * Renders the stored brief Markdown into a PDF/A document.
 *
 * Pipeline: Markdown → HTML (via commonmark-java) → PDF (via
 * openhtmltopdf). Inline CSS keeps the rendered document looking like
 * the in-app brief view; intentionally conservative typography so
 * page breaks are predictable for board-pack printing.
 */
@Component
class BriefPdfRenderer {

    private val parser: Parser = Parser.builder().build()
    private val htmlRenderer: HtmlRenderer = HtmlRenderer.builder().build()

    fun render(markdown: String): ByteArray {
        val bodyHtml = htmlRenderer.render(parser.parse(markdown))
        val fullHtml = """
            <!DOCTYPE html>
            <html lang="en">
              <head>
                <meta charset="utf-8"/>
                <title>Impact Brief</title>
                <style>$STYLES</style>
              </head>
              <body>
                $bodyHtml
              </body>
            </html>
        """.trimIndent()

        ByteArrayOutputStream().use { output ->
            PdfRendererBuilder().apply {
                useFastMode()
                withHtmlContent(fullHtml, null)
                toStream(output)
            }.run()
            return output.toByteArray()
        }
    }

    companion object {
        private val STYLES = """
            @page { size: A4; margin: 22mm 18mm; }
            body { font-family: 'Helvetica', 'Arial', sans-serif; font-size: 10.5pt; color: #1e293b; line-height: 1.55; }
            h1 { font-size: 20pt; color: #312e81; margin: 0 0 12pt 0; padding-bottom: 6pt; border-bottom: 2pt solid #c7d2fe; }
            h2 { font-size: 13pt; color: #1e293b; margin: 18pt 0 6pt 0; padding-bottom: 2pt; border-bottom: 1pt solid #e2e8f0; }
            h3 { font-size: 11.5pt; color: #1e293b; margin: 14pt 0 4pt 0; }
            p { margin: 0 0 8pt 0; }
            ul, ol { margin: 0 0 8pt 0; padding-left: 18pt; }
            li { margin-bottom: 3pt; }
            blockquote { margin: 8pt 0; padding-left: 10pt; border-left: 2pt solid #c7d2fe; color: #475569; }
            code { font-family: 'Menlo', 'Consolas', monospace; font-size: 9pt; background: #f1f5f9; padding: 1pt 3pt; border-radius: 2pt; }
            em { color: #475569; }
            strong { color: #1e293b; }
        """.trimIndent()
    }
}
