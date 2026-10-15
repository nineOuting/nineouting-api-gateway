package com.nineouting.backend.gateway.config;

import com.nineouting.backend.common.web.error.CommonErrorCode;
import java.util.List;
import java.util.Locale;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

/**
 * access 토큰을 user-service 공개키(JWKS)로 검증한다. 통과한 요청은 Authorization 헤더를 그대로 서비스에 넘기고,
 * 서비스도 한 번 더 검증한다.
 */
@Configuration
public class SecurityConfig {

    private static final String[] PUBLIC_PATHS = {
            "/api/auth/**", "/actuator/**",
            // API 문서 (Swagger UI와 서비스별 문서)
            "/swagger-ui.html", "/swagger-ui/**", "/webjars/**", "/v3/api-docs/**", "/docs/**"
    };
    private static final List<String> CORS_METHODS = List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");
    private static final long CORS_MAX_AGE_SECONDS = 3600;

    @Bean
    SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http, ReactiveJwtDecoder jwtDecoder,
                                                  JsonErrorWriter errorWriter) {
        ServerAuthenticationEntryPoint entryPoint = (exchange, e) -> errorWriter.write(exchange,
                isExpired(e) ? GatewayErrorCode.TOKEN_EXPIRED : CommonErrorCode.UNAUTHORIZED);
        ServerAccessDeniedHandler accessDeniedHandler = (exchange, e) ->
                errorWriter.write(exchange, CommonErrorCode.FORBIDDEN);

        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .cors(Customizer.withDefaults())
                .authorizeExchange(exchange -> exchange
                        .pathMatchers(HttpMethod.OPTIONS).permitAll()
                        .pathMatchers(PUBLIC_PATHS).permitAll()
                        .anyExchange().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtDecoder(jwtDecoder))
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .exceptionHandling(e -> e.authenticationEntryPoint(entryPoint).accessDeniedHandler(accessDeniedHandler))
                .build();
    }

    /** 공개키를 받아 캐시하고, 서명·만료·발급자를 검사한다. */
    @Bean
    ReactiveJwtDecoder jwtDecoder(GatewaySecurityProperties properties) {
        NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder.withJwkSetUri(properties.jwksUri()).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(properties.issuer()));
        return decoder;
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(GatewaySecurityProperties properties) {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(properties.corsAllowedOrigins());
        cors.setAllowedMethods(CORS_METHODS);
        cors.setAllowedHeaders(List.of("*"));
        cors.setAllowCredentials(true);
        cors.setMaxAge(CORS_MAX_AGE_SECONDS);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cors);
        return source;
    }

    /** Spring Security는 만료를 별도 예외로 주지 않아 메시지("Jwt expired at ...")로 구분한다. */
    private static boolean isExpired(AuthenticationException e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t.getMessage() != null && t.getMessage().toLowerCase(Locale.ROOT).contains("expired")) {
                return true;
            }
        }
        return false;
    }
}
