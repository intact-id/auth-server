package com.intact_id.config;

import com.intact_id.security.filter.ApiKeyAuthFilter;
import com.intact_id.security.filter.JwtAuthenticationFilter;
import com.intact_id.security.filter.UserActivityAuditFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity // Enable @PreAuthorize, @PostAuthorize, etc.
@RequiredArgsConstructor
public class SecurityConfig {

        private final ApiKeyAuthFilter apiKeyAuthFilter;
        private final JwtAuthenticationFilter jwtAuthFilter;
        private final UserActivityAuditFilter userActivityAuditFilter;
        private final CorsConfigurationSource corsConfigurationSource;

        @Bean
        public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
                http
                                // Disable CSRF for API
                                .csrf(AbstractHttpConfigurer::disable)

                                // Enable CORS
                                .cors(cors -> cors.configurationSource(corsConfigurationSource))

                                // Stateless session management
                                .sessionManagement(session -> session
                                                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                                // Configure authorization
                                .authorizeHttpRequests(auth -> auth
                                                // Public endpoints - no authentication required
                                                .requestMatchers(
                                                                "/api/public/**",
                                                                "/api/auth/login",
                                                                "/api/auth/refresh",
                                                                "/api/v1/tiers/**",
                                                                "/swagger-ui/**",
                                                                "/swagger-ui.html",
                                                                "/webjars/**",
                                                                "/v3/api-docs/**",
                                                                "/actuator/**",
                                                                "/favicon.ico",
                                                                "/error")
                                                .permitAll()

                                                // All other requests require authentication (either JWT or API Key)
                                                .anyRequest().authenticated())

                                // Add filters in correct order:
                                // 1. JWT filter (for admin users with Bearer tokens)
                                // 2. API Key filter (for company API access with X-API-Key)
                                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                                .addFilterAfter(apiKeyAuthFilter, JwtAuthenticationFilter.class)
                                .addFilterAfter(userActivityAuditFilter, ApiKeyAuthFilter.class);

                return http.build();
        }
}
