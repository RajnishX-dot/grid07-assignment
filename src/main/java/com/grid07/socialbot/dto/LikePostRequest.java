package com.grid07.socialbot.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class LikePostRequest {

    @NotNull(message = "userId is required")
    private Long userId;
}
