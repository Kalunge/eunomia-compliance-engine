package io.kalunge.eunomia.impact

import com.fasterxml.jackson.databind.ObjectMapper
import dev.langchain4j.data.message.SystemMessage
import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.model.chat.ChatLanguageModel
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Production ImpactAnalyzer. One LLM call per obligation; presents the
 * tenant's catalogue as a numbered list and asks for indices, never
 * names — so the only failure mode is "wrong index" (filtered) rather
 * than "invented product" (impossible to filter cleanly).
 */
@Component
class LangChain4jImpactAnalyzerAgent(
    private val chatModel: ChatLanguageModel,
    private val objectMapper: ObjectMapper,
) : ImpactAnalyzerAgent {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun tag(
        obligation: ObligationContext,
        catalogue: List<CatalogueItem>,
    ): List<Int> {
        if (catalogue.isEmpty()) return emptyList()

        val messages = listOf(
            SystemMessage.from(SYSTEM_PROMPT),
            UserMessage.from(buildUserPrompt(obligation, catalogue)),
        )
        val raw = chatModel.generate(messages).content().text()
        log.debug("ImpactAnalyzer raw response: {}", raw)

        return parseIndices(raw, catalogueSize = catalogue.size)
    }

    private fun buildUserPrompt(obligation: ObligationContext, catalogue: List<CatalogueItem>): String {
        val sb = StringBuilder()
        sb.appendLine("OBLIGATION:")
        sb.appendLine(obligation.summary)
        sb.appendLine()
        sb.appendLine("VERBATIM SOURCE EXCERPT:")
        sb.appendLine(obligation.sourceExcerpt)
        sb.appendLine()
        sb.appendLine("PRODUCT/SERVICE CATALOGUE:")
        catalogue.forEachIndexed { i, item ->
            val descSuffix = if (!item.description.isNullOrBlank()) " — ${item.description}" else ""
            sb.appendLine("[$i] ${item.name}$descSuffix")
        }
        sb.appendLine()
        sb.appendLine("Which catalogue entries does this obligation affect? Output JSON.")
        return sb.toString()
    }

    private fun parseIndices(raw: String, catalogueSize: Int): List<Int> {
        val cleaned = sliceJsonObject(raw)
            ?: throw ImpactParseException("ImpactAnalyzer produced non-JSON output: ${raw.take(200)}")

        val tree = try {
            objectMapper.readTree(cleaned)
        } catch (e: Exception) {
            throw ImpactParseException("ImpactAnalyzer output is not valid JSON: ${cleaned.take(200)}", e)
        }

        val array = tree.get("impactedIndices")
            ?: throw ImpactParseException("ImpactAnalyzer output missing 'impactedIndices' array: ${cleaned.take(200)}")
        if (!array.isArray) {
            throw ImpactParseException("'impactedIndices' is not an array: ${cleaned.take(200)}")
        }

        return array.mapNotNull { node ->
            if (!node.isInt) null
            else node.intValue().takeIf { it in 0 until catalogueSize }
        }.distinct()
    }

    private fun sliceJsonObject(raw: String): String? {
        val first = raw.indexOf('{')
        val last = raw.lastIndexOf('}')
        if (first < 0 || last <= first) return null
        return raw.substring(first, last + 1)
    }

    companion object {
        private val SYSTEM_PROMPT = """
            You are a banking products and services taxonomist. Given a regulatory
            obligation and a numbered catalogue of products/services a bank offers,
            identify which catalogue entries the obligation directly affects.

            Rules:
            1. Return ONLY entries that appear in the supplied catalogue. Do not
               invent new categories.
            2. Return entries by their integer index — never by name.
            3. An obligation "affects" a product when the obligation imposes a
               control, restriction, or reporting requirement that the bank would
               apply at that product's operational layer.
            4. If the obligation is generic enough to touch every product (e.g.,
               entity-wide reporting), include all indices.
            5. If unsure whether an index applies, exclude it. Precision over recall.

            Output strict JSON only, no markdown, no commentary:
            { "impactedIndices": [0, 3, 7] }
        """.trimIndent()
    }
}
