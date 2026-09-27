package xyz.lukix.keygo.dto;

/**
 * 批量申领请求体（count 缺省时按 1 处理）
 */
public record BatchClaimRequest(Integer count) {
}
