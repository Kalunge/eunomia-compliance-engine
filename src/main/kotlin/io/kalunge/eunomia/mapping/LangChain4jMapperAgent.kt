package io.kalunge.eunomia.mapping

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import dev.langchain4j.data.message.SystemMessage
import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.model.chat.ChatLanguageModel
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Production Mapper agent. Calls a ChatLanguageModel (Groq GPT OSS
 * 120B via OpenAI adapter in production, stubbed in tests) with the
 * system prompt + user prompt described in
 * docs/08-mapper-agent-deep-dive.md.
 *
 * All K candidates for one obligation go in a single prompt so the
 * judge can make comparative decisions and we save 4x on per-call
 * overhead vs one prompt per candidate.
 */
@Component
class LangChain4jMapperAgent(
    private val chatModel: ChatLanguageModel,
    private val objectMapper: ObjectMapper,
) : MapperAgent {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun judge(
        obligation: ObligationContext,
        candidates: List<ChunkCandidate>,
    ): List<MappingJudgment> {
        if (candidates.isEmpty()) return emptyList()

        val messages = listOf(
            SystemMessage.from(SYSTEM_PROMPT),
            UserMessage.from(buildUserPrompt(obligation, candidates)),
        )
        val raw = chatModel.generate(messages).content().text()
        log.debug("Mapper raw response: {}", raw)

        return parseJudgments(raw, expectedCount = candidates.size)
    }

    private fun buildUserPrompt(obligation: ObligationContext, candidates: List<ChunkCandidate>): String {
        val sb = StringBuilder()
        sb.appendLine("OBLIGATION:")
        sb.appendLine(obligation.summary)
        sb.appendLine()
        sb.appendLine("VERBATIM SOURCE EXCERPT:")
        sb.appendLine(obligation.sourceExcerpt)
        sb.appendLine()
        sb.appendLine("CANDIDATE POLICY CLAUSES:")
        candidates.forEachIndexed { i, c ->
            sb.appendLine("[$i] ${c.text}")
            if (i < candidates.size - 1) sb.appendLine("---")
        }
        sb.appendLine()
        sb.appendLine(
            "For each candidate, output a judgment with the candidateIndex you saw, " +
                "a boolean satisfied, a confidence 0..1, and a one-sentence reasoning.",
        )
        return sb.toString()
    }

    private fun parseJudgments(raw: String, expectedCount: Int): List<MappingJudgment> {
        val cleaned = sliceJsonObject(raw)
            ?: throw MappingParseException("Mapper produced non-JSON output: ${raw.take(200)}")

        val tree: JsonNode = try {
            objectMapper.readTree(cleaned)
        } catch (e: Exception) {
            throw MappingParseException("Mapper output is not valid JSON: ${cleaned.take(200)}", e)
        }

        val array = tree.get("judgments")
            ?: throw MappingParseException("Mapper output missing 'judgments' array: ${cleaned.take(200)}")
        if (!array.isArray) {
            throw MappingParseException("'judgments' is not an array: ${cleaned.take(200)}")
        }

        return array.mapNotNull { node ->
            val idx = node.get("candidateIndex")?.takeIf { it.isInt }?.intValue() ?: return@mapNotNull null
            if (idx !in 0 until expectedCount) return@mapNotNull null
            val satisfied = node.get("satisfied")?.takeIf { it.isBoolean }?.booleanValue() ?: false
            val confidence = node.get("confidence")?.let {
                when {
                    it.isNumber -> it.doubleValue().coerceIn(0.0, 1.0)
                    else -> 0.0
                }
            } ?: 0.0
            val reasoning = node.get("reasoning")?.takeIf { it.isTextual }?.textValue()?.trim()?.takeIf { it.isNotBlank() }
            MappingJudgment(candidateIndex = idx, satisfied = satisfied, confidence = confidence, reasoning = reasoning)
        }
    }

    /**
     * Slice from the first `{` to the last `}` so we tolerate markdown
     * fences, preambles, and trailing commentary the model occasionally
     * adds despite the strict-JSON rule. Same trick as the Extractor.
     */
    private fun sliceJsonObject(raw: String): String? {
        val first = raw.indexOf('{')
        val last = raw.lastIndexOf('}')
        if (first < 0 || last <= first) return null
        return raw.substring(first, last + 1)
    }

    companion object {
        private val SYSTEM_PROMPT = """
            You are a banking compliance reviewer. Given a regulatory obligation
            and a list of candidate internal policy clauses, judge whether each
            clause satisfies the obligation.

            A clause SATISFIES an obligation when it imposes a control that, if
            followed, fulfils what the obligation requires. The clause does not
            need to be identical to the obligation — paraphrases, stricter
            controls, and alternative phrasings all count.

            A clause does NOT satisfy an obligation when:
            - It addresses a different operational area (e.g., retention vs.
              customer reimbursement)
            - It imposes a control that is meaningfully weaker than the
              obligation requires (e.g., monthly vs. daily monitoring when daily
              is required)
            - It mentions the topic but provides no operative rule

            Output strict JSON only, no markdown, no commentary:
            {
              "judgments": [
                { "candidateIndex": 0, "satisfied": true, "confidence": 0.92,
                  "reasoning": "one sentence" }
              ]
            }
        """.trimIndent()
    }
}
