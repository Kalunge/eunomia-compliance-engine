package io.kalunge.eunomia.policy

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class TokenWindowPolicyChunkerTest {

    @Test
    fun `empty text produces no chunks`() {
        val chunker = TokenWindowPolicyChunker(tokensPerChunk = 100, tokenOverlap = 20)
        assertThat(chunker.chunk("")).isEmpty()
        assertThat(chunker.chunk("   ")).isEmpty()
    }

    @Test
    fun `short text fits in a single chunk`() {
        val chunker = TokenWindowPolicyChunker(tokensPerChunk = 600, tokenOverlap = 100)
        val chunks = chunker.chunk("Banks shall verify customer identity for cash deposits above KES 70,000.")

        assertThat(chunks).hasSize(1)
        assertThat(chunks[0].chunkIndex).isEqualTo(0)
        assertThat(chunks[0].text).contains("Banks shall verify")
    }

    @Test
    fun `long text splits into multiple chunks with overlap`() {
        val sentence = "Agents must perform KYC verification on every cash transaction. "
        val longText = sentence.repeat(200) // ~2000+ tokens

        val chunker = TokenWindowPolicyChunker(tokensPerChunk = 200, tokenOverlap = 50)
        val chunks = chunker.chunk(longText)

        assertThat(chunks.size).isGreaterThan(1)
        // chunkIndex must be 0, 1, 2, … strictly increasing
        assertThat(chunks.map { it.chunkIndex }).isEqualTo((0 until chunks.size).toList())
        // every chunk has content
        assertThat(chunks).allMatch { it.text.isNotBlank() }
    }

    @Test
    fun `overlap means consecutive chunks share content`() {
        val sentence = "Identification documents shall be retained for seven years. "
        val longText = sentence.repeat(50)

        val chunker = TokenWindowPolicyChunker(tokensPerChunk = 100, tokenOverlap = 30)
        val chunks = chunker.chunk(longText)

        assertThat(chunks.size).isGreaterThan(1)
        // The tail of chunk N should appear at the head of chunk N+1 (within reason —
        // tokens may decode to slightly different strings around boundaries).
        val firstTail = chunks[0].text.takeLast(50)
        val secondHead = chunks[1].text.take(80)
        assertThat(secondHead).contains(firstTail.substring(0, 20))
    }

    @Test
    fun `overlap larger than chunk size is rejected`() {
        val chunker = TokenWindowPolicyChunker(tokensPerChunk = 100, tokenOverlap = 100)
        org.junit.jupiter.api.assertThrows<IllegalArgumentException> {
            chunker.chunk("non-empty text")
        }
    }
}
