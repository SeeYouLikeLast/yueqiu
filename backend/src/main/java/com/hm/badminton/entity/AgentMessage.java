package com.hm.badminton.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("agent_message")
public class AgentMessage implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long conversationId;
    private Long userId;
    private String role;
    private String content;
    private String cardsJson;
    private String toolName;
    private String toolResultJson;
    private LocalDateTime createdAt;
}
