package com.gains.mgr.config;

import com.gains.mgr.auth.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

/**
 * JWT authentication filter — intercepts every HTTP request and validates the token.
 *
 * OncePerRequestFilter is a Spring base class that guarantees the filter
 * runs exactly once per request (not twice in forward/include scenarios).
 *
 * Request flow:
 *   Browser sends: GET /api/services  with  Authorization: Bearer eyJ...
 *       |
 *       v
 *   JwtFilter.doFilterInternal()
 *       - reads the Authorization header
 *       - validates the JWT token
 *       - if valid: sets the authenticated user in SecurityContext
 *       |
 *       v
 *   Spring Security checks SecurityContext
 *       - user is authenticated → allow the request through
 *       - no user set → return 401 Unauthorized
 *       |
 *       v
 *   Controller method executes
 */
@Component
public class JwtFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

    public JwtFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        // Read the Authorization header from the HTTP request
        String header = request.getHeader("Authorization");

        // Check that the header exists and follows the "Bearer <token>" format
        if (header != null && header.startsWith("Bearer ")) {

            // Strip the "Bearer " prefix (7 characters) to get the raw token
            String token = header.substring(7);

            if (jwtUtil.validateToken(token)) {
                String username = jwtUtil.getUsernameFromToken(token);

                // Tell Spring Security this user is authenticated.
                // Parameters:
                //   principal   = username (who the user is)
                //   credentials = null (password not needed after token validation)
                //   authorities = empty list (no roles used yet)
                UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(
                                username,
                                null,
                                Collections.emptyList()
                        );

                // Store the authentication in the thread-local SecurityContext.
                // Spring Security reads this when deciding whether to allow the request.
                SecurityContextHolder.getContext().setAuthentication(auth);

                // Store username as a request attribute so controllers can read it
                // via @RequestAttribute("username") without re-parsing the token.
                request.setAttribute("username", username);
            }
        }

        // Always continue the filter chain — letting Spring Security
        // make the final allow/deny decision based on SecurityContext.
        filterChain.doFilter(request, response);
    }
}
