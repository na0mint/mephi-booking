package ru.mephi.common;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
@Configuration
public class JwtConfiguration {
    @Bean
    SecretKey jwtKey(@Value("${security.jwt.secret}") String secret) {
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32)
            throw new IllegalArgumentException("JWT secret must be at least 32 bytes");
        return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }
    @Bean
    JwtEncoder jwtEncoder(SecretKey key) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(key));
    }
    @Bean
    JwtDecoder jwtDecoder(SecretKey key) {
        NimbusJwtDecoder decoder =
                NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer("mephi-booking"));
        return decoder;
    }
    @Bean
    JwtTokens jwtTokens(JwtEncoder encoder) {
        return new JwtTokens(encoder);
    }
}
