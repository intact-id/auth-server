package com.intact_id.service.kyc;

import com.intact_id.constant.enums.ApiKeyEnvironment;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class KycSessionService {

    private static final String TOKEN_TYPE = "widget-session";
    private static final long SESSION_TTL_MS = 15 * 60 * 1000L; // 15 min

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.issuer}")
    private String issuer;

    public String createSessionToken(String companyId, ApiKeyEnvironment environment, String tier) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("tokenType", TOKEN_TYPE);
        claims.put("companyId", companyId);
        claims.put("environment", environment.name());
        claims.put("tier", tier);
        claims.put("jti", UUID.randomUUID().toString());

        Date now = new Date();
        return Jwts.builder()
                .claims(claims)
                .subject("widget-session")
                .issuer(issuer)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + SESSION_TTL_MS))
                .signWith(signingKey(), Jwts.SIG.HS256)
                .compact();
    }

    public Claims validateAndExtract(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            if (!TOKEN_TYPE.equals(claims.get("tokenType", String.class))) {
                throw new JwtException("Not a widget session token");
            }
            return claims;
        } catch (JwtException e) {
            log.warn("Invalid widget session token: {}", e.getMessage());
            throw e;
        }
    }

    private SecretKey signingKey() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
    }
}
