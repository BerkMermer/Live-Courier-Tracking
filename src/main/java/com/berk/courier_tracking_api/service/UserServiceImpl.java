package com.berk.courier_tracking_api.service;

import com.berk.courier_tracking_api.dto.AuthResponse;
import com.berk.courier_tracking_api.dto.CourierRegisterRequest;
import com.berk.courier_tracking_api.dto.UserLoginRequest;
import com.berk.courier_tracking_api.dto.UserRegisterRequest;
import com.berk.courier_tracking_api.dto.UserResponse;
import com.berk.courier_tracking_api.entity.CourierProfile;
import com.berk.courier_tracking_api.entity.User;
import com.berk.courier_tracking_api.enums.CourierStatus;
import com.berk.courier_tracking_api.enums.UserRole;
import com.berk.courier_tracking_api.exception.BusinessException;
import com.berk.courier_tracking_api.exception.ErrorCode;
import com.berk.courier_tracking_api.repository.CourierProfileRepository;
import com.berk.courier_tracking_api.repository.UserRepository;
import com.berk.courier_tracking_api.security.JwtService;
import com.berk.courier_tracking_api.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final CourierProfileRepository courierProfileRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Override
    @Transactional
    public AuthResponse registerUser(UserRegisterRequest request) {
        assertUniqueContact(request.email(), request.phoneNumber());

        User user = new User();
        user.setFullName(request.fullName());
        user.setEmail(request.email());
        user.setPhoneNumber(request.phoneNumber());
        user.setRole(UserRole.CUSTOMER);
        user.setPasswordHash(passwordEncoder.encode(request.password()));

        User savedUser = userRepository.save(user);
        return toAuthResponse(savedUser);
    }

    @Override
    @Transactional
    public AuthResponse registerCourier(CourierRegisterRequest request) {
        assertUniqueContact(request.email(), request.phoneNumber());

        User user = new User();
        user.setFullName(request.fullName());
        user.setEmail(request.email());
        user.setPhoneNumber(request.phoneNumber());
        user.setRole(UserRole.COURIER);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        User savedUser = userRepository.save(user);

        CourierProfile profile = new CourierProfile();
        profile.setUser(savedUser);
        profile.setPhoneNumber(request.phoneNumber());
        profile.setVehiclePlate(request.vehiclePlate());
        profile.setStatus(CourierStatus.AVAILABLE);
        courierProfileRepository.save(profile);

        return toAuthResponse(savedUser);
    }

    @Override
    public AuthResponse loginUser(UserLoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        return toAuthResponse(user);
    }

    private void assertUniqueContact(String email, String phoneNumber) {
        if (userRepository.existsByEmail(email)) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_REGISTERED,
                    "Bu email adresi zaten kayıtlı: " + email);
        }
        if (userRepository.existsByPhoneNumber(phoneNumber)) {
            throw new BusinessException(ErrorCode.PHONE_ALREADY_REGISTERED,
                    "Bu telefon numarası zaten kayıtlı: " + phoneNumber);
        }
    }

    private AuthResponse toAuthResponse(User user) {
        return AuthResponse.of(jwtService.generateToken(UserPrincipal.from(user)), UserResponse.from(user));
    }
}
