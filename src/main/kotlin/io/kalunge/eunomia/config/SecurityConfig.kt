package io.kalunge.eunomia.config

import io.kalunge.eunomia.auth.JwtAuthenticationFilter
import io.kalunge.eunomia.auth.JwtProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.HttpStatusEntryPoint
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

/**
 * Real Spring Security wiring for Sprint 1.
 *
 * Public surface (no auth required):
 *   - GET  /actuator/health and sub-paths, /actuator/info
 *   - GET  /docs and sub-paths, /v3/api-docs, /swagger-ui
 *   - POST /api/v1/auth/login, /api/v1/auth/register
 *
 * Everything else requires a valid JWT in the Authorization header.
 */
@Configuration
@EnableConfigurationProperties(JwtProperties::class)
class SecurityConfig {

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder(12)

    @Bean
    fun securityFilterChain(
        http: HttpSecurity,
        jwtFilter: JwtAuthenticationFilter,
    ): SecurityFilterChain {
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
                    ).permitAll()
                    .requestMatchers(
                        "/api/v1/auth/login",
                        "/api/v1/auth/register",
                    ).permitAll()
                    .anyRequest().authenticated()
            }
            .httpBasic { it.disable() }
            .formLogin { it.disable() }
            // Spring Security 6 defaults to 403 for unauthenticated calls.
            // For a JWT-protected REST API the semantically-correct status
            // is 401: client needs to authenticate, not "authenticated but
            // forbidden." Override the entry point to return 401.
            .exceptionHandling { it.authenticationEntryPoint(HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)) }
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter::class.java)

        return http.build()
    }
}
