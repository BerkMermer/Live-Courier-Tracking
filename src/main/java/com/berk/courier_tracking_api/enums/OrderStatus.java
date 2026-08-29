package com.berk.courier_tracking_api.enums;

import java.util.List;

public enum OrderStatus {
    PENDING, ASSIGNED, PICKED_UP, DELIVERED, CANCELLED;

    /** Customer may live-track only while the courier is assigned / en route. */
    public static List<OrderStatus> liveTracking() {
        return List.of(ASSIGNED, PICKED_UP);
    }

    public boolean isTerminal() {
        return this == DELIVERED || this == CANCELLED;
    }
}
