package xyz.lukix.keygo.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import xyz.lukix.keygo.entity.Unit;

import java.util.List;

public interface UnitRepository extends JpaRepository<Unit, Long> {

    /**
     * 查询所有使用单位（按ID升序，匹配插入顺序）
     */
    List<Unit> findAllByOrderByIdAsc();

    /**
     * 按名称精确查询
     */
    boolean existsByName(String name);
}
