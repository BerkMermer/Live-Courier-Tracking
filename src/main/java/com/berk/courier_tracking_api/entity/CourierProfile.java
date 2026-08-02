package com.berk.courier_tracking_api.entity;

import com.berk.courier_tracking_api.enums.CourierStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(
        name = "courier_profiles",
        indexes = {
                @Index(name = "idx_courier_status", columnList = "status"),
                @Index(name = "idx_courier_phone", columnList = "phone_number")
        }
)
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CourierProfile extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY)

    @JoinColumn(name = "user_id", nullable = false, unique = true)

    private User user;

    @Column(name = "phone_number", nullable = false)

    private String phoneNumber;

    @Column(name = "vehicle_plate", nullable = false)
    private String vehiclePlate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private CourierStatus status;

    @Column(name = "last_known_lat")

    private Double lastKnownLat;

    @Column(name = "last_known_lng")
    private Double lastKnownLng;

    @Column(name = "last_location_update")
    private LocalDateTime lastLocationUpdate;


    @OneToMany(mappedBy = "courier", fetch = FetchType.LAZY)

    private List<Order> orders;
}