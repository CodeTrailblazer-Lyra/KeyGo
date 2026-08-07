package xyz.lukix.keygo.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 系统公告：管理员发布，所有用户登录后在获取页查看
 */
@Data
@Entity
@Table(name = "announcement")
public class Announcement {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 公告内容 */
    @Column(nullable = false, length = 500)
    private String content;

    /** 发布人用户名 */
    @Column(length = 50)
    private String publisher;

    /** 是否置顶 */
    @Column(nullable = false)
    private Boolean pinned = false;

    @CreationTimestamp
    private LocalDateTime createTime;
}
