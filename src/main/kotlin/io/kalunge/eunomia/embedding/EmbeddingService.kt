package io.kalunge.eunomia.embedding

/**
 * Turns text into a dense vector. Strategy interface so the underlying
 * provider (Cohere now, OpenAI or self-hosted later) can be swapped
 * without touching the chunker, the Mapper agent, or any other caller.
 *
 * Cohere v3 distinguishes between "search_document" (text to be stored
 * and later retrieved) and "search_query" (text used to retrieve), so
 * the interface mirrors that split. Implementations that don't care
 * about input type (the test stub, a future local model) can ignore
 * the distinction.
 */
interface EmbeddingService {

    /** Vector size produced by this provider. Used by callers (DDL, ANN). */
    val dimension: Int

    /**
     * Embed a batch of texts as documents — i.e. text that will be
     * stored and later searched against. Batching matters: Cohere bills
     * per request, not per token, so one call with 50 chunks is much
     * cheaper than 50 single-chunk calls. Returns vectors in the same
     * order as the input.
     */
    fun embedDocuments(texts: List<String>): List<FloatArray>

    /**
     * Embed a single text as a query — i.e. text used to look up
     * matching documents. Internally uses a different input_type so the
     * resulting vector projects into the query subspace Cohere's model
     * was trained for.
     */
    fun embedQuery(text: String): FloatArray
}
