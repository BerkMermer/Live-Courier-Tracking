package com.berk.courier_tracking_api.service;

import com.berk.courier_tracking_api.dto.AuthResponse;
import com.berk.courier_tracking_api.dto.UserLoginRequest;
import com.berk.courier_tracking_api.dto.UserRegisterRequest;
import com.berk.courier_tracking_api.dto.UserResponse;
import com.berk.courier_tracking_api.entity.User;
import com.berk.courier_tracking_api.enums.UserRole;
import com.berk.courier_tracking_api.exception.BusinessException;
import com.berk.courier_tracking_api.exception.ErrorCode;
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
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Override
    @Transactional
    public AuthResponse registerUser(UserRegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_REGISTERED,
                    "Bu email adresi zaten kayıtlı: " + request.email());
        }

        User user = new User();
        user.setFullName(request.fullName());
        user.setEmail(request.email());
        user.setPhoneNumber(request.phoneNumber());
        user.setRole(UserRole.CUSTOMER);
        user.setPasswordHash(passwordEncoder.encode(request.password()));

        User savedUser = userRepository.save(user);

        UserResponse userResponse = UserResponse.from(savedUser);
        String token = jwtService.generateToken(UserPrincipal.from(savedUser));
        return AuthResponse.of(token, userResponse);
    }

    @Override
    public AuthResponse loginUser(UserLoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        UserResponse userResponse = UserResponse.from(user);
        String token = jwtService.generateToken(UserPrincipal.from(user));
        return AuthResponse.of(token, userResponse);
    }
}
