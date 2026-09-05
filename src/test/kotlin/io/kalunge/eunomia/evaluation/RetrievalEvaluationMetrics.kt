package io.kalunge.eunomia.evaluation

data class RetrievalCorpus(
    val schemaVersion: Int,
    val chunks: List<RetrievalCorpusChunk>,
)

data class RetrievalCorpusChunk(
    val id: String,
    val text: String,
    val tenant: String = "primary",
)

data class RetrievalEvaluationInput(
    val query: String,
)

data class ExpectedRetrievalOutput(
    val relevantChunkIds: Set<String>,
)

data class RetrievalEvaluationScore(
    val recallAt1: Double,
    val recallAt3: Double,
    val reciprocalRank: Double,
    val resultCompleteness: Double,
    val tenantIsolation: Double,
) {
    fun asMetrics(): Map<String, Double> = mapOf(
        "recallAt1" to recallAt1,
        "recallAt3" to recallAt3,
        "reciprocalRank" to reciprocalRank,
        "resultCompleteness" to resultCompleteness,
        "tenantIsolation" to tenantIsolation,
    )
}

object RetrievalEvaluationMetrics {

    fun score(
        rankedChunkIds: List<String>,
        relevantChunkIds: Set<String>,
        forbiddenChunkIds: Set<String> = emptySet(),
    ): RetrievalEvaluationScore {
        require(relevantChunkIds.isNotEmpty()) {
            "Retrieval evaluation requires at least one relevant chunk."
        }

        val recallAt1 = recallAt(rankedChunkIds, relevantChunkIds, 1)
        val recallAt3 = recallAt(rankedChunkIds, relevantChunkIds, 3)
        val firstRelevantRank = rankedChunkIds.indexOfFirst { it in relevantChunkIds }
        val reciprocalRank = if (firstRelevantRank < 0) {
            0.0
        } else {
            1.0 / (firstRelevantRank + 1)
        }
        val resultCompleteness = minOf(rankedChunkIds.size.toDouble() / 3.0, 1.0)
        val tenantIsolation = if (rankedChunkIds.none { it in forbiddenChunkIds }) 1.0 else 0.0

        return RetrievalEvaluationScore(
            recallAt1 = recallAt1,
            recallAt3 = recallAt3,
            reciprocalRank = reciprocalRank,
            resultCompleteness = resultCompleteness,
            tenantIsolation = tenantIsolation,
        )
    }

    private fun recallAt(
        rankedChunkIds: List<String>,
        relevantChunkIds: Set<String>,
        k: Int,
    ): Double =
        rankedChunkIds.take(k).count { it in relevantChunkIds }.toDouble() / relevantChunkIds.size
}
