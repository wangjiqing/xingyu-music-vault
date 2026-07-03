<script setup lang="ts">
import type { LyricAlignmentJob } from '../../api/lyricAlignment'
import {
  alignmentExecutionStatusLabel,
  alignmentExecutionStatusTagType,
  alignmentImportStatusLabel,
  alignmentImportStatusTagType,
  alignmentReviewStatusLabel,
  alignmentReviewStatusTagType,
  alignmentWorkerOutcomeLabel,
  alignmentWorkerOutcomeTagType,
} from '../../constants/lyricAlignmentStatus'

defineProps<{
  job: Pick<LyricAlignmentJob, 'status' | 'workerOutcome' | 'reviewStatus' | 'importStatus'>
  size?: 'small' | 'default'
}>()
</script>

<template>
  <div class="alignment-status-tags">
    <el-tag :type="alignmentExecutionStatusTagType(job.status)" :size="size || 'small'">
      {{ alignmentExecutionStatusLabel(job.status) }}
    </el-tag>
    <el-tag
      v-if="job.workerOutcome"
      :type="alignmentWorkerOutcomeTagType(job.workerOutcome)"
      :size="size || 'small'"
    >
      {{ alignmentWorkerOutcomeLabel(job.workerOutcome) }}
    </el-tag>
    <el-tag :type="alignmentReviewStatusTagType(job.reviewStatus)" :size="size || 'small'">
      {{ alignmentReviewStatusLabel(job.reviewStatus) }}
    </el-tag>
    <el-tag :type="alignmentImportStatusTagType(job.importStatus)" :size="size || 'small'">
      {{ alignmentImportStatusLabel(job.importStatus) }}
    </el-tag>
  </div>
</template>

<style scoped>
.alignment-status-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
}
</style>
