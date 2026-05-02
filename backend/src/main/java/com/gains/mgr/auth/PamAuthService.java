package com.gains.mgr.auth;

import java.util.concurrent.TimeUnit;

import org.jvnet.libpam.PAM;
import org.jvnet.libpam.PAMException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Authentication service using Linux PAM (Pluggable Authentication Modules).
 *
 * <p>PAM is the standard Linux authentication framework.
 * It validates the same usernames and passwords used for SSH login —
 * no separate user database is needed.
 *
 * <p>Requirements:
 * The process user must belong to the 'shadow' group to read /etc/shadow:
 * sudo usermod -aG shadow <service_user>
 *
 * @Service - Spring stereotype for the service layer (business logic).
 * Functionally equivalent to @Component, but semantically clearer.
 */
@Service
public class PamAuthService {

    // Standard Java logging via SLF4J facade
    // Log messages appear in journalctl -u gains-manager
    private static final Logger log = LoggerFactory.getLogger(PamAuthService.class);

    /**
     * Authenticates a user against the Linux PAM stack.
     *
     * @param username Linux system username (same as SSH login)
     * @param password user's password
     * @return true if authentication succeeded, false otherwise
     */
    public boolean authenticate(String username, String password) {
        if (username == null || username.isBlank()) {
            return false;
        }
        if (password == null || password.isBlank()) {
            return false;
        }
        if (!username.matches("[a-zA-Z0-9_-]+")) {
            return false;
        }

        try {
            // Step 1: verify password via PAM
            PAM pam = new PAM("sshd");
            pam.authenticate(username, password);

            // Step 2: allow only users who have sudo rights on this machine
            ProcessBuilder pb = new ProcessBuilder("sudo", "-l", "-U", username);
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            proc.waitFor(3, TimeUnit.SECONDS);
            String sudoList = new String(proc.getInputStream().readAllBytes());

            if (sudoList.contains("not allowed to run sudo")) {
                log.warn("Access denied for {} — no sudo rights", username);
                return false;
            }

            log.info("PAM auth SUCCESS for user: {}", username);
            return true;

        } catch (PAMException e) {
            log.warn("PAM auth FAILED for user: {} — {}", username, e.getMessage());
            return false;
        } catch (Exception e) {
            log.error("PAM system error for user {}: {}", username, e.getMessage());
            return false;
        }

    }
}
