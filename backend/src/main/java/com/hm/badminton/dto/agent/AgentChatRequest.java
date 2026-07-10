package com.hm.badminton.dto.agent;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class AgentChatRequest {
    private Long conversationId;
    @NotBlank(message = "请输入你的问题")
    private String message;
    private String sportCode;
    private List<String> sportCodes;
    private String city;
    private Double lng;
    private Double lat;
}
