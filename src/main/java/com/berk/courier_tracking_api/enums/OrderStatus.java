package com.berk.courier_tracking_api.enums;

public enum OrderStatus {
    PENDING, ASSIGNED, PICKED_UP, DELIVERED, CANCELLED;

    public boolean isTerminal() {
        return this == DELIVERED || this == CANCELLED;
    }
}
