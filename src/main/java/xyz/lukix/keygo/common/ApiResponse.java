package xyz.lukix.keygo.common;

/**
 * 统一 API 响应包装
 *
 * @param code      业务状态码：0=成功，400=参数错误，401=未认证，403=无权限，404=不存在，409=冲突（如名称重复），429=限流，500=内部错误
 * @param message   提示信息
 * @param data      业务数据（可为 null）
 * @param timestamp 服务端响应时间戳（毫秒）
 */
public record ApiResponse<T>(int code, String message, T data, long timestamp) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(0, "ok", data, System.currentTimeMillis());
    }

    public static <T> ApiResponse<T> ok(String message, T data) {
        return new ApiResponse<>(0, message, data, System.currentTimeMillis());
    }

    public static <T> ApiResponse<T> error(int code, String message) {
        return new ApiResponse<>(code, message, null, System.currentTimeMillis());
    }

    public static <T> ApiResponse<T> error(int code, String message, T data) {
        return new ApiResponse<>(code, message, data, System.currentTimeMillis());
    }
}
