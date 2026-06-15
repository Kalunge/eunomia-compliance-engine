package io.kalunge.eunomia.policy

import com.knuddels.jtokkit.Encodings
import com.knuddels.jtokkit.api.Encoding
import com.knuddels.jtokkit.api.EncodingType
import com.knuddels.jtokkit.api.IntArrayList
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * Strategy interface. Future swap: semantic chunking that respects
 * paragraph and section boundaries; for now we slide a token window.
 */
interface PolicyChunker {
    fun chunk(text: String): List<PolicyChunkText>
}

/**
 * One text window produced by the chunker. Page numbers are null in
 * Sprint 2 because Tika gives us plain text without page boundaries —
 * page-aware extraction lands in Sprint 3.
 */
data class PolicyChunkText(
    val text: String,
    val chunkIndex: Int,
    val pageStart: Int? = null,
    val pageEnd: Int? = null,
)

/**
 * Fixed-size token window with overlap. 600/100 are the RAG defaults
 * from LlamaIndex/LangChain/Pinecone: large enough that one window
 * usually carries a complete operational rule, small enough that
 * irrelevant context doesn't drown the embedding signal.
 *
 * Tokeniser: cl100k_base (OpenAI). Cohere's own tokeniser differs
 * slightly, but the deviation is small and we'd rather have
 * deterministic chunk sizes than perfect-match token counts.
 */
@Component
class TokenWindowPolicyChunker(
    @Value("\${eunomia.policy.chunk.tokens-per-chunk:600}") private val tokensPerChunk: Int,
    @Value("\${eunomia.policy.chunk.token-overlap:100}") private val tokenOverlap: Int,
) : PolicyChunker {

    private val encoding: Encoding =
        Encodings.newDefaultEncodingRegistry().getEncoding(EncodingType.CL100K_BASE)

    override fun chunk(text: String): List<PolicyChunkText> {
        if (text.isBlank()) return emptyList()
        require(tokenOverlap < tokensPerChunk) {
            "token-overlap ($tokenOverlap) must be less than tokens-per-chunk ($tokensPerChunk)"
        }

        val tokens = encoding.encode(text)
        val tokenCount = tokens.size()
        if (tokenCount == 0) return emptyList()

        val stride = tokensPerChunk - tokenOverlap
        val result = mutableListOf<PolicyChunkText>()
        var start = 0
        var index = 0
        while (start < tokenCount) {
            val end = (start + tokensPerChunk).coerceAtMost(tokenCount)
            val window = IntArrayList(end - start)
            for (i in start until end) window.add(tokens.get(i))
            result.add(PolicyChunkText(text = encoding.decode(window), chunkIndex = index))
            if (end == tokenCount) break
            start += stride
            index++
        }
        return result
    }
}
