package com.gains.mgr.auth;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

/**
 * Utility class for JWT (JSON Web Token) operations.
 *
 * <p>A JWT is a compact string with three parts: header.payload.signature
 * It stores user information on the client side without server-side sessions.
 * Example: "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiJ9.xyz"
 *
 * @Component - Spring creates a single instance (Singleton) of this class
 * and injects it wherever JwtUtil is required.
 */
@Component
public class JwtUtil {

    // @Value reads the property value from application.yml
    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Value("${app.jwt.expiration}")
    private long jwtExpiration;

    /**
     * Builds a signing key from the secret string.
     * The key is used to sign tokens — without the correct key
     * a token cannot be forged or tampered with.
     */
    private SecretKey getKey() {
        // Pad the secret to at least 32 characters, then take the first 32
        String padded = jwtSecret.length() >= 32
                ? jwtSecret.substring(0, 32)
                : jwtSecret + "!".repeat(32 - jwtSecret.length());
        return Keys.hmacShaKeyFor(padded.getBytes());
    }

    /**
     * Generates a JWT token for the given username.
     * The token contains: subject (username), issued-at timestamp, expiration timestamp.
     *
     * @param username the authenticated user's login name
     * @return JWT string starting with "eyJ..."
     */
    public String generateToken(String username) {
        return Jwts.builder()
                .subject(username)                                               // who the token belongs to
                .issuedAt(new Date())                                            // creation time
                .expiration(new Date(System.currentTimeMillis() + jwtExpiration)) // expiry time
                .signWith(getKey())                                              // sign with secret key
                .compact();                                                      // build the final string
    }

    /**
     * Validates a JWT token — checks signature and expiration.
     *
     * @param token JWT string from the Authorization header
     * @return true if the token is valid and not expired
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(getKey())
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (Exception e) {
            // Token is invalid, expired, or tampered with
            return false;
        }
    }

    /**
     * Extracts the username (subject) from a JWT token.
     *
     * @param token valid JWT string
     * @return username stored inside the token
     */
    public String getUsernameFromToken(String token) {
        return Jwts.parser()
                .verifyWith(getKey())
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
    }
}
