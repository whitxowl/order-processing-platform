package com.whitxowl.gateway.config;

import com.whitxowl.gateway.security.JwtGatewayFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;

@Configuration
@RequiredArgsConstructor
public class GatewayConfig {

    private final JwtGatewayFilter jwtGatewayFilter;

    @Value("${services.auth-service-url}")
    private String authServiceUrl;

    @Value("${services.user-service-url}")
    private String userServiceUrl;

    @Value("${services.product-service-url}")
    private String productServiceUrl;

    @Value("${services.inventory-service-url}")
    private String inventoryServiceUrl;

    @Value("${services.order-service-url}")
    private String orderServiceUrl;

    @Value("${services.notification-service-url}")
    private String notificationServiceUrl;

    @Bean
    public RouteLocator routeLocator(RouteLocatorBuilder builder) {
        return builder.routes()

                .route("auth-public", r -> r
                        .path("/api/v1/auth/**")
                        .uri(authServiceUrl))

                .route("products-public", r -> r
                        .path("/api/v1/products/**")
                        .and().method(HttpMethod.GET)
                        .uri(productServiceUrl))

                .route("products-secured", r -> r
                        .path("/api/v1/products/**")
                        .filters(f -> f.filter(jwtGatewayFilter))
                        .uri(productServiceUrl))

                .route("users-secured", r -> r
                        .path("/api/v1/users/**")
                        .filters(f -> f.filter(jwtGatewayFilter))
                        .uri(userServiceUrl))

                .route("inventory-secured", r -> r
                        .path("/api/v1/inventory/**")
                        .filters(f -> f.filter(jwtGatewayFilter))
                        .uri(inventoryServiceUrl))

                .route("orders-secured", r -> r
                        .path("/api/v1/orders/**")
                        .filters(f -> f.filter(jwtGatewayFilter))
                        .uri(orderServiceUrl))

                .route("notifications-secured", r -> r
                        .path("/api/v1/notifications/**")
                        .filters(f -> f.filter(jwtGatewayFilter))
                        .uri(notificationServiceUrl))

                .build();
    }
}