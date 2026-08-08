package xyz.lukix.keygo.repository;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;
import xyz.lukix.keygo.entity.ActivationCode;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.time.LocalDateTime;
import java.util.Collection;
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
     * 查询所有激活码（按ID升序，匹配插入顺序）
     */
    List<ActivationCode> findAllByOrderByIdAsc();

    /**
     * 分页查询所有激活码（未使用在前按ID升序，已使用在后按最后使用时间倒序）
     */
    Page<ActivationCode> findAllByOrderByUsedAscFetchTimeDescIdAsc(Pageable pageable);

    /**
     * 分页按状态查询激活码（未使用按ID升序）
     */
    Page<ActivationCode> findByUsedOrderByIdAsc(boolean used, Pageable pageable);

    /**
     * 分页按状态查询激活码（已使用按最后使用时间倒序）
     */
    Page<ActivationCode> findByUsedOrderByFetchTimeDesc(boolean used, Pageable pageable);

    /**
     * 分页按激活码代码模糊搜索（按ID升序）
     */
    Page<ActivationCode> findByCodeContainingIgnoreCaseOrderByIdAsc(String keyword, Pageable pageable);

    /**
     * 统计未使用的激活码数量
     */
    long countByUsedFalse();

    /**
     * 按获取人分页查询激活码（按获取时间倒序）
     */
    Page<ActivationCode> findByFetchUserOrderByFetchTimeDesc(String fetchUser, Pageable pageable);

    /**
     * 批量标记为已使用（单条 UPDATE ... WHERE id IN，事务内执行）
     */
    @Modifying
    @Query("UPDATE ActivationCode a SET a.used = true, a.fetchTime = :now WHERE a.id IN :ids")
    int markUsedIn(@Param("ids") Collection<Long> ids, @Param("now") LocalDateTime now);

    /**
     * 批量标记为未使用（单条 UPDATE ... WHERE id IN，事务内执行）
     */
    @Modifying
    @Query("UPDATE ActivationCode a SET a.used = false, a.fetchTime = null WHERE a.id IN :ids")
    int markUnusedIn(@Param("ids") Collection<Long> ids);

    /**
     * 批量删除（单条 DELETE ... WHERE id IN，事务内执行）
     */
    @Modifying
    @Query("DELETE FROM ActivationCode a WHERE a.id IN :ids")
    int deleteIn(@Param("ids") Collection<Long> ids);

    /**
     * 统计已使用的激活码数量
     */
    long countByUsedTrue();

    /**
     * 按激活码代码模糊搜索
     */
    List<ActivationCode> findByCodeContainingIgnoreCaseOrderByCreateTimeDesc(String keyword);
}
