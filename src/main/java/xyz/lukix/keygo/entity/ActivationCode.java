package xyz.lukix.keygo.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import xyz.lukix.keygo.common.AppConstants;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "activation_code", indexes = {
        @Index(name = "idx_activation_code_used", columnList = "used"),
        @Index(name = "idx_activation_code_fetch_user", columnList = "fetch_user"),
        @Index(name = "idx_activation_code_fetch_time", columnList = "fetch_time")
})
public class ActivationCode {

    /** 备注字段最大长度（与列定义一致，服务层/控制层统一引用） */
    public static final int MAX_REMARK_LENGTH = 200;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 明文激活码（导入时原样存储，前端展示与核销均使用此值）
     */
    @Column(name = "code", nullable = false, unique = true, length = AppConstants.MAX_CODE_LENGTH)
    private String code;

    @Column(nullable = false)
    private Boolean used = false;

    /** 领取/核销时间（未使用时为 null） */
    private LocalDateTime fetchTime;

    /** 获取人用户名（未获取时为 null） */
    @Column(length = 50)
    private String fetchUser;

    /** 核销绑定的设备标识（未核销时为 null） */
    @Column(length = 100)
    private String deviceId;

    /** 过期时间（null=永不过期） */
    private LocalDateTime expireTime;

    /** 备注（来源/批次/用途） */
    @Column(length = MAX_REMARK_LENGTH)
    private String remark;

    // ==================== 官方联网验证结果（统信 UOS licensequery） ====================

    /** 官方接口返回的 use_status（1=已使用）；null 表示从未验证过 */
    @Column(name = "official_use_status")
    private Integer officialUseStatus;

    /** 官方记录的激活/使用时间（由响应 use_time 的 epoch 秒转换） */
    @Column(name = "official_use_time")
    private LocalDateTime officialUseTime;

    /** 官方产品名称（响应 name_cn），可用于核对码与产品类型是否匹配 */
    @Column(name = "official_product", length = 100)
    private String officialProduct;

    /** 官方记录的激活设备（响应 manu_name + manu_device_name 拼接） */
    @Column(name = "official_device", length = 200)
    private String officialDevice;

    /** 官方接口顶层 code（0=查询成功，非 0=码无效或官方侧异常） */
    @Column(name = "official_code")
    private Integer officialCode;

    /** 本地最后一次成功向官方发起验证的时间 */
    @Column(name = "official_checked_at")
    private LocalDateTime officialCheckedAt;

    @CreationTimestamp
    private LocalDateTime createTime;
}
