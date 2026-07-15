package com.hm.badminton.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "hm.amap")
public class AmapProperties {

    private String key = "";
    private String endpoint = "https://restapi.amap.com/v5/place/around";
    private String ipLocationEndpoint = "https://restapi.amap.com/v3/ip";
    private String defaultCity = "西安";
    private double defaultLongitude = 108.946465;
    private double defaultLatitude = 34.347269;
    private int radius = 5000;
    private int pageSize = 20;
}
