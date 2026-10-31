package com.intact_id.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * CORS configuration for allowing dashboard frontend to access the API
 */
@Configuration
public class CorsConfig {

    @Value("#{'${cors.allowed-origins}'.split(',')}")
    private List<String> allowedOrigins;

    private static final List<String> ALLOWED_METHODS = Arrays.asList(
            "GET",
            "POST",
            "PUT",
            "PATCH",
            "DELETE",
            "OPTIONS"
    );



    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();

        // Widget endpoints are embedded on third-party origins — allow any origin,
        // but no credentials (auth is via X-Session-Token header, not cookies).
        CorsConfiguration widgetConfig = new CorsConfiguration();
        widgetConfig.setAllowedOriginPatterns(Arrays.asList("*"));
        widgetConfig.setAllowedMethods(ALLOWED_METHODS);
        widgetConfig.setAllowedHeaders(Arrays.asList("*"));
        widgetConfig.setAllowCredentials(false);
        widgetConfig.setMaxAge(3600L);
        source.registerCorsConfiguration("/api/v1/kyc/widget/**", widgetConfig);
        source.registerCorsConfiguration("/api/v1/kyc/handoff/**", widgetConfig);

        // All other endpoints: restrict to known dashboard/API origins.
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(ALLOWED_METHODS);
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);
        source.registerCorsConfiguration("/**", configuration);

        return source;
    }
}
