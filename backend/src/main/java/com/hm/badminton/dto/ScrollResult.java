package com.hm.badminton.dto;

import java.util.List;

public record ScrollResult<T>(List<T> list, Long minTime, Integer offset) {
}
