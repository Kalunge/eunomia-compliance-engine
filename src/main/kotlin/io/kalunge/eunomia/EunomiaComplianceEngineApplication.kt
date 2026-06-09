package io.kalunge.eunomia

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class EunomiaComplianceEngineApplication

fun main(args: Array<String>) {
	runApplication<EunomiaComplianceEngineApplication>(*args)
}
