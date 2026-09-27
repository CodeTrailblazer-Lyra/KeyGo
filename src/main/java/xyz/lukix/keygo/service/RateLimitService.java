package xyz.lukix.keygo.service;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 基于内存固定窗口的简易限流器（单实例部署场景）
 * <p>
 * 用法：{@code rateLimitService.tryAcquire("claim:" + username, 5, Duration.ofMinutes(1))}
 * <p>
 * 内存回收三层机制，避免 IP 等无界维度的 key 持续累积导致内存耗尽：
 * <ul>
 *   <li>惰性清理：正常每 {@link #CLEANUP_INTERVAL_MILLIS} 随请求触发一次过期窗口回收；</li>
 *   <li>加急清理：条目数超过 {@link #MAX_WINDOWS}（如 IP 打散式突发）时，
 *       清理间隔缩短为 {@link #FAST_CLEANUP_INTERVAL_MILLIS}，防止膨胀；</li>
 *   <li>定时清扫：低流量时段惰性清理不触发时，由周期任务兜底回收残留窗口。</li>
 * </ul>
 * 注意：多实例部署时需替换为 Redis 等分布式限流方案。
 */
@Slf4j
@Service
public class RateLimitService {

    /** 过期窗口清理的最小触发间隔（正常态） */
    static final long CLEANUP_INTERVAL_MILLIS = 60_000L;

    /** 条目数超过上限时的加急清理间隔，CAS 节流保证突发下也不会每次请求都全表扫描 */
    static final long FAST_CLEANUP_INTERVAL_MILLIS = 5_000L;

    /** windows 条目数软上限：达到后即进入加急清理模式，在突破前先回收 */
    static final int MAX_WINDOWS = 10_000;

    /** 定时清扫周期：空闲时段兜底回收 */
    static final long SWEEP_INTERVAL_MILLIS = 5 * 60_000L;

    /** key -> 窗口状态 */
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    /** 上次清理时间戳（毫秒） */
    private final AtomicLong lastCleanupTime = new AtomicLong(0);

    private final long cleanupIntervalMillis;
    private final long fastCleanupIntervalMillis;

    public RateLimitService() {
        this(CLEANUP_INTERVAL_MILLIS, FAST_CLEANUP_INTERVAL_MILLIS);
    }

    /** 测试可注入更短的清理间隔以验证加急路径 */
    RateLimitService(long cleanupIntervalMillis, long fastCleanupIntervalMillis) {
        this.cleanupIntervalMillis = cleanupIntervalMillis;
        this.fastCleanupIntervalMillis = fastCleanupIntervalMillis;
    }

    /**
     * 尝试获取一个限流配额
     *
     * @param key    限流维度键（如 "claim:username"、"register:ip"）
     * @param limit  窗口内允许的最大次数
     * @param window 窗口时长
     * @return true=放行，false=超过限制
     */
    public boolean tryAcquire(String key, int limit, Duration window) {
        if (limit <= 0) {
            return false;
        }
        long now = System.currentTimeMillis();
        maybeEvictExpired(now);
        long windowStart = now - window.toMillis();

        Window w = windows.compute(key, (k, cur) -> {
            if (cur == null || cur.windowStart < windowStart) {
                return new Window(now, window.toMillis());
            }
            cur.count.incrementAndGet();
            return cur;
        });
        return w.count.get() <= limit;
    }

    /**
     * 距上次清理超过当前模式对应的间隔时触发一次过期窗口回收；
     * 条目数超过 {@link #MAX_WINDOWS} 时切换到加急间隔
     */
    private void maybeEvictExpired(long now) {
        boolean overCapacity = windows.size() >= MAX_WINDOWS;
        long interval = overCapacity ? fastCleanupIntervalMillis : cleanupIntervalMillis;
        long last = lastCleanupTime.get();
        if (now - last >= interval && lastCleanupTime.compareAndSet(last, now)) {
            if (overCapacity) {
                log.info("限流窗口数 {} 超过上限 {}，执行加急过期清理", windows.size(), MAX_WINDOWS);
            }
            evictExpired(now);
        }
    }

    /**
     * 定时清扫：流量稀疏时段惰性清理长期不触发，由此兜底回收已过期的残留窗口
     */
    @Scheduled(fixedDelay = SWEEP_INTERVAL_MILLIS, initialDelay = SWEEP_INTERVAL_MILLIS)
    public void scheduledSweep() {
        evictExpired(System.currentTimeMillis());
    }

    /**
     * 移除窗口起点已超出自身时长的条目（不再被访问的 key 不会永久驻留）
     *
     * @return 被清理的条目数
     */
    int evictExpired(long now) {
        int before = windows.size();
        windows.entrySet().removeIf(e -> now - e.getValue().windowStart > e.getValue().windowMillis);
        return before - windows.size();
    }

    /** 当前跟踪的窗口数（监控/测试用） */
    int trackedWindows() {
        return windows.size();
    }

    /**
     * 提取客户端真实 IP（优先 X-Forwarded-For 首项，兼容反向代理）
     */
    public String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            String first = (comma > 0 ? forwarded.substring(0, comma) : forwarded).trim();
            if (!first.isEmpty()) {
                return first;
            }
        }
        return request.getRemoteAddr();
    }

    /** 固定窗口状态：窗口起点 + 窗口时长 + 窗口内请求计数 */
    private static final class Window {
        final long windowStart;
        final long windowMillis;
        final AtomicInteger count;

        Window(long windowStart, long windowMillis) {
            this.windowStart = windowStart;
            this.windowMillis = windowMillis;
            this.count = new AtomicInteger(1);
        }
    }
}
