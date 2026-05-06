package com.grid07.socialbot.dto;

import com.grid07.socialbot.entity.AuthorType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreatePostRequest {

    @NotNull(message = "authorId is required")
    private Long authorId;

    @NotNull(message = "authorType is required (USER or BOT)")
    private AuthorType authorType;

    @NotBlank(message = "content is required")
    private String content;
}
