package com.grid07.socialbot.service;

import com.grid07.socialbot.entity.Bot;
import com.grid07.socialbot.entity.User;
import com.grid07.socialbot.repository.BotRepository;
import com.grid07.socialbot.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserBotService {

    private final UserRepository userRepository;
    private final BotRepository botRepository;

    @Transactional
    public User createUser(String username, boolean isPremium) {
        User user = User.builder()
                .username(username)
                .isPremium(isPremium)
                .build();
        return userRepository.save(user);
    }

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    public User getUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + id));
    }

    @Transactional
    public Bot createBot(String name, String personaDescription) {
        Bot bot = Bot.builder()
                .name(name)
                .personaDescription(personaDescription)
                .build();
        return botRepository.save(bot);
    }

    public List<Bot> getAllBots() {
        return botRepository.findAll();
    }

    public Bot getBot(Long id) {
        return botRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Bot not found: " + id));
    }
}
