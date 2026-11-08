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
     * 统计结果投影：总数与已使用数（单条聚合查询，可用数 = total - used）
     */
    interface StatsView {
        long getTotal();

        long getUsed();
    }

    /**
     * 悲观锁方式获取未使用的激活码（按 ID 升序保证 FIFO，取几条由 Pageable 的 size 决定）
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000"))
    @Query("SELECT a FROM ActivationCode a WHERE a.used = false ORDER BY a.id ASC")
    List<ActivationCode> findUnusedWithLock(Pageable pageable);

    /**
     * 核销悲观锁查询：按 code 精确匹配未使用的激活码。
     * PG 行锁下并发核销同一码时，后到事务会等锁并在提交后重新校验 used=false，天然防重复核销
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000"))
    @Query("SELECT a FROM ActivationCode a WHERE a.code = :code AND a.used = false")
    List<ActivationCode> findUnusedByCodeForUpdate(@Param("code") String code);
    
    /** 按 code 查询（不加锁，用于区分“不存在”与“已被核销”） */
    Optional<ActivationCode> findByCode(String code);

    /**
     * 按状态查询激活码列表（按创建时间降序）
     */
    List<ActivationCode> findByUsedOrderByCreateTimeDesc(boolean used);

    /**
     * 查询所有激活码（按 ID 升序，匹配插入顺序）
     */
    List<ActivationCode> findAllByOrderByIdAsc();

    /**
     * 查询全部已申领/已使用的激活码（官方状态每日定时同步的范围）
     */
    List<ActivationCode> findByUsedTrueOrderByIdAsc();

    /**
     * 分页查询所有激活码（未使用在前按 ID 升序，已使用在后按最后使用时间倒序）
     */
    Page<ActivationCode> findAllByOrderByUsedAscFetchTimeDescIdAsc(Pageable pageable);

    /**
     * 分页按状态查询激活码（未使用按 ID 升序）
     */
    Page<ActivationCode> findByUsedOrderByIdAsc(boolean used, Pageable pageable);

    /**
     * 分页按状态查询激活码（已使用按最后使用时间倒序）
     */
    Page<ActivationCode> findByUsedOrderByFetchTimeDesc(boolean used, Pageable pageable);

    /**
     * 按备注模糊搜索（管理端关键字搜索，替代原明文激活码搜索）
     */
    Page<ActivationCode> findByRemarkContainingIgnoreCaseOrderByIdAsc(String keyword, Pageable pageable);

    /**
     * 单次聚合统计（总数/已用数），替代三条 count 查询
     */
    @Query("SELECT COUNT(a.id) AS total, "
            + "COALESCE(SUM(CASE WHEN a.used = true THEN 1 ELSE 0 END), 0) AS used "
            + "FROM ActivationCode a")
    StatsView fetchStats();

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
     * 批量标记为未使用（单条 UPDATE ... WHERE id IN，事务内执行）。
     * 与单条恢复口径一致：同时清除获取人/设备绑定，避免恢复后仍出现在“我的获取记录”、残留旧设备信息
     */
    @Modifying
    @Query("UPDATE ActivationCode a SET a.used = false, a.fetchTime = null, a.fetchUser = null, "
            + "a.deviceId = null WHERE a.id IN :ids")
    int markUnusedIn(@Param("ids") Collection<Long> ids);

    /**
     * 批量删除（单条 DELETE ... WHERE id IN，事务内执行）
     */
    @Modifying
    @Query("DELETE FROM ActivationCode a WHERE a.id IN :ids")
    int deleteIn(@Param("ids") Collection<Long> ids);

    /**
     * 按备注模糊搜索（导出用，替代原明文激活码搜索）
     */
    List<ActivationCode> findByRemarkContainingIgnoreCaseOrderByCreateTimeDesc(String keyword);

    /**
     * 批量查询已存在的激活码记录（导入前去重，按 code 匹配）
     */
    List<ActivationCode> findByCodeIn(Collection<String> codes);
}
