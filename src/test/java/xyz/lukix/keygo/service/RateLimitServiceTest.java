package xyz.lukix.keygo.service;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RateLimitServiceTest {

    @Test
    void allowsUpToLimitThenBlocks() {
        RateLimitService service = new RateLimitService();
        for (int i = 0; i < 5; i++) {
            assertTrue(service.tryAcquire("claim:user", 5, Duration.ofMinutes(1)), "第 " + (i + 1) + " 次应放行");
        }
        assertFalse(service.tryAcquire("claim:user", 5, Duration.ofMinutes(1)), "超出配额应拒绝");
    }

    @Test
    void zeroLimitAlwaysRejects() {
        RateLimitService service = new RateLimitService();
        assertFalse(service.tryAcquire("k", 0, Duration.ofMinutes(1)));
    }

    @Test
    void windowResetsAfterExpiry() throws InterruptedException {
        RateLimitService service = new RateLimitService();
        assertTrue(service.tryAcquire("k", 1, Duration.ofMillis(60)));
        assertFalse(service.tryAcquire("k", 1, Duration.ofMillis(60)));
        Thread.sleep(100);
        assertTrue(service.tryAcquire("k", 1, Duration.ofMillis(60)), "窗口过期后应重新放行");
    }

    @Test
    void evictExpiredRemovesOnlyStaleWindows() {
        RateLimitService service = new RateLimitService();
        service.tryAcquire("register:1.1.1.1", 10, Duration.ofMillis(30));
        service.tryAcquire("claim:bob", 5, Duration.ofMinutes(10));

        // 模拟 1 分钟后的清理：30ms 短窗口已过期被清理，10 分钟窗口仍有效被保留
        int removed = service.evictExpired(System.currentTimeMillis() + 60_000);
        assertEquals(1, removed, "仅过期窗口被移除，活跃窗口保留");

        // 被清理的 key 重新按新窗口计数（不残留旧计数）
        assertTrue(service.tryAcquire("register:1.1.1.1", 10, Duration.ofMillis(30)));
    }

    @Test
    void overCapacityTriggersFastEviction() throws InterruptedException {
        // fast 间隔设为 0：条目数达到上限后，下一次请求立即触发清理
        RateLimitService service = new RateLimitService(60_000, 0);
        // 种子长窗口 key：验证加急清理只回收过期条目，不误伤活跃窗口
        service.tryAcquire("keep", 3, Duration.ofMinutes(10));
        for (int i = 0; i < RateLimitService.MAX_WINDOWS - 1; i++) {
            service.tryAcquire("burst:" + i, 3, Duration.ofMillis(1));
        }
        assertEquals(RateLimitService.MAX_WINDOWS, service.trackedWindows());

        Thread.sleep(20); // 1ms 短窗口全部过期；正常态 60s 清理间隔未到
        service.tryAcquire("new", 3, Duration.ofMinutes(10));

        // 达到上限触发加急回收：只剩两个 10 分钟活跃窗口，突发残留不随请求持续累积
        assertEquals(2, service.trackedWindows(), "超上限时应回收全部过期窗口");
        assertTrue(service.tryAcquire("keep", 3, Duration.ofMinutes(10)));
    }
}
