package com.intact_id.service.kyc;

import com.intact_id.constant.enums.ApiKeyEnvironment;
import com.intact_id.constant.enums.kyc.WidgetChannel;
import com.intact_id.security.authentication.WidgetSessionContext;
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

    public String createSessionToken(String companyId, ApiKeyEnvironment environment, String tier,
                                     String externalReference) {
        String normalizedReference = externalReference != null && !externalReference.isBlank()
                ? externalReference.trim()
                : null;
        WidgetSessionContext context = new WidgetSessionContext(
                companyId, environment, tier, normalizedReference, null, null, WidgetChannel.EMBEDDED);
        return issueToken(context, new Date(System.currentTimeMillis() + SESSION_TTL_MS));
    }

    public String issueToken(WidgetSessionContext context, Date expiresAt) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("tokenType", TOKEN_TYPE);
        claims.put("companyId", context.companyId());
        claims.put("environment", context.environment().name());
        claims.put("tier", context.tier());
        claims.put("channel", context.channel().name());
        putIfPresent(claims, "externalReference", context.externalReference());
        putIfPresent(claims, "draftId", context.draftId());
        putIfPresent(claims, "handoffId", context.handoffId());
        claims.put("jti", UUID.randomUUID().toString());

        return Jwts.builder()
                .claims(claims)
                .subject("widget-session")
                .issuer(issuer)
                .issuedAt(new Date())
                .expiration(expiresAt)
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

    public WidgetSessionContext toContext(Claims claims) {
        String channel = claims.get("channel", String.class);
        return new WidgetSessionContext(
                claims.get("companyId", String.class),
                ApiKeyEnvironment.valueOf(claims.get("environment", String.class)),
                claims.get("tier", String.class),
                claims.get("externalReference", String.class),
                claims.get("draftId", String.class),
                claims.get("handoffId", String.class),
                channel != null ? WidgetChannel.valueOf(channel) : WidgetChannel.EMBEDDED);
    }

    private static void putIfPresent(Map<String, Object> claims, String key, String value) {
        if (value != null) {
            claims.put(key, value);
        }
    }

    private SecretKey signingKey() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
    }
}
