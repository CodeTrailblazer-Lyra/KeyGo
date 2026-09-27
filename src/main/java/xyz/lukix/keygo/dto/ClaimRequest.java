package xyz.lukix.keygo.dto;

/**
 * 申领激活码请求体（unit 可缺省：非空时写入激活码备注）
 */
public record ClaimRequest(String unit) {
}
