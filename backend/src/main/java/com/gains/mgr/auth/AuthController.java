package com.gains.mgr.auth;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST controller for authentication endpoints.
 *
 * <p>>@RestController = @Controller + @ResponseBody
 * Methods return data (serialized to JSON) instead of view names.
 *
 * @RequestMapping("/api/auth") - all endpoints in this class are prefixed with /api/auth
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    // Constructor injection — the recommended way to inject dependencies in Spring.
    // Spring scans the context, finds matching beans, and passes them here.
    private final PamAuthService pamAuthService;
    private final JwtUtil jwtUtil;

    public AuthController(PamAuthService pamAuthService, JwtUtil jwtUtil) {
        this.pamAuthService = pamAuthService;
        this.jwtUtil = jwtUtil;
    }

    // DTO (Data Transfer Object) — plain classes used to carry data between layers.
    //
    // 'record' is a Java 17 feature that creates an immutable class automatically:
    // generates constructor, getters (username(), password()), equals, hashCode, toString.
    // Much shorter than a traditional POJO class with getters/setters.
    public record LoginRequest(String username, String password) {
    }

    public record LoginResponse(String token, String username) {
    }

    /**
     * POST /api/auth/login
     *
     * <p>Accepts username and password, verifies via PAM,
     * returns a JWT token on success.
     *
     * @RequestBody - Spring automatically deserializes the JSON request body into LoginRequest
     * * ResponseEntity<?> - lets us control the HTTP status code of the response
     * * 200 OK on success, 401 Unauthorized on failure
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        boolean authenticated = pamAuthService.authenticate(
                request.username(),
                request.password()
        );

        if (authenticated) {
            String token = jwtUtil.generateToken(request.username());
            // 200 OK + token in body
            return ResponseEntity.ok(new LoginResponse(token, request.username()));
        } else {
            // 401 Unauthorized + error message
            return ResponseEntity
                    .status(401)
                    .body(Map.of("error", "Invalid username or password"));
        }
    }

    /**
     * GET /api/auth/me
     *
     * <p>Returns the currently authenticated user's name.
     * The "username" attribute is set by JwtFilter after token validation.
     *
     * @RequestAttribute - reads an attribute set on HttpServletRequest
     * (set by JwtFilter, not from the URL or body)
     */
    @GetMapping("/me")
    public ResponseEntity<Map<String, String>> me(
            @RequestAttribute("username") String username) {
        return ResponseEntity.ok(Map.of("username", username));
    }
}
