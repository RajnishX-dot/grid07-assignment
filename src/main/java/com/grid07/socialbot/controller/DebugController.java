package com.grid07.socialbot.controller;

import com.grid07.socialbot.dto.ApiResponse;
import com.grid07.socialbot.service.RedisViralityService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/debug")
@RequiredArgsConstructor
public class DebugController {

    private final RedisViralityService redisViralityService;

    // handy for checking virality + bot count live from redis
    @GetMapping("/posts/{postId}/stats")
    public ResponseEntity<ApiResponse<Map<String, Long>>> getPostStats(@PathVariable Long postId) {
        Map<String, Long> stats = Map.of(
                "viralityScore", redisViralityService.getViralityScore(postId),
                "botReplyCount", redisViralityService.getBotReplyCount(postId)
        );
        return ResponseEntity.ok(ApiResponse.ok(stats));
    }
}
