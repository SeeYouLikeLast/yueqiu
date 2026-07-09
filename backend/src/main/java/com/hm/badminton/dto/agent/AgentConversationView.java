package com.hm.badminton.dto.agent;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AgentConversationView {
    private Long id;
    private String title;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
