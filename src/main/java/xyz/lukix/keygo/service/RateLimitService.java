package xyz.lukix.keygo.service;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 基于内存固定窗口的简易限流器（单实例部署场景）
 * <p>
 * 用法：{@code rateLimitService.tryAcquire("claim:" + username, 5, Duration.ofMinutes(1))}
 * <p>
 * 注意：多实例部署时需替换为 Redis 等分布式限流方案。
 */
@Service
public class RateLimitService {

    /** key -> 窗口状态 */
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

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
        long windowStart = now - window.toMillis();

        Window w = windows.compute(key, (k, cur) -> {
            if (cur == null || cur.count == null || cur.windowStart < windowStart) {
                return new Window(now);
            }
            cur.count.incrementAndGet();
            return cur;
        });
        return w.count.get() <= limit;
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

    /** 固定窗口状态：窗口起点 + 窗口内请求计数 */
    private static final class Window {
        final long windowStart;
        final AtomicInteger count;

        Window(long windowStart) {
            this.windowStart = windowStart;
            this.count = new AtomicInteger(1);
        }
    }
}
