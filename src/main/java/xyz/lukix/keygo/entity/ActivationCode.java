package xyz.lukix.keygo.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "activation_code")
public class ActivationCode {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 48)
    private String code;

    @Column(nullable = false)
    private Boolean used = false;

    private LocalDateTime fetchTime;

    /** 获取人用户名（未获取时为 null） */
    @Column(length = 50)
    private String fetchUser;

    /** 备注（来源/批次/用途） */
    @Column(length = 200)
    private String remark;

    @CreationTimestamp
    private LocalDateTime createTime;
}