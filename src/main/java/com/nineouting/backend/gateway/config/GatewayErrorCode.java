package com.nineouting.backend.gateway.config;

import com.nineouting.backend.common.web.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum GatewayErrorCode implements ErrorCode {

    /** security-common의 TOKEN_EXPIRED와 같은 코드. 프론트는 refresh 토큰으로 다시 발급받는다. */
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "로그인이 만료되었습니다. 다시 로그인해 주세요"),
    SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "서비스에 일시적으로 연결할 수 없습니다. 잠시 후 다시 시도해 주세요");

    private final HttpStatus status;
    private final String message;

    @Override
    public String getCode() {
        return name();
    }
}
