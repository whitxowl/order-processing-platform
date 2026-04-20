package com.whitxowl.authservice.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "security.jwt")
@Getter
@Setter
public class JwtProperties {

    private String secret;

    private Duration accessTtl = Duration.ofMinutes(15);

    private Duration refreshTtl = Duration.ofDays(7);

    private String issuer = "auth-service";
}