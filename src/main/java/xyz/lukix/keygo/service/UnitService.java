package xyz.lukix.keygo.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.lukix.keygo.entity.Unit;
import xyz.lukix.keygo.repository.UnitRepository;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 使用单位服务
 */
@Service
@RequiredArgsConstructor
public class UnitService {

    private final UnitRepository unitRepository;

    /**
     * 查询所有使用单位（按ID升序）
     */
    @Transactional(readOnly = true)
    public List<Unit> listAll() {
        return unitRepository.findAllByOrderByIdAsc();
    }

    /**
     * 新建使用单位；名称已存在时返回 Optional.empty()
     */
    @Transactional
    public Optional<Unit> create(String name) {
        if (unitRepository.existsByName(name)) {
            return Optional.empty();
        }
        Unit unit = new Unit();
        unit.setName(name);
        return Optional.of(unitRepository.save(unit));
    }

    /**
     * 批量导入使用单位：文件内重复、与库中已重复的名称均计入 duplicates 并跳过
     *
     * @param names 已去除首尾空白、忽略空行后的名称列表
     * @return added=新增数；skipped=跳过行数；duplicates=重复名称列表
     */
    @Transactional
    public ImportResult importUnits(List<String> names) {
        Set<String> existing = new HashSet<>();
        unitRepository.findAll().forEach(u -> existing.add(u.getName()));

        List<Unit> toAdd = new ArrayList<>();
        List<String> duplicates = new ArrayList<>();
        for (String name : names) {
            if (existing.contains(name)) {
                duplicates.add(name);
                continue;
            }
            existing.add(name);
            Unit unit = new Unit();
            unit.setName(name);
            toAdd.add(unit);
        }
        unitRepository.saveAll(toAdd);
        return new ImportResult(toAdd.size(), duplicates.size(), duplicates);
    }

    /**
     * 删除使用单位；不存在时返回 false
     */
    @Transactional
    public boolean delete(Long id) {
        if (!unitRepository.existsById(id)) {
            return false;
        }
        unitRepository.deleteById(id);
        return true;
    }

    /**
     * 导入结果
     *
     * @param added      新增数量
     * @param skipped    跳过数量（与 duplicates 数量一致）
     * @param duplicates 重复名称列表（文件内重复 + 与已有单位重复）
     */
    public record ImportResult(int added, int skipped, List<String> duplicates) {
    }
}
