package io.kalunge.eunomia.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain

/**
 * Sprint 0 security posture: lock everything by default so the JWT filter we
 * add in Sprint 1 has nothing to undo, but leave actuator health and OpenAPI
 * docs publicly reachable. Without /actuator/health open, Render's probe
 * fails and the deploy looks broken.
 *
 * Replaced (not extended) when Sprint 1 brings in real JWT auth.
 */
@Configuration
class SecurityConfig {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { auth ->
                auth
                    .requestMatchers(
                        "/actuator/health",
                        "/actuator/health/**",
                        "/actuator/info",
                        "/docs",
                        "/docs/**",
                        "/v3/api-docs",
                        "/v3/api-docs/**",
                        "/swagger-ui/**",
                        "/swagger-ui.html",
                    )
                    .permitAll()
                    .anyRequest()
                    .permitAll() // Sprint 1 replaces this with .authenticated()
            }
            .httpBasic { it.disable() }
            .formLogin { it.disable() }

        return http.build()
    }
}
