<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import type { MusicItem, WorkbenchArtwork, WorkbenchLyric, WorkbenchWordLyric } from '../../api/music'
import { currentThemeAssets } from '../../theme/currentTheme'

interface LyricLine {
  time: number | null
  text: string
}

interface WordToken {
  time: number
  endTime: number
  text: string
}

interface WordLyricLine {
  time: number
  text: string
  words: WordToken[]
}

interface DisplayHeaderLine {
  text: string
  startMs: number
  endMs: number
}

const props = defineProps<{
  lyrics: WorkbenchLyric
  wordLyrics: WorkbenchWordLyric
  music: MusicItem
  artwork: WorkbenchArtwork
  currentTime: number
  duration: number
}>()

const scrollRef = ref<HTMLElement>()
const lineRefs = ref<HTMLElement[]>([])
const headerLineRefs = ref<HTMLElement[]>([])
const selectedLyricMode = ref<'word' | 'line'>('word')

const lyricLines = computed<LyricLine[]>(() => {
  if (!props.lyrics.available || !props.lyrics.content) return []
  const lines: LyricLine[] = []
  for (const rawLine of props.lyrics.content.split(/\r?\n/)) {
    const timestamps = [...rawLine.matchAll(/\[(\d{1,2}):(\d{1,2})(?:[.:](\d{1,3}))?\]/g)]
    const text = rawLine.replace(/(\[[^\]]+\])+/g, '').trim()
    if (!text) continue
    if (timestamps.length === 0) {
      lines.push({ time: null, text })
      continue
    }
    for (const match of timestamps) {
      lines.push({ time: parseTimestamp(match), text })
    }
  }
  const timed = lines.filter((line) => line.time != null)
  if (timed.length === 0) return lines
  return [...timed].sort((left, right) => (left.time ?? 0) - (right.time ?? 0))
})

const timedLyricLines = computed(() => lyricLines.value.filter((line) => line.time != null))
const wordLyricLines = computed<WordLyricLine[]>(() => parseWordLyrics(props.wordLyrics))
const presentationHeaderLines = computed<DisplayHeaderLine[]>(() => {
  const presentation = props.lyrics.alignmentPresentation
  const lines = presentation?.preservedHeaderLines
  if (!Array.isArray(lines)) return []
  const globalHints = Array.isArray(presentation?.presentationHints) ? presentation.presentationHints : []
  return lines.flatMap((line, index) => {
    const text = typeof line?.text === 'string' ? line.text.trim() : ''
    const kind = typeof line?.kind === 'string' ? line.kind.toUpperCase() : ''
    const hint = line?.presentationHints || globalHints[index]
    const startMs = Number(hint?.suggestedStartMs)
    const endMs = Number(hint?.suggestedEndMs)
    if (!text || kind === 'LRC_METADATA' || hint?.displayOnly === false) return []
    if (!Number.isFinite(startMs) || !Number.isFinite(endMs) || endMs <= startMs) return []
    return [{ text, startMs: Math.max(0, startMs), endMs }]
  })
})
const canUseWordLyrics = computed(() => wordLyricLines.value.length > 0)
const canUseLineLyrics = computed(() => lyricLines.value.length > 0)
const useWordLyrics = computed(() => canUseWordLyrics.value && selectedLyricMode.value === 'word')
const displayMode = computed(() => (useWordLyrics.value ? '逐字歌词' : '行级歌词'))
const activeHeaderIndex = computed(() => {
  const currentMs = props.currentTime * 1000
  return presentationHeaderLines.value.findIndex((line) => currentMs >= line.startMs && currentMs < line.endMs)
})
const activeIndex = computed(() => {
  if (useWordLyrics.value) return activeWordLineIndex.value
  if (timedLyricLines.value.length === 0) return -1
  let result = -1
  for (let index = 0; index < timedLyricLines.value.length; index += 1) {
    if ((timedLyricLines.value[index].time ?? 0) <= props.currentTime + 0.15) {
      result = index
    } else {
      break
    }
  }
  return result
})
const activeWordLineIndex = computed(() => {
  if (wordLyricLines.value.length === 0) return -1
  let result = -1
  for (let index = 0; index < wordLyricLines.value.length; index += 1) {
    if (wordLyricLines.value[index].time <= props.currentTime + 0.08) {
      result = index
    } else {
      break
    }
  }
  return result
})
const displayTitle = computed(() => props.music.title || props.music.fileName || '--')
const displayArtist = computed(() => props.music.artist || 'Unknown')
const displayAlbum = computed(() => props.music.album || '未知专辑')

watch(
  activeIndex,
  async (index) => {
    if (index < 0) return
    await nextTick()
    const container = scrollRef.value
    const line = lineRefs.value[index]
    if (!container || !line) return
    const targetTop = line.offsetTop - container.clientHeight / 2 + line.clientHeight / 2
    container.scrollTo({ top: Math.max(targetTop, 0), behavior: 'smooth' })
  },
)

watch(
  activeHeaderIndex,
  async (index) => {
    if (index < 0) return
    await nextTick()
    scrollToLine(headerLineRefs.value[index])
  },
)

watch(
  () => props.lyrics.content,
  () => {
    lineRefs.value = []
    headerLineRefs.value = []
    if (scrollRef.value) {
      scrollRef.value.scrollTop = 0
    }
  },
)

watch(
  () => props.wordLyrics.content,
  () => {
    lineRefs.value = []
    selectedLyricMode.value = canUseWordLyrics.value ? 'word' : 'line'
    if (scrollRef.value) {
      scrollRef.value.scrollTop = 0
    }
  },
)

watch(
  canUseWordLyrics,
  (available) => {
    selectedLyricMode.value = available ? 'word' : 'line'
  },
  { immediate: true },
)

function parseTimestamp(match: RegExpMatchArray): number {
  const minutes = Number(match[1] || 0)
  const seconds = Number(match[2] || 0)
  const fractionText = match[3] || '0'
  const fraction = Number(fractionText.padEnd(3, '0').slice(0, 3)) / 1000
  return minutes * 60 + seconds + fraction
}

function parseTimeText(value: string): number {
  const match = value.match(/^(\d{1,2}):(\d{1,2})(?:[.:](\d{1,3}))?$/)
  if (!match) return Number.NaN
  return parseTimestamp(match)
}

function setLineRef(element: unknown, index: number) {
  if (element instanceof HTMLElement) {
    lineRefs.value[index] = element
  }
}

function setHeaderLineRef(element: unknown, index: number) {
  if (element instanceof HTMLElement) {
    headerLineRefs.value[index] = element
  }
}

function scrollToLine(line?: HTMLElement) {
  const container = scrollRef.value
  if (!container || !line) return
  const targetTop = line.offsetTop - container.clientHeight / 2 + line.clientHeight / 2
  container.scrollTo({ top: Math.max(targetTop, 0), behavior: 'smooth' })
}

function headerPassed(line: DisplayHeaderLine): boolean {
  return props.currentTime * 1000 >= line.endMs
}

function parseWordLyrics(wordLyrics: WorkbenchWordLyric): WordLyricLine[] {
  if (!wordLyrics.available || !wordLyrics.content) return []
  try {
    const lines: WordLyricLine[] = []
    let currentLine: WordLyricLine | null = null
    const pushCurrentLine = () => {
      if (!currentLine || currentLine.words.length === 0) return
      const text = currentLine.words.map((word) => word.text).join('').trim()
      if (!text) return
      lines.push({ ...currentLine, text })
    }
    const appendWords = (target: WordLyricLine, value: string) => {
      const wordPattern = /<(\d{1,2}:\d{1,2}(?:[.:]\d{1,3})?)(?:,(\d{1,2}:\d{1,2}(?:[.:]\d{1,3})?))?>([^<\[]*)/g
      for (const match of value.matchAll(wordPattern)) {
        const time = parseTimeText(match[1])
        const parsedEndTime = match[2] ? parseTimeText(match[2]) : Number.NaN
        const endTime = Number.isFinite(parsedEndTime) && parsedEndTime > time ? parsedEndTime : time + 0.22
        const text = (match[3] || '').replace(/\[[^\]]+\]/g, '')
        if (!Number.isFinite(time) || !text) continue
        target.words.push({ time, endTime, text })
      }
    }
    for (const rawLine of wordLyrics.content.split(/\r?\n/)) {
      const line = rawLine.trim()
      if (!line) continue
      const lineTimeMatch = line.match(/^\[(\d{1,2}:\d{1,2}(?:[.:]\d{1,3})?)(?:,\d{1,2}:\d{1,2}(?:[.:]\d{1,3})?)?\](.*)$/)
      if (lineTimeMatch) {
        const time = parseTimeText(lineTimeMatch[1])
        if (!Number.isFinite(time)) continue
        pushCurrentLine()
        currentLine = { time, text: '', words: [] }
        appendWords(currentLine, lineTimeMatch[2] || '')
        continue
      }
      if (!currentLine) continue
      appendWords(currentLine, line)
    }
    pushCurrentLine()
    return lines.sort((left, right) => left.time - right.time)
  } catch {
    return []
  }
}

function wordProgress(word: WordToken): string {
  const duration = Math.max(word.endTime - word.time, 0.08)
  const progress = Math.min(Math.max((props.currentTime - word.time) / duration, 0), 1)
  return `${Math.round(progress * 100)}%`
}
</script>

<template>
  <div class="lyrics-panel">
    <div class="lyrics-stage">
      <div class="lyrics-mode-control">
        <el-radio-group
          v-if="canUseWordLyrics && canUseLineLyrics"
          v-model="selectedLyricMode"
          size="small"
        >
          <el-radio-button label="word">逐字歌词</el-radio-button>
          <el-radio-button label="line">行级歌词</el-radio-button>
        </el-radio-group>
        <el-tag v-else size="small" :type="useWordLyrics ? 'success' : 'info'">
          {{ displayMode }}
        </el-tag>
      </div>
      <div v-if="useWordLyrics" ref="scrollRef" class="lyrics-scroll">
        <div
          v-for="(line, index) in presentationHeaderLines"
          :key="`header-word-${index}-${line.startMs}`"
          class="lyric-line header-line"
          :class="{ active: index === activeHeaderIndex, passed: headerPassed(line) }"
          :ref="(element) => setHeaderLineRef(element, index)"
        >
          {{ line.text }}
        </div>
        <div
          v-for="(line, index) in wordLyricLines"
          :key="`${index}-${line.time}`"
          class="lyric-line word-line"
          :class="{ active: index === activeIndex, passed: activeIndex > index }"
          :ref="(element) => setLineRef(element, index)"
        >
          <span
            v-for="(word, wordIndex) in line.words"
            :key="`${wordIndex}-${word.time}`"
            class="word-token"
            :style="{ '--word-progress': wordProgress(word) }"
          >
            {{ word.text }}
          </span>
        </div>
      </div>
      <div v-else-if="lyricLines.length > 0" ref="scrollRef" class="lyrics-scroll">
        <div
          v-for="(line, index) in presentationHeaderLines"
          :key="`header-line-${index}-${line.startMs}`"
          class="lyric-line header-line"
          :class="{ active: index === activeHeaderIndex, passed: headerPassed(line) }"
          :ref="(element) => setHeaderLineRef(element, index)"
        >
          {{ line.text }}
        </div>
        <div
          v-for="(line, index) in lyricLines"
          :key="`${index}-${line}`"
          class="lyric-line"
          :class="{ active: index === activeIndex, passed: activeIndex > index }"
          :ref="(element) => setLineRef(element, index)"
        >
          {{ line.text }}
        </div>
      </div>
      <el-empty
        v-else
        class="lyrics-empty"
        description="暂无歌词"
        :image="currentThemeAssets.emptyStates.lyrics"
        :image-size="160"
      />
    </div>
    <aside class="lyrics-side">
      <!-- Reserved for v1.2.2 read-only confirmation actions. -->
      <div class="lyrics-actions-placeholder" aria-hidden="true" />
      <div class="lyrics-cover-wrap">
        <img
          v-if="artwork.available && artwork.previewUrl"
          class="lyrics-cover"
          :src="artwork.previewUrl"
          alt="歌曲封面"
        />
        <div v-else class="lyrics-cover lyrics-cover-empty">
          {{ displayTitle.slice(0, 1) }}
        </div>
      </div>
      <dl class="lyrics-song-info">
        <div>
          <dt>歌曲名</dt>
          <dd>{{ displayTitle }}</dd>
        </div>
        <div>
          <dt>歌手</dt>
          <dd>{{ displayArtist }}</dd>
        </div>
        <div>
          <dt>专辑</dt>
          <dd>{{ displayAlbum }}</dd>
        </div>
      </dl>
    </aside>
  </div>
</template>

<style scoped>
@property --word-progress {
  syntax: '<percentage>';
  inherits: false;
  initial-value: 0%;
}

.lyrics-panel {
  display: grid;
  grid-template-columns: minmax(0, 1fr) calc(280px * var(--workbench-scale, 1));
  gap: calc(26px * var(--workbench-scale, 1));
  height: 100%;
  min-height: 0;
}
.lyrics-stage {
  position: relative;
  min-height: 0;
  overflow: hidden;
  border-radius: 8px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.18), rgba(255, 255, 255, 0.04));
}
.lyrics-mode-control {
  position: absolute;
  top: 12px;
  right: 12px;
  z-index: 1;
}
.lyrics-scroll {
  height: 100%;
  min-height: 0;
  overflow-y: auto;
  padding:
    calc(28px * var(--workbench-scale, 1))
    calc(24px * var(--workbench-scale, 1))
    calc(42px * var(--workbench-scale, 1));
  scrollbar-width: thin;
}
.header-line {
  color: var(--el-text-color-secondary);
  font-size: calc(16px * var(--workbench-scale, 1));
  font-weight: 500;
}
.lyric-line {
  max-width: calc(720px * var(--workbench-scale, 1));
  margin: 0 auto calc(18px * var(--workbench-scale, 1));
  color: color-mix(in srgb, var(--el-text-color-primary) 58%, transparent);
  font-size: calc(18px * var(--workbench-scale, 1));
  line-height: 1.8;
  text-align: center;
  text-wrap: balance;
  transition: color 0.2s ease, font-size 0.2s ease, font-weight 0.2s ease, opacity 0.2s ease;
}
.lyric-line.passed {
  opacity: 0.62;
}
.lyric-line.active {
  color: var(--xy-lyric-active, var(--xy-primary, var(--el-color-primary)));
  font-size: calc(22px * var(--workbench-scale, 1));
  font-weight: 800;
  opacity: 1;
}
.word-line {
  font-variant-east-asian: proportional-width;
}
.word-line.active {
  color: color-mix(in srgb, var(--el-text-color-primary) 58%, transparent);
}
.word-line.passed {
  color: color-mix(in srgb, var(--el-text-color-primary) 58%, transparent);
}
.word-token {
  --word-progress: 0%;
  --word-rest-color: color-mix(in srgb, var(--el-text-color-primary) 58%, transparent);
  --word-active-color: var(--xy-lyric-active, var(--xy-primary, var(--el-color-primary)));
  display: inline;
  color: var(--word-rest-color);
  background:
    linear-gradient(
      90deg,
      var(--word-active-color) 0%,
      var(--word-active-color) var(--word-progress),
      var(--word-rest-color) var(--word-progress),
      var(--word-rest-color) 100%
    );
  background-clip: text;
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  transition: --word-progress 0.16s linear, text-shadow 0.16s ease;
}
.word-line.active .word-token {
  text-shadow: 0 0 1px color-mix(in srgb, var(--xy-lyric-active, var(--xy-primary, var(--el-color-primary))) 18%, transparent);
}
.lyrics-empty {
  height: 100%;
  justify-content: center;
}
.lyrics-side {
  display: flex;
  flex-direction: column;
  align-items: center;
  min-height: 0;
  padding-top: calc(4px * var(--workbench-scale, 1));
}
.lyrics-actions-placeholder {
  align-self: stretch;
  height: calc(42px * var(--workbench-scale, 1));
  margin-bottom: calc(18px * var(--workbench-scale, 1));
}
.lyrics-cover-wrap {
  width: calc(176px * var(--workbench-scale, 1));
  height: calc(176px * var(--workbench-scale, 1));
  filter: drop-shadow(10px 12px 18px rgba(31, 45, 61, 0.18));
}
.lyrics-cover {
  width: 100%;
  height: 100%;
  object-fit: cover;
  border-radius: 8px;
  border: 1px solid rgba(255, 255, 255, 0.42);
}
.lyrics-cover-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  background: color-mix(in srgb, var(--el-fill-color-light) 84%, transparent);
  color: var(--el-text-color-secondary);
  font-size: calc(48px * var(--workbench-scale, 1));
  font-weight: 700;
}
.lyrics-song-info {
  width: 100%;
  margin: calc(18px * var(--workbench-scale, 1)) 0 0;
  color: var(--el-text-color-secondary);
  font-size: calc(13px * var(--workbench-scale, 1));
}
.lyrics-song-info div {
  display: grid;
  grid-template-columns: calc(56px * var(--workbench-scale, 1)) minmax(0, 1fr);
  gap: calc(8px * var(--workbench-scale, 1));
  margin-bottom: calc(10px * var(--workbench-scale, 1));
}
.lyrics-song-info dt {
  color: color-mix(in srgb, var(--el-text-color-secondary) 82%, transparent);
  text-align: right;
}
.lyrics-song-info dd {
  min-width: 0;
  margin: 0;
  overflow: hidden;
  color: var(--el-text-color-primary);
  text-overflow: ellipsis;
  white-space: nowrap;
}
@media (max-width: 980px) {
  .lyrics-panel {
    grid-template-columns: 1fr;
  }
  .lyrics-side {
    display: none;
  }
}
</style>
