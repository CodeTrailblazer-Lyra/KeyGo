package xyz.lukix.keygo.common;

import java.time.Duration;

/**
 * 应用常量配置
 */
public final class AppConstants {

    private AppConstants() {
        // 防止实例化
    }

    // ==================== 限流配置 ====================

    /**
     * 申领限流：每用户每分钟最大次数
     */
    public static final int CLAIM_LIMIT = 5;

    /**
     * 申领限流时间窗口
     */
    public static final Duration CLAIM_WINDOW = Duration.ofMinutes(1);

    /**
     * 核销限流：每 IP 每分钟最大次数
     */
    public static final int REDEEM_LIMIT = 15;

    /**
     * 注册限流：每 IP 每小时最大次数
     */
    public static final int REGISTER_LIMIT = 10;

    /**
     * 注册限流时间窗口
     */
    public static final Duration REGISTER_WINDOW = Duration.ofHours(1);

    // ==================== 批量操作配置 ====================

    /**
     * 单次批量操作允许的 ID 数量上限
     */
    public static final int MAX_BATCH_SIZE = 100;

    /**
     * 单次 Excel 导入允许的最大行数
     */
    public static final int MAX_IMPORT_ROWS = 5000;

    /**
     * 批量申领数量上限
     */
    public static final int MAX_BATCH_CLAIM_COUNT = 100;

    // ==================== 缓存配置 ====================

    /**
     * 统计缓存 Key
     */
    public static final String STATS_CACHE_KEY = "keygo:cache:stats";

    /**
     * 统计缓存 TTL
     */
    public static final Duration STATS_CACHE_TTL = Duration.ofSeconds(30);

    // ==================== 激活码配置 ====================

    /**
     * 明文激活码最大长度
     */
    public static final int MAX_CODE_LENGTH = 48;

    /**
     * 设备 ID 最大存储长度
     */
    public static final int MAX_DEVICE_ID_LENGTH = 100;

    /**
     * Excel 导出 SXSSFWorkbook 滑动窗口大小
     */
    public static final int SXSS_WINDOW_SIZE = 100;

    // ==================== 分页配置 ====================

    /**
     * 默认分页大小
     */
    public static final int DEFAULT_PAGE_SIZE = 20;

    /**
     * 最大分页大小
     */
    public static final int MAX_PAGE_SIZE = 100;

    // ==================== 响应消息 ====================

    /**
     * 对外统一的核销失败话术：不区分"不存在/已核销"，防止接口被用作码有效性探测
     */
    public static final String REDEEM_FAIL_MESSAGE = "激活码无效或已被核销";

    /**
     * 申领成功消息
     */
    public static final String CLAIM_SUCCESS_MESSAGE = "申领成功";

    /**
     * 核销成功消息
     */
    public static final String REDEEM_SUCCESS_MESSAGE = "核销成功";
}
