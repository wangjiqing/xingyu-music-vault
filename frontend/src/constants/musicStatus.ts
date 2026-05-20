import type { MusicItem } from '../api/music'

export const MUSIC_LIST_VIEW_MODE_STORAGE_KEY = 'musicListViewMode'

export const MUSIC_LIST_VIEW_MODE = {
  TABLE: 'table',
  CARD: 'card',
} as const

export type MusicListViewMode = (typeof MUSIC_LIST_VIEW_MODE)[keyof typeof MUSIC_LIST_VIEW_MODE]

export const DEFAULT_MUSIC_LIST_VIEW_MODE: MusicListViewMode = MUSIC_LIST_VIEW_MODE.CARD

export const LYRIC_STATUS = {
  BOUND: 'BOUND',
  NO_LYRIC: 'NO_LYRIC',
  UNMATCHED: 'UNMATCHED',
  PARSE_FAILED: 'PARSE_FAILED',
  MISSING_FILE: 'MISSING_FILE',
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
    [LYRIC_STATUS.BOUND]: '有歌词',
    [LYRIC_STATUS.NO_LYRIC]: '无歌词',
    [LYRIC_STATUS.UNMATCHED]: '待匹配',
    [LYRIC_STATUS.PARSE_FAILED]: '解析失败',
    [LYRIC_STATUS.MISSING_FILE]: '文件缺失',
  }
  return status ? map[status] || status : '无歌词'
}

export function lyricStatusTagType(status: string | null | undefined): StatusTagType {
  const map: Record<string, StatusTagType> = {
    [LYRIC_STATUS.BOUND]: 'success',
    [LYRIC_STATUS.NO_LYRIC]: 'info',
    [LYRIC_STATUS.UNMATCHED]: 'warning',
    [LYRIC_STATUS.PARSE_FAILED]: 'danger',
    [LYRIC_STATUS.MISSING_FILE]: 'danger',
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
