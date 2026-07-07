import http from './http'
import type { MusicItem } from './music'

export interface LyricOverview {
  totalSongs: number
  songsWithLyrics: number
  lyricsCoverageRate: number
  songsWithSwlrc: number
  swlrcCoverageRate: number
  songsWithLrcOnly: number
  songsWithoutLyrics: number
  alignmentRunning: number
  draftPending: number
}

export interface DailyLyricRecommendation {
  id: number
  recommendationDate: string
  slotNo: number
  recommendationType: 'LRC_UPGRADE' | 'NO_LYRICS' | string
  actionStatus: string
  music: MusicItem
}

export interface DailyLyricRecommendationResponse {
  items: DailyLyricRecommendation[]
}

export interface RandomLyricCandidatesResponse {
  items: MusicItem[]
  message: string | null
}

export async function fetchLyricOverview(): Promise<LyricOverview> {
  const { data } = await http.get('/api/admin/lyrics/overview')
  return data
}

export async function fetchDailyLyricRecommendations(): Promise<DailyLyricRecommendationResponse> {
  const { data } = await http.get('/api/admin/lyrics/recommendations/daily')
  return data
}

export async function startDailyLyricRecommendation(id: number): Promise<DailyLyricRecommendation> {
  const { data } = await http.post(`/api/admin/lyrics/recommendations/${id}/start`)
  return data
}

export async function skipDailyLyricRecommendation(id: number): Promise<DailyLyricRecommendationResponse> {
  const { data } = await http.post(`/api/admin/lyrics/recommendations/${id}/skip`)
  return data
}

export async function replaceDailyLyricRecommendation(id: number): Promise<DailyLyricRecommendation> {
  const { data } = await http.post(`/api/admin/lyrics/recommendations/${id}/replace`)
  return data
}

export async function fetchRandomLyricCandidates(count: number): Promise<RandomLyricCandidatesResponse> {
  const { data } = await http.post('/api/admin/lyrics/recommendations/random', { count })
  return data
}
