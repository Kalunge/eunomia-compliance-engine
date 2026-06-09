package io.kalunge.eunomia

import org.springframework.boot.fromApplication
import org.springframework.boot.with


fun main(args: Array<String>) {
	fromApplication<EunomiaComplianceEngineApplication>().with(TestcontainersConfiguration::class).run(*args)
}
