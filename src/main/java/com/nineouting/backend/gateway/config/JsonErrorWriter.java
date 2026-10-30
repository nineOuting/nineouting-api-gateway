package com.nineouting.backend.gateway.config;

import com.nineouting.backend.common.web.error.ErrorCode;
import com.nineouting.backend.common.web.response.ApiResponse;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

/**
 * gateway가 직접 만드는 에러도 서비스와 같은 ApiResponse 형식으로 쓴다.
 */
@Component
public class JsonErrorWriter {

    private final JsonMapper jsonMapper;

    public JsonErrorWriter(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    public Mono<Void> write(ServerWebExchange exchange, ErrorCode errorCode) {
        ServerHttpResponse response = exchange.getResponse();
        if (response.isCommitted()) {
            return Mono.empty();
        }
        response.setStatusCode(errorCode.getStatus());
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        DataBuffer body = response.bufferFactory().wrap(jsonMapper.writeValueAsBytes(ApiResponse.fail(errorCode)));
        return response.writeWith(Mono.just(body));
    }
}
