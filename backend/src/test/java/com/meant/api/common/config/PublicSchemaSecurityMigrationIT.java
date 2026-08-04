package com.meant.api.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.PostgresIntegrationTestSupport;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
class PublicSchemaSecurityMigrationIT extends PostgresIntegrationTestSupport {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void enablesRowLevelSecurityOnExistingPublicTables() {
        List<String> unsecuredTables = jdbcTemplate.queryForList("""
                SELECT relation.relname
                FROM pg_class relation
                JOIN pg_namespace namespace ON namespace.oid = relation.relnamespace
                WHERE namespace.nspname = 'public'
                  AND relation.relkind IN ('r', 'p')
                  AND NOT relation.relrowsecurity
                  AND NOT EXISTS (
                      SELECT 1
                      FROM pg_depend dependency
                      WHERE dependency.classid = 'pg_class'::regclass
                        AND dependency.objid = relation.oid
                        AND dependency.deptype = 'e'
                  )
                ORDER BY relation.relname
                """, String.class);

        assertThat(unsecuredTables).isEmpty();
    }

    @Test
    @Transactional
    void automaticallyEnablesRowLevelSecurityOnFuturePublicTables() {
        jdbcTemplate.execute("CREATE TABLE public.rls_migration_probe (id uuid PRIMARY KEY)");

        Boolean rowLevelSecurityEnabled = jdbcTemplate.queryForObject("""
                SELECT relation.relrowsecurity
                FROM pg_class relation
                JOIN pg_namespace namespace ON namespace.oid = relation.relnamespace
                WHERE namespace.nspname = 'public'
                  AND relation.relname = 'rls_migration_probe'
                """, Boolean.class);

        assertThat(rowLevelSecurityEnabled).isTrue();
    }
}
