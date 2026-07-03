package com.hm.badminton.service.catalog;

import com.hm.badminton.entity.SportType;

import java.util.List;

public interface ISportCatalogService {
    List<SportType> list();

    SportType require(String code);

    String normalize(String code);
}


