package com.hm.badminton.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
public class EquipmentCategory implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String sportCode;
    private String name;
    private String icon;
    private Integer sort;
}
