package com.tem.spring.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 네이버, 카카오, 구글, 애플, 메타마스크 소셜 원클릭 로그인 요청 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SocialLoginRequest {

    @NotBlank(message = "소셜 제공자(provider)는 필수입니다. (NAVER, KAKAO, GOOGLE, APPLE, METAMASK)")
    private String provider;

    @NotBlank(message = "소셜 고유 식별자(providerId)는 필수입니다.")
    private String providerId;

    private String nickname;

    private String email;

    private String walletAddress;
}
