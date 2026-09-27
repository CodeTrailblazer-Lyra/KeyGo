package xyz.lukix.keygo.dto;

/**
 * 设备核销请求体（code 为激活码明文，deviceId 为设备标识）
 */
public record RedeemRequest(String code, String deviceId) {
}
