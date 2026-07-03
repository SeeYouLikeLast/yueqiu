package com.hm.badminton.service.auth;

public interface IBloomFilterService {
    Boolean mightContain(String key, String value);

    void put(String key, String value);
}


