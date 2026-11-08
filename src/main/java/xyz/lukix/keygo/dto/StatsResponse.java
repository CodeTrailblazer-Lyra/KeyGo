package xyz.lukix.keygo.dto;

/**
 * 统计信息响应
 *
 * @param total     总数
 * @param available 可用数
 * @param used      已使用数
 */
public record StatsResponse(
        long total,
        long available,
        long used
) {
}
