package xyz.lukix.keygo.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.springframework.beans.factory.annotation.Value;

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

    @CreationTimestamp
    private LocalDateTime createTime;
}