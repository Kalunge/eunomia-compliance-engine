package io.kalunge.eunomia.mapping

import com.fasterxml.jackson.databind.ObjectMapper
import dev.langchain4j.data.message.AiMessage
import dev.langchain4j.data.message.ChatMessage
import dev.langchain4j.data.message.SystemMessage
import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.model.chat.ChatLanguageModel
import dev.langchain4j.model.output.Response
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID

class LangChain4jMapperAgentTest {

    private val chatModel: ChatLanguageModel = mockk()
    private val objectMapper = ObjectMapper()
    private val agent = LangChain4jMapperAgent(chatModel, objectMapper)

    private val obligation = ObligationContext(
        summary = "Banks shall verify customer identity for transactions above KES 70,000.",
        sourceExcerpt = "Banks shall ensure that every agent collects and verifies identification documents…",
    )

    private val candidates = listOf(
        candidate(0, "Agents shall capture KYC documents for transactions ≥ KES 50,000."),
        candidate(1, "Records shall be retained for seven years."),
    )

    @Test
    fun `empty candidates returns empty judgments and never calls the model`() {
        val out = agent.judge(obligation, emptyList())
        assertThat(out).isEmpty()
        verify(exactly = 0) { chatModel.generate(any<List<ChatMessage>>()) }
    }

    @Test
    fun `parses well-formed judgments and routes by candidateIndex`() {
        givenModelReturns(
            """
            {
              "judgments": [
                { "candidateIndex": 0, "satisfied": true, "confidence": 0.91, "reasoning": "matches KYC control" },
                { "candidateIndex": 1, "satisfied": false, "confidence": 0.12, "reasoning": "different topic — retention" }
              ]
            }
            """.trimIndent(),
        )

        val judgments = agent.judge(obligation, candidates)

        assertThat(judgments).hasSize(2)
        assertThat(judgments[0].candidateIndex).isEqualTo(0)
        assertThat(judgments[0].satisfied).isTrue
        assertThat(judgments[0].confidence).isEqualTo(0.91)
        assertThat(judgments[0].reasoning).isEqualTo("matches KYC control")
        assertThat(judgments[1].satisfied).isFalse
        assertThat(judgments[1].confidence).isEqualTo(0.12)
    }

    @Test
    fun `tolerates a markdown fence around the JSON`() {
        givenModelReturns(
            """
            Sure! Here's my analysis:
            ```json
            { "judgments": [{ "candidateIndex": 0, "satisfied": true, "confidence": 0.8, "reasoning": "ok" }] }
            ```
            """.trimIndent(),
        )

        val judgments = agent.judge(obligation, candidates.take(1))

        assertThat(judgments).hasSize(1)
        assertThat(judgments[0].confidence).isEqualTo(0.8)
    }

    @Test
    fun `silently drops judgments whose candidateIndex is out of range`() {
        givenModelReturns(
            """
            { "judgments": [
              { "candidateIndex": 0, "satisfied": true, "confidence": 0.9, "reasoning": "fine" },
              { "candidateIndex": 7, "satisfied": true, "confidence": 0.9, "reasoning": "phantom" }
            ] }
            """.trimIndent(),
        )

        val judgments = agent.judge(obligation, candidates)
        assertThat(judgments).hasSize(1)
        assertThat(judgments[0].candidateIndex).isEqualTo(0)
    }

    @Test
    fun `clamps out-of-range confidence to the unit interval`() {
        givenModelReturns(
            """
            { "judgments": [
              { "candidateIndex": 0, "satisfied": true, "confidence": 1.5, "reasoning": "overconfident" },
              { "candidateIndex": 1, "satisfied": false, "confidence": -0.2, "reasoning": "underconfident" }
            ] }
            """.trimIndent(),
        )

        val judgments = agent.judge(obligation, candidates)
        assertThat(judgments[0].confidence).isEqualTo(1.0)
        assertThat(judgments[1].confidence).isEqualTo(0.0)
    }

    @Test
    fun `non-JSON output throws MappingParseException`() {
        givenModelReturns("I cannot help with that request.")
        assertThrows<MappingParseException> { agent.judge(obligation, candidates) }
    }

    @Test
    fun `JSON without a judgments array throws MappingParseException`() {
        givenModelReturns("""{ "decisions": [] }""")
        assertThrows<MappingParseException> { agent.judge(obligation, candidates) }
    }

    @Test
    fun `prompt includes both summary and source excerpt and labelled candidates`() {
        val capturedMessages = slot<List<ChatMessage>>()
        every { chatModel.generate(capture(capturedMessages)) } returns Response.from(
            AiMessage.from("""{ "judgments": [] }"""),
        )

        agent.judge(obligation, candidates)

        val messages = capturedMessages.captured
        val systemText = (messages.first { it is SystemMessage } as SystemMessage).text()
        val userText = (messages.first { it is UserMessage } as UserMessage).singleText()

        assertThat(systemText).contains("compliance reviewer")
        assertThat(userText).contains(obligation.summary)
        assertThat(userText).contains(obligation.sourceExcerpt)
        assertThat(userText).contains("[0]")
        assertThat(userText).contains("[1]")
    }

    private fun givenModelReturns(text: String) {
        every { chatModel.generate(any<List<ChatMessage>>()) } returns Response.from(AiMessage.from(text))
    }

    private fun candidate(index: Int, text: String) = ChunkCandidate(
        chunkId = UUID.randomUUID(),
        policyId = UUID.randomUUID(),
        chunkIndex = index,
        text = text,
        distance = 0.1 * (index + 1),
    )
}
