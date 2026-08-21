package com.tem.spring.wallet.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WithdrawRequest {

    @NotNull(message = "유저 ID가 필요합니다.")
    private Long userId;

    @DecimalMin(value = "1.0", message = "최소 출금 가능 수량은 1.0 AETHER 입니다.")
    private double amount;

    @NotBlank(message = "출금 유형을 선택하세요. (WEB3_WALLET 또는 BYBIT_UID)")
    private String destinationType; // WEB3_WALLET, BYBIT_UID

    @NotBlank(message = "출금 목적지 주소/UID를 입력해주세요.")
    private String destinationAddress; // 0x... 또는 Bybit UID
}
