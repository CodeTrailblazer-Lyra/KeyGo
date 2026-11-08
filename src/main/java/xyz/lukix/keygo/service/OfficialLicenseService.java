package xyz.lukix.keygo.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import xyz.lukix.keygo.common.AppConstants;
import xyz.lukix.keygo.entity.ActivationCode;
import xyz.lukix.keygo.repository.ActivationCodeRepository;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 官方激活码联网验证服务（统信 UOS 授权开放接口）。
 * <p>
 * 本系统的 {@code used} 字段只反映"是否在本平台申领/核销"，无法得知该码在
 * 统信官方侧的真实激活情况。本服务按码明文调用官方 legalquery 接口，把官方的
 * 使用状态、激活时间、产品名与激活设备回写到 {@code activation_code} 表，
 * 供管理端比对是否存在"本地未使用但官方已激活"等异常。
 * <p>
 * 当检测到官方已激活但本地仍标记为未使用时，自动将本地状态同步为已使用，
 * 确保数据一致性（适用于线下发放后补录等场景）。
 * <p>
 * 设计约束：
 * <ul>
 *   <li><b>尽力而作</b>：网络异常/官方不可达只记日志并保留上次结果，绝不影响主流程；</li>
 *   <li><b>串行 + 间隔</b>：批量验证在专用单线程池串行执行并按 {@code interval-ms} 休眠，
 *       避免高频外呼被官方限流或封禁；</li>
 *   <li><b>不重叠</b>：{@code running} 标志用 CAS 抢占，定时任务与手动全量验证互斥。</li>
 * </ul>
 */
@Slf4j
@Service
public class OfficialLicenseService {

    /** 官方顶层 code=0 表示查询成功 */
    private static final int OFFICIAL_OK_CODE = 0;
    /** 官方 use_status=1 表示该码已被使用 */
    private static final int OFFICIAL_USED_STATUS = 1;
    /**
     * 官方以 9999999999（约公元 2286 年）表示"无限期"的哨兵值，
     * 超过此界限的时间不是真实业务时间，按空处理避免界面显示荒谬年份
     */
    private static final long MAX_MEANINGFUL_EPOCH = 4102444800L; // 2100-01-01

    /** official_product 列长度上限（与 DDL 一致） */
    private static final int MAX_PRODUCT_LENGTH = 100;
    /** official_device 列长度上限（与 DDL 一致） */
    private static final int MAX_DEVICE_LENGTH = 200;
    /** 估算单次官方外呼耗时（含建连/等待响应/解析），仅用于任务耗时预告 */
    private static final long EXPECTED_REQUEST_MILLIS = 800L;

    private final ActivationCodeRepository codeRepository;
    private final ObjectMapper objectMapper;
    private final RedisJsonCache redisJsonCache;
    private final RestClient restClient;
    private final boolean enabled;
    private final String licenseUrl;
    private final long intervalMs;

    /** 批次任务运行标志：定时同步与手动全量验证互斥 */
    private final AtomicBoolean running = new AtomicBoolean(false);

    public OfficialLicenseService(ActivationCodeRepository codeRepository,
                                  ObjectMapper objectMapper,
                                  RedisJsonCache redisJsonCache,
                                  @Value("${keygo.license.enabled:true}") boolean enabled,
                                  @Value("${keygo.license.url:https://license.chinauos.com/v1/openapi/legalquery}") String licenseUrl,
                                  @Value("${keygo.license.interval-ms:300}") long intervalMs,
                                  @Value("${keygo.license.timeout-seconds:10}") long timeoutSeconds) {
        this.codeRepository = codeRepository;
        this.objectMapper = objectMapper;
        this.redisJsonCache = redisJsonCache;
        this.enabled = enabled;
        this.licenseUrl = licenseUrl;
        this.intervalMs = Math.max(0, intervalMs);
        this.restClient = RestClient.builder()
                .requestFactory(requestFactory(timeoutSeconds))
                .build();
    }

    private static SimpleClientHttpRequestFactory requestFactory(long timeoutSeconds) {
        int millis = (int) Duration.ofSeconds(Math.max(1, timeoutSeconds)).toMillis();
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(millis);
        factory.setReadTimeout(millis);
        return factory;
    }

    // ==================== 对外能力 ====================

    /** 功能总开关（关闭时接口层直接返回未启用，不发起外呼） */
    public boolean isEnabled() {
        return enabled;
    }

    /** 当前是否有批次验证进行中 */
    public boolean isRunning() {
        return running.get();
    }

    /**
     * 抢占批次任务执行权。
     *
     * @return true 表示已抢到（调用方必须随后调用 {@link #runCheckAsync(boolean)} 或 {@link #release()}）
     */
    public boolean tryBegin() {
        return enabled && running.compareAndSet(false, true);
    }

    /** 释放批次任务执行权 */
    public void release() {
        running.set(false);
    }

    /**
     * 估算验证 {@code total} 个码的耗时（秒）：请求间隔 + 单次外呼经验耗时。
     * 仅用于给操作者一个心理预期，不参与业务判断。
     */
    public long estimateSeconds(long total) {
        return (long) Math.ceil(total * (intervalMs + EXPECTED_REQUEST_MILLIS) / 1000D);
    }

    /**
     * 异步执行一批官方验证（由控制层在 {@link #tryBegin()} 成功后跨 Bean 调用，确保走代理生效）。
     *
     * @param includeUnused true=全量（含未申领的码），false=仅已申领的码
     */
    @Async("licenseCheckExecutor")
    public void runCheckAsync(boolean includeUnused) {
        try {
            doCheck(includeUnused);
        } finally {
            release();
        }
    }

    /**
     * 启动时执行一次官方验证：检查所有码（含未申领的），发现官方已激活但本地未使用的码时自动同步。
     * <p>
     * 若发现官方已激活但本地未使用的码，自动同步本地状态为已使用。
     */
    public void syncUsedCodesOnStartup() {
        if (!tryBegin()) {
            log.debug("官方激活码启动同步跳过（功能未启用或已有验证任务在跑）");
            return;
        }
        try {
            doCheck(true);  // true=全量检查，确保能发现并修复不一致状态
        } finally {
            release();
        }
    }

    /**
     * 单个激活码的验证结果。
     *
     * @param found   激活码是否存在
     * @param checked 本次是否成功拿到官方响应（false 时库中仍为上一次结果）
     * @param code    最新记录；{@code found=false} 时为 null
     */
    public record SingleCheckResult(boolean found, boolean checked, ActivationCode code) {
    }

    /**
     * 单个激活码立即验证（同步阻塞，管理端“验证”按钮使用）。
     */
    public SingleCheckResult checkById(Long id) {
        Optional<ActivationCode> found = codeRepository.findById(id);
        if (found.isEmpty()) {
            return new SingleCheckResult(false, false, null);
        }
        ActivationCode code = found.get();
        return new SingleCheckResult(true, queryAndSave(code).queried(), code);
    }

    // ==================== 内部实现 ====================

    /**
     * 遍历待验证的码，逐个查询官方并回写结果。
     */
    private void doCheck(boolean includeUnused) {
        List<ActivationCode> targets = includeUnused
                ? codeRepository.findAllByOrderByIdAsc()
                : codeRepository.findByUsedTrueOrderByIdAsc();
        if (targets.isEmpty()) {
            log.info("官方激活码验证：无待验证记录（范围={}）", includeUnused ? "全量" : "已申领");
            return;
        }

        long start = System.currentTimeMillis();
        int ok = 0;
        int failed = 0;
        int synced = 0;
        log.info("官方激活码验证开始：范围={}，共 {} 个，间隔 {}ms",
                includeUnused ? "全量" : "已申领", targets.size(), intervalMs);

        for (ActivationCode code : targets) {
            if (Thread.currentThread().isInterrupted()) {
                log.warn("官方激活码验证被中断，已完成 {}/{}", ok + failed, targets.size());
                break;
            }
            CheckOutcome outcome = queryAndSave(code);
            if (outcome.queried()) {
                ok++;
                if (outcome.synced()) {
                    synced++;
                }
            } else {
                failed++;
            }
            if (!sleepBetweenRequests()) {
                log.warn("官方激活码验证提前结束，已完成 {}/{}", ok + failed, targets.size());
                break;
            }
        }

        log.info("官方激活码验证完成：成功 {}，失败 {}，自动同步为已使用 {}，耗时 {}ms",
                ok, failed, synced, System.currentTimeMillis() - start);
        if (synced > 0) {
            redisJsonCache.evict(AppConstants.STATS_CACHE_KEY);
            log.info("已自动将 {} 个官方已激活但本地未使用的码同步为已使用状态", synced);
        }
    }

    /**
     * 单码验证内部结果。
     *
     * @param queried 是否成功拿到并解析官方响应（false 时未改库）
     * @param synced  是否自动将本地状态从"未使用"同步为"已使用"（官方已激活但本地未使用时发生）
     */
    private record CheckOutcome(boolean queried, boolean synced) {
    }

    /**
     * 请求官方接口并回写结果；任何异常都不外抛，仅记日志（保留该码上一次的验证结果）。
     * <p>
     * 若官方显示已激活但本地仍为未使用，自动同步本地状态为已使用（含 fetchTime）。
     */
    private CheckOutcome queryAndSave(ActivationCode code) {
        try {
            String body = restClient.get()
                    .uri(UriComponentsBuilder.fromUriString(licenseUrl)
                            .queryParam("_allow_anonymous", "true")
                            .queryParam("code", code.getCode())
                            .build()
                            .encode()
                            .toUri())
                    .retrieve()
                    .body(String.class);

            OfficialResponse resp = parse(body);
            applyResponse(code, resp);

            // 检测并修复不一致：官方已激活但本地未使用
            boolean synced = false;
            if (!Boolean.TRUE.equals(code.getUsed())
                    && resp.useStatus() != null && resp.useStatus() == OFFICIAL_USED_STATUS) {
                code.setUsed(true);
                LocalDateTime officialTime = toLocalDateTime(resp.useTimeEpoch());
                code.setFetchTime(officialTime != null ? officialTime : LocalDateTime.now());
                synced = true;
                log.info("自动同步：激活码 {}（id={}）官方已激活，本地状态已更新为已使用",
                        code.getCode(), code.getId());
            }

            codeRepository.save(code);
            return new CheckOutcome(true, synced);
        } catch (Exception e) {
            log.warn("官方激活码验证失败，id={}：{}", code.getId(), e.toString());
            return new CheckOutcome(false, false);
        }
    }

    /** 官方响应解析结果（record 仅在本类内部流转） */
    private record OfficialResponse(int topCode, Integer useStatus, Long useTimeEpoch,
                                    String product, String device) {
    }

    /**
     * 解析官方 JSON：顶层 {@code code} 为 0 时 {@code row} 内才是有效业务数据。
     *
     * @throws IllegalArgumentException     响应为空或缺少顶层 code 字段
     * @throws JsonProcessingException 响应不是合法 JSON
     */
    private OfficialResponse parse(String body) throws JsonProcessingException {
        if (body == null || body.isBlank()) {
            throw new IllegalArgumentException("官方接口返回空响应");
        }
        JsonNode root = objectMapper.readTree(body);
        int topCode = root.path("code").asInt(Integer.MIN_VALUE);
        if (topCode == Integer.MIN_VALUE) {
            throw new IllegalArgumentException("官方接口响应缺少 code 字段");
        }
        JsonNode row = root.path("row");
        if (row.isMissingNode() || row.isNull()) {
            return new OfficialResponse(topCode, null, null, null, null);
        }
        return new OfficialResponse(
                topCode,
                row.path("use_status").isNumber() ? row.path("use_status").asInt() : null,
                row.path("use_time").isNumber() ? row.path("use_time").asLong() : null,
                text(row.path("name_cn")),
                joinNonBlank(text(row.path("manu_name")), text(row.path("manu_device_name"))));
    }

    /**
     * 把官方响应写入实体的待持久化字段。
     * <p>
     * 查询失败（官方顶层 code 非 0，通常是码不存在）时清空状态字段但保留 checked_at，
     * 使管理端能区分"从未验证"与"验证过、官方不认这个码"。
     */
    private void applyResponse(ActivationCode code, OfficialResponse resp) {
        code.setOfficialCheckedAt(LocalDateTime.now());
        code.setOfficialCode(resp.topCode());

        if (resp.topCode() != OFFICIAL_OK_CODE) {
            code.setOfficialUseStatus(null);
            code.setOfficialUseTime(null);
            code.setOfficialProduct(null);
            code.setOfficialDevice(null);
            return;
        }
        code.setOfficialUseStatus(resp.useStatus());
        code.setOfficialUseTime(toLocalDateTime(resp.useTimeEpoch()));
        code.setOfficialProduct(truncate(resp.product(), MAX_PRODUCT_LENGTH));
        code.setOfficialDevice(truncate(resp.device(), MAX_DEVICE_LENGTH));
    }

    /** epoch 秒转本地时间；0/负值与"无限期"哨兵值按空处理 */
    private static LocalDateTime toLocalDateTime(Long epochSeconds) {
        if (epochSeconds == null || epochSeconds <= 0 || epochSeconds > MAX_MEANINGFUL_EPOCH) {
            return null;
        }
        return LocalDateTime.ofInstant(Instant.ofEpochSecond(epochSeconds), ZoneId.systemDefault());
    }

    private static String text(JsonNode node) {
        if (node == null || !node.isTextual()) {
            return null;
        }
        String value = node.asText().trim();
        return value.isEmpty() ? null : value;
    }

    /** 拼接厂商与设备名，任一为空时只取另一个 */
    private static String joinNonBlank(String first, String second) {
        if (first == null) {
            return second;
        }
        return second == null ? first : first + " / " + second;
    }

    private static String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    /**
     * 批次请求间隔休眠。
     *
     * @return false 表示线程被中断（已恢复中断标志，调用方应停止本轮验证）
     */
    private boolean sleepBetweenRequests() {
        if (intervalMs <= 0) {
            return true;
        }
        try {
            Thread.sleep(intervalMs);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
