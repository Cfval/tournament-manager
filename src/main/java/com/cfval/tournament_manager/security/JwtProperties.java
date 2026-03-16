package com.cfval.tournament_manager.security;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.security.jwt")
@Getter
@Setter
public class JwtProperties {

    private String secret;
    private long expiration;
}
