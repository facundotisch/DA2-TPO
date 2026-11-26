package logired.backend.config;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import logired.backend.usuario.service.LoginRateLimiter;

@Configuration
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   LoginRateLimiter loginRateLimiter) throws Exception {

        http
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        // /ws/** es el canal SOAP legado (sin datos personales, protegido contra XXE).
                        .requestMatchers("/api/auth/login", "/api/auth/register", "/ws/**").permitAll()
                        // Los eventos JMS recientes solo los ve un administrador (antes eran públicos).
                        .requestMatchers("/api/mensajeria/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/pedido", "/api/pedido/**")
                        .hasAnyRole("OPERADOR", "ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/pedido/**")
                        .hasAnyRole("OPERADOR", "ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/pedido/**")
                        .hasAnyRole("OPERADOR", "ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/pedido/**")
                        .hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/productos", "/api/inventario")
                        .hasAnyRole("OPERADOR", "ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/productos/**", "/api/inventario/**")
                        .hasAnyRole("OPERADOR", "ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/productos/**", "/api/inventario/**")
                        .hasRole("ADMIN")
                        .anyRequest().authenticated()
                )
                .headers(headers -> headers
                        // La API solo devuelve JSON/XML: no necesita cargar scripts, estilos ni frames.
                        .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'none'; frame-ancestors 'none'"))
                        .httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31536000)))
                .addFilterBefore(new BasicAuthRateLimitFilter(loginRateLimiter), BasicAuthenticationFilter.class)
                .httpBasic(Customizer.withDefaults());

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of("http://localhost:*", "http://127.0.0.1:*"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "HEAD"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setExposedHeaders(List.of("ETag", "Retry-After", "Idempotency-Replayed"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
