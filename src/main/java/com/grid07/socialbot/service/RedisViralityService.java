package com.grid07.socialbot.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class RedisViralityService {

    private final RedisTemplate<String, String> redisTemplate;

    @Value("${app.bot.max-bot-replies:100}")
    private int maxBotReplies;

    @Value("${app.bot.max-comment-depth:20}")
    private int maxCommentDepth;

    @Value("${app.bot.cooldown-minutes:10}")
    private long botCooldownMinutes;

    @Value("${app.notification.cooldown-minutes:15}")
    private long notifCooldownMinutes;

    // key helpers
    public static String viralityKey(Long postId) {
        return "post:" + postId + ":virality_score";
    }

    public static String botCountKey(Long postId) {
        return "post:" + postId + ":bot_count";
    }

    public static String botCooldownKey(Long botId, Long humanId) {
        return "cooldown:bot_" + botId + ":human_" + humanId;
    }

    public static String notifCooldownKey(Long userId) {
        return "notif:cooldown:" + userId;
    }

    public static String pendingNotifKey(Long userId) {
        return "user:" + userId + ":pending_notifs";
    }

    // virality score stuff

    public long incrementViralityBotReply(Long postId) {
        Long score = redisTemplate.opsForValue().increment(viralityKey(postId), 1L);
        log.info("Virality +1 (bot reply) for post:{} → {}", postId, score);
        return score == null ? 0L : score;
    }

    public long incrementViralityHumanLike(Long postId) {
        Long score = redisTemplate.opsForValue().increment(viralityKey(postId), 20L);
        log.info("Virality +20 (human like) for post:{} → {}", postId, score);
        return score == null ? 0L : score;
    }

    public long incrementViralityHumanComment(Long postId) {
        Long score = redisTemplate.opsForValue().increment(viralityKey(postId), 50L);
        log.info("Virality +50 (human comment) for post:{} → {}", postId, score);
        return score == null ? 0L : score;
    }

    public long getViralityScore(Long postId) {
        String val = redisTemplate.opsForValue().get(viralityKey(postId));
        return val == null ? 0L : Long.parseLong(val);
    }

    // Lua script for atomic bot cap — INCR, check if over limit, DECR + return -1 if so
    // need this to be atomic so 200 concurrent requests stop at exactly 100
    private static final String BOT_COUNT_LUA = """
            local key = KEYS[1]
            local max = tonumber(ARGV[1])
            local current = redis.call('INCR', key)
            if current > max then
                redis.call('DECR', key)
                return -1
            end
            return current
            """;

    // returns -1 if cap is hit, otherwise returns the new count
    public long tryClaimBotReplySlot(Long postId) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText(BOT_COUNT_LUA);
        script.setResultType(Long.class);

        Long result = redisTemplate.execute(
                script,
                Collections.singletonList(botCountKey(postId)),
                String.valueOf(maxBotReplies)
        );
        return result == null ? -1L : result;
    }

    public long getBotReplyCount(Long postId) {
        String val = redisTemplate.opsForValue().get(botCountKey(postId));
        return val == null ? 0L : Long.parseLong(val);
    }

    // compensate if we claimed a slot but then the cooldown check fails
    public void releaseBotReplySlot(Long postId) {
        redisTemplate.opsForValue().decrement(botCountKey(postId));
    }

    public boolean isDepthAllowed(int depthLevel) {
        return depthLevel <= maxCommentDepth;
    }

    // SETNX with TTL - atomic, no need for separate expiry logic
    public boolean tryAcquireBotCooldown(Long botId, Long humanId) {
        String key = botCooldownKey(botId, humanId);
        Boolean set = redisTemplate.opsForValue().setIfAbsent(
                key,
                "1",
                botCooldownMinutes,
                TimeUnit.MINUTES
        );
        return Boolean.TRUE.equals(set);
    }

    public boolean isBotOnCooldown(Long botId, Long humanId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(botCooldownKey(botId, humanId)));
    }

    // notification cooldown - same SETNX pattern
    public boolean tryAcquireNotifCooldown(Long userId) {
        String key = notifCooldownKey(userId);
        Boolean set = redisTemplate.opsForValue().setIfAbsent(
                key,
                "1",
                notifCooldownMinutes,
                TimeUnit.MINUTES
        );
        return Boolean.TRUE.equals(set);
    }

    public boolean isUserOnNotifCooldown(Long userId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(notifCooldownKey(userId)));
    }

    public void pushPendingNotification(Long userId, String message) {
        redisTemplate.opsForList().rightPush(pendingNotifKey(userId), message);
        log.info("Queued pending notification for user:{} → {}", userId, message);
    }

    // drain all pending notifications for a user
    public List<String> drainPendingNotifications(Long userId) {
        String key = pendingNotifKey(userId);
        Long size = redisTemplate.opsForList().size(key);
        if (size == null || size == 0) {
            return Collections.emptyList();
        }
        List<String> items = redisTemplate.opsForList().range(key, 0, size - 1);
        redisTemplate.delete(key);
        return items == null ? Collections.emptyList() : items;
    }

    // scan for users with pending notifications in redis
    // TODO: maybe add pagination later if this gets slow with lots of users
    public List<Long> getUsersWithPendingNotifications() {
        List<Long> userIds = new ArrayList<>();
        try {
            redisTemplate.execute((RedisConnection conn) -> {
                Cursor<byte[]> cursor = conn.scan(
                        ScanOptions.scanOptions()
                                .match("user:*:pending_notifs")
                                .count(100)
                                .build()
                );
                while (cursor.hasNext()) {
                    String key = new String(cursor.next());
                    // "user:{id}:pending_notifs" -> split on : to get id
                    String[] parts = key.split(":");
                    if (parts.length == 3) {
                        try {
                            userIds.add(Long.parseLong(parts[1]));
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }
                return null;
            });
        } catch (Exception e) {
            log.error("Error scanning pending notification keys", e);
        }
        return userIds;
    }
}
