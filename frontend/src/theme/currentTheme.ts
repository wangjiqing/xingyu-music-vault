import { computed, ref } from 'vue'

export interface ThemeCandidate {
  id: string
  name: string
  englishName: string
  season: string
  position: string
}

export interface CurrentThemeConfig extends ThemeCandidate {}

export interface ThemeAssets {
  logoMark: string
  logoHorizontal: string
  banner: string
  backgroundDesktop: string
  backgroundMobile: string
  favicon: {
    ico: string
    small: string
    appleTouchIcon: string
  }
  emptyStates: {
    home: string
    songs: string
    albums: string
    artists: string
    lyrics: string
    cover: string
    metadataPending: string
  }
}

export const availableThemes: ThemeCandidate[] = [
  {
    id: 'spring-dawn',
    name: '春日晨光',
    englishName: 'Spring Dawn',
    season: 'spring',
    position: '四季主题中的春季主题',
  },
  {
    id: 'midsummer-starlight',
    name: '仲夏星河',
    englishName: 'Midsummer Starlight',
    season: 'summer',
    position: '四季主题中的夏季主题',
  },
  {
    id: 'autumn-vinyl',
    name: '秋日唱片',
    englishName: 'Autumn Vinyl',
    season: 'autumn',
    position: '四季主题中的秋季主题',
  },
  {
    id: 'winter-moonlight',
    name: '冬夜雪境',
    englishName: 'Winter Moonlight',
    season: 'winter',
    position: '四季主题中的冬季主题',
  },
]

export const defaultThemeId = 'midsummer-starlight'
export const themeStorageKey = 'xingyu-music-vault-theme'

function hasBrowserStorage(): boolean {
  return typeof window !== 'undefined' && typeof window.localStorage !== 'undefined'
}

function normalizeThemeId(value: unknown): string {
  if (typeof value !== 'string') return defaultThemeId
  return availableThemes.some((theme) => theme.id === value) ? value : defaultThemeId
}

export function getCurrentThemeId(): string {
  if (!hasBrowserStorage()) return defaultThemeId
  return normalizeThemeId(window.localStorage.getItem(themeStorageKey))
}

export const activeThemeId = ref(getCurrentThemeId())

export function setCurrentThemeId(themeId: string): void {
  activeThemeId.value = normalizeThemeId(themeId)
  if (hasBrowserStorage()) {
    window.localStorage.setItem(themeStorageKey, activeThemeId.value)
  }
  applyDocumentThemeAssets(activeThemeId.value)
}

export function themeBasePath(themeId = activeThemeId.value): string {
  return `/themes/${normalizeThemeId(themeId)}`
}

export function themeAssets(themeId = activeThemeId.value): ThemeAssets {
  const normalizedThemeId = normalizeThemeId(themeId)
  const basePath = themeBasePath(themeId)
  const metadataPending =
    normalizedThemeId === 'spring-dawn'
      ? `${basePath}/empty-states/empty-home.png`
      : `${basePath}/empty-states/metadata-pending.png`

  return {
    logoMark: `${basePath}/logo/logo-mark.png`,
    logoHorizontal: `${basePath}/logo/logo-horizontal.png`,
    banner: `${basePath}/banner/readme-banner.webp`,
    backgroundDesktop: `${basePath}/background/background-desktop.webp`,
    backgroundMobile: `${basePath}/background/background-mobile.webp`,
    favicon: {
      ico: `${basePath}/favicon/favicon.ico`,
      small: `${basePath}/favicon/favicon-32x32.png`,
      appleTouchIcon: `${basePath}/favicon/apple-touch-icon.png`,
    },
    emptyStates: {
      home: `${basePath}/empty-states/empty-home.png`,
      songs: `${basePath}/empty-states/empty-songs.png`,
      albums: `${basePath}/empty-states/empty-albums.png`,
      artists: `${basePath}/empty-states/empty-artists.png`,
      lyrics: `${basePath}/empty-states/empty-lyrics.png`,
      cover: `${basePath}/empty-states/empty-cover.png`,
      metadataPending,
    },
  }
}

export const currentThemeId = activeThemeId
export const currentThemeBasePath = computed(() => themeBasePath(activeThemeId.value))
export const currentThemeAssets = computed(() => themeAssets(activeThemeId.value))

function setLinkHref(id: string, href: string): void {
  if (typeof document === 'undefined') return
  const link = document.getElementById(id)
  if (link instanceof HTMLLinkElement) {
    link.href = href
  }
}

export function applyDocumentThemeAssets(themeId = activeThemeId.value): void {
  const assets = themeAssets(themeId)
  setLinkHref('xy-theme-favicon', assets.favicon.ico)
  setLinkHref('xy-theme-favicon-32', assets.favicon.small)
  setLinkHref('xy-theme-apple-touch-icon', assets.favicon.appleTouchIcon)
  setLinkHref('xy-theme-style', `${themeBasePath(themeId)}/theme.css`)
}

applyDocumentThemeAssets(activeThemeId.value)

function normalizeText(value: unknown): string {
  return typeof value === 'string' ? value.trim() : ''
}

export async function fetchCurrentTheme(themeId = activeThemeId.value): Promise<CurrentThemeConfig | null> {
  const resolvedThemeId = normalizeThemeId(themeId)
  const res = await fetch(`${themeBasePath(resolvedThemeId)}/theme.json`)
  if (!res.ok) return null
  const theme = await res.json() as {
    themeId?: unknown
    name?: unknown
    englishName?: unknown
    season?: unknown
  }
  return {
    id: normalizeText(theme.themeId) || resolvedThemeId,
    name: normalizeText(theme.name),
    englishName: normalizeText(theme.englishName),
    season: normalizeText(theme.season),
    position: availableThemes.find((item) => item.id === resolvedThemeId)?.position ?? '',
  }
}
