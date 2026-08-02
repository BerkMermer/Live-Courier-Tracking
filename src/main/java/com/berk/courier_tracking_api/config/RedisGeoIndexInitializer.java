package com.berk.courier_tracking_api.config;

import com.berk.courier_tracking_api.entity.CourierProfile;
import com.berk.courier_tracking_api.repository.CourierProfileRepository;
import com.berk.courier_tracking_api.service.RedisLocationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/** Rebuilds the Redis GEO index from PostgreSQL courier locations on startup. */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisGeoIndexInitializer implements ApplicationRunner {

    private final CourierProfileRepository courierProfileRepository;
    private final RedisLocationService redisLocationService;

    @Override
    public void run(ApplicationArguments args) {
        List<CourierProfile> couriersWithKnownLocation =
                courierProfileRepository.findByLastKnownLatIsNotNullAndLastKnownLngIsNotNull();

        if (couriersWithKnownLocation.isEmpty()) {
            log.info("Redis GEO re-index: PostgreSQL'de bilinen konumlu kurye bulunamadı, atlanıyor");
            return;
        }

        int successCount = 0;
        for (CourierProfile courier : couriersWithKnownLocation) {
            try {
                redisLocationService.addCourierLocation(
                        courier.getId(),
                        courier.getLastKnownLat(),
                        courier.getLastKnownLng()
                );
                successCount++;
            } catch (Exception e) {
                log.warn("Redis GEO re-index: courierId={} için yazma başarısız: {}",
                        courier.getId(), e.getMessage());
            }
        }

        log.info("Redis GEO re-index tamamlandı: {}/{} kurye Redis GEO set'ine yeniden yazıldı",
                successCount, couriersWithKnownLocation.size());
    }
}
