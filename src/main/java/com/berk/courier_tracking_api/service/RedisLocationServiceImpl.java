package com.berk.courier_tracking_api.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.geo.*;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/** Redis GEO for courier locations: GEOADD to write, GEORADIUS to find nearest. */
@Slf4j
@Service
@RequiredArgsConstructor
public class RedisLocationServiceImpl implements RedisLocationService {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final String GEO_KEY = "couriers:active:locations";

    private static final double DEFAULT_SEARCH_RADIUS_KM = 10.0;

    @Value("${courier.nearby.limit:20}")
    private int nearbyLimit;

    @Override
    public void addCourierLocation(Long courierId, double latitude, double longitude) {
        try {
            redisTemplate.opsForGeo().add(
                    GEO_KEY,
                    new Point(longitude, latitude),
                    courierId.toString()
            );
            log.debug("Kurye konum güncellendi → Redis GEO | courierId={}, lat={}, lng={}",
                    courierId, latitude, longitude);
        } catch (Exception e) {
            log.warn("Redis GEO yazma hatası (courierId={}): {}", courierId, e.getMessage());
        }
    }

    @Override
    public List<Long> findNearbyCouriers(double latitude, double longitude) {
        try {
            GeoResults<RedisGeoCommands.GeoLocation<Object>> results =
                    redisTemplate.opsForGeo().radius(
                            GEO_KEY,
                            new Circle(
                                    new Point(longitude, latitude),
                                    new Distance(DEFAULT_SEARCH_RADIUS_KM, Metrics.KILOMETERS)
                            ),
                            RedisGeoCommands.GeoRadiusCommandArgs
                                    .newGeoRadiusArgs()
                                    .sortAscending()
                                    .limit(nearbyLimit)
                            );

            if (results == null || results.getContent().isEmpty()) {
                log.debug("Redis GEO: Yakında kurye bulunamadı (lat={}, lng={}, radius={}km)",
                        latitude, longitude, DEFAULT_SEARCH_RADIUS_KM);
                return Collections.emptyList();
            }

            List<Long> courierIds = results.getContent().stream()
                    .map(result -> Long.parseLong(result.getContent().getName().toString()))
                    .toList();

            log.debug("Redis GEO: {} kurye bulundu (lat={}, lng={}, radius={}km)",
                    courierIds.size(), latitude, longitude, DEFAULT_SEARCH_RADIUS_KM);

            return courierIds;

        } catch (Exception e) {
            log.warn("Redis GEO arama hatası: {}. Haversine fallback kullanılacak.", e.getMessage());
            return Collections.emptyList();
        }
    }
}
