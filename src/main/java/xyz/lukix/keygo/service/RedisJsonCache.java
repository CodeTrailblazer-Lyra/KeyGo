package xyz.lukix.keygo.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * Redis JSON 轻量缓存，定位是给 PostgreSQL 减压的旁路缓存而非强依赖：
 * <ul>
 *   <li>Redis 读写任何异常都降级为直接回源查询，不影响业务正确性；</li>
 *   <li>连续失败后进入冷却期（不再尝试连接），避免 Redis 整体不可用时每个请求白付一次连接超时；</li>
 *   <li>evict 延迟到事务提交后执行，防止"先删缓存、事务未提交时被读请求用旧值回填"。</li>
 * </ul>
 * 值序列化用 Jackson 3（Boot 4 默认，原生支持 java.time 与 record），不开多态类型。
 */
@Slf4j
@Component
public class RedisJsonCache {

    /** 连接失败后的冷却时长：期间所有操作直接回源，不触碰 Redis */
    private static final long COOLDOWN_MILLIS = 30_000L;

    private final StringRedisTemplate redisTemplate;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final boolean enabled;
    private final AtomicLong redisDownUntil = new AtomicLong();

    public RedisJsonCache(StringRedisTemplate redisTemplate,
                          @Value("${keygo.cache.enabled:true}") boolean enabled) {
        this.redisTemplate = redisTemplate;
        this.enabled = enabled;
    }

    /** 优先读缓存；未命中回源并在 TTL 内回填。loader 结果须为可 JSON 序列化的稳定结构 */
    public <T> T getOrLoad(String key, Duration ttl, TypeReference<T> type, Supplier<T> loader) {
        if (!enabled || inCooldown()) {
            return loader.get();
        }
        try {
            String cached = redisTemplate.opsForValue().get(key);
            if (cached != null) {
                return jsonMapper.readValue(cached, type);
            }
        } catch (Exception e) {
            markRedisDown(key, "读", e);
            return loader.get();
        }
        T value = loader.get();
        try {
            redisTemplate.opsForValue().set(key, jsonMapper.writeValueAsString(value), ttl);
        } catch (Exception e) {
            markRedisDown(key, "写", e);
        }
        return value;
    }

    /** 写操作后主动失效；处于事务中则推迟到提交后执行（回源方法均为只读，缓存一致性由下次读取重建保证） */
    public void evict(String... keys) {
        if (!enabled || inCooldown()) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    doEvict(keys);
                }
            });
        } else {
            doEvict(keys);
        }
    }

    private void doEvict(String[] keys) {
        if (inCooldown()) {
            return;
        }
        try {
            redisTemplate.delete(Arrays.asList(keys));
        } catch (Exception e) {
            markRedisDown(Arrays.toString(keys), "失效", e);
        }
    }

    private boolean inCooldown() {
        return System.currentTimeMillis() < redisDownUntil.get();
    }

    private void markRedisDown(String key, String op, Exception e) {
        redisDownUntil.set(System.currentTimeMillis() + COOLDOWN_MILLIS);
        log.warn("Redis 缓存{}失败，进入 {} 冷却（期间直接回源数据库）: key={}", op, COOLDOWN_MILLIS + "ms", key, e);
    }
}
