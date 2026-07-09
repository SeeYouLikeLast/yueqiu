package com.hm.badminton.service.agent.tools;

import com.hm.badminton.dto.LoginUser;
import com.hm.badminton.utils.UserContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class UserPreferenceAgentTool {

    private final UserContext userContext;

    public UserPreferenceAgentTool(UserContext userContext) {
        this.userContext = userContext;
    }

    @Tool(name = "getCurrentUserPreference", description = "Get current logged-in user's city and location preference.")
    public Map<String, Object> getCurrentUserPreference() {
        Map<String, Object> result = new LinkedHashMap<>();
        userContext.current().ifPresent(user -> fill(result, user));
        return result;
    }

    private void fill(Map<String, Object> result, LoginUser user) {
        result.put("userId", user.getId());
        result.put("city", user.getCity());
        result.put("lng", user.getLongitude());
        result.put("lat", user.getLatitude());
        result.put("level", user.getLevel());
    }
}
