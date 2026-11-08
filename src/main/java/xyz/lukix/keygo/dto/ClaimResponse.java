package xyz.lukix.keygo.dto;

import java.time.LocalDateTime;

/**
 * 申领结果响应
 *
 * @param code      激活码明文
 * @param fetchTime 获取时间
 * @param stats     统计信息
 */
public record ClaimResponse(
        String code,
        LocalDateTime fetchTime,
        StatsResponse stats
) {
}
