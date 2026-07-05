export const ALIGNMENT_EXECUTION_STATUS: Record<string, string> = {
  CREATING: '创建中',
  QUEUED: '等待 Worker',
  RUNNING: '对齐中',
  COMPLETED: '已生成结果',
  FAILED: '对齐失败',
  ABANDONED: '已放弃',
}

export const ALIGNMENT_REVIEW_STATUS: Record<string, string> = {
  NOT_AVAILABLE: '暂不可审核',
  PENDING: '待人工审核',
  APPROVED: '已审核通过',
  REJECTED: '已驳回',
}

export const ALIGNMENT_IMPORT_STATUS: Record<string, string> = {
  NOT_IMPORTED: '未导入',
  IMPORTED: '已导入',
  IMPORT_FAILED: '导入失败',
}

export const ALIGNMENT_WORKER_OUTCOME: Record<string, string> = {
  SUCCEEDED: '对齐完成',
  NEEDS_REVIEW: '建议重点检查',
  FAILED: 'Worker 执行失败',
  ABANDONED: 'Worker 已放弃',
}

export const LYRIC_TASK_TYPE: Record<string, string> = {
  LYRICS_ALIGNMENT: '逐字歌词对齐',
  LYRIC_DRAFT_EXTRACTION: '歌词草稿提取',
  LYRIC_DRAFT_MANUAL: '手工歌词草稿',
}

export const LYRIC_DRAFT_STATUS: Record<string, string> = {
  PENDING_REVIEW: '待校对',
  EDITING: '编辑中',
  CONFIRMED: '已确认可信歌词',
  REJECTED: '已驳回',
}

export function alignmentExecutionStatusLabel(status?: string | null): string {
  if (!status) return '-'
  return ALIGNMENT_EXECUTION_STATUS[status] || status
}

export function alignmentReviewStatusLabel(status?: string | null): string {
  if (!status) return '-'
  return ALIGNMENT_REVIEW_STATUS[status] || status
}

export function alignmentImportStatusLabel(status?: string | null): string {
  if (!status) return '-'
  return ALIGNMENT_IMPORT_STATUS[status] || status
}

export function alignmentWorkerOutcomeLabel(outcome?: string | null): string {
  if (!outcome) return '-'
  return ALIGNMENT_WORKER_OUTCOME[outcome] || outcome
}

export function lyricTaskTypeLabel(taskType?: string | null): string {
  if (!taskType) return '逐字歌词对齐'
  return LYRIC_TASK_TYPE[taskType] || taskType
}

export function lyricDraftStatusLabel(status?: string | null): string {
  if (!status) return '-'
  return LYRIC_DRAFT_STATUS[status] || status
}

export function alignmentExecutionStatusTagType(status?: string | null): string {
  const map: Record<string, string> = {
    CREATING: 'info',
    QUEUED: 'info',
    RUNNING: 'warning',
    COMPLETED: 'success',
    FAILED: 'danger',
    ABANDONED: 'info',
  }
  return status ? map[status] || 'info' : 'info'
}

export function alignmentReviewStatusTagType(status?: string | null): string {
  const map: Record<string, string> = {
    NOT_AVAILABLE: 'info',
    PENDING: 'warning',
    APPROVED: 'success',
    REJECTED: 'danger',
  }
  return status ? map[status] || 'info' : 'info'
}

export function alignmentImportStatusTagType(status?: string | null): string {
  const map: Record<string, string> = {
    NOT_IMPORTED: 'info',
    IMPORTED: 'success',
    IMPORT_FAILED: 'danger',
  }
  return status ? map[status] || 'info' : 'info'
}

export function alignmentWorkerOutcomeTagType(outcome?: string | null): string {
  const map: Record<string, string> = {
    SUCCEEDED: 'success',
    NEEDS_REVIEW: 'warning',
    FAILED: 'danger',
    ABANDONED: 'info',
  }
  return outcome ? map[outcome] || 'info' : 'info'
}

export function lyricTaskTypeTagType(taskType?: string | null): string {
  return taskType === 'LYRIC_DRAFT_EXTRACTION' ? 'warning' : 'primary'
}

export function lyricDraftStatusTagType(status?: string | null): string {
  const map: Record<string, string> = {
    PENDING_REVIEW: 'warning',
    EDITING: 'primary',
    CONFIRMED: 'success',
    REJECTED: 'danger',
  }
  return status ? map[status] || 'info' : 'info'
}

export function shortAlignmentJobId(id: string): string {
  if (!id) return '-'
  return id.length > 13 ? `${id.slice(0, 8)}...${id.slice(-4)}` : id
}
