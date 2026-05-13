package com.xingyu.musicvault.job;

import java.time.LocalDateTime;
import java.util.List;

public final class ScanJobDtos {
    private ScanJobDtos() {
    }

    public record ScanJobResponse(
            Long id,
            String jobType,
            String status,
            List<String> musicDirs,
            long totalFiles,
            long scannedFiles,
            long newFiles,
            long updatedFiles,
            long skippedFiles,
            long errorFiles,
            String errorMessage,
            LocalDateTime startedAt,
            LocalDateTime finishedAt,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        public static ScanJobResponse from(ScanJob scanJob) {
            return new ScanJobResponse(
                    scanJob.id,
                    scanJob.jobType,
                    scanJob.status,
                    splitMusicDirs(scanJob.musicDirs),
                    scanJob.totalFiles,
                    scanJob.scannedFiles,
                    scanJob.newFiles,
                    scanJob.updatedFiles,
                    scanJob.skippedFiles,
                    scanJob.errorFiles,
                    scanJob.errorMessage,
                    scanJob.startedAt,
                    scanJob.finishedAt,
                    scanJob.createdAt,
                    scanJob.updatedAt
            );
        }
    }

    public record CreateScanJobRequest(String jobType, List<String> musicDirs) {
    }

    static List<String> splitMusicDirs(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return List.of(value.split(",")).stream()
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .toList();
    }
}
