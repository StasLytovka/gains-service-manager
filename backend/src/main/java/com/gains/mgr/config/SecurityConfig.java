package com.gains.mgr.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Spring Security configuration.
 *
 * @Configuration - marks this class as a source of @Bean definitions.
 *   Spring reads it at startup and registers the returned objects in the context.
 *
 * @EnableWebSecurity - activates Spring Security's web support,
 *   replacing the default auto-configured security.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtFilter jwtFilter;

    public SecurityConfig(JwtFilter jwtFilter) {
        this.jwtFilter = jwtFilter;
    }

    /**
     * Defines the security filter chain — the rules for every HTTP request.
     *
     * @Bean - tells Spring to register the returned object as a bean
     *         so it can be injected elsewhere and managed by the framework.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // Disable CSRF (Cross-Site Request Forgery) protection.
            // CSRF tokens are for session-based auth; JWT is stateless so not needed.
            .csrf(csrf -> csrf.disable())

            // Apply CORS configuration (defined below)
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))

            // Use STATELESS sessions — Spring will NOT create HTTP sessions.
            // Each request must carry its own JWT token; no cookies, no server state.
            .sessionManagement(session ->
                    session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // Define which endpoints require authentication
            .authorizeHttpRequests(auth -> {
                auth.requestMatchers("/api/auth/login").permitAll(); // login is public
                auth.anyRequest().authenticated();                    // everything else requires a valid token
            })

            // Register our JWT filter BEFORE Spring's default username/password filter.
            // This ensures the token is validated before any other security checks.
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * CORS (Cross-Origin Resource Sharing) configuration.
     *
     * Browsers block requests from one origin (e.g. localhost:4200) to another
     * (e.g. localhost:8090) unless the server explicitly allows it.
     * This bean configures which origins, methods and headers are permitted.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        // Allow requests from Angular dev server and production domain
        config.setAllowedOrigins(List.of(
                "http://localhost:4200",
                "http://multitenantpoc.gainsystems.com",
                "https://multitenantpoc.gainsystems.com"
        ));

        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));   // allow all request headers
        config.setAllowCredentials(true);          // allow cookies / Authorization header

        // Apply this CORS config to all endpoints
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
