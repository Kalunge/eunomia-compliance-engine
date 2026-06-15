package io.kalunge.eunomia.summarize

import dev.langchain4j.data.message.SystemMessage
import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.model.chat.ChatLanguageModel
import io.kalunge.eunomia.domain.GapStatus
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Production Summarizer. One LLM call per regulation. The composed
 * Markdown is stored verbatim on `regulations.brief_markdown`; markdown
 * fences (which the model occasionally adds despite the prompt) are
 * stripped post-hoc to keep the stored artefact clean.
 */
@Component
class LangChain4jSummarizerAgent(
    private val chatModel: ChatLanguageModel,
) : SummarizerAgent {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun compose(input: BriefInput): String {
        val messages = listOf(
            SystemMessage.from(SYSTEM_PROMPT),
            UserMessage.from(buildUserPrompt(input)),
        )
        val raw = chatModel.generate(messages).content().text()
        log.debug("Summarizer raw response: {}", raw.take(500))
        return stripFences(raw).trim()
            .ifBlank { throw BriefGenerationException("Summarizer returned empty output") }
    }

    private fun buildUserPrompt(input: BriefInput): String {
        val sb = StringBuilder()
        sb.appendLine("REGULATION:")
        sb.appendLine("- Title: ${input.regulationTitle}")
        sb.appendLine("- Source file: ${input.sourceFilename}")
        sb.appendLine("- Uploaded: ${UPLOAD_DATE_FMT.format(input.uploadedAt.atZone(ZoneOffset.UTC))}")
        sb.appendLine()
        sb.appendLine("OBLIGATIONS (${input.obligations.size}):")
        input.obligations.forEachIndexed { i, o ->
            val statusLabel = when (o.gapStatus) {
                GapStatus.MAPPED -> "MAPPED"
                GapStatus.GAP -> "GAP"
                GapStatus.UNKNOWN -> "UNKNOWN"
            }
            sb.appendLine("[${i + 1}] $statusLabel — ${o.summary}")
            sb.appendLine("    Source excerpt: \"${o.sourceExcerpt.take(300)}\"")
            if (o.impactedProducts.isNotEmpty()) {
                sb.appendLine("    Affects: ${o.impactedProducts.joinToString(", ")}")
            } else {
                sb.appendLine("    Affects: (none tagged)")
            }
            if (o.matches.isNotEmpty()) {
                sb.appendLine("    Cited policy clauses:")
                o.matches.take(3).forEach { m ->
                    val pct = (m.confidence * 100).toInt()
                    sb.appendLine("      - ${m.policyTitle} ($pct% confidence): \"${m.chunkText.take(220)}\"")
                }
            }
            sb.appendLine()
        }
        sb.appendLine("Compose the brief now.")
        return sb.toString()
    }

    /**
     * Drop ```...``` fences the model sometimes wraps the whole
     * response in despite the strict-output rule. Conservative — only
     * strips if both opening and closing fences are present.
     */
    private fun stripFences(raw: String): String {
        val trimmed = raw.trim()
        if (!trimmed.startsWith("```")) return raw
        val afterOpen = trimmed.substringAfter('\n', "")
        val withoutClose = afterOpen.substringBeforeLast("```", afterOpen)
        return if (withoutClose.isNotBlank()) withoutClose else raw
    }

    companion object {
        private val UPLOAD_DATE_FMT = DateTimeFormatter.ofPattern("d MMM yyyy")

        private val SYSTEM_PROMPT = """
            You are a senior banking compliance writer. Given a regulation,
            its extracted obligations, their mapping status against the bank's
            internal policies, and the products each obligation affects, compose
            an impact brief suitable for the bank's senior leadership.

            The brief must be honest about gaps — do not soften them or
            recommend deferred action. Each gap is a control the bank does not
            yet have; leadership reads the brief to decide what to fix.

            Structure the output as Markdown with these sections, in order:

            # {Regulation Title} — Impact Brief

            ## Executive Summary
            3-5 sentences. State the regulation's purpose, the number of
            obligations identified, and the headline finding (e.g. "4 of 9
            obligations are not currently covered by internal policy").

            ## Obligations Imposed
            A numbered list. For each obligation: a one-line summary, the
            affected products (in parentheses), and (Mapped) or (Gap).

            ## Gaps Requiring Action
            Each gap as its own subsection with a single recommended next
            step. State the gap factually; do not euphemise. If there are no
            gaps, write "All obligations are covered by existing policy."

            ## Already Mapped
            Brief paragraph naming the obligations that have existing policy
            controls and the policy clauses that satisfy them.

            ## Affected Operational Lines
            A bulleted list of products/services touched by this regulation,
            with the count of obligations that affect each.

            Output Markdown only. Do not include front-matter, code fences,
            or commentary.
        """.trimIndent()
    }
}
