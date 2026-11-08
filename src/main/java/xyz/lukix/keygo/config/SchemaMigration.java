package xyz.lukix.keygo.config;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import jakarta.annotation.PostConstruct;

/**
 * 数据库 Schema 迁移组件。
 * <p>
 * 处理旧版 <code>code_hash</code> 列到 <code>code</code> 列的在线重命名，
 * 以及后续版本新增列的幂等补建。
 * 迁移是幂等的：列已不存在时自动跳过，已存在时不重复执行，不报错。
 * <p>
 * 迁移逻辑不使用 Spring SQL 脚本执行器，因为 PL/pgSQL
 * <code>DO $$ ... END $$;</code> 块内部的 {@code ;} 会被误切为语句分隔符。
 * <p>
 * 必须在 JPA/Hibernate 初始化之前完成，因此通过 {@code @PostConstruct}
 * 在 Bean 创建阶段立即执行。
 *
 * @see org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration
 * @see org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration
 */
@Configuration
public class SchemaMigration {

    private static final Logger log = LoggerFactory.getLogger(SchemaMigration.class);

    private final JdbcTemplate jdbc;

    public SchemaMigration(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    /**
     * 执行所有一次性 Schema 迁移。
     * <p>
     * 每条迁移语句独立 try-catch，确保单条失败不影响其余迁移。
     */
    @PostConstruct
    public void migrate() {
        renameColumnIfPresent("activation_code", "code_hash", "code", "VARCHAR(48)");
        renameColumnIfPresent("activation_log", "code_hash", "code", "VARCHAR(200)");

        // 官方联网验证（统信 UOS licensequery）新增列：生产环境 ddl-auto=validate
        // 不会自动加列，此处兜底保证列存在（与 sql/activation_code.sql 中的 ADD COLUMN 一致）
        ensureColumn("activation_code", "official_use_status", "INTEGER");
        ensureColumn("activation_code", "official_use_time", "TIMESTAMP");
        ensureColumn("activation_code", "official_product", "VARCHAR(100)");
        ensureColumn("activation_code", "official_device", "VARCHAR(200)");
        ensureColumn("activation_code", "official_code", "INTEGER");
        ensureColumn("activation_code", "official_checked_at", "TIMESTAMP");
    }

    /**
     * 确保本 Bean 在 Spring Boot 的 SQL 脚本初始化器之前创建。
     * <p>
     * {@code BeanFactoryPostProcessor} 阶段只修改 Bean 定义（添加依赖关系），
     * 不触发实际实例化；真正的 Bean 创建顺序由依赖图决定。
     */
    @Bean
    static BeanFactoryPostProcessor schemaMigrationOrderPostProcessor() {
        return beanFactory -> {
            if (beanFactory.containsBeanDefinition("dataSourceScriptDatabaseInitializer")) {
                beanFactory.getBeanDefinition("dataSourceScriptDatabaseInitializer")
                        .setDependsOn("schemaMigration");
            }
        };
    }

    /**
     * 幂等补建列：仅当表存在且目标列缺失时才执行 ADD COLUMN。
     * <p>
     * 表不存在时静默跳过（首次建库由 sql 初始化脚本负责建表建列）。
     *
     * @param table  表名
     * @param column 列名
     * @param type   列类型（含长度）
     */
    private void ensureColumn(String table, String column, String type) {
        try {
            if (!columnExists(table, column)) {
                jdbc.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + type);
                log.info("迁移：新增列 {}.{}", table, column);
            }
        } catch (Exception e) {
            log.warn("迁移 {}.{} 执行异常（可能已处于目标状态）：{}", table, column, e.getMessage());
        }
    }

    /**
     * 判断列是否存在（表不存在时同样返回 false）
     */
    private boolean columnExists(String table, String column) {
        Boolean exists = jdbc.queryForObject(
                "SELECT EXISTS ("
                        + "  SELECT 1 FROM information_schema.columns"
                        + "  WHERE table_name = ? AND column_name = ?"
                        + ")",
                Boolean.class,
                table, column);
        return Boolean.TRUE.equals(exists);
    }

    /**
     * 幂等重命名列：仅当旧列存在且新列不存在时才执行 RENAME，随后统一调整类型。
     *
     * @param table     表名
     * @param oldColumn 旧列名
     * @param newColumn 新列名
     * @param newType   目标类型（含长度），用于 ALTER COLUMN ... TYPE
     */
    private void renameColumnIfPresent(String table, String oldColumn, String newColumn, String newType) {
        try {
            Boolean oldExists = jdbc.queryForObject(
                    "SELECT EXISTS ("
                            + "  SELECT 1 FROM information_schema.columns"
                            + "  WHERE table_name = ? AND column_name = ?"
                            + ")",
                    Boolean.class,
                    table, oldColumn);

            Boolean newExists = jdbc.queryForObject(
                    "SELECT EXISTS ("
                            + "  SELECT 1 FROM information_schema.columns"
                            + "  WHERE table_name = ? AND column_name = ?"
                            + ")",
                    Boolean.class,
                    table, newColumn);

            if (Boolean.TRUE.equals(oldExists) && !Boolean.TRUE.equals(newExists)) {
                jdbc.execute("ALTER TABLE " + table + " RENAME COLUMN " + oldColumn + " TO " + newColumn);
                log.info("迁移：{}.{} → {}", table, oldColumn, newColumn);
            } else {
                log.debug("迁移跳过：{}.{} 无需重命名（旧列不存在或新列已存在）", table, oldColumn);
            }

            // 统一调整列类型（幂等：已是目标类型时 PostgreSQL 忽略）
            jdbc.execute("ALTER TABLE " + table + " ALTER COLUMN " + newColumn + " TYPE " + newType);
        } catch (Exception e) {
            log.warn("迁移 {}.{} 执行异常（可能已处于目标状态）：{}", table, oldColumn, e.getMessage());
        }
    }
}
