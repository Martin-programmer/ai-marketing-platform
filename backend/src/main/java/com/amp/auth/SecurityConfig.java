package com.amp.auth;

import org.springframework.beans.factory.annotation.Value;
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
 * <p>
 * When {@code app.security.dev-mode=true} (local/test profiles) the
 * {@link DevAuthFilter} is registered so that requests can be
 * authenticated via {@code X-Dev-User-*} headers.
 * <p>
 * In all other cases the {@link JwtAuthFilter} is used and
 * authentication requires a valid Bearer JWT.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /** true only when running under the local/test profiles */
    @Value("${app.security.dev-mode:false}")
    private boolean devMode;

    private final JwtService jwtService;
    private final UserAccountRepository userAccountRepository;

    public SecurityConfig(JwtService jwtService, UserAccountRepository userAccountRepository) {
        this.jwtService = jwtService;
        this.userAccountRepository = userAccountRepository;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        JwtAuthFilter jwtAuthFilter = new JwtAuthFilter(jwtService, userAccountRepository);
        DevAuthFilter devAuthFilter = new DevAuthFilter(userAccountRepository);

        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                        "/actuator/**",
                        "/swagger-ui.html",
                        "/swagger-ui/**",
                        "/v3/api-docs",
                        "/v3/api-docs/**",
                        "/api-docs",
                        "/api-docs/**",
                        "/webjars/**",
                        "/api/v1/auth/**"
                ).permitAll()
                .anyRequest().authenticated()
            );

        // Always register JWT filter so Bearer tokens work in every profile
        http.addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        if (devMode) {
            // Local / test: also allow header-based dev authentication
            http.addFilterBefore(devAuthFilter, jwtAuthFilter.getClass());
        }

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("http://localhost:5173"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
