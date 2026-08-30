package com.tem.spring.gamification.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthCall;
import org.web3j.protocol.core.methods.response.EthGetTransactionCount;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.http.HttpService;
import org.web3j.utils.Numeric;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;

/**
 * [방식 2] 자체 서버 핫월렛 온체인 전송 서비스 (Web3j + EVM 스마트 컨트랙트 직접 서명 및 송금)
 * Polygon(기본), BSC, Ethereum 네트워크의 USDT (ERC-20 / BEP-20) 트랜잭션을 직접 온체인 브로드캐스트합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class Web3EscrowTransferService {

    @Value("${escrow.wallet.private-key:}")
    private String configuredPrivateKey;

    @Value("${escrow.wallet.address:0xb0390a087488E304cA32996532Ab9f40028511fE}")
    private String defaultEscrowAddress;

    // Polygon Mainnet USDT: 0xc2132D05D31c914a87C6611C10748AEb04B58e8F (Decimals: 6)
    private static final String POLYGON_USDT = "0xc2132D05D31c914a87C6611C10748AEb04B58e8F";
    private static final String POLYGON_RPC = "https://polygon.llamarpc.com";

    // BSC Mainnet USDT: 0x55d398326f99059fF775485246999027B3197955 (Decimals: 18)
    private static final String BSC_USDT = "0x55d398326f99059fF775485246999027B3197955";
    private static final String BSC_RPC = "https://bsc-dataseed.binance.org";

    /**
     * 1. 온체인 ERC-20 USDT 직접 서명 및 전송 (Transfer)
     * 개인키가 등록되어 있으면 실제 블록체인 노드로 전송하고, 없으면 안전한 시뮬레이션 해시를 반환합니다.
     */
    public OnChainTransferResult sendOnChainTransfer(String toAddress, double amount, String network) {
        String net = network != null ? network.toLowerCase() : "polygon";
        String rpcUrl = "bsc".equals(net) ? BSC_RPC : POLYGON_RPC;
        String contractAddress = "bsc".equals(net) ? BSC_USDT : POLYGON_USDT;
        int decimals = "bsc".equals(net) ? 18 : 6;

        log.info("[Web3Escrow] Initiating On-Chain Transfer: Network={}, Amount={} USDT, To={}", net, amount, toAddress);

        String privateKey = configuredPrivateKey != null ? configuredPrivateKey.trim() : "";
        if (privateKey.startsWith("0x")) {
            privateKey = privateKey.substring(2);
        }

        // 개인키가 없거나 mock인 경우 안전한 시뮬레이션 모드로 동작
        if (privateKey.isBlank() || "mock".equalsIgnoreCase(privateKey) || privateKey.length() < 64) {
            String simTxHash = "0x" + UUID.randomUUID().toString().replace("-", "") + "WEB3_ONCHAIN";
            log.info("[Web3Escrow] Running in Simulation Mode (No private key set). Generated Tx: {}", simTxHash);
            return OnChainTransferResult.builder()
                    .success(true)
                    .isLiveOnChain(false)
                    .txHash(simTxHash)
                    .network(net.toUpperCase())
                    .amount(amount)
                    .destinationAddress(toAddress)
                    .explorerUrl("https://" + ("bsc".equals(net) ? "bscscan.com" : "polygonscan.com") + "/tx/" + simTxHash)
                    .message("모의 온체인 전송 완료 (실제 개인키 연결 시 즉시 리얼 블록체인 전송으로 전환됩니다)")
                    .build();
        }

        try {
            Web3j web3j = Web3j.build(new HttpService(rpcUrl));
            Credentials credentials = Credentials.create(privateKey);
            String fromAddress = credentials.getAddress();

            log.info("[Web3Escrow] Signing transaction with Escrow Hot-Wallet: {}", fromAddress);

            // 토큰 수량 변환 (Polygon USDT: 6자리, BSC USDT: 18자리)
            BigDecimal rawAmount = BigDecimal.valueOf(amount).multiply(BigDecimal.TEN.pow(decimals));
            BigInteger tokenAmount = rawAmount.toBigInteger();

            // ERC-20 transfer(address to, uint256 value) 함수 인코딩
            Function transferFunction = new Function(
                    "transfer",
                    Arrays.asList(new Address(toAddress), new Uint256(tokenAmount)),
                    Collections.singletonList(new TypeReference<Type>() {})
            );
            String encodedFunction = FunctionEncoder.encode(transferFunction);

            // Nonce 및 Gas 조회
            EthGetTransactionCount ethGetTransactionCount = web3j.ethGetTransactionCount(
                    fromAddress, DefaultBlockParameterName.LATEST).send();
            BigInteger nonce = ethGetTransactionCount.getTransactionCount();

            BigInteger gasPrice = web3j.ethGasPrice().send().getGasPrice();
            BigInteger gasLimit = BigInteger.valueOf(100000); // ERC-20 Transfer 기본 한도

            // 트랜잭션 서명 및 브로드캐스팅
            RawTransaction rawTransaction = RawTransaction.createTransaction(
                    nonce,
                    gasPrice,
                    gasLimit,
                    contractAddress,
                    BigInteger.ZERO,
                    encodedFunction
            );

            byte[] signedMessage = TransactionEncoder.signMessage(rawTransaction, credentials);
            String hexValue = Numeric.toHexString(signedMessage);

            EthSendTransaction ethSendTransaction = web3j.ethSendRawTransaction(hexValue).send();

            if (ethSendTransaction.hasError()) {
                log.error("[Web3Escrow] RPC Node Error: {}", ethSendTransaction.getError().getMessage());
                throw new RuntimeException("온체인 전송 노드 오류: " + ethSendTransaction.getError().getMessage());
            }

            String realTxHash = ethSendTransaction.getTransactionHash();
            log.info("[Web3Escrow] 🚀 REAL ON-CHAIN BROADCAST SUCCESS! TxHash: {}", realTxHash);

            String explorerBase = "bsc".equals(net) ? "https://bscscan.com/tx/" : "https://polygonscan.com/tx/";

            return OnChainTransferResult.builder()
                    .success(true)
                    .isLiveOnChain(true)
                    .txHash(realTxHash)
                    .network(net.toUpperCase())
                    .amount(amount)
                    .destinationAddress(toAddress)
                    .fromAddress(fromAddress)
                    .explorerUrl(explorerBase + realTxHash)
                    .message("블록체인 메인넷 온체인 트랜잭션 전송 완료")
                    .build();

        } catch (Exception e) {
            log.error("[Web3Escrow] Failed to send on-chain transfer", e);
            throw new RuntimeException("온체인 트랜잭션 전송 실패: " + e.getMessage(), e);
        }
    }

    /**
     * 2. 온체인 실시간 USDT 잔액 조회 (ERC-20 balanceOf)
     */
    public double getOnChainUsdtBalance(String walletAddress, String network) {
        if (walletAddress == null || walletAddress.isBlank()) {
            walletAddress = defaultEscrowAddress;
        }
        String net = network != null ? network.toLowerCase() : "polygon";
        String rpcUrl = "bsc".equals(net) ? BSC_RPC : POLYGON_RPC;
        String contractAddress = "bsc".equals(net) ? BSC_USDT : POLYGON_USDT;
        int decimals = "bsc".equals(net) ? 18 : 6;

        try {
            Web3j web3j = Web3j.build(new HttpService(rpcUrl));
            Function balanceOfFunction = new Function(
                    "balanceOf",
                    Collections.singletonList(new Address(walletAddress)),
                    Collections.singletonList(new TypeReference<Uint256>() {})
            );
            String encoded = FunctionEncoder.encode(balanceOfFunction);

            EthCall response = web3j.ethCall(
                    Transaction.createEthCallTransaction(walletAddress, contractAddress, encoded),
                    DefaultBlockParameterName.LATEST
            ).send();

            if (response.getValue() != null && !response.getValue().isBlank() && !"0x".equals(response.getValue())) {
                List<Type> results = FunctionReturnDecoder.decode(response.getValue(), balanceOfFunction.getOutputParameters());
                if (!results.isEmpty()) {
                    BigInteger balanceRaw = (BigInteger) results.get(0).getValue();
                    return new BigDecimal(balanceRaw).divide(BigDecimal.TEN.pow(decimals)).doubleValue();
                }
            }
            return 0.0;
        } catch (Exception e) {
            log.warn("[Web3Escrow] Failed to fetch on-chain balance for {}: {}", walletAddress, e.getMessage());
            return 0.0;
        }
    }

    @lombok.Getter
    @lombok.Setter
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    @lombok.Builder
    public static class OnChainTransferResult {
        private boolean success;
        private boolean isLiveOnChain;
        private String txHash;
        private String network;
        private double amount;
        private String destinationAddress;
        private String fromAddress;
        private String explorerUrl;
        private String message;
    }
}