package com.whitxowl.gateway.security;

import com.whitxowl.gateway.service.JwtService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtGatewayFilterTest {

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private JwtGatewayFilter filter;

    @Test
    void noAuthHeader_shouldReturn401() {
        GatewayFilterChain chain = mock(GatewayFilterChain.class);

        MockServerHttpRequest request = MockServerHttpRequest
                .post("/api/v1/products")
                .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(chain, never()).filter(any());
    }

    @Test
    void malformedAuthHeader_shouldReturn401() {
        GatewayFilterChain chain = mock(GatewayFilterChain.class);

        MockServerHttpRequest request = MockServerHttpRequest
                .post("/api/v1/products")
                .header(HttpHeaders.AUTHORIZATION, "Basic dXNlcjpwYXNz")
                .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(chain, never()).filter(any());
    }

    @Test
    void invalidToken_shouldReturn401() {
        when(jwtService.parse(anyString())).thenThrow(new JwtException("bad token"));

        GatewayFilterChain chain = mock(GatewayFilterChain.class);

        MockServerHttpRequest request = MockServerHttpRequest
                .post("/api/v1/products")
                .header(HttpHeaders.AUTHORIZATION, "Bearer invalid.token.here")
                .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(chain, never()).filter(any());
    }

    @Test
    void validToken_shouldMutateExchangeWithUserHeaders() {
        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn("user-uuid-123");
        when(claims.get("email", String.class)).thenReturn("user@test.com");
        when(jwtService.parse("valid-token")).thenReturn(claims);
        when(jwtService.getRoles(claims)).thenReturn(List.of("ROLE_USER"));

        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        MockServerHttpRequest request = MockServerHttpRequest
                .post("/api/v1/products")
                .header(HttpHeaders.AUTHORIZATION, "Bearer valid-token")
                .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        filter.filter(exchange, chain).block();

        ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(chain).filter(captor.capture());

        HttpHeaders headers = captor.getValue().getRequest().getHeaders();
        assertThat(headers.getFirst("X-User-Id")).isEqualTo("user-uuid-123");
        assertThat(headers.getFirst("X-User-Email")).isEqualTo("user@test.com");
        assertThat(headers.getFirst("X-User-Roles")).isEqualTo("ROLE_USER");
    }

    @Test
    void validToken_multipleRoles_shouldJoinWithComma() {
        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn("admin-uuid-456");
        when(claims.get("email", String.class)).thenReturn("admin@test.com");
        when(jwtService.parse("admin-token")).thenReturn(claims);
        when(jwtService.getRoles(claims)).thenReturn(List.of("ROLE_USER", "ROLE_ADMIN"));

        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        MockServerHttpRequest request = MockServerHttpRequest
                .post("/api/v1/products")
                .header(HttpHeaders.AUTHORIZATION, "Bearer admin-token")
                .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        filter.filter(exchange, chain).block();

        ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(chain).filter(captor.capture());

        assertThat(captor.getValue().getRequest().getHeaders().getFirst("X-User-Roles"))
                .isEqualTo("ROLE_USER,ROLE_ADMIN");
    }
}