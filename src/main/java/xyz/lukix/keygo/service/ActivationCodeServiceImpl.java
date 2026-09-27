package xyz.lukix.keygo.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import xyz.lukix.keygo.common.HashUtil;
import xyz.lukix.keygo.entity.ActivationCode;
import xyz.lukix.keygo.repository.ActivationCodeRepository;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ActivationCodeServiceImpl implements ActivationCodeService {

    /** 明文码最大长度（与列宽一致，导入时超长行跳过） */
    private static final int MAX_CODE_LENGTH = 48;
    /** 设备 ID 最大存储长度 */
    private static final int MAX_DEVICE_ID_LENGTH = 100;

    /** 统计缓存：列表/申领页共享同一份聚合结果，TTL 兜底 + 一切改变计数的写操作主动失效 */
    static final String STATS_CACHE_KEY = "keygo:cache:stats";
    private static final Duration STATS_CACHE_TTL = Duration.ofSeconds(30);
    private static final TypeReference<Stats> STATS_CACHE_TYPE = new TypeReference<>() {
    };

    private final ActivationCodeRepository activationCodeRepository;
    private final ActivationLogService activationLogService;
    private final RedisJsonCache redisJsonCache;

    @Override
    @Transactional
    public RedeemResult redeem(String rawCode, String deviceId, String clientIp) {
        String normalized = rawCode == null ? "" : rawCode.trim().toUpperCase(Locale.ROOT);
        String device = deviceId == null ? "" : deviceId.trim();
        if (normalized.isEmpty() || device.isEmpty()) {
            String inputHash = normalized.isEmpty() ? null : HashUtil.sha256Hex(normalized);
            activationLogService.recordRedeem(inputHash, device, clientIp, "INVALID");
            return new RedeemResult(RedeemStatus.INVALID, null);
        }
        String codeHash = HashUtil.sha256Hex(normalized);
        // hash 优先命中新码；未命中再按明文降级匹配存量老码
        List<ActivationCode> locked = activationCodeRepository.findUnusedByCodeHashForUpdate(codeHash);
        if (locked.isEmpty()) {
            locked = activationCodeRepository.findUnusedByCodeForUpdate(normalized);
        }
        if (locked.isEmpty()) {
            boolean exists = activationCodeRepository.findByCodeHash(codeHash).isPresent()
                    || activationCodeRepository.findByCode(normalized).isPresent();
            activationLogService.recordRedeem(codeHash, device, clientIp, exists ? "ALREADY_USED" : "INVALID");
            return new RedeemResult(exists ? RedeemStatus.ALREADY_USED : RedeemStatus.INVALID, null);
        }
        ActivationCode code = locked.getFirst();
        LocalDateTime now = LocalDateTime.now();
        if (code.getExpireTime() != null && code.getExpireTime().isBefore(now)) {
            activationLogService.recordRedeem(codeHash, device, clientIp, "EXPIRED");
            return new RedeemResult(RedeemStatus.EXPIRED, code.getId());
        }
        code.setUsed(true);
        // fetch_time 与现有"已使用"列表排序/统计口径共用，核销同样记为最后使用时间
        code.setFetchTime(now);
        code.setBindTime(now);
        code.setDeviceId(device.length() > MAX_DEVICE_ID_LENGTH ? device.substring(0, MAX_DEVICE_ID_LENGTH) : device);
        activationCodeRepository.save(code);
        redisJsonCache.evict(STATS_CACHE_KEY);
        activationLogService.recordRedeem(codeHash, device, clientIp, "SUCCESS");
        return new RedeemResult(RedeemStatus.SUCCESS, code.getId());
    }

    @Override
    @Transactional
    public Optional<ActivationCode> claimCode(String username, String remark) {
        List<ActivationCode> codes = activationCodeRepository.findUnusedWithLock(PageRequest.of(0, 1));
        if (codes.isEmpty()) {
            return Optional.empty();
        }
        ActivationCode code = codes.getFirst();
        code.setUsed(true);
        code.setFetchTime(LocalDateTime.now());
        code.setFetchUser(username);
        // 备注仅在传入非空时覆盖，缺省/空白保持原值
        if (remark != null && !remark.isBlank()) {
            String trimmed = remark.trim();
            code.setRemark(trimmed.length() > ActivationCode.MAX_REMARK_LENGTH
                    ? trimmed.substring(0, ActivationCode.MAX_REMARK_LENGTH) : trimmed);
        }
        redisJsonCache.evict(STATS_CACHE_KEY);
        return Optional.of(activationCodeRepository.save(code));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ActivationCode> listAll() {
        return activationCodeRepository.findAllByOrderByIdAsc();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ActivationCode> listByUsed(boolean used) {
        return activationCodeRepository.findByUsedOrderByCreateTimeDesc(used);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ActivationCode> pageQuery(String status, String keyword, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page - 1, 0), Math.min(Math.max(size, 1), 100));
        if (keyword != null && !keyword.isBlank()) {
            return activationCodeRepository.findByCodeContainingIgnoreCaseOrderByIdAsc(keyword.trim(), pageable);
        }
        if ("used".equals(status)) {
            return activationCodeRepository.findByUsedOrderByFetchTimeDesc(true, pageable);
        }
        if ("unused".equals(status)) {
            return activationCodeRepository.findByUsedOrderByIdAsc(false, pageable);
        }
        return activationCodeRepository.findAllByOrderByUsedAscFetchTimeDescIdAsc(pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public Stats getStats() {
        return redisJsonCache.getOrLoad(STATS_CACHE_KEY, STATS_CACHE_TTL, STATS_CACHE_TYPE, this::loadStatsFromDb);
    }

    private Stats loadStatsFromDb() {
        ActivationCodeRepository.StatsView view = activationCodeRepository.fetchStats();
        long used = view.getUsed();
        return new Stats(view.getTotal(), view.getTotal() - used, used);
    }

    @Override
    @Transactional
    public boolean markAsUsed(Long id) {
        Optional<ActivationCode> found = activationCodeRepository.findById(id);
        if (found.isEmpty()) {
            return false;
        }
        ActivationCode code = found.get();
        code.setUsed(true);
        code.setFetchTime(LocalDateTime.now());
        activationCodeRepository.save(code);
        redisJsonCache.evict(STATS_CACHE_KEY);
        return true;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ActivationCode> findById(Long id) {
        return activationCodeRepository.findById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ActivationCode> searchByCode(String keyword) {
        return activationCodeRepository.findByCodeContainingIgnoreCaseOrderByCreateTimeDesc(keyword);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ActivationCode> pageMyCodes(String username, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page - 1, 0), Math.min(Math.max(size, 1), 100));
        return activationCodeRepository.findByFetchUserOrderByFetchTimeDesc(username, pageable);
    }

    @Override
    @Transactional
    public void updateRemark(Long id, String remark) {
        activationCodeRepository.findById(id).ifPresent(code -> {
            code.setRemark(remark);
            activationCodeRepository.save(code);
        });
    }

    @Override
    @Transactional
    public ImportResult batchImportCodes(List<String[]> rows) {
        // 规范化 + 文件内去重（同一 code 多次出现仅导入第一条）；
        // 超长行计入 skipped，避免单条坏数据触发列宽约束导致整个导入事务失败
        Map<String, String> candidates = new LinkedHashMap<>();
        int totalRows = 0;
        for (String[] r : rows) {
            if (r == null || r.length == 0 || r[0] == null || r[0].isBlank()) {
                continue;
            }
            totalRows++;
            String code = r[0].trim().toUpperCase(Locale.ROOT);
            if (code.length() > MAX_CODE_LENGTH) {
                continue;
            }
            String remark = null;
            if (r.length > 1 && r[1] != null && !r[1].isBlank()) {
                String trimmed = r[1].trim();
                remark = trimmed.length() > ActivationCode.MAX_REMARK_LENGTH
                        ? trimmed.substring(0, ActivationCode.MAX_REMARK_LENGTH) : trimmed;
            }
            candidates.putIfAbsent(code, remark);
        }
        // 库内已存在的 code 直接跳过，避免唯一约束冲突导致整个导入事务失败
        Set<String> existing = new HashSet<>(
                activationCodeRepository.findByCodeIn(candidates.keySet()).stream()
                        .map(ActivationCode::getCode)
                        .toList());
        List<ActivationCode> entities = candidates.entrySet().stream()
                .filter(e -> !existing.contains(e.getKey()))
                .map(e -> {
                    ActivationCode a = new ActivationCode();
                    a.setCode(e.getKey());
                    a.setUsed(false);
                    a.setRemark(e.getValue());
                    return a;
                })
                .toList();
        activationCodeRepository.saveAll(entities);
        redisJsonCache.evict(STATS_CACHE_KEY);
        // skipped 含义：有效行中未实际导入的数量（文件内重复 + 库内已存在）
        return new ImportResult(entities.size(), totalRows - entities.size());
    }

    @Override
    @Transactional
    public int batchMarkAsUsed(List<Long> ids) {
        // 单条 UPDATE ... WHERE id IN，避免逐条循环
        int updated = activationCodeRepository.markUsedIn(ids, LocalDateTime.now());
        redisJsonCache.evict(STATS_CACHE_KEY);
        return updated;
    }

    @Override
    @Transactional
    public boolean markAsUnused(Long id) {
        Optional<ActivationCode> found = activationCodeRepository.findById(id);
        if (found.isEmpty()) {
            return false;
        }
        ActivationCode code = found.get();
        // 恢复为未使用需清空获取人与设备绑定，否则残留旧领取记录、"我的获取记录"仍可见
        code.setUsed(false);
        code.setFetchTime(null);
        code.setFetchUser(null);
        code.setDeviceId(null);
        code.setBindTime(null);
        activationCodeRepository.save(code);
        redisJsonCache.evict(STATS_CACHE_KEY);
        return true;
    }

    @Override
    @Transactional
    public int batchMarkAsUnused(List<Long> ids) {
        // 单条 UPDATE ... WHERE id IN，避免逐条循环
        int updated = activationCodeRepository.markUnusedIn(ids);
        redisJsonCache.evict(STATS_CACHE_KEY);
        return updated;
    }

    @Override
    @Transactional
    public List<ActivationCode> batchClaim(int count, String username) {
        // 一次锁定 count 条（单轮悲观锁代替逐条加锁），不足时按实际锁定数量发放
        List<ActivationCode> locked = activationCodeRepository.findUnusedWithLock(PageRequest.of(0, count));
        LocalDateTime now = LocalDateTime.now();
        for (ActivationCode code : locked) {
            code.setUsed(true);
            code.setFetchTime(now);
            code.setFetchUser(username);
        }
        List<ActivationCode> saved = activationCodeRepository.saveAll(locked);
        redisJsonCache.evict(STATS_CACHE_KEY);
        return saved;
    }

    @Override
    @Transactional
    public boolean deleteCode(Long id) {
        if (!activationCodeRepository.existsById(id)) {
            return false;
        }
        activationCodeRepository.deleteById(id);
        redisJsonCache.evict(STATS_CACHE_KEY);
        return true;
    }

    @Override
    @Transactional
    public int batchDeleteCodes(List<Long> ids) {
        // 单条 DELETE ... WHERE id IN，避免逐条循环
        int deleted = activationCodeRepository.deleteIn(ids);
        redisJsonCache.evict(STATS_CACHE_KEY);
        return deleted;
    }
}
