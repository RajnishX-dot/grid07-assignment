package com.grid07.socialbot.service;

import com.grid07.socialbot.dto.CommentResponse;
import com.grid07.socialbot.dto.CreateCommentRequest;
import com.grid07.socialbot.entity.AuthorType;
import com.grid07.socialbot.entity.Comment;
import com.grid07.socialbot.entity.Post;
import com.grid07.socialbot.repository.BotRepository;
import com.grid07.socialbot.repository.CommentRepository;
import com.grid07.socialbot.repository.PostRepository;
import com.grid07.socialbot.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CommentService {

    private final CommentRepository commentRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final BotRepository botRepository;
    private final RedisViralityService redisViralityService;

    @Value("${app.bot.max-comment-depth:20}")
    private int maxCommentDepth;

    @Transactional
    public CommentResponse addComment(Long postId, CreateCommentRequest req) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Post not found: " + postId));

        // check depth before anything else
        if (!redisViralityService.isDepthAllowed(req.getDepthLevel())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Comment depth " + req.getDepthLevel() + " exceeds maximum depth of " + maxCommentDepth
            );
        }

        if (req.getAuthorType() == AuthorType.BOT) {
            botRepository.findById(req.getAuthorId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Bot not found: " + req.getAuthorId()));

            // check bot limit before saving - Lua script handles this atomically
            long slotResult = redisViralityService.tryClaimBotReplySlot(postId);
            if (slotResult == -1L) {
                log.warn("Bot {} rejected: post {} hit max bot replies", req.getAuthorId(), postId);
                throw new ResponseStatusException(
                        HttpStatus.TOO_MANY_REQUESTS,
                        "Post " + postId + " has reached the maximum bot reply limit (100). Request rejected."
                );
            }

            // if post is by a human, check bot-human cooldown
            if (post.getAuthorType() == AuthorType.USER) {
                boolean allowed = redisViralityService.tryAcquireBotCooldown(req.getAuthorId(), post.getAuthorId());
                if (!allowed) {
                    // give back the slot we just claimed since we're rejecting
                    redisViralityService.releaseBotReplySlot(postId);
                    log.warn("Bot {} rejected: cooldown with human {}", req.getAuthorId(), post.getAuthorId());
                    throw new ResponseStatusException(
                            HttpStatus.TOO_MANY_REQUESTS,
                            "Bot " + req.getAuthorId() + " is on cooldown with this human. Try again in 10 minutes."
                    );
                }
            }

            redisViralityService.incrementViralityBotReply(postId);

            if (post.getAuthorType() == AuthorType.USER) {
                sendBotInteractionNotification(post.getAuthorId(), req.getAuthorId(), postId);
            }

        } else {
            userRepository.findById(req.getAuthorId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found: " + req.getAuthorId()));

            // human comment = +50 virality
            redisViralityService.incrementViralityHumanComment(postId);
        }

        // all checks passed, now save
        Comment comment = Comment.builder()
                .post(post)
                .authorId(req.getAuthorId())
                .authorType(req.getAuthorType())
                .content(req.getContent())
                .depthLevel(req.getDepthLevel())
                .parentCommentId(req.getParentCommentId())
                .build();

        comment = commentRepository.save(comment);
        log.info("Comment {} saved on post {} by {} {}", comment.getId(), postId, req.getAuthorType(), req.getAuthorId());

        return toResponse(comment);
    }

    public List<CommentResponse> getCommentsByPost(Long postId) {
        postRepository.findById(postId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Post not found: " + postId));
        return commentRepository.findByPostId(postId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    private void sendBotInteractionNotification(Long userId, Long botId, Long postId) {
        String msg = "Bot " + botId + " replied to your post #" + postId;
        boolean canNotify = redisViralityService.tryAcquireNotifCooldown(userId);
        if (canNotify) {
            log.info("[PUSH NOTIFICATION] Sending to user {}: {}", userId, msg);
        } else {
            redisViralityService.pushPendingNotification(userId, msg);
            log.info("[NOTIFICATION QUEUED] user:{} on cooldown, queued: {}", userId, msg);
        }
    }

    private CommentResponse toResponse(Comment comment) {
        return CommentResponse.builder()
                .id(comment.getId())
                .postId(comment.getPost().getId())
                .authorId(comment.getAuthorId())
                .authorType(comment.getAuthorType())
                .content(comment.getContent())
                .depthLevel(comment.getDepthLevel())
                .parentCommentId(comment.getParentCommentId())
                .createdAt(comment.getCreatedAt())
                .build();
    }
}
