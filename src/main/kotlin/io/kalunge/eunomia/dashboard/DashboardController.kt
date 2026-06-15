package io.kalunge.eunomia.dashboard

import io.kalunge.eunomia.tenancy.TenantContext
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/dashboard")
class DashboardController(
    private val dashboardService: DashboardService,
) {

    @GetMapping
    fun summary(): DashboardSummary {
        val tenantId = TenantContext.requireTenantId()
        return dashboardService.summary(tenantId)
    }
}
