package xyz.lukix.keygo.dto;

/**
 * 导入结果响应
 *
 * @param count   实际导入数
 * @param skipped 跳过数（重复/已存在）
 * @param stats   统计信息
 */
public record ImportResponse(
        int count,
        int skipped,
        StatsResponse stats
) {
}
