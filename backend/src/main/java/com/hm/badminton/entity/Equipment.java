package com.hm.badminton.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
public class Equipment implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String sportCode;
    private Long categoryId;
    private String categoryName;
    private String name;
    private String brand;
    private String description;
    private String coverUrl;
    private BigDecimal price;
    private Integer stock;
    private BigDecimal score;
    private Integer sold;
    private Integer status;
    private LocalDateTime createdAt;
}

