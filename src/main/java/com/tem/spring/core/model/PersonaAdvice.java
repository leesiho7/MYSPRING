package com.tem.spring.core.model;

import lombok.Builder;
import lombok.Value;

/**
 * ChromaDB 페르소나 지식 베이스 검색을 통한 투자 대가들의 다각도 자문
 */
@Value
@Builder
public class PersonaAdvice {
    String warrenBuffett;   // 가치투자/펀더멘털 관점 (인내, 기업가치, 장기보유)
    String jimSimons;       // 퀀트/수학적 모멘텀 관점 (승률, 손익비, 통계적 우위, 칼손절)
    String rayDalio;        // 매크로/올웨더/리스크 패리티 관점 (금리, 유동성, 리스크 관리)
}
