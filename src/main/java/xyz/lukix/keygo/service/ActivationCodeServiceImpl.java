package xyz.lukix.keygo.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
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
    public Optional<ActivationCode> claimCode() {
        List<ActivationCode> codes = activationCodeRepository.findFirstUnusedWithLock(PageRequest.of(0, 1));
        if (codes.isEmpty()) {
            return Optional.empty();
        }
        ActivationCode code = codes.getFirst();
        code.setUsed(true);
        code.setFetchTime(LocalDateTime.now());
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
        List<ActivationCode> codes = activationCodeRepository.findAllById(ids);
        for (ActivationCode c : codes) {
            c.setUsed(false);
            c.setFetchTime(null);
        }
        activationCodeRepository.saveAll(codes);
        return codes.size();
    }

    @Override
    @Transactional
    public List<ActivationCode> batchClaim(int count) {
        List<ActivationCode> result = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        for (int i = 0; i < count; i++) {
            List<ActivationCode> locked = activationCodeRepository.findFirstUnusedWithLock(PageRequest.of(0, 1));
            if (locked.isEmpty()) break;
            ActivationCode code = locked.getFirst();
            code.setUsed(true);
            code.setFetchTime(now);
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
        activationCodeRepository.deleteAllById(ids);
        return ids.size();
    }
}
