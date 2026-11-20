package logired.backend.usuario.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class LoginRateLimiter {

    private final ConcurrentHashMap<String, FailureWindow> failuresByAddress =
            new ConcurrentHashMap<>();
    private final int maximumFailures;
    private final Duration window;
    private final AtomicInteger operations = new AtomicInteger();

    public LoginRateLimiter(
            @Value("${logired.auth.rate-limit.max-attempts:5}") int maximumFailures,
            @Value("${logired.auth.rate-limit.window-seconds:60}") long windowSeconds) {
        this.maximumFailures = Math.max(1, maximumFailures);
        this.window = Duration.ofSeconds(Math.max(1, windowSeconds));
    }

    public long retryAfterSeconds(String address) {
        cleanupExpiredWindows();
        AtomicLong retryAfter = new AtomicLong();
        failuresByAddress.computeIfPresent(address, (key, failureWindow) -> {
            synchronized (failureWindow) {
                purgeExpiredFailures(failureWindow, Instant.now());
                if (failureWindow.failures.isEmpty()) {
                    return null;
                }
                if (failureWindow.failures.size() >= maximumFailures) {
                    retryAfter.set(secondsUntilReset(failureWindow.failures.peekFirst()));
                }
                return failureWindow;
            }
        });
        return retryAfter.get();
    }

    public long recordFailure(String address) {
        cleanupExpiredWindows();
        AtomicLong retryAfter = new AtomicLong();
        failuresByAddress.compute(address, (key, current) -> {
            FailureWindow failureWindow = current == null ? new FailureWindow() : current;
            synchronized (failureWindow) {
                Instant now = Instant.now();
                purgeExpiredFailures(failureWindow, now);
                failureWindow.failures.addLast(now);
                if (failureWindow.failures.size() >= maximumFailures) {
                    retryAfter.set(secondsUntilReset(failureWindow.failures.peekFirst()));
                }
                return failureWindow;
            }
        });
        return retryAfter.get();
    }

    public void clear(String address) {
        failuresByAddress.remove(address);
    }

    private void cleanupExpiredWindows() {
        if (operations.incrementAndGet() % 256 != 0) {
            return;
        }
        Instant now = Instant.now();
        for (String address : failuresByAddress.keySet()) {
            failuresByAddress.computeIfPresent(address, (key, failureWindow) -> {
                synchronized (failureWindow) {
                    purgeExpiredFailures(failureWindow, now);
                    return failureWindow.failures.isEmpty() ? null : failureWindow;
                }
            });
        }
    }

    private void purgeExpiredFailures(FailureWindow failureWindow, Instant now) {
        Instant cutoff = now.minus(window);
        while (!failureWindow.failures.isEmpty()
                && !failureWindow.failures.peekFirst().isAfter(cutoff)) {
            failureWindow.failures.removeFirst();
        }
    }

    private long secondsUntilReset(Instant firstFailure) {
        long remainingMillis = Duration.between(Instant.now(), firstFailure.plus(window)).toMillis();
        return Math.max(1, (remainingMillis + 999) / 1000);
    }

    private static final class FailureWindow {
        private final ArrayDeque<Instant> failures = new ArrayDeque<>();
    }
}
