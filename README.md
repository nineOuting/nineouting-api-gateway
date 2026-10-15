# nineouting-api-gateway

외부 요청을 각 서비스로 보내고 access 토큰(JWT)을 검증하는 API Gateway. **개발 단계용**이며 AWS 배포 시 Istio Ingress Gateway로 바꾼다.

- 설계: [nineouting-docs/docs/services/api-gateway.md](https://github.com/nineOuting/nineouting-docs/blob/main/docs/services/api-gateway.md)
- 포트: `8080` (외부), `8090` (관리: `/actuator/health`, `/actuator/prometheus`)
- Spring Cloud Gateway **WebFlux** (WebSocket 중계 때문), Spring Boot 4.0.8 + Spring Cloud 2025.1.3

## 라우팅

| 경로 | 서비스 | 로그인 |
|------|--------|--------|
| `/api/auth/**` | user-service | 불필요 |
| `/api/minutes/**`, `/api/minutes-templates/**`, `/api/meetings/*/minutes`, `/api/folders/*/minutes`, `/api/users/me/review-requests`, `/api/users/me/recent-minutes` | minutes-service | 필요 |
| `/api/users/me/recent-folders` | workspace-service | 필요 |
| `/api/users/**` | user-service | 필요 |
| `/api/meetings/**`, `/api/folders/*/meetings/**`, `/api/folders/*/pending-agendas` | meeting-service | 필요 |
| `/api/workspaces/**`, `/api/invites/**`, `/api/folders/**` (위 meeting 경로 제외) | workspace-service | 필요 |
| `/api/notifications/**` (SSE 포함) | notification-service | 필요 |

라우트에 없는 경로(`/internal/**`, `/.well-known/**` 포함)는 `404 NOT_FOUND`.

## API 문서 (Swagger)

`http://localhost:8080/swagger-ui.html` — 서비스 5개의 API 문서를 한 화면에서 고른다 (로그인 없이 열림).
`/docs/{서비스}/v3/api-docs` 라우트가 각 서비스의 `/v3/api-docs`를 가져온다. Authorize 버튼에 로그인으로 받은 access 토큰을 넣으면 "Try it out"이 Gateway를 거쳐 실제로 호출된다.
운영에서 닫기: `SPRINGDOC_SWAGGER_UI_ENABLED=false`, `SPRINGDOC_API_DOCS_ENABLED=false`.

## 로컬 실행

필요한 것: JDK 21, 실행 중인 user-service(8081)

```bash
./gradlew bootRun --args='--spring.profiles.active=local'
```

공통 모듈을 받으려면 `~/.gradle/gradle.properties`에 `gpr.user`, `gpr.key`(read:packages 토큰)가 있어야 한다.

## 환경 변수

| 이름 | 기본값 | 설명 |
|------|--------|------|
| `SERVER_PORT` | `8080` | |
| `MANAGEMENT_PORT` | `8090` | health·prometheus |
| `USER_SERVICE_URL` | `http://localhost:8081` | |
| `WORKSPACE_SERVICE_URL` | `http://localhost:8082` | |
| `MEETING_SERVICE_URL` | `http://localhost:8083` | |
| `MINUTES_SERVICE_URL` | `http://localhost:8084` | |
| `NOTIFICATION_SERVICE_URL` | `http://localhost:8085` | |
| `JWT_JWKS_URI` | `http://localhost:8081/.well-known/jwks.json` | access 토큰 검증용 공개키 |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173` | 프론트 주소 (쉼표로 여러 개) |

## 테스트

```bash
./gradlew test   # user-service 대신 가짜 HTTP 서버를 띄워 실제 중계·토큰 검증·CORS·404·503을 확인한다
```

## Docker 이미지

```bash
docker build --secret id=gpr_actor,env=GITHUB_ACTOR --secret id=gpr_token,env=GPR_TOKEN -t api-gateway .
```
