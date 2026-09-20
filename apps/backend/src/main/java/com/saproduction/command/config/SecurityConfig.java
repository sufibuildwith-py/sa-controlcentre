package com.saproduction.command.config;

import com.saproduction.command.auth.BearerSessionFilter;
import com.saproduction.command.auth.DesktopBoundaryFilter;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.*;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.*;

@Configuration
public class SecurityConfig {
  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(12);
  }

  @Bean
  AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
    return config.getAuthenticationManager();
  }

  @Bean
  CorsConfigurationSource corsConfigurationSource(
      @Value("${app.desktop-origins}") List<String> origins) {
    CorsConfiguration c = new CorsConfiguration();
    c.setAllowedOrigins(origins);
    c.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    c.setAllowedHeaders(List.of("Content-Type", "Accept", "Authorization", "X-SA-Desktop-Key"));
    c.setAllowCredentials(false);
    c.setMaxAge(3600L);
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", c);
    return source;
  }

  @Bean
  SecurityFilterChain security(
      HttpSecurity http,
      BearerSessionFilter bearerSessionFilter,
      DesktopBoundaryFilter desktopBoundaryFilter)
      throws Exception {
    return http
        // The native API is authenticated only by an explicit Authorization bearer token.
        // Browsers cannot attach it cross-site automatically, so cookie CSRF does not apply.
        .cors(c -> {})
        .csrf(c -> c.disable())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            a ->
                a.requestMatchers(
                        "/api/v1/auth/login",
                        "/api/v1/owner-setup",
                        "/api/v1/integrations/whatsapp/webhook",
                        "/actuator/health")
                    .permitAll()
                    .requestMatchers(HttpMethod.OPTIONS, "/**")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .logout(l -> l.disable())
        .headers(h -> h.frameOptions(f -> f.deny()).contentTypeOptions(c -> {}))
        .addFilterBefore(bearerSessionFilter, UsernamePasswordAuthenticationFilter.class)
        .addFilterBefore(desktopBoundaryFilter, BearerSessionFilter.class)
        .build();
  }
}
