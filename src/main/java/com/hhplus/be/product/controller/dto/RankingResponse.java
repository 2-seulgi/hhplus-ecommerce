package com.hhplus.be.product.controller.dto;

import com.hhplus.be.product.service.dto.RankingResult;
import lombok.Builder;

import java.util.List;

/**
 * 실시간 랭킹 조회 응답 DTO
 *
 * GET /api/v1/products/rankings?period=daily&limit=10
 */
@Builder
public record RankingResponse(
        String period,           // 조회 기간: daily, weekly, all
        int totalCount,          // 전체 랭킹 항목 수
        List<RankingItem> rankings  // 랭킹 목록
) {
    public static RankingResponse from(String period, RankingResult result) {
        List<RankingItem> items = result.rankings().stream()
                .map(RankingItem::from)
                .toList();

        return RankingResponse.builder()
                .period(period)
                .totalCount(items.size())
                .rankings(items)
                .build();
    }

    /**
     * 랭킹 항목 (상품 정보 포함)
     */
    @Builder
    public record RankingItem(
            int rank,              // 순위 (1위부터)
            Long productId,        // 상품 ID
            String productName,    // 상품명
            int price,             // 가격
            int salesCount         // 판매량 (Redis score)
    ) {
        public static RankingItem from(RankingResult.RankingItem item) {
            return RankingItem.builder()
                    .rank(item.rank())
                    .productId(item.productId())
                    .productName(item.productName())
                    .price(item.price())
                    .salesCount(item.salesCount())
                    .build();
        }
    }
}