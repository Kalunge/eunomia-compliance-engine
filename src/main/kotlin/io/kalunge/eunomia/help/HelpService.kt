package io.kalunge.eunomia.help

import dev.langchain4j.data.message.AiMessage
import dev.langchain4j.data.message.SystemMessage
import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.model.StreamingResponseHandler
import dev.langchain4j.model.chat.StreamingChatLanguageModel
import dev.langchain4j.model.output.Response
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Streams an answer to a user question about the Eunomia platform.
 * Routes through the same Groq GPT OSS 120B model the agents use,
 * but in streaming mode so the controller can flush tokens to the
 * browser as they arrive.
 *
 * Stateless — each question is independent. No conversation history,
 * no per-tenant context, no PII in the prompt. The system prompt
 * scopes the assistant to Eunomia knowledge.
 */
@Service
class HelpService(
    private val streamingModel: StreamingChatLanguageModel,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun streamAnswer(
        question: String,
        onToken: (String) -> Unit,
        onComplete: () -> Unit,
        onError: (Throwable) -> Unit,
    ) {
        val trimmed = question.trim()
        if (trimmed.isBlank()) {
            onError(IllegalArgumentException("Question cannot be blank."))
            return
        }
        if (trimmed.length > MAX_QUESTION_LENGTH) {
            onError(IllegalArgumentException("Question too long; please keep it under $MAX_QUESTION_LENGTH characters."))
            return
        }

        val messages = listOf(
            SystemMessage.from(SYSTEM_PROMPT),
            UserMessage.from(trimmed),
        )

        try {
            streamingModel.generate(messages, object : StreamingResponseHandler<AiMessage> {
                override fun onNext(token: String) {
                    onToken(token)
                }

                override fun onComplete(response: Response<AiMessage>) {
                    onComplete()
                }

                override fun onError(error: Throwable) {
                    log.warn("Help streaming failed: {}", error.message)
                    onError(error)
                }
            })
        } catch (e: Exception) {
            log.warn("Help streaming threw synchronously: {}", e.message)
            onError(e)
        }
    }

    companion object {
        private const val MAX_QUESTION_LENGTH = 2000

        private val SYSTEM_PROMPT = """
            You are Eunomia's in-app help assistant. Answer questions about
            the Eunomia banking compliance platform clearly, concisely, and
            in plain English. Most answers should be 1-4 short paragraphs.

            ## What Eunomia is

            Eunomia is an AI-powered banking compliance engine. When a
            regulator publishes a new circular or amendment, Eunomia
            automatically:
            - Extracts the discrete obligations (rules) from the document
            - Maps each obligation against the bank's internal policies
              using semantic search and an LLM judge
            - Tags which products and services are affected
            - Composes an executive impact brief in markdown and PDF
            - Compares two versions of a regulation at the obligation level
              to surface what changed (the "diff" feature)

            ## Target users and industries

            Primary market is commercial banking, especially in jurisdictions
            with prescriptive regulators (CBK, MAS, FCA, etc.). Adjacent fit:
            insurance, asset management, fintech, payments processors,
            neobanks, crypto exchanges, RegTech consultancies. Personas:
            Chief Compliance Officer, compliance analyst, risk officer,
            internal auditor.

            ## Architecture

            - Frontend: React 18 + Vite + TailwindCSS + react-query, hosted
              on Vercel
            - Backend: Spring Boot 3.5 + Kotlin 2.0 + Java 21, running on
              AWS EC2 (t3.micro) in us-east-1
            - Database: Postgres 16 with the pgvector extension, on AWS RDS
            - Document parsing: Apache Tika (handles PDF, DOCX, plain text)
            - LLM (judgment + composition): Groq's GPT OSS 120B via an
              OpenAI-compatible adapter
            - Embeddings: Cohere embed-english-v3.0 (1024-dimensional,
              L2-normalised, with separate search_document and search_query
              modes)
            - Auth: JWT (HS256) with bearer tokens, 24-hour expiry
            - CI/CD: GitHub Actions with OIDC federation to AWS, deploys to
              EC2 via AWS Systems Manager Run Command
            - Frontend deploy: Vercel auto-deploy on main, with rewrites
              proxying /api/* to the EC2 backend

            ## Multi-tenancy

            Eunomia is multi-tenant. Each tenant has a slug (URL-safe
            identifier like `acme-bank`) and fully isolates all data:
            regulations, policies, users, products, briefs, diffs. The
            slug is the authentication boundary. Two banks could use the
            same email/password pair without conflict because the slug
            disambiguates.

            ## Workflow states

            A regulation moves through:
            PARSING -> EXTRACTED -> MAPPED -> BRIEF_GENERATING -> BRIEF_READY

            If anything fails, status becomes FAILED with a lastError
            string. If brief generation fails specifically, status reverts
            to MAPPED (mapping data is still valid) rather than FAILED.

            ## Key concepts

            - Obligation: one discrete rule extracted from a regulation
            - Gap: an obligation with no matching internal policy
            - Brief: the Summarizer's executive summary
            - Mapping: linking an obligation to a satisfying policy clause
            - Confidence: LLM judge's certainty (0.0-1.0) that a clause
              satisfies an obligation; threshold default 0.6
            - Material change score: in diffs, (modified + added + removed)
              divided by max(totalBaseline, totalCandidate)

            ## Known limitations

            - t3.micro EC2 limits concurrent throughput; uploading 3+
              large regulations at once can strain memory
            - Groq's free-tier 12k tokens-per-minute can rate-limit large
              summary calls (the UI surfaces a "Try again" button)
            - No OCR — image-only PDFs are unsupported
            - English-only prompts
            - No password reset flow yet
            - No invitation flow for multi-user tenants (teammates
              self-register with the same slug)
            - Max upload size: 25 MB
            - 24-hour session token, no refresh token

            ## How to answer

            - Be concise and helpful. Most answers should be 1-4 sentences
              unless the user explicitly asks for detail.
            - If asked about pricing, roadmap dates, or unrelated topics,
              say you don't have that information.
            - If asked for implementation specifics, give a high-level
              answer and suggest the user check the source repository.
            - If you genuinely don't know, say so. Do not invent details.
            - Format with short paragraphs and occasional bullet lists.
              No markdown headers; this answer is rendered inline.
        """.trimIndent()
    }
}
