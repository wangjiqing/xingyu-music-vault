export const currentThemeId = 'midsummer-starlight'

export interface CurrentThemeConfig {
  name: string
  englishName: string
}

function normalizeText(value: unknown): string {
  return typeof value === 'string' ? value.trim() : ''
}

export async function fetchCurrentTheme(): Promise<CurrentThemeConfig | null> {
  const res = await fetch(`/themes/${currentThemeId}/theme.json`)
  if (!res.ok) return null
  const theme = await res.json() as {
    name?: unknown
    englishName?: unknown
  }
  return {
    name: normalizeText(theme.name),
    englishName: normalizeText(theme.englishName),
  }
}
