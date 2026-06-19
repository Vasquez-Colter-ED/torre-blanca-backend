package pe.torreblanca.backend.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private Long expiration;

    private SecretKey getKey() {
        String base64 = Base64.getEncoder().encodeToString(secret.getBytes());
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(base64));
    }

    // Genera el token e incluye un identificador único de sesión (jti).
    // Ese jti se guarda también en la BD; si el usuario vuelve a loguearse
    // se genera uno nuevo, invalidando el anterior automáticamente.
    public String generateToken(String email) {
        String jti = UUID.randomUUID().toString();
        return Jwts.builder()
                .subject(email)
                .id(jti)                         // campo estándar JWT para el identificador de sesión
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getKey())
                .compact();
    }

    public String getEmailFromToken(String token) {
        return getClaims(token).getSubject();
    }

    // Extrae el jti del token para compararlo con el que está en la BD
    public String getJtiFromToken(String token) {
        return getClaims(token).getId();
    }

    public boolean validateToken(String token) {
        try {
            getClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    private Claims getClaims(String token) {
        return Jwts.parser()
                .verifyWith(getKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
