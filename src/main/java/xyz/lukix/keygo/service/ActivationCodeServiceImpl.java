package xyz.lukix.keygo.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import xyz.lukix.keygo.common.AppConstants;
import xyz.lukix.keygo.entity.ActivationCode;
import xyz.lukix.keygo.repository.ActivationCodeRepository;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ActivationCodeServiceImpl implements ActivationCodeService {

    /** 统计缓存：列表/申领页共享同一份聚合结果，TTL 兜底 + 一切改变计数的写操作主动失效 */
    private static final TypeReference<Stats> STATS_CACHE_TYPE = new TypeReference<>() {
    };

    private final ActivationCodeRepository activationCodeRepository;
    private final ActivationLogService activationLogService;
    private final RedisJsonCache redisJsonCache;

    @Override
    @Transactional
    public RedeemResult redeem(String inputCode, String deviceId, String clientIp) {
        String code = inputCode == null ? "" : inputCode.trim();
        String device = deviceId == null ? "" : deviceId.trim();
        if (code.isEmpty() || device.isEmpty()) {
            activationLogService.recordRedeem(code.isEmpty() ? null : code, device, clientIp, "INVALID");
            return new RedeemResult(RedeemStatus.INVALID, null);
        }
        // 按明文精确匹配
        List<ActivationCode> locked = activationCodeRepository.findUnusedByCodeForUpdate(code);
        if (locked.isEmpty()) {
            boolean exists = activationCodeRepository.findByCode(code).isPresent();
            activationLogService.recordRedeem(code, device, clientIp, exists ? "ALREADY_USED" : "INVALID");
            return new RedeemResult(exists ? RedeemStatus.ALREADY_USED : RedeemStatus.INVALID, null);
        }
        ActivationCode entity = locked.getFirst();
        LocalDateTime now = LocalDateTime.now();
        if (entity.getExpireTime() != null && entity.getExpireTime().isBefore(now)) {
            activationLogService.recordRedeem(code, device, clientIp, "EXPIRED");
            return new RedeemResult(RedeemStatus.EXPIRED, entity.getId());
        }
        entity.setUsed(true);
        entity.setFetchTime(now);
        entity.setDeviceId(device.substring(0, Math.clamp(device.length(), 0, AppConstants.MAX_DEVICE_ID_LENGTH)));
        activationCodeRepository.save(entity);
        redisJsonCache.evict(AppConstants.STATS_CACHE_KEY);
        activationLogService.recordRedeem(code, device, clientIp, "SUCCESS");
        return new RedeemResult(RedeemStatus.SUCCESS, entity.getId());
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
        if (remark != null && !remark.isBlank()) {
            String trimmed = remark.trim();
            code.setRemark(trimmed.substring(0, Math.clamp(trimmed.length(), 0, ActivationCode.MAX_REMARK_LENGTH)));
        }
        redisJsonCache.evict(AppConstants.STATS_CACHE_KEY);
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
        Pageable pageable = PageRequest.of(Math.max(page - 1, 0), Math.clamp(size, 1, AppConstants.MAX_PAGE_SIZE));
        if (keyword != null && !keyword.isBlank()) {
            return activationCodeRepository.findByRemarkContainingIgnoreCaseOrderByIdAsc(keyword.trim(), pageable);
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
        return redisJsonCache.getOrLoad(AppConstants.STATS_CACHE_KEY, AppConstants.STATS_CACHE_TTL, STATS_CACHE_TYPE, this::loadStatsFromDb);
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
        redisJsonCache.evict(AppConstants.STATS_CACHE_KEY);
        return true;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ActivationCode> findById(Long id) {
        return activationCodeRepository.findById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ActivationCode> searchByRemark(String keyword) {
        return activationCodeRepository.findByRemarkContainingIgnoreCaseOrderByCreateTimeDesc(keyword);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ActivationCode> pageMyCodes(String username, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page - 1, 0), Math.clamp(size, 1, AppConstants.MAX_PAGE_SIZE));
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
        // 4 列格式：{code, used, fetchUser, remark}；后三项可选
        // 文件内去重（同一明文多次出现仅导入第一条）；超长行跳过
        record ImportCandidate(String code, boolean used, String fetchUser, String remark) {}
        Map<String, ImportCandidate> candidates = new LinkedHashMap<>();
        int totalRows = 0;
        for (String[] r : rows) {
            if (r == null || r.length == 0 || r[0] == null || r[0].isBlank()) {
                continue;
            }
            totalRows++;
            String code = r[0].trim();
            if (code.length() > AppConstants.MAX_CODE_LENGTH) {
                continue;
            }
            if (candidates.containsKey(code)) {
                continue;
            }
            boolean used = r.length > 1 && parseUsed(r[1]);
            String fetchUser = null;
            if (r.length > 2 && r[2] != null && !r[2].isBlank()) {
                fetchUser = r[2].trim().substring(0, Math.clamp(r[2].trim().length(), 0, 50));
            }
            String remark = null;
            if (r.length > 3 && r[3] != null && !r[3].isBlank()) {
                String trimmed = r[3].trim();
                remark = trimmed.substring(0, Math.clamp(trimmed.length(), 0, ActivationCode.MAX_REMARK_LENGTH));
            }
            candidates.put(code, new ImportCandidate(code, used, fetchUser, remark));
        }
        // 库内已存在的直接跳过，避免唯一约束冲突导致整个导入事务失败
        Set<String> existing = new HashSet<>(
                activationCodeRepository.findByCodeIn(candidates.keySet()).stream()
                        .map(ActivationCode::getCode)
                        .toList());
        List<ActivationCode> entities = candidates.values().stream()
                .filter(c -> !existing.contains(c.code()))
                .map(c -> {
                    ActivationCode a = new ActivationCode();
                    a.setCode(c.code());
                    a.setUsed(c.used());
                    a.setFetchUser(c.fetchUser());
                    if (c.used()) {
                        a.setFetchTime(LocalDateTime.now());
                    }
                    a.setRemark(c.remark());
                    return a;
                })
                .toList();
        activationCodeRepository.saveAll(entities);
        redisJsonCache.evict(AppConstants.STATS_CACHE_KEY);
        return new ImportResult(entities.size(), totalRows - entities.size());
    }

    /**
     * 解析"是否使用"字段，支持中英文常见表达
     */
    private boolean parseUsed(String value) {
        if (value == null) return false;
        String v = value.trim().toLowerCase(Locale.ROOT);
        return "是".equals(v) || "yes".equals(v) || "true".equals(v) || "1".equals(v) || "已使用".equals(v);
    }

    @Override
    @Transactional
    public int batchMarkAsUsed(List<Long> ids) {
        int updated = activationCodeRepository.markUsedIn(ids, LocalDateTime.now());
        redisJsonCache.evict(AppConstants.STATS_CACHE_KEY);
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
        code.setUsed(false);
        code.setFetchTime(null);
        code.setFetchUser(null);
        code.setDeviceId(null);
        activationCodeRepository.save(code);
        redisJsonCache.evict(AppConstants.STATS_CACHE_KEY);
        return true;
    }

    @Override
    @Transactional
    public int batchMarkAsUnused(List<Long> ids) {
        int updated = activationCodeRepository.markUnusedIn(ids);
        redisJsonCache.evict(AppConstants.STATS_CACHE_KEY);
        return updated;
    }

    @Override
    @Transactional
    public List<ActivationCode> batchClaim(int count, String username) {
        List<ActivationCode> locked = activationCodeRepository.findUnusedWithLock(PageRequest.of(0, count));
        LocalDateTime now = LocalDateTime.now();
        for (ActivationCode code : locked) {
            code.setUsed(true);
            code.setFetchTime(now);
            code.setFetchUser(username);
        }
        List<ActivationCode> saved = activationCodeRepository.saveAll(locked);
        redisJsonCache.evict(AppConstants.STATS_CACHE_KEY);
        return saved;
    }

    @Override
    @Transactional
    public boolean deleteCode(Long id) {
        if (!activationCodeRepository.existsById(id)) {
            return false;
        }
        activationCodeRepository.deleteById(id);
        redisJsonCache.evict(AppConstants.STATS_CACHE_KEY);
        return true;
    }

    @Override
    @Transactional
    public int batchDeleteCodes(List<Long> ids) {
        int deleted = activationCodeRepository.deleteIn(ids);
        redisJsonCache.evict(AppConstants.STATS_CACHE_KEY);
        return deleted;
    }
}
