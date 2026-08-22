package com.tem.spring.auth.service;

import com.tem.spring.auth.dto.AuthResponse;
import com.tem.spring.auth.dto.LoginRequest;
import com.tem.spring.auth.dto.SignUpRequest;
import com.tem.spring.auth.entity.UserEntity;
import com.tem.spring.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;

/**
 * 사용자 회원가입, 로그인 및 인증 비즈니스 로직 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;

    @Transactional
    public AuthResponse signUp(SignUpRequest request) {
        log.info("[AuthService] Processing sign up for username: {}", request.getUsername());

        if (userRepository.existsByUsername(request.getUsername())) {
            return AuthResponse.builder()
                    .success(false)
                    .message("이미 존재하는 아이디입니다.")
                    .build();
        }

        if (userRepository.existsByNickname(request.getNickname())) {
            return AuthResponse.builder()
                    .success(false)
                    .message("이미 사용 중인 닉네임입니다.")
                    .build();
        }

        String hashedPassword = hashPassword(request.getPassword());

        UserEntity user = UserEntity.builder()
                .username(request.getUsername())
                .password(hashedPassword)
                .nickname(request.getNickname())
                .walletAddress(request.getWalletAddress())
                .reputationScore(100)
                .role("ROLE_USER")
                .createdAt(LocalDateTime.now())
                .build();

        UserEntity savedUser = userRepository.save(user);

        String token = generateToken(savedUser);

        return AuthResponse.builder()
                .success(true)
                .message("회원가입이 성공적으로 완료되었습니다.")
                .userId(savedUser.getId())
                .username(savedUser.getUsername())
                .nickname(savedUser.getNickname())
                .walletAddress(savedUser.getWalletAddress())
                .reputationScore(savedUser.getReputationScore())
                .tokenBalance(savedUser.getTokenBalance())
                .role(savedUser.getRole())
                .accessToken(token)
                .build();
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        log.info("[AuthService] Processing login for username: {}", request.getUsername());

        UserEntity user = userRepository.findByUsername(request.getUsername())
                .orElse(null);

        if (user == null) {
            return AuthResponse.builder()
                    .success(false)
                    .message("아이디 또는 비밀번호가 일치하지 않습니다.")
                    .build();
        }

        String inputHashed = hashPassword(request.getPassword());
        if (!user.getPassword().equals(inputHashed)) {
            return AuthResponse.builder()
                    .success(false)
                    .message("아이디 또는 비밀번호가 일치하지 않습니다.")
                    .build();
        }

        String token = generateToken(user);

        return AuthResponse.builder()
                .success(true)
                .message("로그인 성공")
                .userId(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .walletAddress(user.getWalletAddress())
                .reputationScore(user.getReputationScore())
                .tokenBalance(user.getTokenBalance())
                .role(user.getRole())
                .accessToken(token)
                .build();
    }

    @Transactional
    public AuthResponse socialLogin(com.tem.spring.auth.dto.SocialLoginRequest request) {
        String provider = request.getProvider().toUpperCase();
        String providerId = request.getProviderId();
        String socialUsername = provider + "_" + providerId;

        log.info("[AuthService] Processing 1-Click Social Login: Provider={}, ProviderId={}", provider, providerId);

        UserEntity user = userRepository.findByUsername(socialUsername)
                .orElseGet(() -> {
                    String baseNickname = request.getNickname() != null && !request.getNickname().isBlank()
                            ? request.getNickname()
                            : provider + "_투자자_" + (providerId.length() > 4 ? providerId.substring(providerId.length() - 4) : providerId);

                    String finalNickname = baseNickname;
                    int suffix = 1;
                    while (userRepository.existsByNickname(finalNickname)) {
                        finalNickname = baseNickname + "_" + suffix++;
                    }

                    UserEntity newUser = UserEntity.builder()
                            .username(socialUsername)
                            .password("N/A_OAUTH2_SECURED")
                            .nickname(finalNickname)
                            .walletAddress(request.getWalletAddress())
                            .tokenBalance(50.0)
                            .reputationScore(100)
                            .role("ROLE_USER")
                            .createdAt(LocalDateTime.now())
                            .build();

                    log.info("[AuthService] Auto-registering new user from {} OAuth: Nickname={}", provider, finalNickname);
                    return userRepository.save(newUser);
                });

        String token = generateToken(user);

        return AuthResponse.builder()
                .success(true)
                .message(provider + " 간편 로그인 성공")
                .userId(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .walletAddress(user.getWalletAddress())
                .reputationScore(user.getReputationScore())
                .tokenBalance(user.getTokenBalance())
                .role(user.getRole())
                .accessToken(token)
                .build();
    }

    private String hashPassword(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(password.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("비밀번호 암호화 알고리즘 오류", e);
        }
    }

    private String generateToken(UserEntity user) {
        String payload = user.getId() + ":" + user.getUsername() + ":" + UUID.randomUUID();
        return Base64.getEncoder().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    }
}
