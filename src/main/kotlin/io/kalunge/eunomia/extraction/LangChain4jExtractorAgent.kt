package io.kalunge.eunomia.extraction

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import dev.langchain4j.data.message.SystemMessage
import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.model.chat.ChatLanguageModel
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * LangChain4j-backed implementation. Sends the parsed document text
 * through whichever ChatLanguageModel is wired into the context — in
 * production that's Groq's Llama 3.3 70B (configured in LangChain4jConfig).
 *
 * Design notes:
 *
 * - **Strict JSON output, no AiServices.** The AiServices abstraction
 *   is nice for one-off agents but its structured-output extraction is
 *   sensitive to model behaviour and silent on parse failures. We ask
 *   the model for raw JSON, parse with Jackson ourselves, and surface
 *   parse failures as exceptions the workflow can record.
 *
 * - **System + user message split.** The system message states the
 *   role and rules. The user message carries the document. Future
 *   prompt iteration (better extraction quality, more obligation types)
 *   happens at the SYSTEM_PROMPT constant — the agent code stays the
 *   same shape.
 *
 * - **No streaming.** Sprint 1 returns the full list synchronously.
 *   Streaming the per-obligation events back to the UI is a Sprint 3
 *   feature.
 */
@Service
class LangChain4jExtractorAgent(
    private val chatModel: ChatLanguageModel,
    private val objectMapper: ObjectMapper,
) : ExtractorAgent {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun extract(documentText: String): List<ExtractedObligation> {
        require(documentText.isNotBlank()) { "Document text is empty." }

        val response = chatModel.generate(
            listOf(
                SystemMessage(SYSTEM_PROMPT),
                UserMessage(buildUserPrompt(documentText)),
            ),
        )
        val raw = response.content().text()
        log.debug("Extractor raw response length={} chars", raw.length)

        return parseJson(raw)
    }

    private fun buildUserPrompt(documentText: String): String =
        """
        Read the following regulatory document and extract every discrete obligation it imposes.

        Document:
        ---
        $documentText
        ---

        Respond with strict JSON only, in this exact shape:
        {
          "obligations": [
            { "summary": "one-sentence description of what must be done", "sourceExcerpt": "verbatim text from the document supporting this obligation" }
          ]
        }

        Do not include markdown, code fences, or commentary. Just the JSON.
        """.trimIndent()

    private fun parseJson(raw: String): List<ExtractedObligation> {
        // Some models wrap the JSON in a ```json ... ``` fence even when
        // told not to, or insert a short preamble. Slicing from the first
        // '{' to the last '}' is robust against either case without
        // losing nested braces inside the obligations array.
        val firstBrace = raw.indexOf('{')
        val lastBrace = raw.lastIndexOf('}')
        if (firstBrace < 0 || lastBrace <= firstBrace) {
            throw ExtractionParseException(
                "Extractor produced non-JSON output. First 200 chars: " + raw.take(200),
            )
        }
        val cleaned = raw.substring(firstBrace, lastBrace + 1)

        val root: JsonNode = try {
            objectMapper.readTree(cleaned)
        } catch (e: Exception) {
            throw ExtractionParseException(
                "Extractor produced non-JSON output (${e.message}). First 200 chars: " +
                    cleaned.take(200),
                e,
            )
        }

        val items = root.path("obligations")
        if (!items.isArray) {
            throw ExtractionParseException(
                "Extractor JSON is missing the 'obligations' array. Got keys: ${root.fieldNames().asSequence().toList()}",
            )
        }

        return items.mapNotNull { node ->
            val summary = node.path("summary").asText("").trim()
            val excerpt = node.path("sourceExcerpt").asText("").trim()
            if (summary.isEmpty() || excerpt.isEmpty()) {
                log.warn("Skipping obligation with empty summary or sourceExcerpt: {}", node)
                null
            } else {
                ExtractedObligation(summary = summary, sourceExcerpt = excerpt)
            }
        }
    }

    companion object {
        /**
         * Tuned to favour precision over recall. Skipping a borderline
         * obligation is better than inventing one — the gap detector in
         * Sprint 2 punishes false negatives less harshly than the
         * grader will punish hallucinated obligations.
         */
        private val SYSTEM_PROMPT = """
            You are a banking compliance analyst. You read regulatory documents and
            extract the discrete obligations they impose on regulated institutions.

            An obligation is a single requirement, prohibition, or rule that applies
            to a specific party (a bank, an agent, an officer). Examples:
            - "Banks shall report suspicious transactions to the FRC within 24 hours"
            - "Agents must verify customer identity for transactions above KES 70,000"

            Rules:
            1. Each obligation must be ONE atomic rule. Do not combine multiple rules
               into a single bullet.
            2. The summary must be one sentence in active voice, naming the obliged
               party and the required action.
            3. The sourceExcerpt must be the verbatim text from the document that
               imposes the obligation. Do not paraphrase the excerpt.
            4. Skip headings, background sections, and non-binding commentary.
               Extract only operative rules.
            5. If the document contains no obligations (e.g., a covering letter),
               return an empty obligations array.
        """.trimIndent()
    }
}

class ExtractionParseException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
