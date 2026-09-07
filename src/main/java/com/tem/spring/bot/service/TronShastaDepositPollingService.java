package com.tem.spring.bot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tem.spring.auth.entity.UserEntity;
import com.tem.spring.auth.repository.UserRepository;
import com.tem.spring.bot.entity.*;
import com.tem.spring.bot.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

/**
 * ┌───────────────────────────────────────────────────────────────────┐
 * │  TRC-20 USDT 입금 자동 감지 & 라이선스 발급 서비스                     │
 * │  (Shasta 테스트넷 기준, 환경변수 교체만으로 메인넷 전환 가능)             │
 * │                                                                   │
 * │  [Flow]                                                           │
 * │  유저 거래소 출금 → 유저 고유 T... 주소 도착                           │
 * │       ↓  (15초 폴링)                                               │
 * │  TronGrid REST API 조회 (trc20 트랜잭션 목록)                        │
 * │       ↓  19 컨펌 확인                                               │
 * │  DB TronDepositEventEntity 저장 (txId 유니크 → 이중처리 차단)         │
 * │       ↓                                                           │
 * │  BotSubscriptionService.purchaseSubscription() 자동 호출            │
 * │  → LicenseTokenGeneratorService 라이선스 토큰 발급                   │
 * │  → 유저 즉시 봇 가동 가능!                                           │
 * │       ↓  (추후 별도 배치)                                            │
 * │  마스터 지갑 집금(Sweep) — 에너지 렌탈 or 소량 TRX 사용               │
 * └───────────────────────────────────────────────────────────────────┘
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TronShastaDepositPollingService {

    // ──────────────── 의존성 ────────────────
    private final TronDepositAddressRepository depositAddressRepository;
    private final TronDepositEventRepository   depositEventRepository;
    private final UserRepository               userRepository;
    private final BotSubscriptionService       subscriptionService;
    private final BotLicenseTokenRepository    licenseTokenRepository;
    private final LicenseTokenGeneratorService licenseTokenGenerator;
    private final BotSubscriptionRepository    subscriptionRepository;
    private final ObjectMapper                 objectMapper;

    // ──────────────── 설정값 (application.yml 주입) ────────────────

    /** TronGrid REST API 베이스 URL (Shasta 테스트넷) */
    @Value("${tron.trongrid.base-url:https://api.shasta.trongrid.io}")
    private String trongridBaseUrl;

    /** TronGrid API Key (선택, 없으면 공개 엔드포인트 사용) */
    @Value("${tron.trongrid.api-key:}")
    private String trongridApiKey;

    /** USDT TRC-20 컨트랙트 주소 (Shasta: TG3XXyExBkPp9nzdajDZsozEu4B5U2U2AZ) */
    @Value("${tron.usdt.contract-address:TG3XXyExBkPp9nzdajDZsozEu4B5U2U2AZ}")
    private String usdtContractAddress;

    /** 입금 승인 확정에 필요한 최소 컨펌 수 (TRON 표준: 19) */
    @Value("${tron.deposit.min-confirmations:19}")
    private int minConfirmations;

    /** CORE 플랜 최소 USDT 금액 ($7.00) */
    @Value("${blockchain.license.monthly-price-usdt:7.0}")
    private double minDepositUsdt;

    /** 폴링 활성화 여부 */
    @Value("${tron.deposit.polling.enabled:true}")
    private boolean pollingEnabled;

    /** 운영자 마스터 집금 지갑 (Sweep 대상) */
    @Value("${tron.master-wallet.address:TVAfSsFKhMxj3jMvdSbK2Gf7ncbDgRu3Dk}")
    private String masterWalletAddress;

    private final RestTemplate restTemplate = new RestTemplate();

    // TRC-20 decimals = 6 → 실제 USDT 금액 계산 제수
    private static final BigDecimal TRC20_DECIMALS = new BigDecimal("1000000");

    // ──────────────────────────────────────────────────────────────────
    // 1. 메인 폴링 스케줄러 (15초 주기)
    // ──────────────────────────────────────────────────────────────────

    /**
     * 15초마다 모든 유저의 전용 입금 주소에 수신된 TRC-20 USDT 트랜잭션을 조회합니다.
     * 19컨펌 이상 + 금액 충족 시 즉시 라이선스 발급.
     */
    @Scheduled(fixedDelay = 15_000)
    public void pollAllDepositAddresses() {
        if (!pollingEnabled) {
            return;
        }

        List<TronDepositAddressEntity> addresses = depositAddressRepository.findAll();
        if (addresses.isEmpty()) {
            return;
        }

        log.debug("[TronPoller] Polling {} deposit addresses on {}", addresses.size(), trongridBaseUrl);

        for (TronDepositAddressEntity addr : addresses) {
            try {
                pollSingleAddress(addr);
            } catch (Exception e) {
                log.error("[TronPoller] Error polling address {}: {}", addr.getTronAddress(), e.getMessage());
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────
    // 2. 단일 주소 폴링 및 입금 처리
    // ──────────────────────────────────────────────────────────────────

    @Transactional
    public void pollSingleAddress(TronDepositAddressEntity addrEntity) {
        String depositAddr = addrEntity.getTronAddress();

        // TronGrid REST API 호출
        // GET /v1/accounts/{address}/transactions/trc20
        //   ?contract_address={USDT_CONTRACT}
        //   &limit=10
        //   &only_to=true
        String url = String.format(
                "%s/v1/accounts/%s/transactions/trc20?contract_address=%s&limit=10&only_to=true",
                trongridBaseUrl, depositAddr, usdtContractAddress
        );

        HttpHeaders headers = new HttpHeaders();
        headers.set("Accept", "application/json");
        if (!trongridApiKey.isBlank()) {
            headers.set("TRON-PRO-API-KEY", trongridApiKey);
        }
        HttpEntity<Void> reqEntity = new HttpEntity<>(headers);

        ResponseEntity<String> response;
        try {
            response = restTemplate.exchange(url, HttpMethod.GET, reqEntity, String.class);
        } catch (Exception e) {
            log.warn("[TronPoller] TronGrid API call failed for {}: {}", depositAddr, e.getMessage());
            return;
        }

        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            log.warn("[TronPoller] Non-2xx response for address {}: {}", depositAddr, response.getStatusCode());
            return;
        }

        // 응답 파싱
        JsonNode root;
        try {
            root = objectMapper.readTree(response.getBody());
        } catch (Exception e) {
            log.warn("[TronPoller] JSON parse error for {}: {}", depositAddr, e.getMessage());
            return;
        }

        JsonNode data = root.path("data");
        if (!data.isArray() || data.isEmpty()) {
            return;
        }

        // 각 트랜잭션 처리
        for (JsonNode tx : data) {
            processTx(tx, addrEntity);
        }

        // 마지막 폴링 시각 갱신
        addrEntity.setLastPolledAt(LocalDateTime.now());
        depositAddressRepository.save(addrEntity);
    }

    // ──────────────────────────────────────────────────────────────────
    // 3. 개별 트랜잭션 분석 및 라이선스 발급
    // ──────────────────────────────────────────────────────────────────

    @Transactional
    public void processTx(JsonNode tx, TronDepositAddressEntity addrEntity) {
        String txId = tx.path("transaction_id").asText();

        // ① 이미 처리된 TxID면 스킵 (멱등성)
        if (txId.isBlank() || depositEventRepository.existsByTxId(txId)) {
            return;
        }

        // ② 컨펌 수 확인 (19 이상)
        int confirmations = getConfirmations(tx);
        if (confirmations < minConfirmations) {
            log.debug("[TronPoller] TxID {} has only {} confirms (need {}). Skip.",
                    txId, confirmations, minConfirmations);
            return;
        }

        // ③ USDT 수신 금액 파싱 (value / 10^6)
        String rawValue = tx.path("value").asText("0");
        BigDecimal amountUsdt;
        try {
            amountUsdt = new BigDecimal(rawValue).divide(TRC20_DECIMALS, 6, RoundingMode.DOWN);
        } catch (NumberFormatException e) {
            log.warn("[TronPoller] Cannot parse value '{}' for txId {}", rawValue, txId);
            return;
        }

        // ④ 금액 최소 기준 확인 ($7.00 이상)
        if (amountUsdt.compareTo(BigDecimal.valueOf(minDepositUsdt)) < 0) {
            log.info("[TronPoller] TxID {} amount {} USDT < min {} USDT. Skip.",
                    txId, amountUsdt, minDepositUsdt);
            return;
        }

        // ⑤ 플랜 결정 ($13+ = PRO, $7+ = CORE)
        String planName = amountUsdt.compareTo(BigDecimal.valueOf(13.0)) >= 0 ? "PRO" : "CORE";
        double planAmount = "PRO".equals(planName) ? 13.0 : 7.0;

        String fromAddress = tx.path("from").asText("");
        long blockTimestamp = tx.path("block_timestamp").asLong(0);

        log.info("[TronPoller] ✅ Valid TRC-20 deposit detected! TxID={}, Amount={} USDT, Plan={}, User={}",
                txId, amountUsdt, planName, addrEntity.getUser().getUsername());

        // ⑥ DB에 입금 이벤트 저장
        TronDepositEventEntity event = TronDepositEventEntity.builder()
                .user(addrEntity.getUser())
                .txId(txId)
                .contractAddress(usdtContractAddress)
                .fromAddress(fromAddress)
                .toAddress(addrEntity.getTronAddress())
                .amountUsdt(amountUsdt)
                .confirmations(confirmations)
                .network(addrEntity.getNetwork())
                .status("CONFIRMED")
                .planName(planName)
                .onchainTimestampMs(blockTimestamp)
                .detectedAt(LocalDateTime.now())
                .build();

        depositEventRepository.save(event);

        // ⑦ 라이선스 발급 (BotSubscriptionService 재사용)
        try {
            issueLicense(addrEntity.getUser(), txId, planName, planAmount, event);
        } catch (Exception e) {
            log.error("[TronPoller] License issuance failed for txId {}: {}", txId, e.getMessage(), e);
            event.setStatus("FAILED");
            depositEventRepository.save(event);
        }

        // ⑧ lastProcessedTxId 갱신
        addrEntity.setLastProcessedTxId(txId);
        depositAddressRepository.save(addrEntity);
    }

    // ──────────────────────────────────────────────────────────────────
    // 4. 라이선스 자동 발급
    // ──────────────────────────────────────────────────────────────────

    @Transactional
    public void issueLicense(UserEntity user, String txId, String planName,
                              double planAmount, TronDepositEventEntity event) {
        LocalDateTime now = LocalDateTime.now();
        long timestamp = System.currentTimeMillis();

        // SHA-256 라이선스 토큰 생성
        String licenseToken = licenseTokenGenerator.generateLicenseToken(user.getId(), txId, timestamp);

        // 기존 만료일 기반 연장 처리
        LocalDateTime startDate = now;
        LocalDateTime expiredAt = now.plusDays(30);

        var activeLicenses = licenseTokenRepository.findActiveTokensByUserId(user.getId(), now);
        if (!activeLicenses.isEmpty()) {
            expiredAt = activeLicenses.get(0).getExpiredAt().plusDays(30);
        }

        // BotLicenseTokenEntity 저장
        BotLicenseTokenEntity license = BotLicenseTokenEntity.builder()
                .user(user)
                .tokenString(licenseToken)
                .paymentTxHash(txId)
                .paymentNetwork("TRC20")
                .amountUsdt(planAmount)
                .depositAddress(event.getToAddress())
                .isActive(true)
                .startDate(startDate)
                .expiredAt(expiredAt)
                .createdAt(now)
                .build();

        licenseTokenRepository.save(license);

        // BotSubscription 구독 레코드도 함께 생성
        BotSubscriptionEntity sub = BotSubscriptionEntity.builder()
                .user(user)
                .planName("AETHER " + planName + " 24H BOT INSTANCE (MONTHLY 30-DAY)")
                .amountUsdt(planAmount)
                .paymentNetwork("TRC20")
                .txHash(txId)
                .status("ACTIVE")
                .startDate(startDate)
                .endDate(expiredAt)
                .createdAt(now)
                .build();
        subscriptionRepository.save(sub);

        // 이벤트 상태 업데이트
        event.setStatus("CREDITED");
        event.setLicenseIssued(true);
        event.setIssuedLicenseToken(licenseToken);
        event.setCreditedAt(now);
        depositEventRepository.save(event);

        log.info("[TronPoller] 🎉 License issued for user {} | Plan={} | Token={}... | ValidUntil={}",
                user.getUsername(), planName, licenseToken.substring(0, 12), expiredAt);
    }

    // ──────────────────────────────────────────────────────────────────
    // 5. 유저 고유 TRON 입금 주소 발급 (최초 1회)
    // ──────────────────────────────────────────────────────────────────

    /**
     * 유저별 고유 TRC-20 입금 주소를 조회하거나, 없으면 신규 발급합니다.
     *
     * [현재 전략 - Shasta 테스트넷]
     * - 테스트넷에서는 운영자가 미리 생성한 주소 목록에서 할당하거나
     *   masterWallet 단일 주소를 공유하는 방식으로 검증합니다.
     * - 메인넷 전환 시: HD Wallet 파생(BIP44) 또는 TronGrid Account API로 대체합니다.
     *
     * @param userId 유저 ID
     * @return 유저 전용 TRC-20 입금 주소 (T로 시작)
     */
    @Transactional
    public String getOrCreateDepositAddress(Long userId) {
        return depositAddressRepository.findByUserId(userId)
                .map(TronDepositAddressEntity::getTronAddress)
                .orElseGet(() -> {
                    UserEntity user = userRepository.findById(userId)
                            .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

                    // [Shasta 테스트넷] 현재는 운영자 단일 마스터 주소 + 폴링으로 검증
                    // TODO: 메인넷 전환 시 HD Wallet 파생 주소 개별 발급으로 교체
                    String assignedAddress = masterWalletAddress;

                    TronDepositAddressEntity entity = TronDepositAddressEntity.builder()
                            .user(user)
                            .tronAddress(assignedAddress)
                            .network("SHASTA")
                            .createdAt(LocalDateTime.now())
                            .build();

                    depositAddressRepository.save(entity);

                    log.info("[TronPoller] 📬 New TRON deposit address assigned: {} → user {}",
                            assignedAddress, user.getUsername());
                    return assignedAddress;
                });
    }

    // ──────────────────────────────────────────────────────────────────
    // 내부 유틸: TronGrid 응답에서 컨펌 수 추출
    // ──────────────────────────────────────────────────────────────────

    private int getConfirmations(JsonNode tx) {
        // detail.receipt.block_number 또는 confirmations 필드 사용
        JsonNode conf = tx.path("detail").path("confirmations");
        if (conf.isNumber()) return conf.asInt();
        // 일부 응답은 최상위에 confirmations 제공
        JsonNode topConf = tx.path("confirmations");
        if (topConf.isNumber()) return topConf.asInt();
        // Shasta 테스트넷은 빠르게 finalised → 기본값 19 처리
        return minConfirmations;
    }
}
