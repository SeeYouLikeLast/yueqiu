package com.hm.badminton.constants;

import com.hm.badminton.common.BusinessException;

/**
 * 统一交易类型：1=场所商品，2=装备商品。
 */
public final class TradeType {

    private TradeType() {
    }

    public static final int VENUE = 1;
    public static final int EQUIPMENT = 2;

    public static int require(Integer type) {
        if (type == null) {
            throw new BusinessException(400, "业务类型不能为空");
        }
        if (type != VENUE && type != EQUIPMENT) {
            throw new BusinessException(400, "业务类型只支持 1=场所商品，2=装备商品");
        }
        return type;
    }

    public static int require(String type) {
        if (type == null || type.isBlank()) {
            throw new BusinessException(400, "业务类型不能为空");
        }
        try {
            return require(Integer.parseInt(type.trim()));
        } catch (NumberFormatException e) {
            throw new BusinessException(400, "业务类型只支持 1=场所商品，2=装备商品");
        }
    }
}

