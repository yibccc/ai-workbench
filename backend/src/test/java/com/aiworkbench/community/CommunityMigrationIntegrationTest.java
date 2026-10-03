package com.aiworkbench.community;

import java.sql.Connection;
import java.sql.Statement;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.UUID;
import java.util.function.Function;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
class CommunityMigrationIntegrationTest {
    @Autowired DataSource dataSource;

    @Test void emptySchemaAndOwnedV16UpgradePreserveOriginalDataAndChecksums() throws Exception {
        String empty = schema(), upgraded = schema();
        try {
            migrate(empty,null);
            inSchema(empty,jdbc -> {
                assertThat(jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE version='17' AND success",Integer.class)).isEqualTo(1);
                assertThat(jdbc.queryForObject("SELECT count(*) FROM community_posts",Integer.class)).isZero();
                return null;
            });
            migrate(upgraded,"16");
            var checksums = inSchema(upgraded,jdbc -> jdbc.queryForList("SELECT version,checksum FROM flyway_schema_history WHERE version IS NOT NULL ORDER BY installed_rank"));
            UUID owner=UUID.randomUUID(),record=UUID.randomUUID();
            Instant at=Instant.parse("2057-02-03T04:00:00Z");
            inSchema(upgraded,jdbc -> {
                jdbc.update("INSERT INTO user_accounts(id,username,password_hash,role) VALUES (?,'owned-v16','hash','USER')",owner);
                jdbc.update("INSERT INTO work_records(id,user_id,content,occurred_at) VALUES (?,?,'保留原私有记录',?)",record,owner,Timestamp.from(at));
                return null;
            });
            migrate(upgraded,null);
            inSchema(upgraded,jdbc -> {
                assertThat(jdbc.queryForList("SELECT version,checksum FROM flyway_schema_history WHERE version IS NOT NULL AND version::int<=16 ORDER BY installed_rank"))
                        .isEqualTo(checksums);
                assertThat(jdbc.queryForObject("SELECT content FROM work_records WHERE user_id=? AND id=?",String.class,owner,record)).isEqualTo("保留原私有记录");
                assertThat(jdbc.queryForObject("SELECT count(*) FROM community_posts",Integer.class)).isZero();
                return null;
            });
        } finally { drop(empty);drop(upgraded); }
    }

    @Test void sameOwnerTypePointerAndStateConstraintsAreDatabaseInvariants() throws Exception {
        String schema=schema();
        try {
            migrate(schema,"17");
            inSchema(schema,jdbc -> {
                UUID a=UUID.randomUUID(),b=UUID.randomUUID(),post=UUID.randomUUID(),other=UUID.randomUUID(),revision=UUID.randomUUID();
                jdbc.update("INSERT INTO user_accounts(id,username,password_hash,role) VALUES (?,'migration-a','hash','USER'),(?,'migration-b','hash','USER')",a,b);
                jdbc.update("INSERT INTO community_posts(id,owner_id,type) VALUES (?,?,'BLOG'),(?,?,'BLOG')",post,a,other,b);
                assertThatThrownBy(() -> jdbc.update("INSERT INTO community_post_drafts(post_id,owner_id,type) VALUES (?,?,'BLOG')",post,b))
                        .isInstanceOf(DataIntegrityViolationException.class);
                assertThatThrownBy(() -> jdbc.update("INSERT INTO community_post_drafts(post_id,owner_id,type) VALUES (?,?,'MOMENT')",post,a))
                        .isInstanceOf(DataIntegrityViolationException.class);
                jdbc.update("INSERT INTO community_post_drafts(post_id,owner_id,type) VALUES (?,?,'BLOG')",post,a);
                assertThatThrownBy(() -> jdbc.update("UPDATE community_posts SET type='MOMENT' WHERE id=?",post))
                        .isInstanceOf(DataIntegrityViolationException.class);
                assertThatThrownBy(() -> jdbc.update("UPDATE community_posts SET status='PUBLISHED' WHERE id=?",post))
                        .isInstanceOf(DataIntegrityViolationException.class);
                jdbc.update("""
                        INSERT INTO community_post_revisions(id,post_id,owner_id,revision_no,type,title,body_markdown,source_selection,
                            request_id,request_fingerprint,result_version,published_at)
                        VALUES (?,?,?,1,'BLOG','标题','正文','[]',?,repeat('0',64),1,CURRENT_TIMESTAMP)
                        """,revision,other,b,UUID.randomUUID());
                assertThatThrownBy(() -> jdbc.update("UPDATE community_posts SET status='PUBLISHED',first_published_at=CURRENT_TIMESTAMP,current_revision_id=? WHERE id=?",revision,post))
                        .isInstanceOf(DataIntegrityViolationException.class);
                return null;
            });
        } finally { drop(schema); }
    }

    private String schema() { return "community_migration_"+UUID.randomUUID().toString().replace("-",""); }
    private void migrate(String schema,String target) {
        var config=Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).locations("classpath:db/migration");
        if(target!=null)config.target(target);
        config.load().migrate();
    }
    private <T> T inSchema(String schema,Function<JdbcTemplate,T> work) throws Exception {
        try(Connection connection=dataSource.getConnection();Statement statement=connection.createStatement()) {
            String original=connection.getSchema();
            statement.execute("SET search_path TO "+schema);
            try { return work.apply(new JdbcTemplate(new SingleConnectionDataSource(connection,true))); }
            finally { connection.setSchema(original); }
        }
    }
    private void drop(String schema) throws Exception {
        // This name is generated and owned by the test; the shared approved schema is never dropped.
        if(!schema.matches("community_migration_[0-9a-f]{32}"))throw new IllegalArgumentException("Unexpected test schema");
        try(Connection connection=dataSource.getConnection();Statement statement=connection.createStatement()) {
            statement.execute("DROP SCHEMA IF EXISTS "+schema+" CASCADE");
        }
    }
}
