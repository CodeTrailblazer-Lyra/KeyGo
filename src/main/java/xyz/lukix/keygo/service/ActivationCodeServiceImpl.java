package xyz.lukix.keygo.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.lukix.keygo.entity.ActivationCode;
import xyz.lukix.keygo.repository.ActivationCodeRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ActivationCodeServiceImpl implements ActivationCodeService {

    private final ActivationCodeRepository activationCodeRepository;

    @Override
    @Transactional
    public Optional<ActivationCode> claimCode() {
        // 悲观锁：SELECT ... FOR UPDATE，防止并发抢到同一个激活码
        List<ActivationCode> codes = activationCodeRepository.findFirstUnusedWithLock(PageRequest.of(0, 1));
        if (codes.isEmpty()) {
            return Optional.empty();
        }
        ActivationCode code = codes.get(0);
        code.setUsed(true);
        code.setFetchTime(LocalDateTime.now());
        return Optional.of(activationCodeRepository.save(code));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ActivationCode> listAll() {
        return activationCodeRepository.findAllByOrderByCreateTimeDesc();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ActivationCode> listByUsed(boolean used) {
        return activationCodeRepository.findByUsedOrderByCreateTimeDesc(used);
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
    @Transactional
    public int batchImportCodes(List<String> codes) {
        List<ActivationCode> entities = codes.stream()
                .filter(c -> c != null && !c.isBlank())
                .map(c -> {
                    ActivationCode a = new ActivationCode();
                    a.setCode(c.trim().toUpperCase());
                    a.setUsed(false);
                    return a;
                })
                .toList();
        activationCodeRepository.saveAll(entities);
        return entities.size();
    }

    @Override
    @Transactional
    public int batchMarkAsUsed(List<Long> ids) {
        List<ActivationCode> codes = activationCodeRepository.findAllById(ids);
        LocalDateTime now = LocalDateTime.now();
        for (ActivationCode c : codes) {
            c.setUsed(true);
            c.setFetchTime(now);
        }
        activationCodeRepository.saveAll(codes);
        return codes.size();
    }
}
