package xyz.lukix.keygo.dto;

/**
 * 批量操作结果响应
 *
 * @param count 操作数量
 * @param stats 统计信息（可选）
 */
public record BatchOperationResponse(
        int count,
        StatsResponse stats
) {
    /**
     * 构建不含统计信息的响应
     */
    public static BatchOperationResponse of(int count) {
        return new BatchOperationResponse(count, null);
    }

    /**
     * 构建含统计信息的响应
     */
    public static BatchOperationResponse of(int count, StatsResponse stats) {
        return new BatchOperationResponse(count, stats);
    }
}
