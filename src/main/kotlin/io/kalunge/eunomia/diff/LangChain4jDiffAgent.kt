package io.kalunge.eunomia.diff

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import dev.langchain4j.data.message.SystemMessage
import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.model.chat.ChatLanguageModel
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Production Diff agent. Calls the same ChatLanguageModel bean as the
 * Extractor and Mapper (Groq GPT OSS 120B via OpenAI adapter in
 * production, RoutingStubChatLanguageModel in tests) with the
 * regulatory-analyst system prompt described in
 * docs/12-regulation-diff.md.
 *
 * One LLM call per candidate obligation. All K baselines for that
 * candidate go in a single prompt so the judge picks the best match
 * comparatively and we save call overhead.
 */
@Component
class LangChain4jDiffAgent(
    private val chatModel: ChatLanguageModel,
    private val objectMapper: ObjectMapper,
) : DiffAgent {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun judge(candidate: DiffSide, baselines: List<DiffSide>): DiffJudgement {
        if (baselines.isEmpty()) {
            // Defensive: the service should already skip the LLM call in
            // this case, but if it routes here we return ADDED without
            // calling the model.
            return DiffJudgement(
                verdict = DiffVerdict.ADDED,
                baselineIndex = null,
                rationale = "No baseline obligations to compare against.",
            )
        }

        val messages = listOf(
            SystemMessage.from(SYSTEM_PROMPT),
            UserMessage.from(buildUserPrompt(candidate, baselines)),
        )
        val raw = chatModel.generate(messages).content().text()
        log.debug("Diff raw response: {}", raw)

        return parseJudgement(raw, baselineCount = baselines.size)
    }

    private fun buildUserPrompt(candidate: DiffSide, baselines: List<DiffSide>): String {
        val sb = StringBuilder()
        sb.appendLine("CANDIDATE OBLIGATION (from version 2):")
        sb.appendLine(candidate.summary)
        sb.appendLine("Source excerpt: \"${candidate.sourceExcerpt}\"")
        sb.appendLine()
        sb.appendLine("BASELINE OBLIGATIONS (from version 1):")
        baselines.forEachIndexed { i, b ->
            sb.appendLine("[$i] ${b.summary}")
            sb.appendLine("    Source: \"${b.sourceExcerpt}\"")
        }
        sb.appendLine()
        sb.appendLine("Classify the relationship.")
        return sb.toString()
    }

    private fun parseJudgement(raw: String, baselineCount: Int): DiffJudgement {
        val cleaned = sliceJsonObject(raw)
            ?: throw DiffParseException("Diff agent produced non-JSON output: ${raw.take(200)}")

        val tree: JsonNode = try {
            objectMapper.readTree(cleaned)
        } catch (e: Exception) {
            throw DiffParseException("Diff agent output is not valid JSON: ${cleaned.take(200)}", e)
        }

        val verdictRaw = tree.get("verdict")?.takeIf { it.isTextual }?.textValue()?.trim()?.lowercase()
            ?: throw DiffParseException("Diff agent output missing 'verdict': ${cleaned.take(200)}")

        val verdict = when (verdictRaw) {
            "unchanged" -> DiffVerdict.UNCHANGED
            "modified" -> DiffVerdict.MODIFIED
            "added" -> DiffVerdict.ADDED
            else -> throw DiffParseException("Diff agent returned unknown verdict '$verdictRaw'")
        }

        val baselineIndex = tree.get("baselineIndex")
            ?.takeIf { it.isInt }
            ?.intValue()
            ?.takeIf { it in 0 until baselineCount }

        val rationale = tree.get("rationale")?.takeIf { it.isTextual }?.textValue()?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: "No rationale provided."

        // UNCHANGED/MODIFIED require a baselineIndex; if the LLM
        // claimed one of those verdicts but the index is missing or
        // out of range, downgrade to ADDED rather than failing the
        // whole diff. Logged so a maintainer can investigate model
        // drift.
        if (verdict != DiffVerdict.ADDED && baselineIndex == null) {
            log.warn(
                "Diff agent returned verdict={} without a valid baselineIndex; treating as ADDED. Raw: {}",
                verdict, cleaned.take(200),
            )
            return DiffJudgement(
                verdict = DiffVerdict.ADDED,
                baselineIndex = null,
                rationale = rationale,
            )
        }

        return DiffJudgement(
            verdict = verdict,
            baselineIndex = if (verdict == DiffVerdict.ADDED) null else baselineIndex,
            rationale = rationale,
        )
    }

    /**
     * Slice from the first `{` to the last `}` so we tolerate markdown
     * fences, preambles, and trailing commentary the model occasionally
     * adds despite the strict-JSON rule. Same trick as the Mapper and
     * Extractor.
     */
    private fun sliceJsonObject(raw: String): String? {
        val first = raw.indexOf('{')
        val last = raw.lastIndexOf('}')
        if (first < 0 || last <= first) return null
        return raw.substring(first, last + 1)
    }

    companion object {
        private val SYSTEM_PROMPT = """
            You are a regulatory analyst. Your job is to compare two obligations
            extracted from different regulations and classify their relationship.

            Given a CANDIDATE obligation (from the newer regulation) and up to
            three BASELINE obligations (the closest semantic matches from the
            older regulation), output a strict JSON judgement:

            {
              "verdict": "unchanged" | "modified" | "added",
              "baselineIndex": 0,
              "rationale": "one sentence"
            }

            Rules:
            1. "unchanged" — the candidate restates the same operational rule as
               the baseline. Wording differs but the controls, thresholds, parties,
               and timeframes are equivalent.
            2. "modified" — the candidate is clearly the same rule but the
               substance has shifted: a threshold changed, a timeframe tightened,
               a party scope expanded, or an additional condition was layered on.
               Quote the changed element in the rationale (e.g., "threshold raised
               from KES 50,000 to KES 70,000").
            3. "added" — the candidate addresses a different rule entirely. None
               of the baselines covers the same operational ground.
            4. If multiple baselines could plausibly match, pick the closest in
               substance and explain the call in the rationale.

            For "unchanged" and "modified" the baselineIndex MUST be a valid
            index into the BASELINE list (0, 1, or 2). For "added" omit
            baselineIndex or set it to null.

            Output strict JSON only, no markdown.
        """.trimIndent()
    }
}
