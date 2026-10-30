package com.nineouting.backend.gateway.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param jwksUri            user-service 공개키 주소 (access 토큰 검증)
 * @param issuer             받을 토큰의 발급자
 * @param corsAllowedOrigins 프론트 주소 (refresh 토큰 쿠키를 주고받으므로 *는 쓸 수 없다)
 */
@ConfigurationProperties(prefix = "nineouting.gateway")
public record GatewaySecurityProperties(String jwksUri, String issuer, List<String> corsAllowedOrigins) {
}
