package com.hhplus.be.product.service.dto;

import lombok.Builder;

import java.util.List;

/**
 * 실시간 랭킹 조회 결과
 *
 * Redis Sorted Set에서 조회한 상품 ID + 판매량과
 * DB에서 조회한 상품 상세 정보를 결합한 결과
 */
@Builder
public record RankingResult(
        List<RankingItem> rankings  // 랭킹 목록 (순위 포함)
) {
    /**
     * 랭킹 항목 (상품 정보 + 판매량)
     */
    @Builder
    public record RankingItem(
            int rank,              // 순위 (1위부터)
            Long productId,        // 상품 ID
            String productName,    // 상품명
            int price,             // 가격
            int salesCount         // 판매량 (Redis score)
    ) {}
}