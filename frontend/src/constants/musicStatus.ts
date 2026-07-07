import type { MusicItem } from '../api/music'

export const MUSIC_LIST_VIEW_MODE_STORAGE_KEY = 'musicListViewMode'

export const MUSIC_LIST_VIEW_MODE = {
  TABLE: 'table',
  CARD: 'card',
} as const

export type MusicListViewMode = (typeof MUSIC_LIST_VIEW_MODE)[keyof typeof MUSIC_LIST_VIEW_MODE]

export const DEFAULT_MUSIC_LIST_VIEW_MODE: MusicListViewMode = MUSIC_LIST_VIEW_MODE.CARD

export const LYRIC_STATUS = {
  SWLRC_READY: 'SWLRC_READY',
  LRC_READY: 'LRC_READY',
  NO_LYRICS: 'NO_LYRICS',
  ALIGNMENT_RUNNING: 'ALIGNMENT_RUNNING',
  DRAFT_PENDING: 'DRAFT_PENDING',
  FAILED: 'FAILED',
  // Backward-compatible values returned by legacy lyric scan APIs. Song lists use the v1.3.3 values above.
  BOUND: 'BOUND',
  NO_LYRIC: 'NO_LYRIC',
} as const

export const ARTWORK_STATUS = {
  BOUND: 'BOUND',
} as const

export const SOURCE_TYPE = {
  LOCAL_FILE: 'LOCAL_FILE',
  MANUAL: 'MANUAL',
  ONLINE: 'ONLINE',
} as const

export const LYRIC_BIND_STATUS = {
  BOUND: 'BOUND',
  UNBOUND: 'UNBOUND',
} as const

export const LYRIC_PARSE_STATUS = {
  SUCCESS: 'SUCCESS',
  FAILED: 'FAILED',
  UNKNOWN: 'UNKNOWN',
} as const

export type StatusTagType = 'success' | 'info' | 'warning' | 'danger'

export function lyricStatusLabel(status: string | null | undefined) {
  const map: Record<string, string> = {
    [LYRIC_STATUS.SWLRC_READY]: '已有 SWLRC',
    [LYRIC_STATUS.LRC_READY]: '仅有 LRC',
    [LYRIC_STATUS.NO_LYRICS]: '无歌词',
    [LYRIC_STATUS.ALIGNMENT_RUNNING]: '制作中',
    [LYRIC_STATUS.DRAFT_PENDING]: '待确认',
    [LYRIC_STATUS.FAILED]: '制作异常',
    [LYRIC_STATUS.BOUND]: '有歌词',
    [LYRIC_STATUS.NO_LYRIC]: '无歌词',
  }
  return status ? map[status] || status : '无歌词'
}

export function lyricStatusTagType(status: string | null | undefined): StatusTagType {
  const map: Record<string, StatusTagType> = {
    [LYRIC_STATUS.SWLRC_READY]: 'success',
    [LYRIC_STATUS.LRC_READY]: 'warning',
    [LYRIC_STATUS.NO_LYRICS]: 'info',
    [LYRIC_STATUS.ALIGNMENT_RUNNING]: 'warning',
    [LYRIC_STATUS.DRAFT_PENDING]: 'warning',
    [LYRIC_STATUS.FAILED]: 'danger',
    [LYRIC_STATUS.BOUND]: 'success',
    [LYRIC_STATUS.NO_LYRIC]: 'info',
  }
  return status ? map[status] || 'info' : 'info'
}

export function artworkStatusLabel(item: MusicItem) {
  if (item.artworkStatus === ARTWORK_STATUS.BOUND && item.artworkFileExists === false) {
    return '封面缺失'
  }
  return item.artworkStatus === ARTWORK_STATUS.BOUND ? '有封面' : '无封面'
}

export function artworkStatusTagType(item: MusicItem): StatusTagType {
  if (item.artworkStatus === ARTWORK_STATUS.BOUND && item.artworkFileExists === false) {
    return 'danger'
  }
  return item.artworkStatus === ARTWORK_STATUS.BOUND ? 'success' : 'info'
}

export function hasCompleteMusicMetadata(item: MusicItem) {
  return Boolean(item.title?.trim() && item.artist?.trim() && item.album?.trim())
}

export function sourceTypeLabel(sourceType: string) {
  const map: Record<string, string> = {
    [SOURCE_TYPE.LOCAL_FILE]: '本地文件',
    [SOURCE_TYPE.MANUAL]: '手动',
    [SOURCE_TYPE.ONLINE]: '在线',
  }
  return map[sourceType] || sourceType
}

export function lyricBindStatusLabel(status: string) {
  return status === LYRIC_BIND_STATUS.BOUND ? '已绑定' : '未绑定'
}

export function lyricBindStatusTagType(status: string): StatusTagType {
  return status === LYRIC_BIND_STATUS.BOUND ? 'success' : 'info'
}

export function lyricParseStatusLabel(status: string) {
  const map: Record<string, string> = {
    [LYRIC_PARSE_STATUS.SUCCESS]: '成功',
    [LYRIC_PARSE_STATUS.FAILED]: '失败',
    [LYRIC_PARSE_STATUS.UNKNOWN]: '未知',
  }
  return map[status] || status
}

export function lyricParseStatusTagType(status: string): StatusTagType {
  const map: Record<string, StatusTagType> = {
    [LYRIC_PARSE_STATUS.SUCCESS]: 'success',
    [LYRIC_PARSE_STATUS.FAILED]: 'danger',
    [LYRIC_PARSE_STATUS.UNKNOWN]: 'warning',
  }
  return map[status] || 'info'
}
