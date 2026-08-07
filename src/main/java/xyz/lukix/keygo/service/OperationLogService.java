package xyz.lukix.keygo.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.lukix.keygo.entity.OperationLog;
import xyz.lukix.keygo.repository.OperationLogRepository;

/**
 * 操作日志服务
 */
@Service
@RequiredArgsConstructor
public class OperationLogService {

    private final OperationLogRepository operationLogRepository;

    /**
     * 记录一条操作日志
     */
    @Transactional
    public void record(String username, String action, String target, String detail) {
        OperationLog log = new OperationLog();
        log.setUsername(username);
        log.setAction(action);
        log.setTarget(target);
        log.setDetail(detail);
        operationLogRepository.save(log);
    }

    /**
     * 分页查询日志（按时间倒序），page 从 1 开始
     */
    @Transactional(readOnly = true)
    public Page<OperationLog> pageLogs(int page, int size) {
        return operationLogRepository.findAllByOrderByCreateTimeDesc(
                PageRequest.of(Math.max(page - 1, 0), Math.min(Math.max(size, 1), 100)));
    }
}
