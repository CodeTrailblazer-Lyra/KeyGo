package xyz.lukix.keygo.repository;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.domain.Pageable;
import xyz.lukix.keygo.entity.ActivationCode;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.util.List;
import java.util.Optional;

public interface ActivationCodeRepository extends JpaRepository<ActivationCode, Long> {

    /**
     * 获取一个未使用的激活码（按ID升序，确保FIFO）
     */
    Optional<ActivationCode> findFirstByUsedFalseOrderByIdAsc();

    /**
     * 悲观锁方式获取一个未使用的激活码（防止并发抢码）
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000"))
    @Query("SELECT a FROM ActivationCode a WHERE a.used = false ORDER BY a.id ASC")
    List<ActivationCode> findFirstUnusedWithLock(Pageable pageable);

    /**
     * 按状态查询激活码列表（按创建时间降序）
     */
    List<ActivationCode> findByUsedOrderByCreateTimeDesc(boolean used);

    /**
     * 查询所有激活码（按创建时间降序）
     */
    List<ActivationCode> findAllByOrderByCreateTimeDesc();

    /**
     * 统计未使用的激活码数量
     */
    long countByUsedFalse();

    /**
     * 统计已使用的激活码数量
     */
    long countByUsedTrue();

    /**
     * 按激活码代码模糊搜索
     */
    List<ActivationCode> findByCodeContainingIgnoreCaseOrderByCreateTimeDesc(String keyword);
}
