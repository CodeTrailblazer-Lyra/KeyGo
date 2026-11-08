package xyz.lukix.keygo.dto;

import java.util.List;

/**
 * 批量申领结果响应
 *
 * @param count 申领数量
 * @param codes 激活码明文列表
 * @param stats 统计信息
 */
public record BatchClaimResponse(
        int count,
        List<String> codes,
        StatsResponse stats
) {
}
