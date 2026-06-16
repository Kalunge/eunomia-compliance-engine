package io.kalunge.eunomia.diff

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import dev.langchain4j.data.message.SystemMessage
import dev.langchain4j.data.message.UserMessage
import io.kalunge.eunomia.extraction.StubChatLanguageModel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * Unit tests for the Diff agent. The ChatLanguageModel is stubbed, so
 * these are fast (< 1s) and never touch Groq.
 */
class LangChain4jDiffAgentTest {

    private val mapper: ObjectMapper = jacksonObjectMapper()

    private fun candidate() = DiffSide(
        obligationId = UUID.randomUUID(),
        orderIndex = 0,
        summary = "Banks shall report suspicious transactions within 24 hours.",
        sourceExcerpt = "Where a bank observes a suspicious transaction it shall report within 24 hours.",
    )

    private fun baselines() = listOf(
        DiffSide(UUID.randomUUID(), 0, "Report SARs within 24 hours.", "A SAR shall be filed within 24h."),
        DiffSide(UUID.randomUUID(), 1, "Verify customer ID above KES 50,000.", "ID checks for >KES 50,000."),
        DiffSide(UUID.randomUUID(), 2, "Retain records for 7 years.", "Records: 7yr retention."),
    )

    @Test
    fun `parses unchanged verdict with baselineIndex`() {
        val json = """
            { "verdict": "unchanged", "baselineIndex": 0, "rationale": "Same SAR rule, paraphrased." }
        """.trimIndent()
        val agent = LangChain4jDiffAgent(StubChatLanguageModel(json), mapper)

        val j = agent.judge(candidate(), baselines())

        assertEquals(DiffVerdict.UNCHANGED, j.verdict)
        assertEquals(0, j.baselineIndex)
        assertEquals("Same SAR rule, paraphrased.", j.rationale)
    }

    @Test
    fun `parses modified verdict with baselineIndex and rationale`() {
        val json = """
            { "verdict": "modified", "baselineIndex": 1, "rationale": "Threshold raised from KES 50,000 to KES 70,000." }
        """.trimIndent()
        val agent = LangChain4jDiffAgent(StubChatLanguageModel(json), mapper)

        val j = agent.judge(candidate(), baselines())

        assertEquals(DiffVerdict.MODIFIED, j.verdict)
        assertEquals(1, j.baselineIndex)
        assertTrue(j.rationale.contains("Threshold"))
    }

    @Test
    fun `parses added verdict without baselineIndex`() {
        val json = """
            { "verdict": "added", "rationale": "No baseline covers crypto reporting." }
        """.trimIndent()
        val agent = LangChain4jDiffAgent(StubChatLanguageModel(json), mapper)

        val j = agent.judge(candidate(), baselines())

        assertEquals(DiffVerdict.ADDED, j.verdict)
        assertNull(j.baselineIndex)
        assertTrue(j.rationale.contains("crypto"))
    }

    @Test
    fun `tolerates markdown fence around the JSON`() {
        val fenced = """
            ```json
            { "verdict": "unchanged", "baselineIndex": 0, "rationale": "Same." }
            ```
        """.trimIndent()
        val agent = LangChain4jDiffAgent(StubChatLanguageModel(fenced), mapper)

        val j = agent.judge(candidate(), baselines())

        assertEquals(DiffVerdict.UNCHANGED, j.verdict)
        assertEquals(0, j.baselineIndex)
    }

    @Test
    fun `tolerates uppercase verdict from the LLM`() {
        val json = """{ "verdict": "MODIFIED", "baselineIndex": 0, "rationale": "shifted" }"""
        val agent = LangChain4jDiffAgent(StubChatLanguageModel(json), mapper)

        val j = agent.judge(candidate(), baselines())

        assertEquals(DiffVerdict.MODIFIED, j.verdict)
        assertEquals(0, j.baselineIndex)
    }

    @Test
    fun `returns ADDED without calling the model when baselines is empty`() {
        val stub = StubChatLanguageModel("""{ "should": "never see this" }""")
        val agent = LangChain4jDiffAgent(stub, mapper)

        val j = agent.judge(candidate(), emptyList())

        assertEquals(DiffVerdict.ADDED, j.verdict)
        assertNull(j.baselineIndex)
        assertTrue(stub.lastMessages.isEmpty(), "Should not have invoked the model")
    }

    @Test
    fun `throws DiffParseException when output is not JSON`() {
        val agent = LangChain4jDiffAgent(
            StubChatLanguageModel("Sorry, I can't answer that."),
            mapper,
        )

        val ex = assertThrows(DiffParseException::class.java) {
            agent.judge(candidate(), baselines())
        }
        assertTrue(ex.message!!.contains("non-JSON"))
    }

    @Test
    fun `throws DiffParseException when verdict is missing`() {
        val agent = LangChain4jDiffAgent(
            StubChatLanguageModel("""{ "rationale": "I have an opinion but no verdict" }"""),
            mapper,
        )

        val ex = assertThrows(DiffParseException::class.java) {
            agent.judge(candidate(), baselines())
        }
        assertTrue(ex.message!!.contains("verdict"))
    }

    @Test
    fun `throws DiffParseException when verdict is unknown`() {
        val agent = LangChain4jDiffAgent(
            StubChatLanguageModel("""{ "verdict": "maybe", "rationale": "I'm not sure" }"""),
            mapper,
        )

        val ex = assertThrows(DiffParseException::class.java) {
            agent.judge(candidate(), baselines())
        }
        assertTrue(ex.message!!.contains("unknown verdict"))
    }

    @Test
    fun `demotes to ADDED when unchanged verdict is missing a baselineIndex`() {
        val agent = LangChain4jDiffAgent(
            StubChatLanguageModel("""{ "verdict": "unchanged", "rationale": "claims a match without saying which" }"""),
            mapper,
        )

        val j = agent.judge(candidate(), baselines())

        assertEquals(DiffVerdict.ADDED, j.verdict)
        assertNull(j.baselineIndex)
    }

    @Test
    fun `demotes to ADDED when modified verdict has a baselineIndex out of range`() {
        val agent = LangChain4jDiffAgent(
            StubChatLanguageModel("""{ "verdict": "modified", "baselineIndex": 99, "rationale": "out of bounds" }"""),
            mapper,
        )

        val j = agent.judge(candidate(), baselines())

        assertEquals(DiffVerdict.ADDED, j.verdict)
        assertNull(j.baselineIndex)
    }

    @Test
    fun `nulls out baselineIndex when verdict is added but the LLM included a stray index`() {
        val agent = LangChain4jDiffAgent(
            StubChatLanguageModel("""{ "verdict": "added", "baselineIndex": 0, "rationale": "actually new" }"""),
            mapper,
        )

        val j = agent.judge(candidate(), baselines())

        assertEquals(DiffVerdict.ADDED, j.verdict)
        assertNull(j.baselineIndex)
    }

    @Test
    fun `sends system + user messages with candidate and baselines in the user prompt`() {
        val stub = StubChatLanguageModel("""{ "verdict": "added", "rationale": "test" }""")
        val agent = LangChain4jDiffAgent(stub, mapper)
        val cand = candidate()
        val base = baselines()

        agent.judge(cand, base)

        assertEquals(2, stub.lastMessages.size, "Expected system + user message")
        assertTrue(stub.lastMessages[0] is SystemMessage, "First message should be the system prompt")
        assertTrue(stub.lastMessages[1] is UserMessage, "Second message should be the user prompt")
        val userText = (stub.lastMessages[1] as UserMessage).singleText()
        assertTrue(userText.contains("CANDIDATE OBLIGATION"), "User prompt should label the candidate")
        assertTrue(userText.contains(cand.summary), "User prompt should include the candidate summary")
        assertTrue(userText.contains("BASELINE OBLIGATIONS"), "User prompt should label the baselines")
        base.forEach { b ->
            assertTrue(userText.contains(b.summary), "Each baseline summary should appear: ${b.summary}")
        }
    }
}
