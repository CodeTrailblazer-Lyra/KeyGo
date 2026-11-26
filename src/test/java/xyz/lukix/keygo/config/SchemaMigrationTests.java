package xyz.lukix.keygo.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.SQLException;
import java.util.List;
import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class SchemaMigrationTests {

    @Test
    void skipsMissingTablesOnEmptyDatabase() {
        DriverManagerDataSource dataSource = dataSource("schema_migration_empty");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        new SchemaMigration(dataSource).migrate();

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables "
                        + "WHERE table_schema = current_schema() AND table_name = ?",
                Integer.class, "activation_code"))
                .isZero();
    }

    @Test
    void migratesLegacySchemaIdempotently() {
        DriverManagerDataSource dataSource = dataSource("schema_migration_legacy");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE activation_code (code_hash VARCHAR(20))");
        jdbc.execute("CREATE TABLE activation_log (code_hash VARCHAR(20))");
        jdbc.update("INSERT INTO activation_code (code_hash) VALUES (?)", "LEGACY-CODE");
        jdbc.update("INSERT INTO activation_log (code_hash) VALUES (?)", "LEGACY-LOG");

        SchemaMigration migration = new SchemaMigration(dataSource);
        migration.migrate();
        migration.migrate();

        assertThat(jdbc.queryForObject("SELECT code FROM activation_code", String.class))
                .isEqualTo("LEGACY-CODE");
        assertThat(jdbc.queryForObject("SELECT code FROM activation_log", String.class))
                .isEqualTo("LEGACY-LOG");
        assertThat(columnLength(jdbc, "activation_code", "code")).isEqualTo(48);
        assertThat(columnLength(jdbc, "activation_log", "code")).isEqualTo(200);
        assertThat(columns(jdbc, "activation_code")).contains(
                "official_use_status",
                "official_use_time",
                "official_product",
                "official_device",
                "official_code",
                "official_checked_at");
    }

    @Test
    void failsImmediatelyWhenDatabaseIsUnreachable() throws SQLException {
        DataSource dataSource = mock(DataSource.class);
        when(dataSource.getConnection()).thenThrow(new SQLException("No route to host"));
        SchemaMigration migration = new SchemaMigration(dataSource);

        assertThatThrownBy(migration::migrate)
                .isInstanceOf(CannotGetJdbcConnectionException.class)
                .hasRootCauseMessage("No route to host");
    }

    private DriverManagerDataSource dataSource(String database) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:" + database
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        return dataSource;
    }

    private Integer columnLength(JdbcTemplate jdbc, String table, String column) {
        return jdbc.queryForObject(
                "SELECT character_maximum_length FROM information_schema.columns "
                        + "WHERE table_schema = current_schema() AND table_name = ? AND column_name = ?",
                Integer.class, table, column);
    }

    private List<String> columns(JdbcTemplate jdbc, String table) {
        return jdbc.queryForList(
                "SELECT column_name FROM information_schema.columns "
                        + "WHERE table_schema = current_schema() AND table_name = ?",
                String.class, table);
    }
}
