package com.xingyu.musicvault.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlywayMigrationRegressionTest {
    private static final String MIGRATION_LOCATION = "classpath:db/migration";

    @TempDir
    Path tempDir;

    @Test
    void emptySqliteDatabaseMigratesToLatestSchemaAndDoesNotRerun() throws Exception {
        String jdbcUrl = jdbcUrl("empty-latest.db");
        Flyway flyway = flyway(jdbcUrl);

        MigrateResult first = flyway.migrate();
        assertEquals(10, first.migrationsExecuted);

        MigrateResult second = flyway.migrate();
        assertEquals(0, second.migrationsExecuted);

        try (Connection connection = DriverManager.getConnection(jdbcUrl)) {
            assertColumns(connection, "tracks",
                    "id", "title", "normalized_title", "artist", "album", "album_artist", "duration",
                    "year", "track_no", "genre", "metadata_status", "lyrics_status", "artwork_status",
                    "metadata_updated_at", "created_at", "updated_at", "metadata_extracted_at", "metadata_source");
            assertColumns(connection, "track_files",
                    "id", "track_id", "file_path", "file_name", "file_ext", "file_size", "last_modified_at",
                    "scan_job_id", "created_at", "updated_at", "deleted_at", "trash_path", "delete_status",
                    "original_path");
            assertColumns(connection, "music_metadata_sync_audit",
                    "id", "batch_id", "music_id", "file_path", "direction", "source_type", "target_type",
                    "mode", "operation_type", "before_database_json", "after_database_json", "before_file_json",
                    "after_file_json", "changed_fields_json", "status", "error_message", "rollback_status",
                    "rollback_of_audit_id", "created_at", "created_by");
            assertIndexes(connection,
                    "idx_tracks_album", "idx_tracks_artist", "idx_tracks_genre",
                    "idx_metadata_sync_audit_music_id", "idx_metadata_sync_audit_batch_id",
                    "idx_metadata_sync_audit_created_at");
            assertEquals("ok", querySingle(connection, "pragma integrity_check"));
        }
    }

    @Test
    void existingV1SqliteDatabaseUpgradesWithoutLosingData() throws Exception {
        String jdbcUrl = jdbcUrl("v1-upgrade.db");
        Flyway.configure()
                .dataSource(jdbcUrl, null, null)
                .locations(MIGRATION_LOCATION)
                .target("1")
                .load()
                .migrate();

        try (Connection connection = DriverManager.getConnection(jdbcUrl);
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    insert into tracks (
                        title, normalized_title, metadata_status, lyrics_status, artwork_status, created_at, updated_at
                    ) values (
                        '旧歌', '旧歌', 'pending', 'pending', 'pending', '2026-01-01T00:00:00', '2026-01-01T00:00:00'
                    )
                    """);
            statement.executeUpdate("""
                    insert into scan_jobs (
                        job_type, status, music_dirs, created_at, updated_at
                    ) values (
                        'library_scan', 'pending', '/tmp/music', '2026-01-01T00:00:00', '2026-01-01T00:00:00'
                    )
                    """);
        }

        MigrateResult result = flyway(jdbcUrl).migrate();
        assertEquals(9, result.migrationsExecuted);

        try (Connection connection = DriverManager.getConnection(jdbcUrl)) {
            assertEquals("旧歌", querySingle(connection, "select title from tracks where id = 1"));
            assertEquals("/tmp/music", querySingle(connection, "select music_dirs from scan_jobs where id = 1"));
            assertColumns(connection, "tracks", "metadata_extracted_at", "metadata_source", "year", "track_no", "genre");
            assertColumns(connection, "music_metadata_sync_audit",
                    "operation_type", "rollback_status", "rollback_of_audit_id");
            assertEquals("ok", querySingle(connection, "pragma integrity_check"));
        }
    }

    private Flyway flyway(String jdbcUrl) {
        return Flyway.configure()
                .dataSource(jdbcUrl, null, null)
                .locations(MIGRATION_LOCATION)
                .load();
    }

    private String jdbcUrl(String fileName) {
        return "jdbc:sqlite:" + tempDir.resolve(fileName);
    }

    private void assertColumns(Connection connection, String tableName, String... expectedColumns) throws SQLException {
        Set<String> columns = new HashSet<>();
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("pragma table_info(" + tableName + ")")) {
            while (resultSet.next()) {
                columns.add(resultSet.getString("name"));
            }
        }
        for (String expectedColumn : expectedColumns) {
            assertTrue(columns.contains(expectedColumn), tableName + " should contain column " + expectedColumn);
        }
    }

    private void assertIndexes(Connection connection, String... expectedIndexes) throws SQLException {
        Set<String> indexes = new HashSet<>();
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("select name from sqlite_master where type = 'index'")) {
            while (resultSet.next()) {
                indexes.add(resultSet.getString("name"));
            }
        }
        for (String expectedIndex : expectedIndexes) {
            assertTrue(indexes.contains(expectedIndex), "schema should contain index " + expectedIndex);
        }
    }

    private String querySingle(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            assertTrue(resultSet.next(), "query should return one row: " + sql);
            return resultSet.getString(1);
        }
    }
}
