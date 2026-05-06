package com.grid07.socialbot.service;

import com.grid07.socialbot.dto.CreatePostRequest;
import com.grid07.socialbot.dto.PostResponse;
import com.grid07.socialbot.entity.AuthorType;
import com.grid07.socialbot.entity.Post;
import com.grid07.socialbot.repository.PostRepository;
import com.grid07.socialbot.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PostService {

    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final RedisViralityService redisViralityService;

    @Transactional
    public PostResponse createPost(CreatePostRequest req) {
        if (req.getAuthorType() == AuthorType.USER) {
            userRepository.findById(req.getAuthorId())
                    .orElseThrow(() -> new IllegalArgumentException("User not found: " + req.getAuthorId()));
        }

        Post post = Post.builder()
                .authorId(req.getAuthorId())
                .authorType(req.getAuthorType())
                .content(req.getContent())
                .likeCount(0)
                .build();

        post = postRepository.save(post);
        log.info("Created post id={} by {} id={}", post.getId(), post.getAuthorType(), post.getAuthorId());

        return toResponse(post);
    }

    @Transactional
    public PostResponse likePost(Long postId, Long userId) {
        userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("Post not found: " + postId));

        post.setLikeCount(post.getLikeCount() + 1);
        post = postRepository.save(post);

        // +20 virality for human like
        long virality = redisViralityService.incrementViralityHumanLike(postId);

        if (post.getAuthorType() == AuthorType.USER) {
            String msg = "User " + userId + " liked your post #" + postId;
            sendNotification(post.getAuthorId(), msg);
        }

        log.info("Post {} liked by user {}. New likeCount={}, virality={}", postId, userId, post.getLikeCount(), virality);

        PostResponse response = toResponse(post);
        response.setViralityScore(virality);
        return response;
    }

    public PostResponse getPost(Long postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("Post not found: " + postId));
        PostResponse response = toResponse(post);
        response.setViralityScore(redisViralityService.getViralityScore(postId));
        return response;
    }

    private void sendNotification(Long userId, String message) {
        boolean canNotify = redisViralityService.tryAcquireNotifCooldown(userId);
        if (canNotify) {
            log.info("[PUSH NOTIFICATION] Sending to user {}: {}", userId, message);
        } else {
            redisViralityService.pushPendingNotification(userId, message);
            log.info("[NOTIFICATION QUEUED] User {} is on cooldown, queued: {}", userId, message);
        }
    }

    private PostResponse toResponse(Post post) {
        return PostResponse.builder()
                .id(post.getId())
                .authorId(post.getAuthorId())
                .authorType(post.getAuthorType())
                .content(post.getContent())
                .createdAt(post.getCreatedAt())
                .likeCount(post.getLikeCount())
                .viralityScore(redisViralityService.getViralityScore(post.getId()))
                .build();
    }
}
