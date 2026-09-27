package xyz.lukix.keygo.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import xyz.lukix.keygo.entity.ActivationLog;

public interface ActivationLogRepository extends JpaRepository<ActivationLog, Long> {

    /** 分页查询核销审计（时间倒序） */
    Page<ActivationLog> findAllByOrderByCreateTimeDesc(Pageable pageable);

    /** 分页查询指定结果的核销审计（时间倒序） */
    Page<ActivationLog> findByResultOrderByCreateTimeDesc(String result, Pageable pageable);
}
