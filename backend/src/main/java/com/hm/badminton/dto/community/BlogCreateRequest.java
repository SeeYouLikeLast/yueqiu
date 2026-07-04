package com.hm.badminton.dto.community;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BlogCreateRequest {
    @NotBlank
    private String sportCode;

    @NotBlank
    private String title;

    @NotBlank
    private String content;

    private List<String> images;

    @NotBlank
    private String relatedType;

    @NotNull
    private Long relatedId;

    @NotBlank
    private String relatedTitle;

    private String relatedCoverUrl;
    private BigDecimal relatedPrice;
}
