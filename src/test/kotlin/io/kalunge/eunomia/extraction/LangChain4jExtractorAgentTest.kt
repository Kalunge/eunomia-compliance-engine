package io.kalunge.eunomia.extraction

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import dev.langchain4j.data.message.SystemMessage
import dev.langchain4j.data.message.UserMessage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Unit tests for the Extractor agent. The ChatLanguageModel is stubbed,
 * so these are fast (< 1s) and never touch Groq.
 */
class LangChain4jExtractorAgentTest {

    private val mapper: ObjectMapper = jacksonObjectMapper()

    @Test
    fun `parses a clean JSON response into ExtractedObligation list`() {
        val json = """
            {
              "obligations": [
                {
                  "summary": "Banks shall report suspicious transactions to the FRC within 24 hours.",
                  "sourceExcerpt": "Where an agent observes a suspicious transaction, the institution shall report to the FRC within 24 hours."
                },
                {
                  "summary": "Agents must verify customer identity for transactions above KES 70,000.",
                  "sourceExcerpt": "Agents shall verify identity for any transaction exceeding KES 70,000."
                }
              ]
            }
        """.trimIndent()
        val agent = LangChain4jExtractorAgent(StubChatLanguageModel(json), mapper)

        val result = agent.extract("Some regulatory text")

        assertEquals(2, result.size)
        assertEquals(
            "Banks shall report suspicious transactions to the FRC within 24 hours.",
            result[0].summary,
        )
        assertTrue(result[0].sourceExcerpt.contains("24 hours"))
        assertEquals(null, result[0].pageStart) // Sprint 1 doesn't extract pages yet
    }

    @Test
    fun `tolerates markdown code fence around the JSON`() {
        val fenced = """
            Here you go:

            ```json
            {
              "obligations": [
                { "summary": "Foo", "sourceExcerpt": "bar" }
              ]
            }
            ```
        """.trimIndent()
        val agent = LangChain4jExtractorAgent(StubChatLanguageModel(fenced), mapper)

        val result = agent.extract("doc")

        assertEquals(1, result.size)
        assertEquals("Foo", result[0].summary)
    }

    @Test
    fun `skips obligations with empty summary or excerpt rather than emitting blanks`() {
        val json = """
            {
              "obligations": [
                { "summary": "valid one", "sourceExcerpt": "good excerpt" },
                { "summary": "", "sourceExcerpt": "no summary" },
                { "summary": "no excerpt", "sourceExcerpt": "   " }
              ]
            }
        """.trimIndent()
        val agent = LangChain4jExtractorAgent(StubChatLanguageModel(json), mapper)

        val result = agent.extract("doc")

        assertEquals(1, result.size)
        assertEquals("valid one", result[0].summary)
    }

    @Test
    fun `returns empty list when the document has no obligations`() {
        val json = """{ "obligations": [] }"""
        val agent = LangChain4jExtractorAgent(StubChatLanguageModel(json), mapper)

        val result = agent.extract("doc")

        assertEquals(0, result.size)
    }

    @Test
    fun `throws ExtractionParseException when output is not JSON`() {
        val agent = LangChain4jExtractorAgent(
            StubChatLanguageModel("Sorry, I don't know how to answer that."),
            mapper,
        )

        val ex = assertThrows(ExtractionParseException::class.java) {
            agent.extract("doc")
        }
        assertTrue(ex.message!!.contains("non-JSON"))
    }

    @Test
    fun `throws when obligations key is missing or wrong shape`() {
        val agent = LangChain4jExtractorAgent(
            StubChatLanguageModel("""{ "wrong_key": [] }"""),
            mapper,
        )

        val ex = assertThrows(ExtractionParseException::class.java) {
            agent.extract("doc")
        }
        assertTrue(ex.message!!.contains("obligations"))
    }

    @Test
    fun `rejects empty document text`() {
        val agent = LangChain4jExtractorAgent(StubChatLanguageModel("{}"), mapper)

        assertThrows(IllegalArgumentException::class.java) {
            agent.extract("   ")
        }
    }

    @Test
    fun `sends system + user message to the underlying model`() {
        val stub = StubChatLanguageModel("""{ "obligations": [] }""")
        val agent = LangChain4jExtractorAgent(stub, mapper)

        agent.extract("The bank shall do X.")

        assertEquals(2, stub.lastMessages.size, "Expected system + user message")
        assertTrue(stub.lastMessages[0] is SystemMessage, "First message should be the system prompt")
        assertTrue(stub.lastMessages[1] is UserMessage, "Second message should be the user prompt")
        val userText = (stub.lastMessages[1] as UserMessage).singleText()
        assertTrue(userText.contains("The bank shall do X."), "User prompt should include the document")
    }
}
