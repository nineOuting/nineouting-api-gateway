package com.nineouting.backend.gateway.config;

import com.nineouting.backend.common.web.error.CommonErrorCode;
import com.nineouting.backend.common.web.error.ErrorCode;
import java.net.ConnectException;
import java.net.UnknownHostException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;
import reactor.core.publisher.Mono;

/**
 * 라우트가 없는 경로(404), 서비스가 꺼져 있는 경우(503) 등 gateway 자체 에러를 ApiResponse 형식으로 바꾼다.
 * Spring Boot 기본 에러 처리(order -1)보다 먼저 실행된다.
 */
@Slf4j
@Order(-2)
@Component
public class GatewayExceptionHandler implements WebExceptionHandler {

    private final JsonErrorWriter errorWriter;

    public GatewayExceptionHandler(JsonErrorWriter errorWriter) {
        this.errorWriter = errorWriter;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        ErrorCode errorCode = toErrorCode(ex);
        if (errorCode == CommonErrorCode.INTERNAL_ERROR) {
            log.error("gateway 처리 중 예외: {}", exchange.getRequest().getPath(), ex);
        } else if (errorCode == GatewayErrorCode.SERVICE_UNAVAILABLE) {
            log.warn("서비스 연결 실패: {} - {}", exchange.getRequest().getPath(), ex.toString());
        }
        return errorWriter.write(exchange, errorCode);
    }

    private static ErrorCode toErrorCode(Throwable ex) {
        if (ex instanceof ResponseStatusException e) {
            HttpStatus status = HttpStatus.resolve(e.getStatusCode().value());
            if (status == HttpStatus.NOT_FOUND) {
                return CommonErrorCode.NOT_FOUND;
            }
            if (status == HttpStatus.METHOD_NOT_ALLOWED) {
                return CommonErrorCode.METHOD_NOT_ALLOWED;
            }
            if (status == HttpStatus.SERVICE_UNAVAILABLE || status == HttpStatus.GATEWAY_TIMEOUT) {
                return GatewayErrorCode.SERVICE_UNAVAILABLE;
            }
            if (status != null && status.is4xxClientError()) {
                return CommonErrorCode.INVALID_INPUT;
            }
        }
        for (Throwable t = ex; t != null; t = t.getCause()) {
            // 서비스가 꺼져 있거나(연결 거부), 아직 배포되지 않아 주소를 찾을 수 없는 경우
            if (t instanceof ConnectException || t instanceof UnknownHostException) {
                return GatewayErrorCode.SERVICE_UNAVAILABLE;
            }
        }
        return CommonErrorCode.INTERNAL_ERROR;
    }
}
