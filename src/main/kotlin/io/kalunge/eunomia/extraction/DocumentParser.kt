package io.kalunge.eunomia.extraction

import java.io.InputStream

/**
 * Strategy interface that turns a regulatory document (PDF/DOCX/txt/md)
 * into plain text the Extractor agent can read.
 *
 * Two reasons this is an interface rather than a concrete Tika class:
 *
 * 1. **Testability** — agent tests can swap in a deterministic parser
 *    that returns canned text, so the test isn't coupled to PDF
 *    binary fixtures.
 * 2. **Future swap** — if we ever outgrow Tika (cloud OCR for scanned
 *    PDFs, say) the agent code doesn't change. Strategy pattern, same
 *    shape as StorageService.
 */
interface DocumentParser {
    /**
     * Parse [input] into a [ParsedDocument]. [filenameHint] helps Tika
     * pick the right parser when the byte fingerprint is ambiguous
     * (e.g., a markdown file with no header — pass the .md filename and
     * Tika handles it correctly).
     *
     * Callers own the [input] lifecycle — the parser does not close it.
     */
    fun parse(input: InputStream, filenameHint: String? = null): ParsedDocument
}

/**
 * Output of parsing.
 *
 * [text] is the full document body. Page boundaries are deferred to
 * Sprint 2; Sprint 1 obligations carry null page ranges.
 *
 * [detectedMimeType] is Tika's own classification, not the MIME the
 * client supplied at upload — useful for catching client-spoofed types
 * before the agent reads them.
 */
data class ParsedDocument(
    val text: String,
    val detectedMimeType: String?,
    val charactersExtracted: Int,
)
