package com.hm.badminton.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
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
@TableName("blogs")
public class Blog implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String sportCode;
    private String title;
    private String content;
    private String imageUrls;
    private String relatedType;
    private Long relatedId;
    private String relatedTitle;
    private String relatedCoverUrl;
    private BigDecimal relatedPrice;
    private Integer liked;
    private Integer status;
    private LocalDateTime deletedAt;
    private LocalDateTime archivedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

