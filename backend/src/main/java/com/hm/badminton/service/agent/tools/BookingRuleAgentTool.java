package com.hm.badminton.service.agent.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

/**
 * Provides the platform's trusted booking rules without invoking recommendation tools.
 *
 * <p>Rules are kept in one backend component so the assistant never asks the model to
 * invent inventory, verification or refund behavior. Product-specific restrictions
 * remain authoritative on the product detail and order confirmation pages.</p>
 */
@Component
public class BookingRuleAgentTool {

    @Tool(name = "queryPlatformBookingRules",
            description = "Read the verified booking, verification and refund rules of the platform.")
    public String queryPlatformBookingRules() {
        return """
                **场馆项目预约规则**

                1. **选择时段**：下单时必须选择具体日期和可售时段，系统会再次核验实时库存；助手推荐不代表已经锁定场地。
                2. **支付与核销**：支付成功后生成场馆订单和核销码，请在订单对应的场所、日期和时段内使用。
                3. **使用限制**：畅打套餐、单场时段和私教课的适用日期、限购数量、是否需要提前预约可能不同，以商品详情中的“使用规则”为准。
                4. **退款规则**：可退时间和逾期限制以具体商品的“退款规则”为准；已核销订单不能再次使用。
                5. **最终确认**：提交订单前请再次核对场所、日期、开始时间、结束时间和价格，实际下单结果以确认页返回为准。

                预约规则查询不会自动选择场所、团购或时段，也不会创建订单。""";
    }
}
