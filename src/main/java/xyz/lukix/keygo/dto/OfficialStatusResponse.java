package xyz.lukix.keygo.dto;

import xyz.lukix.keygo.entity.ActivationCode;

import java.time.LocalDateTime;

/**
 * 官方（统信 UOS）联网验证状态，<b>仅管理端可见</b>。
 * <p>
 * 与本平台自身的 {@code status}（是否已申领/核销）相互独立：本字段反映该码在
 * 官方授权系统里的真实激活情况，两者不一致即提示可能存在线下激活、重复使用等异常。
 *
 * @param useStatus     官方 use_status 原值（1=已使用）；null 表示未取到有效状态
 * @param useStatusText 面向界面的展示文案（已使用/未使用/无效码/查询异常/未验证）
 * @param useTime       官方记录的激活时间
 * @param product       官方产品名称
 * @param device        官方记录的激活设备
 * @param officialCode  官方接口顶层 code（0=查询成功，非 0=码无效或官方侧异常）
 * @param checkedAt     最后一次成功验证时间；null 表示从未验证
 */
public record OfficialStatusResponse(
        Integer useStatus,
        String useStatusText,
        LocalDateTime useTime,
        String product,
        String device,
        Integer officialCode,
        LocalDateTime checkedAt
) {

    /** 官方接口查询成功的顶层码 */
    private static final int OFFICIAL_OK_CODE = 0;
    /** 官方 use_status 表示已使用的取值 */
    private static final int OFFICIAL_USED_STATUS = 1;

    private static final String TEXT_USED = "已使用";
    private static final String TEXT_UNUSED = "未使用";
    private static final String TEXT_INVALID = "无效码";
    private static final String TEXT_ERROR = "查询异常";
    private static final String TEXT_NEVER = "未验证";

    /**
     * 由激活码实体构建官方状态视图。
     * <p>
     * 从未验证过的码也返回非 null 对象（文案为"未验证"），
     * 前端加列时无需判空即可直接渲染 {@code useStatusText}。
     */
    public static OfficialStatusResponse from(ActivationCode entity) {
        Integer useStatus = entity.getOfficialUseStatus();
        Integer officialCode = entity.getOfficialCode();
        LocalDateTime checkedAt = entity.getOfficialCheckedAt();
        return new OfficialStatusResponse(
                useStatus,
                describe(useStatus, officialCode, checkedAt),
                entity.getOfficialUseTime(),
                entity.getOfficialProduct(),
                entity.getOfficialDevice(),
                officialCode,
                checkedAt
        );
    }

    /**
     * 状态文案推导：未验证 &gt; 官方不认此码 &gt; 取不到状态 &gt; 已使用/未使用
     */
    private static String describe(Integer useStatus, Integer officialCode, LocalDateTime checkedAt) {
        if (checkedAt == null) {
            return TEXT_NEVER;
        }
        if (officialCode != null && officialCode != OFFICIAL_OK_CODE) {
            return TEXT_INVALID;
        }
        if (useStatus == null) {
            return TEXT_ERROR;
        }
        return useStatus == OFFICIAL_USED_STATUS ? TEXT_USED : TEXT_UNUSED;
    }
}
