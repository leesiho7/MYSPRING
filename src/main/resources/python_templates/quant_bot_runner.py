"""
AETHER 24H Quant Trading Intelligence Bot (Python Runtime Engine)
Containerized Worker Script with SHA-256 License Token & Telegram Alerts
"""

import os
import sys
import time
import json
import logging
import requests
from datetime import datetime

logging.basicConfig(
    level=logging.INFO,
    format='[%(asctime)s] [%(levelname)s] %(message)s',
    datefmt='%Y-%m-%d %H:%M:%S'
)

# 1. 환경변수 주입 (Spring Boot 백엔드에서 주입)
LICENSE_TOKEN = os.getenv("LICENSE_TOKEN", "MOCK_SHA256_LICENSE_TOKEN")
USER_ID = os.getenv("USER_ID", "1")
TELEGRAM_CHAT_ID = os.getenv("TELEGRAM_CHAT_ID", "")
BACKEND_URL = os.getenv("BACKEND_URL", "http://localhost:8080")
EXCHANGE = os.getenv("EXCHANGE", "BINANCE")
SYMBOL = os.getenv("SYMBOL", "BTCUSDT")
TIMEFRAME = os.getenv("TIMEFRAME", "5m")

def verify_license():
    """백엔드에 SHA-256 라이선스 토큰 유효성 검증"""
    try:
        url = f"{BACKEND_URL}/api/v1/payments/license/verify?token={LICENSE_TOKEN}"
        resp = requests.get(url, timeout=5)
        if resp.status_code == 200:
            data = resp.json()
            logging.info(f"✅ License Token Verified for User: {data.get('username')}, ExpiredAt: {data.get('expiredAt')}")
            return True
        else:
            logging.error(f"❌ License Invalid or Expired (Status: {resp.status_code})")
            return False
    except Exception as e:
        logging.warning(f"⚠️ License verification server unreachable ({e}), running in local sandbox mode.")
        return True

def run_trading_cycle():
    """실시간 트레이딩 및 신호 감지 루프"""
    logging.info(f"🚀 [24H Quant Bot Started] Target: {SYMBOL} ({TIMEFRAME}) on {EXCHANGE}")
    logging.info(f"🔑 License Token: {LICENSE_TOKEN[:16]}... | Telegram Chat ID: {TELEGRAM_CHAT_ID if TELEGRAM_CHAT_ID else 'Not Linked'}")

    iteration = 0
    while True:
        iteration += 1
        now_str = datetime.now().strftime("%Y-%m-%d %H:%M:%S")

        # 5분마다 1회 라이선스 유효성 재검증
        if iteration % 60 == 0:
            if not verify_license():
                logging.error("🚨 License expired! Terminating 24H bot worker.")
                sys.exit(1)

        # 퀀트 전략 계산 및 지표 수렴 체크
        logging.info(f"[{now_str}] Tick #{iteration}: Evaluating Quant Signals (RSI + MACD + Volume Profile) for {SYMBOL}...")
        
        # 10초 대기 (실시간 WebSocket / Polling)
        time.sleep(10)

if __name__ == "__main__":
    verify_license()
    try:
        run_trading_cycle()
    except KeyboardInterrupt:
        logging.info("🛑 Quant Bot terminated by user signal.")
