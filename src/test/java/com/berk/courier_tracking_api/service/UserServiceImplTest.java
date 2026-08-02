package com.berk.courier_tracking_api.service;

import com.berk.courier_tracking_api.dto.AuthResponse;
import com.berk.courier_tracking_api.dto.UserLoginRequest;
import com.berk.courier_tracking_api.dto.UserRegisterRequest;
import com.berk.courier_tracking_api.entity.User;
import com.berk.courier_tracking_api.enums.UserRole;
import com.berk.courier_tracking_api.exception.BusinessException;
import com.berk.courier_tracking_api.exception.ErrorCode;
import com.berk.courier_tracking_api.repository.UserRepository;
import com.berk.courier_tracking_api.security.JwtService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private UserServiceImpl userService;

    @Test
    void registerUser_whenEmailNotRegistered_shouldPersistUserAndReturnAuthResponse() {
        UserRegisterRequest request = new UserRegisterRequest(
                "Berk Mermer", "berk@example.com", "+905551234567", "securePass123");

        when(userRepository.existsByEmail(request.email())).thenReturn(false);
        when(passwordEncoder.encode(request.password())).thenReturn("hashed-password");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User user = inv.getArgument(0);
            user.setId(1L);
            return user;
        });
        when(jwtService.generateToken(any(UserDetails.class))).thenReturn("jwt-token");

        AuthResponse response = userService.registerUser(request);

        assertNotNull(response);
        assertEquals("jwt-token", response.token());
        assertEquals("Berk Mermer", response.user().fullName());
        assertEquals(UserRole.CUSTOMER, response.user().role());

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertEquals("hashed-password", userCaptor.getValue().getPasswordHash());
        assertEquals(UserRole.CUSTOMER, userCaptor.getValue().getRole());
    }

    @Test
    void registerUser_whenEmailAlreadyRegistered_shouldThrowBusinessExceptionWith409() {
        UserRegisterRequest request = new UserRegisterRequest(
                "Berk Mermer", "berk@example.com", "+905551234567", "securePass123");

        when(userRepository.existsByEmail(request.email())).thenReturn(true);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> userService.registerUser(request));

        assertEquals(ErrorCode.EMAIL_ALREADY_REGISTERED, exception.getErrorCode());
    }

    @Test
    void loginUser_whenCredentialsValid_shouldReturnAuthResponse() {
        UserLoginRequest request = new UserLoginRequest("berk@example.com", "securePass123");

        User user = new User();
        user.setId(1L);
        user.setFullName("Berk Mermer");
        user.setEmail("berk@example.com");
        user.setPasswordHash("hashed-password");
        user.setRole(UserRole.CUSTOMER);

        when(userRepository.findByEmail(request.email())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(request.password(), user.getPasswordHash())).thenReturn(true);
        when(jwtService.generateToken(any(UserDetails.class))).thenReturn("jwt-token");

        AuthResponse response = userService.loginUser(request);

        assertNotNull(response);
        assertEquals("jwt-token", response.token());
        assertEquals("berk@example.com", response.user().email());
    }

    @Test
    void loginUser_whenEmailNotFound_shouldThrowBusinessExceptionWith401() {
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> userService.loginUser(new UserLoginRequest("ghost@example.com", "anyPassword")));

        assertEquals(ErrorCode.INVALID_CREDENTIALS, exception.getErrorCode());
    }

    @Test
    void loginUser_whenPasswordWrong_shouldThrowBusinessExceptionWith401() {
        User user = new User();
        user.setId(1L);
        user.setEmail("berk@example.com");
        user.setPasswordHash("hashed-password");
        user.setRole(UserRole.CUSTOMER);

        when(userRepository.findByEmail("berk@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrongPassword", "hashed-password")).thenReturn(false);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> userService.loginUser(new UserLoginRequest("berk@example.com", "wrongPassword")));

        assertEquals(ErrorCode.INVALID_CREDENTIALS, exception.getErrorCode());
    }
}
