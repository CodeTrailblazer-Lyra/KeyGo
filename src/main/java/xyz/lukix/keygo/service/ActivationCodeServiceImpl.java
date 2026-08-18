package xyz.lukix.keygo.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.lukix.keygo.entity.ActivationCode;
import xyz.lukix.keygo.repository.ActivationCodeRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ActivationCodeServiceImpl implements ActivationCodeService {

    private final ActivationCodeRepository activationCodeRepository;

    @Override
    @Transactional
    public Optional<ActivationCode> claimCode(String username, String remark) {
        List<ActivationCode> codes = activationCodeRepository.findFirstUnusedWithLock(PageRequest.of(0, 1));
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
            code.setRemark(trimmed.length() > 200 ? trimmed.substring(0, 200) : trimmed);
        }
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
    public long countAvailable() {
        return activationCodeRepository.countByUsedFalse();
    }

    @Override
    @Transactional(readOnly = true)
    public long countUsed() {
        return activationCodeRepository.countByUsedTrue();
    }

    @Override
    @Transactional(readOnly = true)
    public long countTotal() {
        return activationCodeRepository.count();
    }

    @Override
    @Transactional
    public void markAsUsed(Long id) {
        activationCodeRepository.findById(id).ifPresent(code -> {
            code.setUsed(true);
            code.setFetchTime(LocalDateTime.now());
            activationCodeRepository.save(code);
        });
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
    public int batchImportCodes(List<String[]> rows) {
        List<ActivationCode> entities = rows.stream()
                .filter(r -> r != null && r.length > 0 && r[0] != null && !r[0].isBlank())
                .map(r -> {
                    ActivationCode a = new ActivationCode();
                    a.setCode(r[0].trim().toUpperCase());
                    a.setUsed(false);
                    if (r.length > 1 && r[1] != null && !r[1].isBlank()) {
                        a.setRemark(r[1].trim());
                    }
                    return a;
                })
                .toList();
        activationCodeRepository.saveAll(entities);
        return entities.size();
    }

    @Override
    @Transactional
    public int batchMarkAsUsed(List<Long> ids) {
        // 单条 UPDATE ... WHERE id IN，避免逐条循环
        return activationCodeRepository.markUsedIn(ids, LocalDateTime.now());
    }

    @Override
    @Transactional
    public void markAsUnused(Long id) {
        activationCodeRepository.findById(id).ifPresent(code -> {
            code.setUsed(false);
            code.setFetchTime(null);
            activationCodeRepository.save(code);
        });
    }

    @Override
    @Transactional
    public int batchMarkAsUnused(List<Long> ids) {
        // 单条 UPDATE ... WHERE id IN，避免逐条循环
        return activationCodeRepository.markUnusedIn(ids);
    }

    @Override
    @Transactional
    public List<ActivationCode> batchClaim(int count, String username) {
        List<ActivationCode> result = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        for (int i = 0; i < count; i++) {
            List<ActivationCode> locked = activationCodeRepository.findFirstUnusedWithLock(PageRequest.of(0, 1));
            if (locked.isEmpty()) break;
            ActivationCode code = locked.getFirst();
            code.setUsed(true);
            code.setFetchTime(now);
            code.setFetchUser(username);
            result.add(activationCodeRepository.save(code));
        }
        return result;
    }

    @Override
    @Transactional
    public void deleteCode(Long id) {
        activationCodeRepository.deleteById(id);
    }

    @Override
    @Transactional
    public int batchDeleteCodes(List<Long> ids) {
        // 单条 DELETE ... WHERE id IN，避免逐条循环
        return activationCodeRepository.deleteIn(ids);
    }
}
