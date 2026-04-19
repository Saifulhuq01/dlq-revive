package com.dlqrevive.redrive;

import lombok.Builder;
import lombok.Data;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

@Data
@Builder
public class RedriveRequest {
    @NotBlank(message = "Bootstrap servers are required")
    private String bootstrapServers;

    @NotBlank(message = "Target topic is required")
    private String targetTopic;

    private String expression;

    @NotEmpty(message = "At least one message is required")
    @Valid
    private List<RedriveMessage> messages;

    @NotBlank(message = "User identifier is required")
    private String user;

    @NotBlank(message = "Session ID is required")
    private String sessionId;
}
