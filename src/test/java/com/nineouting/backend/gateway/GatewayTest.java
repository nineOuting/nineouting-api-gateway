package com.nineouting.backend.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Jwks;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * user-service 대신 JDK HTTP 서버를 띄워 실제로 요청을 중계하는지 확인한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayTest {

    private static final String ISSUER = "nineouting-user-service";
    private static final String FRONTEND = "http://localhost:5173";
    private static final KeyPair KEYS = Jwts.SIG.RS256.keyPair().build();
    private static final AtomicReference<String> RECEIVED_AUTHORIZATION = new AtomicReference<>();
    private static final HttpServer USER_SERVICE = startUserService();

    @Autowired
    Environment environment;

    private WebTestClient client;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        String userService = "http://localhost:" + USER_SERVICE.getAddress().getPort();
        registry.add("JWT_JWKS_URI", () -> userService + "/.well-known/jwks.json");
        registry.add("management.server.port", () -> "0");
        // 목록 설정은 한 곳에서 통째로 정해지므로, 503 확인용 라우트를 더하려면 기존 라우트도 함께 적는다
        String routes = "spring.cloud.gateway.server.webflux.routes";
        registry.add(routes + "[0].id", () -> "user-service-auth");
        registry.add(routes + "[0].uri", () -> userService);
        registry.add(routes + "[0].predicates[0]", () -> "Path=/api/auth/**");
        registry.add(routes + "[1].id", () -> "user-service-users");
        registry.add(routes + "[1].uri", () -> userService);
        registry.add(routes + "[1].predicates[0]", () -> "Path=/api/users/**");
        registry.add("spring.cloud.gateway.server.webflux.routes[2].id", () -> "stopped-service");
        registry.add("spring.cloud.gateway.server.webflux.routes[2].uri", () -> "http://127.0.0.1:1");
        registry.add("spring.cloud.gateway.server.webflux.routes[2].predicates[0]", () -> "Path=/api/stopped/**");
    }

    @BeforeEach
    void setUp() {
        client = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + environment.getProperty("local.server.port"))
                .build();
        RECEIVED_AUTHORIZATION.set(null);
    }

    @AfterAll
    static void stopUserService() {
        USER_SERVICE.stop(0);
    }

    @Test
    void 로그인_같은_공개_경로는_토큰_없이_서비스로_간다() {
        client.post().uri("/api/auth/login").exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.path").isEqualTo("/api/auth/login");
    }

    @Test
    void 토큰이_없으면_401_UNAUTHORIZED다() {
        client.get().uri("/api/users/me").exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.success").isEqualTo(false)
                .jsonPath("$.code").isEqualTo("UNAUTHORIZED");
    }

    @Test
    void 올바른_토큰이면_서비스로_보내고_Authorization_헤더를_넘긴다() {
        String token = token(ISSUER, Instant.now().plusSeconds(600), KEYS);

        client.get().uri("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token).exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.path").isEqualTo("/api/users/me");
        assertThat(RECEIVED_AUTHORIZATION.get()).isEqualTo("Bearer " + token);
    }

    @Test
    void 만료된_토큰은_401_TOKEN_EXPIRED다() {
        String token = token(ISSUER, Instant.now().minusSeconds(600), KEYS);

        client.get().uri("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token).exchange()
                .expectStatus().isUnauthorized()
                .expectBody().jsonPath("$.code").isEqualTo("TOKEN_EXPIRED");
    }

    @Test
    void 발급자가_다르거나_다른_키로_서명한_토큰은_401_UNAUTHORIZED다() {
        String wrongIssuer = token("someone-else", Instant.now().plusSeconds(600), KEYS);
        String otherKey = token(ISSUER, Instant.now().plusSeconds(600), Jwts.SIG.RS256.keyPair().build());

        for (String token : new String[] {wrongIssuer, otherKey}) {
            client.get().uri("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token).exchange()
                    .expectStatus().isUnauthorized()
                    .expectBody().jsonPath("$.code").isEqualTo("UNAUTHORIZED");
        }
        assertThat(RECEIVED_AUTHORIZATION.get()).isNull();
    }

    @Test
    void 내부_주소는_외부로_열지_않는다() {
        String token = token(ISSUER, Instant.now().plusSeconds(600), KEYS);

        for (String path : new String[] {"/internal/users?ids=1", "/.well-known/jwks.json"}) {
            client.get().uri(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + token).exchange()
                    .expectStatus().isNotFound()
                    .expectBody().jsonPath("$.code").isEqualTo("NOT_FOUND");
        }
    }

    @Test
    void 서비스가_꺼져_있으면_503_SERVICE_UNAVAILABLE이다() {
        String token = token(ISSUER, Instant.now().plusSeconds(600), KEYS);

        client.get().uri("/api/stopped/x").header(HttpHeaders.AUTHORIZATION, "Bearer " + token).exchange()
                .expectStatus().isEqualTo(503)
                .expectBody().jsonPath("$.code").isEqualTo("SERVICE_UNAVAILABLE");
    }

    @Test
    void 프론트_주소의_CORS_요청만_허용한다() {
        client.options().uri("/api/auth/login")
                .header(HttpHeaders.ORIGIN, FRONTEND)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, FRONTEND)
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true");

        client.options().uri("/api/auth/login")
                .header(HttpHeaders.ORIGIN, "http://evil.example.com")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .exchange()
                .expectStatus().isForbidden();
    }

    private static String token(String issuer, Instant expiresAt, KeyPair keys) {
        return Jwts.builder()
                .header().keyId("key-1").and()
                .issuer(issuer)
                .subject("42")
                .issuedAt(Date.from(expiresAt.minusSeconds(1800)))
                .expiration(Date.from(expiresAt))
                .signWith(keys.getPrivate(), Jwts.SIG.RS256)
                .compact();
    }

    private static HttpServer startUserService() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            String jwks = "{\"keys\":[" + Jwks.json(Jwks.builder().key((RSAPublicKey) KEYS.getPublic()).id("key-1").build()) + "]}";
            server.createContext("/.well-known/jwks.json", exchange -> respond(exchange, jwks));
            server.createContext("/", exchange -> {
                RECEIVED_AUTHORIZATION.set(exchange.getRequestHeaders().getFirst(HttpHeaders.AUTHORIZATION));
                respond(exchange, "{\"path\":\"" + exchange.getRequestURI().getPath() + "\"}");
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void respond(HttpExchange exchange, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
