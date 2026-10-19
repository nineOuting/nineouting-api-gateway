package com.nineouting.backend.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * application.yml의 실제 라우트가 경로마다 맞는 서비스로 가는지 확인한다.
 * 같은 접두사를 여러 서비스가 나눠 쓰므로(/api/users, /api/folders, /api/meetings) 순서가 중요하다.
 */
@SpringBootTest(properties = "management.server.port=0")
class RouteOrderTest {

    @Autowired
    RouteLocator routeLocator;

    @ParameterizedTest
    @CsvSource({
            "/api/auth/login, user-service-auth",
            "/api/users/me, user-service-users",
            "/api/users/me/recent-folders, workspace-service-recent-folders",
            "/api/workspaces/1/folders, workspace-service",
            "/api/folders/3, workspace-service",
            "/api/folders/3/members, workspace-service",
            "/api/folders/3/meetings, meeting-service",
            "/api/folders/3/pending-agendas, meeting-service",
            "/api/meetings/calendar, meeting-service",
            "/api/meetings/7/start, meeting-service",
            "/api/notifications/stream, notification-service",
            "/api/minutes/5, minutes-service",
            "/api/minutes/5/review-request, minutes-service",
            "/api/minutes-templates, minutes-service",
            "/api/meetings/7/minutes, minutes-service",
            "/api/folders/3/minutes, minutes-service",
            "/api/users/me/review-requests, minutes-service",
            "/api/users/me/recent-minutes, minutes-service",
            "/docs/user-service/v3/api-docs, user-service-api-docs",
            "/docs/minutes-service/v3/api-docs, minutes-service-api-docs",
            "/ws/meetings/7, meeting-room-websocket",
            "/ws/stt/meetings/7, stt-websocket"
    })
    void 경로마다_맞는_서비스로_간다(String path, String routeId) {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get(path));

        String matched = routeLocator.getRoutes()
                .concatMap(route -> Flux.from(route.getPredicate().apply(exchange))
                        .filter(Boolean::booleanValue)
                        .map(ignored -> route))
                .next()
                .map(Route::getId)
                .switchIfEmpty(Mono.just("none"))
                .block();

        assertThat(matched).isEqualTo(routeId);
    }
}
