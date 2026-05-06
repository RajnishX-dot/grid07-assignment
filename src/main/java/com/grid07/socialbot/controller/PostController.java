package com.grid07.socialbot.controller;

import com.grid07.socialbot.dto.*;
import com.grid07.socialbot.service.CommentService;
import com.grid07.socialbot.service.PostService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;
    private final CommentService commentService;

    @PostMapping
    public ResponseEntity<ApiResponse<PostResponse>> createPost(@Valid @RequestBody CreatePostRequest req) {
        PostResponse response = postService.createPost(req);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Post created successfully", response));
    }

    @GetMapping("/{postId}")
    public ResponseEntity<ApiResponse<PostResponse>> getPost(@PathVariable Long postId) {
        return ResponseEntity.ok(ApiResponse.ok(postService.getPost(postId)));
    }

    // add comment - runs all the redis guardrails before saving
    @PostMapping("/{postId}/comments")
    public ResponseEntity<ApiResponse<CommentResponse>> addComment(
            @PathVariable Long postId,
            @Valid @RequestBody CreateCommentRequest req) {
        CommentResponse response = commentService.addComment(postId, req);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Comment added successfully", response));
    }

    @GetMapping("/{postId}/comments")
    public ResponseEntity<ApiResponse<List<CommentResponse>>> getComments(@PathVariable Long postId) {
        return ResponseEntity.ok(ApiResponse.ok(commentService.getCommentsByPost(postId)));
    }

    @PostMapping("/{postId}/like")
    public ResponseEntity<ApiResponse<PostResponse>> likePost(
            @PathVariable Long postId,
            @Valid @RequestBody LikePostRequest req) {
        PostResponse response = postService.likePost(postId, req.getUserId());
        return ResponseEntity.ok(ApiResponse.ok("Post liked successfully", response));
    }
}
