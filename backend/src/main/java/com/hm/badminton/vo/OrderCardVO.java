package com.hm.badminton.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderCardVO {
    private String key;
    private String title;
    private String subtitle;
    private String meta;
    private BigDecimal amount;
    private String status;
    private String code;
}
