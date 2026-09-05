package io.kalunge.eunomia.evaluation

import io.kalunge.eunomia.domain.GapStatus
import io.kalunge.eunomia.summarize.BriefInput
import io.kalunge.eunomia.summarize.BriefMatchInput
import io.kalunge.eunomia.summarize.BriefObligationInput
import java.time.Instant
import java.time.ZoneOffset
import kotlin.math.floor
import kotlin.math.roundToInt

data class BriefEvaluationInput(
    val regulationTitle: String,
    val sourceFilename: String,
    val uploadedAt: String,
    val obligations: List<BriefEvaluationObligation>,
) {
    fun toBriefInput(): BriefInput = BriefInput(
        regulationTitle = regulationTitle,
        sourceFilename = sourceFilename,
        uploadedAt = Instant.parse(uploadedAt),
        obligations = obligations.map { obligation ->
            BriefObligationInput(
                summary = obligation.summary,
                sourceExcerpt = obligation.sourceExcerpt,
                gapStatus = obligation.gapStatus,
                impactedProducts = obligation.impactedProducts,
                matches = obligation.matches.map { match ->
                    BriefMatchInput(
                        policyTitle = match.policyTitle,
                        chunkText = match.chunkText,
                        confidence = match.confidence,
                    )
                },
            )
        },
    )
}

data class BriefEvaluationObligation(
    val id: String,
    val summary: String,
    val sourceExcerpt: String,
    val gapStatus: GapStatus,
    val impactedProducts: List<String>,
    val matches: List<BriefEvaluationMatch> = emptyList(),
    val anchorTerms: Set<String>,
)

data class BriefEvaluationMatch(
    val policyTitle: String,
    val chunkText: String,
    val confidence: Double,
)

data class ExpectedBriefOutput(
    val forbiddenTerms: Set<String> = emptySet(),
)

data class BriefEvaluationScore(
    val sectionCoverage: Double,
    val obligationCoverage: Double,
    val statusAccuracy: Double,
    val productCoverage: Double,
    val policyCitationCoverage: Double,
    val gapActionCoverage: Double,
    val numericGrounding: Double,
    val hallucinationGuard: Double,
    val factualGrounding: Double,
    val missingSections: List<String>,
    val missingObligations: List<String>,
    val incorrectStatuses: List<String>,
    val missingProducts: List<String>,
    val missingPolicies: List<String>,
    val unactionedGaps: List<String>,
    val unsupportedNumbers: List<String>,
    val presentForbiddenTerms: List<String>,
) {
    fun asMetrics(parseSuccess: Double = 1.0): Map<String, Double> = mapOf(
        "sectionCoverage" to sectionCoverage,
        "obligationCoverage" to obligationCoverage,
        "statusAccuracy" to statusAccuracy,
        "productCoverage" to productCoverage,
        "policyCitationCoverage" to policyCitationCoverage,
        "gapActionCoverage" to gapActionCoverage,
        "numericGrounding" to numericGrounding,
        "hallucinationGuard" to hallucinationGuard,
        "factualGrounding" to factualGrounding,
        "parseSuccess" to parseSuccess,
    )
}

object BriefEvaluationMetrics {

    val requiredSections = listOf(
        "Executive Summary",
        "Obligations Imposed",
        "Gaps Requiring Action",
        "Already Mapped",
        "Affected Operational Lines",
    )

    private val numberRegex = Regex("""(?<![A-Za-z])\d+(?:\.\d+)?%?""")
    private val actionRegex = Regex(
        """\b(update|create|implement|introduce|amend|establish|enforce|train|configure|document|assign|revise|adopt|require)\b""",
    )

    fun score(
        input: BriefEvaluationInput,
        expected: ExpectedBriefOutput,
        markdown: String,
    ): BriefEvaluationScore {
        val normalizedMarkdown = normalize(markdown)
        val missingSections = requiredSections.filter { section ->
            !Regex("""(?im)^##\s+${Regex.escape(section)}\s*$""").containsMatchIn(markdown)
        }
        val obligationsSection = section(markdown, "Obligations Imposed")
        val obligationLines = obligationsSection.lineSequence()
            .map(::normalize)
            .filter { it.isNotBlank() }
            .toList()

        val missingObligations = input.obligations.filter { obligation ->
            obligationLines.none { line -> containsAnchors(line, obligation.anchorTerms) }
        }.map { it.id }
        val incorrectStatuses = input.obligations.filter { obligation ->
            obligationLines.none { line ->
                containsAnchors(line, obligation.anchorTerms) &&
                    line.contains(obligation.gapStatus.name.lowercase())
            }
        }.map { it.id }

        val products = input.obligations.flatMap { it.impactedProducts }.toSet()
        val missingProducts = products.filterNot { normalizedMarkdown.contains(normalize(it)) }.sorted()
        val policies = input.obligations.flatMap { obligation ->
            obligation.matches.map { it.policyTitle }
        }.toSet()
        val missingPolicies = policies.filterNot { normalizedMarkdown.contains(normalize(it)) }.sorted()

        val gaps = input.obligations.filter { it.gapStatus == GapStatus.GAP }
        val gapsSection = normalize(section(markdown, "Gaps Requiring Action"))
        val unactionedGaps = if (gaps.isEmpty()) {
            if (gapsSection.contains("all obligations are covered by existing policy")) {
                emptyList()
            } else {
                listOf("all-covered-statement")
            }
        } else {
            gaps.filterNot { obligation ->
                val anchorIndex = obligation.anchorTerms
                    .map { gapsSection.indexOf(normalize(it)) }
                    .filter { it >= 0 }
                    .minOrNull()
                    ?: return@filterNot false
                val windowEnd = minOf(anchorIndex + 500, gapsSection.length)
                val actionWindow = gapsSection.substring(anchorIndex, windowEnd)
                actionRegex.containsMatchIn(actionWindow)
            }.map { it.id }
        }

        val outputNumbers = numberRegex.findAll(markdown).map { it.value }.toList()
        val allowedNumbers = allowedNumbers(input)
        val unsupportedNumberOccurrences = outputNumbers
            .filterNot { normalizeNumber(it) in allowedNumbers }
        val unsupportedNumbers = unsupportedNumberOccurrences
            .distinct()
        val presentForbiddenTerms = expected.forbiddenTerms
            .filter { normalizedMarkdown.contains(normalize(it)) }
            .sorted()

        val sectionCoverage = coverage(requiredSections.size, missingSections.size)
        val obligationCoverage = coverage(input.obligations.size, missingObligations.size)
        val statusAccuracy = coverage(input.obligations.size, incorrectStatuses.size)
        val productCoverage = coverage(products.size, missingProducts.size)
        val policyCitationCoverage = coverage(policies.size, missingPolicies.size)
        val gapActionCoverage = coverage(
            if (gaps.isEmpty()) 1 else gaps.size,
            unactionedGaps.size,
        )
        val numericGrounding = if (outputNumbers.isEmpty()) {
            1.0
        } else {
            coverage(outputNumbers.size, unsupportedNumberOccurrences.size)
        }
        val hallucinationGuard = if (presentForbiddenTerms.isEmpty()) 1.0 else 0.0
        val factualGrounding = listOf(
            productCoverage,
            policyCitationCoverage,
            numericGrounding,
            hallucinationGuard,
        ).average()

        return BriefEvaluationScore(
            sectionCoverage = sectionCoverage,
            obligationCoverage = obligationCoverage,
            statusAccuracy = statusAccuracy,
            productCoverage = productCoverage,
            policyCitationCoverage = policyCitationCoverage,
            gapActionCoverage = gapActionCoverage,
            numericGrounding = numericGrounding,
            hallucinationGuard = hallucinationGuard,
            factualGrounding = factualGrounding,
            missingSections = missingSections,
            missingObligations = missingObligations,
            incorrectStatuses = incorrectStatuses,
            missingProducts = missingProducts,
            missingPolicies = missingPolicies,
            unactionedGaps = unactionedGaps,
            unsupportedNumbers = unsupportedNumbers,
            presentForbiddenTerms = presentForbiddenTerms,
        )
    }

    private fun allowedNumbers(input: BriefEvaluationInput): Set<String> {
        val sourceText = buildString {
            append(input.regulationTitle)
            append(' ')
            append(input.sourceFilename)
            input.obligations.forEach { obligation ->
                append(' ')
                append(obligation.summary)
                append(' ')
                append(obligation.sourceExcerpt)
                obligation.matches.forEach { match ->
                    append(' ')
                    append(match.chunkText)
                }
            }
        }
        val allowed = numberRegex.findAll(sourceText)
            .map { normalizeNumber(it.value) }
            .toMutableSet()
        val uploadedAt = Instant.parse(input.uploadedAt).atZone(ZoneOffset.UTC)
        allowed += uploadedAt.dayOfMonth.toString()
        allowed += uploadedAt.year.toString()
        allowed += input.obligations.size.toString()
        allowed += input.obligations.indices.map { (it + 1).toString() }

        val gaps = input.obligations.count { it.gapStatus == GapStatus.GAP }
        val mapped = input.obligations.count { it.gapStatus == GapStatus.MAPPED }
        allowed += gaps.toString()
        allowed += mapped.toString()
        if (input.obligations.isNotEmpty()) {
            listOf(gaps, mapped).forEach { count ->
                val percentage = count * 100.0 / input.obligations.size
                allowed += floor(percentage).toInt().toString()
                allowed += percentage.roundToInt().toString()
            }
        }
        input.obligations.flatMap { it.impactedProducts }
            .groupingBy { it }
            .eachCount()
            .values
            .forEach { allowed += it.toString() }
        input.obligations.flatMap { it.matches }.forEach { match ->
            allowed += (match.confidence * 100).roundToInt().toString()
            allowed += "${(match.confidence * 100).roundToInt()}%"
        }
        return allowed
    }

    private fun section(markdown: String, title: String): String {
        val heading = Regex("""(?im)^##\s+${Regex.escape(title)}\s*$""").find(markdown)
            ?: return ""
        val contentStart = heading.range.last + 1
        val nextHeading = Regex("""(?m)^##\s+""").find(markdown, contentStart)
        return markdown.substring(contentStart, nextHeading?.range?.first ?: markdown.length)
    }

    private fun containsAnchors(text: String, anchors: Set<String>): Boolean =
        anchors.isNotEmpty() && anchors.all { text.contains(normalize(it)) }

    private fun normalize(value: String): String =
        value.lowercase()
            .replace(Regex("""[^a-z0-9%]+"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()

    private fun normalizeNumber(value: String): String =
        value.removeSuffix("%").trimStart('0').ifEmpty { "0" }

    private fun coverage(total: Int, missing: Int): Double =
        if (total == 0) 1.0 else (total - missing).toDouble() / total
}
