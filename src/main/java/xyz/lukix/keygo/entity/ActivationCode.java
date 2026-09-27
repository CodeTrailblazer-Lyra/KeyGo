package xyz.lukix.keygo.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "activation_code", indexes = {
        // 状态筛选/统计走 used；"我的获取记录"走 fetch_user；列表排序走 fetch_time
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

    /** 激活码明文（新码不再写入，仅存 codeHash；存量数据保留明文用于降级匹配核销） */
    @Column(unique = true, length = 48)
    private String code;

    /**
     * 明文的 SHA-256 十六进制（64 位）。
     * 唯一性由 SQL 侧唯一索引 uk_code_hash 保证（多行 NULL 互不冲突）；
     * 哈希仅用于核销比对，无展示价值，不随任何接口返回
     */
    @JsonIgnore
    @Column(length = 64)
    private String codeHash;

    @Column(nullable = false)
    private Boolean used = false;

    private LocalDateTime fetchTime;

    /** 获取人用户名（未获取时为 null） */
    @Column(length = 50)
    private String fetchUser;

    /** 核销绑定的设备标识（未核销时为 null） */
    @Column(length = 100)
    private String deviceId;

    /** 核销绑定时间 */
    private LocalDateTime bindTime;

    /** 过期时间（null=永不过期，兼容存量数据） */
    private LocalDateTime expireTime;

    /** 备注（来源/批次/用途） */
    @Column(length = MAX_REMARK_LENGTH)
    private String remark;

    @CreationTimestamp
    private LocalDateTime createTime;
}