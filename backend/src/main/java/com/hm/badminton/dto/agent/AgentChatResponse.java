package com.hm.badminton.dto.agent;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AgentChatResponse {
    private Long conversationId;
    private String answer;
    private List<AgentCard> cards = new ArrayList<>();
    private List<String> quickReplies = new ArrayList<>();
    private boolean aiEnabled;
}
