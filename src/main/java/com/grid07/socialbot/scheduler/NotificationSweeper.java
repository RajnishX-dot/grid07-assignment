package com.grid07.socialbot.scheduler;

import com.grid07.socialbot.service.RedisViralityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

// sweeps pending notifications every 5 minutes and batches them per user
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationSweeper {

    private final RedisViralityService redisViralityService;

    @Scheduled(cron = "${app.notification.sweeper-cron:0 */5 * * * *}")
    public void sweepPendingNotifications() {
        log.info("[SWEEPER] Starting notification sweep...");

        List<Long> usersWithPending = redisViralityService.getUsersWithPendingNotifications();

        if (usersWithPending.isEmpty()) {
            log.info("[SWEEPER] No pending notifications found.");
            return;
        }

        int totalProcessed = 0;

        for (Long userId : usersWithPending) {
            List<String> notifications = redisViralityService.drainPendingNotifications(userId);

            if (!notifications.isEmpty()) {
                String summary = buildSummaryMessage(userId, notifications);
                log.info("[PUSH NOTIFICATION - BATCHED] {}", summary);
                totalProcessed += notifications.size();
            }
        }

        log.info("[SWEEPER] Done. Processed {} notifications for {} users.",
                totalProcessed, usersWithPending.size());
    }

    private String buildSummaryMessage(Long userId, List<String> notifications) {
        if (notifications.size() == 1) {
            return "User " + userId + ": " + notifications.get(0);
        }
        return "User " + userId + " has " + notifications.size() + " pending notifications: "
                + String.join(" | ", notifications);
    }
}
