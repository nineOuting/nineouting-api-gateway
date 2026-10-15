package com.nineouting.backend.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * 서비스별 API 문서를 모은 Swagger UI가 로그인 없이 열리고 서비스 5개를 고를 수 있는지 확인한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "management.server.port=0")
class SwaggerUiTest {

    @Autowired
    Environment environment;

    @Test
    void Swagger_UI는_토큰_없이_열리고_서비스_문서_5개를_보여_준다() {
        WebTestClient client = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + environment.getProperty("local.server.port"))
                .build();

        client.get().uri("/swagger-ui.html").exchange().expectStatus().is3xxRedirection();
        client.get().uri("/webjars/swagger-ui/index.html").exchange().expectStatus().isOk();
        client.get().uri("/v3/api-docs/swagger-config").exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.urls.length()").isEqualTo(5)
                .jsonPath("$.urls[?(@.name == 'user-service')].url").isEqualTo("/docs/user-service/v3/api-docs")
                // 목록은 이름순으로 나오고, 처음 열 때는 user-service 문서를 보여 준다
                .jsonPath("$['urls.primaryName']").isEqualTo("user-service");
    }
}
