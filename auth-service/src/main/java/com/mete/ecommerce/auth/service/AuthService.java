package com.mete.ecommerce.auth.service;

import com.mete.ecommerce.auth.dto.AuthResponse;
import com.mete.ecommerce.auth.dto.LoginRequest;
import com.mete.ecommerce.auth.dto.RegisterRequest;
import com.mete.ecommerce.auth.entity.User;
import com.mete.ecommerce.auth.exception.EmailAlreadyExistsException;
import com.mete.ecommerce.auth.repository.UserRepository;
import com.mete.ecommerce.auth.security.JwtUtil;
import io.jsonwebtoken.security.Password;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())){
            throw new EmailAlreadyExistsException(request.getEmail());
        }
        User user = User.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(request.getRole().toUpperCase())
                .build();

        userRepository.save(user);
        String token = jwtUtil.generateToken(user.getEmail(), user.getRole());
        return new AuthResponse(token, user.getEmail(), user.getRole());

    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BadCredentialsException("User could not find!"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BadCredentialsException("Invalid Password!");
        }

        String token = jwtUtil.generateToken(user.getEmail(), user.getRole());
        return new AuthResponse(token, user.getEmail(), user.getRole());
    }

    public User getCurrentUser (String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User could not find!"));
    }


}
