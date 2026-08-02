package com.berk.courier_tracking_api.service;

import java.util.List;

public interface RedisLocationService {

    /** Writes courier position via Redis GEOADD. */
    void addCourierLocation(Long courierId, double latitude, double longitude);

    /** Returns nearby courier IDs via Redis GEORADIUS, nearest first. */
    List<Long> findNearbyCouriers(double latitude, double longitude);
}
