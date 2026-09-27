package xyz.lukix.keygo.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.lukix.keygo.entity.ActivationLog;
import xyz.lukix.keygo.repository.ActivationLogRepository;

import java.util.Locale;
import java.util.Set;

/**
 * 核销审计日志服务：异步线程写入，不占用核销请求的响应时间，
 * 也保证核销主事务回滚时审计线索不丢失
 */
@Service
@RequiredArgsConstructor
public class ActivationLogService {

    /** 合法的审计结果取值（查询过滤用，非法值按无过滤处理） */
    private static final Set<String> VALID_RESULTS = Set.of("SUCCESS", "INVALID", "ALREADY_USED", "EXPIRED");

    private final ActivationLogRepository activationLogRepository;

    /** result 取值：SUCCESS/INVALID/ALREADY_USED/EXPIRED */
    @Async("auditExecutor")
    public void recordRedeem(String codeHash, String deviceId, String ip, String result) {
        ActivationLog entry = new ActivationLog();
        entry.setCodeHash(codeHash);
        entry.setDeviceId(deviceId);
        entry.setIp(ip);
        entry.setResult(result);
        activationLogRepository.save(entry);
    }

    /**
     * 分页查询核销审计（时间倒序），page 从 1 开始；result 非法或为空时不过滤
     */
    @Transactional(readOnly = true)
    public Page<ActivationLog> pageLogs(int page, int size, String result) {
        Pageable pageable = PageRequest.of(Math.max(page - 1, 0), Math.min(Math.max(size, 1), 100));
        if (result != null && VALID_RESULTS.contains(result.toUpperCase(Locale.ROOT))) {
            return activationLogRepository.findByResultOrderByCreateTimeDesc(result.toUpperCase(Locale.ROOT), pageable);
        }
        return activationLogRepository.findAllByOrderByCreateTimeDesc(pageable);
    }
}
