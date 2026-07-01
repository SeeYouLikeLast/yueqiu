package com.hm.badminton.service;

public interface IBloomFilterService {
    Boolean mightContain(String key, String value);

    void put(String key, String value);
}
