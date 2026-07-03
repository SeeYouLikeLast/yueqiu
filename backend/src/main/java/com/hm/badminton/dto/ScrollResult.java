package com.hm.badminton.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ScrollResult<T> {
    private List<T> list;
    private Long minTime;
    private Integer offset;
}

