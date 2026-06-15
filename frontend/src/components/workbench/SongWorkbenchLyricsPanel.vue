<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import type { MusicItem, WorkbenchArtwork, WorkbenchLyric } from '../../api/music'
import { currentThemeAssets } from '../../theme/currentTheme'

interface LyricLine {
  time: number | null
  text: string
}

const props = defineProps<{
  lyrics: WorkbenchLyric
  music: MusicItem
  artwork: WorkbenchArtwork
  currentTime: number
  duration: number
}>()

const scrollRef = ref<HTMLElement>()
const lineRefs = ref<HTMLElement[]>([])

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
const activeIndex = computed(() => {
  if (timedLyricLines.value.length === 0) return -1
  let result = 0
  for (let index = 0; index < timedLyricLines.value.length; index += 1) {
    if ((timedLyricLines.value[index].time ?? 0) <= props.currentTime + 0.15) {
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
  () => props.lyrics.content,
  () => {
    lineRefs.value = []
    if (scrollRef.value) {
      scrollRef.value.scrollTop = 0
    }
  },
)

function parseTimestamp(match: RegExpMatchArray): number {
  const minutes = Number(match[1] || 0)
  const seconds = Number(match[2] || 0)
  const fractionText = match[3] || '0'
  const fraction = Number(fractionText.padEnd(3, '0').slice(0, 3)) / 1000
  return minutes * 60 + seconds + fraction
}

function setLineRef(element: unknown, index: number) {
  if (element instanceof HTMLElement) {
    lineRefs.value[index] = element
  }
}
</script>

<template>
  <div class="lyrics-panel">
    <div class="lyrics-stage">
      <div v-if="lyricLines.length > 0" ref="scrollRef" class="lyrics-scroll">
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
.lyrics-panel {
  display: grid;
  grid-template-columns: minmax(0, 1fr) calc(280px * var(--workbench-scale, 1));
  gap: calc(26px * var(--workbench-scale, 1));
  height: 100%;
  min-height: 0;
}
.lyrics-stage {
  min-height: 0;
  overflow: hidden;
  border-radius: 8px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.18), rgba(255, 255, 255, 0.04));
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
