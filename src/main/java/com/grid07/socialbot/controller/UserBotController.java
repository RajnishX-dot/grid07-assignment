package com.grid07.socialbot.controller;

import com.grid07.socialbot.dto.ApiResponse;
import com.grid07.socialbot.entity.Bot;
import com.grid07.socialbot.entity.User;
import com.grid07.socialbot.service.UserBotService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class UserBotController {

    private final UserBotService userBotService;

    @PostMapping("/api/users")
    public ResponseEntity<ApiResponse<User>> createUser(@RequestBody CreateUserRequest req) {
        User user = userBotService.createUser(req.getUsername(), req.isPremium());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("User created", user));
    }

    @GetMapping("/api/users")
    public ResponseEntity<ApiResponse<List<User>>> getAllUsers() {
        return ResponseEntity.ok(ApiResponse.ok(userBotService.getAllUsers()));
    }

    @GetMapping("/api/users/{id}")
    public ResponseEntity<ApiResponse<User>> getUser(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(userBotService.getUser(id)));
    }

    @PostMapping("/api/bots")
    public ResponseEntity<ApiResponse<Bot>> createBot(@RequestBody CreateBotRequest req) {
        Bot bot = userBotService.createBot(req.getName(), req.getPersonaDescription());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Bot created", bot));
    }

    @GetMapping("/api/bots")
    public ResponseEntity<ApiResponse<List<Bot>>> getAllBots() {
        return ResponseEntity.ok(ApiResponse.ok(userBotService.getAllBots()));
    }

    @GetMapping("/api/bots/{id}")
    public ResponseEntity<ApiResponse<Bot>> getBot(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(userBotService.getBot(id)));
    }

    @Data
    static class CreateUserRequest {
        private String username;
        private boolean premium;
    }

    @Data
    static class CreateBotRequest {
        private String name;
        private String personaDescription;
    }
}
