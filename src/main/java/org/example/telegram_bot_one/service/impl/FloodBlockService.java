package org.example.telegram_bot_one.service.impl;

import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class FloodBlockService {
    private volatile Instant blockedUntil = Instant.now();

    public void blockGlobally(int retryAfterSeconds) {
        blockedUntil = Instant.now().plusSeconds(retryAfterSeconds);
    }

    public boolean isBlocked() {
        return Instant.now().isBefore(blockedUntil);
    }
}
