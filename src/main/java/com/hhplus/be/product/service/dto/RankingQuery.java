package com.hhplus.be.product.service.dto;

/**
 * 실시간 랭킹 조회 Query
 *
 * API: GET /api/v1/products/rankings?period=daily&limit=10
 */
public record RankingQuery(
        String period,  // 기간: "daily", "weekly", "all"
        int limit       // 조회 개수 (default: 5)
) {
    public RankingQuery {
        // 유효한 기간 값 검증
        if (!period.equals("daily") && !period.equals("weekly") && !period.equals("all")) {
            throw new IllegalArgumentException(
                    "Invalid period: " + period + ". Must be one of: daily, weekly, all"
            );
        }

        // limit 범위 검증 (1~100)
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException(
                    "Invalid limit: " + limit + ". Must be between 1 and 100"
            );
        }
    }
}