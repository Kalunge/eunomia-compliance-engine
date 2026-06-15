package io.kalunge.eunomia.impact

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

class LangChain4jImpactAnalyzerAgentTest {

    private val chatModel: ChatLanguageModel = mockk()
    private val objectMapper = ObjectMapper()
    private val agent = LangChain4jImpactAnalyzerAgent(chatModel, objectMapper)

    private val obligation = ObligationContext(
        summary = "Banks shall verify identity for transactions above KES 70,000.",
        sourceExcerpt = "Banks shall ensure that every agent collects and verifies identification documents…",
    )

    private val catalogue = listOf(
        CatalogueItem("Agent Banking", "Third-party agent outlets"),
        CatalogueItem("Mobile Banking", "Smartphone and USSD channel"),
        CatalogueItem("Retail Deposits", "Current, savings, and fixed deposits"),
        CatalogueItem("Wholesale Treasury", "Money market and FX trading"),
    )

    @Test
    fun `empty catalogue returns empty list and never calls the model`() {
        val out = agent.tag(obligation, emptyList())
        assertThat(out).isEmpty()
        verify(exactly = 0) { chatModel.generate(any<List<ChatMessage>>()) }
    }

    @Test
    fun `parses well-formed impactedIndices`() {
        givenModelReturns("""{ "impactedIndices": [0, 1, 2] }""")
        val out = agent.tag(obligation, catalogue)
        assertThat(out).containsExactly(0, 1, 2)
    }

    @Test
    fun `filters out-of-range indices`() {
        givenModelReturns("""{ "impactedIndices": [0, 4, 99, 1, -1] }""")
        val out = agent.tag(obligation, catalogue)
        assertThat(out).containsExactly(0, 1)
    }

    @Test
    fun `deduplicates repeated indices`() {
        givenModelReturns("""{ "impactedIndices": [1, 1, 2, 1, 2] }""")
        val out = agent.tag(obligation, catalogue)
        assertThat(out).containsExactly(1, 2)
    }

    @Test
    fun `tolerates markdown fence around the JSON`() {
        givenModelReturns(
            """
            Sure!
            ```json
            { "impactedIndices": [0, 3] }
            ```
            """.trimIndent(),
        )
        val out = agent.tag(obligation, catalogue)
        assertThat(out).containsExactly(0, 3)
    }

    @Test
    fun `empty impactedIndices array returns empty list`() {
        givenModelReturns("""{ "impactedIndices": [] }""")
        val out = agent.tag(obligation, catalogue)
        assertThat(out).isEmpty()
    }

    @Test
    fun `non-JSON output throws ImpactParseException`() {
        givenModelReturns("I cannot help with that request.")
        assertThrows<ImpactParseException> { agent.tag(obligation, catalogue) }
    }

    @Test
    fun `JSON without impactedIndices array throws ImpactParseException`() {
        givenModelReturns("""{ "products": [] }""")
        assertThrows<ImpactParseException> { agent.tag(obligation, catalogue) }
    }

    @Test
    fun `prompt presents numbered catalogue and includes descriptions`() {
        val captured = slot<List<ChatMessage>>()
        every { chatModel.generate(capture(captured)) } returns Response.from(
            AiMessage.from("""{ "impactedIndices": [] }"""),
        )

        agent.tag(obligation, catalogue)

        val systemText = (captured.captured.first { it is SystemMessage } as SystemMessage).text()
        val userText = (captured.captured.first { it is UserMessage } as UserMessage).singleText()

        assertThat(systemText).contains("taxonomist")
        assertThat(userText).contains("[0] Agent Banking")
        assertThat(userText).contains("[1] Mobile Banking")
        assertThat(userText).contains("Third-party agent outlets")
        assertThat(userText).contains(obligation.summary)
    }

    private fun givenModelReturns(text: String) {
        every { chatModel.generate(any<List<ChatMessage>>()) } returns Response.from(AiMessage.from(text))
    }
}
