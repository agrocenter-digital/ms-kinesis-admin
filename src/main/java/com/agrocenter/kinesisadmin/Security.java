package com.agrocenter.kinesisadmin;

import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
class Security {
    @Bean SecurityFilterChain filter(HttpSecurity http, JwtAuthenticationConverter converter) throws Exception {
        return http.csrf(c -> c.disable()).sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a -> a.requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
                .requestMatchers("/api/kinesis-admin/**").hasRole("ADMIN").anyRequest().denyAll())
            .oauth2ResourceServer(o -> o.jwt(j -> j.jwtAuthenticationConverter(converter))).build();
    }
    @Bean JwtAuthenticationConverter converter() {
        var converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            var groups = jwt.getClaimAsStringList("cognito:groups");
            return groups != null && groups.stream().anyMatch(g -> "ADMIN".equals(g.trim().toUpperCase(Locale.ROOT)))
                ? java.util.List.of(new SimpleGrantedAuthority("ROLE_ADMIN")) : java.util.List.of();
        });
        return converter;
    }
    @Bean JwtDecoder decoder(@Value("${COGNITO_ISSUER_URI:}") String issuer, @Value("${COGNITO_JWK_SET_URI:}") String jwks,
                             @Value("${COGNITO_AUDIENCE:}") String audience) {
        if (issuer.isBlank() || jwks.isBlank() || audience.isBlank()) throw new IllegalStateException("Configuración Cognito requerida");
        var decoder = NimbusJwtDecoder.withJwkSetUri(jwks).jwsAlgorithm(SignatureAlgorithm.RS256).build();
        OAuth2TokenValidator<Jwt> access = jwt -> "access".equals(jwt.getClaimAsString("token_use"))
            && (audience.equals(jwt.getClaimAsString("client_id")) || jwt.getAudience().contains(audience))
            ? OAuth2TokenValidatorResult.success() : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(issuer), access));
        return decoder;
    }
}
