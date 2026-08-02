package com.berk.courier_tracking_api.dto;

public record AuthResponse(
        String token,
        UserResponse user
) {

    public static AuthResponse of(String token, UserResponse user) {
        return new AuthResponse(token, user);
    }
}
