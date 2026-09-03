package com.payflow.auth.service;
import com.payflow.auth.repository.RefreshTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.time.Duration;
import java.time.Instant;
@Component
public class TokenCleanupJob {
    private static final Logger log = LoggerFactory.getLogger(TokenCleanupJob.class);
    private final RefreshTokenRepository refreshTokenRepository;
    public TokenCleanupJob(RefreshTokenRepository refreshTokenRepository) {
        this.refreshTokenRepository = refreshTokenRepository;
    }
    @Scheduled(cron = "${payflow.auth.token-cleanup-cron:0 30 3 * * *}")
    @Transactional
    public void purgeExpiredTokens() {
        int removed = refreshTokenRepository.deleteExpired(Instant.now().minus(Duration.ofDays(30)));
        if (removed > 0) {
            log.info("Purged {} expired refresh tokens", removed);
        }
    }
}
