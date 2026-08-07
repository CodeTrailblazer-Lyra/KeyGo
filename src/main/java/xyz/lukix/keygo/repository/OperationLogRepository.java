package xyz.lukix.keygo.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import xyz.lukix.keygo.entity.OperationLog;

public interface OperationLogRepository extends JpaRepository<OperationLog, Long> {

    /** 按时间倒序分页查询所有日志 */
    Page<OperationLog> findAllByOrderByCreateTimeDesc(Pageable pageable);
}
