package com.grid07.socialbot.dto;

import com.grid07.socialbot.entity.AuthorType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostResponse {
    private Long id;
    private Long authorId;
    private AuthorType authorType;
    private String content;
    private LocalDateTime createdAt;
    private Integer likeCount;
    private Long viralityScore;
}
