package com.hm.badminton.dto.agent;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class AgentMessageView {
    private Long id;
    private String role;
    private String content;
    private List<AgentCard> cards = new ArrayList<>();
    private LocalDateTime createdAt;
}
