package io.kalunge.eunomia.evaluation

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

class EvaluationDatasetLoader(
    private val objectMapper: ObjectMapper = jacksonObjectMapper(),
) {

    fun load(resourcePath: String): EvaluationDataset {
        val normalizedPath = resourcePath.removePrefix("/")
        val stream = Thread.currentThread().contextClassLoader
            .getResourceAsStream(normalizedPath)
            ?: throw IllegalArgumentException("Evaluation dataset not found: $normalizedPath")

        return stream.use {
            objectMapper.readValue(it, EvaluationDataset::class.java)
        }.also { dataset ->
            require(dataset.schemaVersion == 1) {
                "Unsupported evaluation dataset schema version ${dataset.schemaVersion}."
            }
            require(dataset.suiteId.isNotBlank()) {
                "Evaluation dataset suiteId cannot be blank."
            }
            require(dataset.cases.isNotEmpty()) {
                "Evaluation dataset '${dataset.suiteId}' must contain at least one case."
            }
            require(dataset.cases.map { it.id }.toSet().size == dataset.cases.size) {
                "Evaluation dataset '${dataset.suiteId}' contains duplicate case IDs."
            }
        }
    }
}
