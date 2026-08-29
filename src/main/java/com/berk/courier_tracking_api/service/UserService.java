package com.berk.courier_tracking_api.service;

import com.berk.courier_tracking_api.dto.AuthResponse;
import com.berk.courier_tracking_api.dto.CourierRegisterRequest;
import com.berk.courier_tracking_api.dto.UserLoginRequest;
import com.berk.courier_tracking_api.dto.UserRegisterRequest;

public interface UserService {

    AuthResponse registerUser(UserRegisterRequest request);

    AuthResponse registerCourier(CourierRegisterRequest request);

    AuthResponse loginUser(UserLoginRequest request);
}
