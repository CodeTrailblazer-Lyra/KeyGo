package xyz.lukix.keygo.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 操作日志：记录关键业务操作（获取/标记/删除/导入/审核等），用于安全审计
 */
@Data
@Entity
@Table(name = "operation_log")
public class OperationLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 操作人用户名 */
    @Column(nullable = false, length = 50)
    private String username;

    /** 操作类型（CLAIM/MARK_USED/DELETE/IMPORT/APPROVE 等） */
    @Column(nullable = false, length = 32)
    private String action;

    /** 操作对象（激活码、用户等标识） */
    @Column(length = 100)
    private String target;

    /** 操作详情 */
    @Column(length = 500)
    private String detail;

    @CreationTimestamp
    private LocalDateTime createTime;
}
