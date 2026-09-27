package xyz.lukix.keygo.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 核销审计日志：异步线程写入，仅记录哈希不记录明文，用于安全回溯
 */
@Data
@Entity
@Table(name = "activation_log", indexes = {
        @Index(name = "idx_activation_log_create_time", columnList = "create_time")
})
public class ActivationLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 被核销激活码的 SHA-256 哈希（无法定位到具体码时为输入明文的哈希，仅排查用） */
    @Column(length = 64)
    private String codeHash;

    /** 发起核销的设备标识 */
    @Column(length = 100)
    private String deviceId;

    /** 客户端 IP（IPv6 最长 45 字符） */
    @Column(length = 45)
    private String ip;

    /** 核销结果：SUCCESS/INVALID/ALREADY_USED/EXPIRED */
    @Column(nullable = false, length = 32)
    private String result;

    @CreationTimestamp
    private LocalDateTime createTime;
}
