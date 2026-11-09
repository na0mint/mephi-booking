package ru.mephi.common;

import java.time.Instant;
import java.util.List;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
public class JwtTokens {
    private final JwtEncoder encoder;
    public JwtTokens(JwtEncoder encoder) {
        this.encoder = encoder;
    }
    public String issue(String subject, String role) {
        return issue(subject, role, 3600);
    }
    public String internal() {
        return issue("booking-service", "INTERNAL", 300);
    }
    private String issue(String subject, String role, long seconds) {
        Instant now = Instant.now();
        var claims = JwtClaimsSet.builder()
                             .issuer("mephi-booking")
                             .subject(subject)
                             .issuedAt(now)
                             .expiresAt(now.plusSeconds(seconds))
                             .claim("roles", List.of(role))
                             .build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }
}
