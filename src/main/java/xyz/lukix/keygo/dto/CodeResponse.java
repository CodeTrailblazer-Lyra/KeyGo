package xyz.lukix.keygo.dto;

import xyz.lukix.keygo.entity.ActivationCode;

import java.time.LocalDateTime;

/**
 * 激活码信息响应
 *
 * @param id         激活码ID
 * @param code       激活码明文
 * @param status     状态（已使用/未使用）
 * @param remark     备注
 * @param fetchUser  获取人
 * @param fetchTime  获取时间
 * @param createTime 创建时间
 * @param official   官方联网验证状态；<b>仅管理端视图返回</b>，用户侧视图恒为 null
 */
public record CodeResponse(
        Long id,
        String code,
        String status,
        String remark,
        String fetchUser,
        LocalDateTime fetchTime,
        LocalDateTime createTime,
        OfficialStatusResponse official
) {
    public static final String STATUS_USED = "已使用";
    public static final String STATUS_UNUSED = "未使用";

    /**
     * 通用视图（不含官方验证状态）。
     * <p>
     * 默认走这个方法，新增面向普通用户的接口即便复用本 DTO 也不会泄露官方同步数据。
     */
    public static CodeResponse from(ActivationCode entity) {
        return of(entity, null);
    }

    /**
     * 管理端视图（附带官方联网验证状态）
     */
    public static CodeResponse adminFrom(ActivationCode entity) {
        return of(entity, OfficialStatusResponse.from(entity));
    }

    private static CodeResponse of(ActivationCode entity, OfficialStatusResponse official) {
        return new CodeResponse(
                entity.getId(),
                entity.getCode(),
                Boolean.TRUE.equals(entity.getUsed()) ? STATUS_USED : STATUS_UNUSED,
                entity.getRemark(),
                entity.getFetchUser(),
                entity.getFetchTime(),
                entity.getCreateTime(),
                official
        );
    }
}
