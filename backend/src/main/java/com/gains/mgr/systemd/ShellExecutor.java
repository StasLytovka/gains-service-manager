package com.gains.mgr.systemd;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class ShellExecutor {

    private static final Logger LOGGER = LoggerFactory.getLogger(ShellExecutor.class);

    private static final boolean IS_WINDOWS = System.getProperty("os.name").toLowerCase().contains("win");

    public String exec(String command, long timeout) {
        LOGGER.info("exec: {}", command);
        List<String> shell = IS_WINDOWS
                ? List.of("cmd", "/c", command)
                : List.of("/bin/bash", "-c", command);
        return run(shell, command, timeout);
    }

    public String exec(List<String> cmd, long timeout) {
        LOGGER.info("exec: {}", String.join(" ", cmd));
        return run(cmd, cmd.get(0), timeout);
    }

    private String run(List<String> cmd, String label, long timeout) {
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            boolean finished = process.waitFor(timeout, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                LOGGER.error("TIMEOUT [{}s]: {}", timeout, label);
                throw new RuntimeException("Command timed out: " + label);
            }

            String output = new String(process.getInputStream().readAllBytes());
            int exitCode = process.exitValue();

            if (exitCode != 0) {
                LOGGER.warn("EXIT [{}]: {} | output: {}", exitCode, label, output.trim());
            } else {
                LOGGER.debug("OK: {} | output: {}", label, output.trim());
            }

            return output;

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.error("FAILED: {} | error: {}", label, e.getMessage());
            throw new RuntimeException("Failed to execute: " + label + " — " + e.getMessage());
        }
    }
}
