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
     * 悲观锁方式获取未使用的激活码（按ID升序保证FIFO，取几条由 Pageable 的 size 决定）。
     * 仅限有明文的码参与申领：生成制新码明文不落库，申领后用户看不到码，故排除
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000"))
    @Query("SELECT a FROM ActivationCode a WHERE a.used = false AND a.code IS NOT NULL ORDER BY a.id ASC")
    List<ActivationCode> findUnusedWithLock(Pageable pageable);

    /**
     * 核销悲观锁查询：按 codeHash 精确匹配未使用的激活码（新码主路径）。
     * PG 行锁下并发核销同一码时，后到事务会等锁并在提交后重新校验 used=false，天然防重复核销
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000"))
    @Query("SELECT a FROM ActivationCode a WHERE a.codeHash = :codeHash AND a.used = false")
    List<ActivationCode> findUnusedByCodeHashForUpdate(@Param("codeHash") String codeHash);

    /**
     * 核销悲观锁降级查询：按明文 code 匹配（仅存量老数据，新码 code 为 NULL 不会命中）
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000"))
    @Query("SELECT a FROM ActivationCode a WHERE a.code = :code AND a.used = false")
    List<ActivationCode> findUnusedByCodeForUpdate(@Param("code") String code);

    /** 按 codeHash 查询（不加锁，用于区分"不存在"与"已被核销"） */
    Optional<ActivationCode> findByCodeHash(String codeHash);

    /** 按明文 code 查询（不加锁，存量数据） */
    Optional<ActivationCode> findByCode(String code);

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
     * 与单条恢复口径一致：同时清除获取人/设备绑定，避免恢复后仍出现在"我的获取记录"、残留旧设备信息
     */
    @Modifying
    @Query("UPDATE ActivationCode a SET a.used = false, a.fetchTime = null, a.fetchUser = null, "
            + "a.deviceId = null, a.bindTime = null WHERE a.id IN :ids")
    int markUnusedIn(@Param("ids") Collection<Long> ids);

    /**
     * 批量删除（单条 DELETE ... WHERE id IN，事务内执行）
     */
    @Modifying
    @Query("DELETE FROM ActivationCode a WHERE a.id IN :ids")
    int deleteIn(@Param("ids") Collection<Long> ids);

    /**
     * 按激活码代码模糊搜索
     */
    List<ActivationCode> findByCodeContainingIgnoreCaseOrderByCreateTimeDesc(String keyword);

    /**
     * 批量查询已存在的激活码记录（导入前去重）
     */
    List<ActivationCode> findByCodeIn(Collection<String> codes);
}
