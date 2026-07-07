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
        assertEquals(22, first.migrationsExecuted);

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
                    "idx_metadata_sync_audit_created_at",
                    "idx_openapi_sync_change_log_version", "idx_openapi_sync_change_log_entity",
                    "idx_openapi_sync_change_log_changed_at");
            assertColumns(connection, "openapi_library_state", "id", "library_version", "last_changed_at");
            assertColumns(connection, "openapi_sync_change_log",
                    "id", "version", "entity_type", "entity_id", "change_type", "changed_fields_json", "changed_at");
            assertColumns(connection, "users",
                    "id", "username", "password_hash", "role", "enabled", "created_at", "updated_at", "last_login_at");
            assertIndexes(connection, "idx_users_username");
            assertColumns(connection, "openapi_credentials",
                    "id", "name", "access_key", "secret_encrypted", "secret_fingerprint", "scopes_json",
                    "enabled", "description", "expires_at", "created_at", "updated_at",
                    "last_used_at", "last_used_ip", "last_used_user_agent");
            assertColumns(connection, "openapi_request_nonces",
                    "id", "access_key", "nonce", "request_timestamp", "created_at", "expires_at");
            assertIndexes(connection, "idx_openapi_request_nonces_expires_at");
            assertColumns(connection, "lyric_alignment_jobs",
                    "id", "task_type", "song_id", "lyric_id", "status", "review_status", "import_status",
                    "audio_relative_path", "worker_audio_path", "trusted_lyrics_hash",
                    "trusted_lyrics_snapshot", "request_snapshot_json", "job_dir", "error_message",
                    "result_summary_json", "created_by", "created_at", "updated_at", "queued_at",
                    "started_at", "completed_at", "failed_at", "worker_outcome", "worker_status_json",
                    "alignment_json_hash", "lrc_hash", "swlrc_hash", "report_hash", "result_available",
                    "sync_message", "reviewed_by", "reviewed_at", "review_note", "imported_by",
                    "imported_at", "import_error_message", "imported_lyric_id");
            assertColumns(connection, "lyrics",
                    "source_task_id", "source_draft_id", "source_text_hash",
                    "parent_lyrics_id", "swlrc_path", "swlrc_hash",
                    "confirmed_at", "confirmed_by");
            assertColumns(connection, "lyric_drafts",
                    "id", "job_id", "music_id", "original_text", "original_text_hash",
                    "editable_text", "editable_text_hash", "draft_status", "report_summary_json",
                    "transcript_raw_hash", "transcript_segments_hash", "report_hash",
                    "confirmed_trusted_lyrics_id", "created_at", "updated_at", "edited_by",
                    "edited_at", "confirmed_by", "confirmed_at", "rejected_by", "rejected_at",
                    "reject_note", "error_message", "source_type", "source_metadata_json");
            assertColumns(connection, "app_settings",
                    "setting_key", "setting_value_encrypted", "enabled", "updated_by", "updated_at",
                    "last_error", "last_checked_at");
            assertColumns(connection, "lyric_draft_sources",
                    "id", "draft_id", "provider", "query", "title", "url", "domain", "selected_by", "selected_at");
            assertColumns(connection, "lyric_alignment_job_events",
                    "id", "task_id", "music_id", "action", "operator", "note", "before_status",
                    "after_status", "error_message", "created_at");
            assertColumns(connection, "lyric_daily_recommendation",
                    "id", "recommendation_date", "slot_no", "music_id", "recommendation_type",
                    "action_status", "replaced_by_id", "created_at", "updated_at", "acted_at");
            assertIndexes(connection,
                    "idx_lyric_alignment_jobs_song_id",
                    "idx_lyric_alignment_jobs_status",
                    "idx_lyric_alignment_jobs_task_type",
                    "idx_lyric_alignment_jobs_created_at",
                    "idx_lyric_alignment_jobs_worker_outcome",
                    "idx_lyrics_content_hash",
                    "idx_lyrics_source_task_id",
                    "idx_lyrics_source_draft_id",
                    "idx_lyrics_alignment_source_task_id",
                    "idx_lyrics_parent_lyrics_id",
                    "idx_lyrics_source_type_source_path",
                    "idx_lyrics_source_type_task",
                    "idx_lyric_drafts_music_id",
                    "idx_lyric_drafts_draft_status",
                    "idx_lyric_drafts_created_at",
                    "idx_lyric_drafts_source_type",
                    "idx_lyric_draft_sources_draft_id",
                    "idx_lyric_alignment_job_events_task_id",
                    "idx_lyric_alignment_job_events_action",
                    "idx_lyric_alignment_job_events_created_at",
                    "idx_lyric_daily_recommendation_date",
                    "idx_lyric_daily_recommendation_music",
                    "idx_lyric_daily_recommendation_date_music",
                    "idx_lyric_daily_recommendation_active_slot");
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
        assertEquals(21, result.migrationsExecuted);

        try (Connection connection = DriverManager.getConnection(jdbcUrl)) {
            assertEquals("旧歌", querySingle(connection, "select title from tracks where id = 1"));
            assertEquals("/tmp/music", querySingle(connection, "select music_dirs from scan_jobs where id = 1"));
            assertColumns(connection, "tracks", "metadata_extracted_at", "metadata_source", "year", "track_no", "genre");
            assertColumns(connection, "music_metadata_sync_audit",
                    "operation_type", "rollback_status", "rollback_of_audit_id");
            assertEquals(1, queryLong(connection, "select library_version from openapi_library_state where id = 1"));
            assertColumns(connection, "users", "username", "password_hash", "role", "enabled", "last_login_at");
            assertColumns(connection, "openapi_credentials", "access_key", "secret_encrypted", "scopes_json", "enabled");
            assertColumns(connection, "openapi_request_nonces", "access_key", "nonce", "expires_at");
            assertColumns(connection, "lyric_alignment_jobs", "id", "song_id", "lyric_id", "status", "trusted_lyrics_snapshot",
                    "task_type", "worker_outcome", "result_available", "sync_message", "reviewed_by", "imported_lyric_id");
            assertColumns(connection, "lyrics", "source_task_id", "source_draft_id", "source_text_hash", "swlrc_path", "confirmed_at");
            assertColumns(connection, "lyric_drafts", "job_id", "music_id", "editable_text", "draft_status", "source_type");
            assertColumns(connection, "app_settings", "setting_key", "setting_value_encrypted", "enabled");
            assertColumns(connection, "lyric_draft_sources", "draft_id", "provider", "url", "selected_at");
            assertColumns(connection, "lyric_alignment_job_events", "task_id", "music_id", "action", "operator");
            assertColumns(connection, "lyric_daily_recommendation",
                    "recommendation_date", "slot_no", "music_id", "recommendation_type", "action_status");
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

    private long queryLong(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            assertTrue(resultSet.next(), "query should return one row: " + sql);
            return resultSet.getLong(1);
        }
    }
}
