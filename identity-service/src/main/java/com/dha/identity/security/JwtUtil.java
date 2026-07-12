package com.dha.identity.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.Map;

/**
 * Issuance only — verification of Identity-minted tokens now happens once,
 * centrally, in the Gateway's ZeroTrustGlobalFilter (which fetches this
 * service's JWKS). Identity's own self-service endpoints trust the
 * X-User-Email header the Gateway forwards rather than re-parsing the token.
 */
@Component
@RequiredArgsConstructor
public class JwtUtil {

    private final RsaKeyProvider keyProvider;

    @Value("${app.jwt.expiry}")
    private long expiry;

    public String generateToken(String subjectEmail, Map<String, Object> claims) {
        return Jwts.builder()
                .setHeaderParam("kid", keyProvider.getKeyId())
                .setClaims(claims)
                .setSubject(subjectEmail)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expiry))
                .signWith(keyProvider.privateKey(), SignatureAlgorithm.RS256)
                .compact();
    }
}
