<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import {
  Headset,
  List,
  Mute,
  Rank,
  RefreshLeft,
  RefreshRight,
} from '@element-plus/icons-vue'

export type PlaybackMode = 'repeat-one' | 'order' | 'repeat-list' | 'shuffle'

const props = defineProps<{
  src: string
  title: string
  artist: string
  album: string
  canPrevious: boolean
  canNext: boolean
  mode: PlaybackMode
  autoplayOnSourceChange: boolean
}>()

const emit = defineEmits<{
  previous: []
  next: []
  ended: []
  error: [message: string]
  timeupdate: [currentTime: number]
  durationchange: [duration: number]
  modeChange: [mode: PlaybackMode]
  playingChange: [playing: boolean]
}>()

const audioRef = ref<HTMLAudioElement>()
const playing = ref(false)
const currentTime = ref(0)
const duration = ref(0)
const seeking = ref(false)
const sliderValue = ref(0)
const volume = ref(72)

const progressMax = computed(() => Math.max(duration.value, 0))
const modeOptions: Array<{ value: PlaybackMode; label: string; icon: unknown }> = [
  { value: 'repeat-one', label: '单曲循环', icon: RefreshLeft },
  { value: 'order', label: '顺序列表', icon: List },
  { value: 'repeat-list', label: '顺序循环', icon: RefreshRight },
  { value: 'shuffle', label: '随机播放', icon: Rank },
]
const currentMode = computed(() => modeOptions.find((item) => item.value === props.mode) || modeOptions[1])

watch(
  () => props.src,
  async () => {
    playing.value = false
    currentTime.value = 0
    duration.value = 0
    sliderValue.value = 0
    await nextTick()
    audioRef.value?.load()
    if (props.autoplayOnSourceChange) {
      playCurrent()
    }
  },
)

watch(volume, (value) => {
  if (audioRef.value) {
    audioRef.value.volume = value / 100
  }
})

function formatTime(seconds: number): string {
  if (!Number.isFinite(seconds) || seconds <= 0) return '00:00'
  const total = Math.floor(seconds)
  const minutes = Math.floor(total / 60)
  const remain = total % 60
  return `${String(minutes).padStart(2, '0')}:${String(remain).padStart(2, '0')}`
}

async function togglePlay() {
  if (audioRef.value?.paused) {
    await playCurrent()
  } else {
    audioRef.value?.pause()
  }
}

async function playCurrent() {
  const audio = audioRef.value
  if (!audio) return
  try {
    audio.volume = volume.value / 100
    await audio.play()
  } catch {
    emit('error', '音频播放失败，请确认文件存在且浏览器支持该格式')
  }
}

function handleLoadedMetadata() {
  duration.value = Number.isFinite(audioRef.value?.duration) ? audioRef.value?.duration || 0 : 0
  emit('durationchange', duration.value)
}

function handleTimeUpdate() {
  const time = audioRef.value?.currentTime || 0
  currentTime.value = time
  if (!seeking.value) {
    sliderValue.value = time
  }
  emit('timeupdate', time)
}

function handleSliderInput(value: number) {
  seeking.value = true
  sliderValue.value = value
}

function handleSliderChange(value: number) {
  if (audioRef.value) {
    audioRef.value.currentTime = value
  }
  currentTime.value = value
  seeking.value = false
}

function handleError() {
  playing.value = false
  emit('playingChange', false)
  emit('error', '音频加载失败，请确认文件存在且浏览器支持该格式')
}

function handlePlay() {
  playing.value = true
  emit('playingChange', true)
}

function handlePause() {
  playing.value = false
  emit('playingChange', false)
}

async function handleEnded() {
  playing.value = false
  emit('playingChange', false)
  if (props.mode === 'repeat-one' && audioRef.value) {
    audioRef.value.currentTime = 0
    await playCurrent()
    return
  }
  emit('ended')
}

function cycleMode() {
  const currentIndex = modeOptions.findIndex((item) => item.value === props.mode)
  const nextIndex = currentIndex < 0 ? 0 : (currentIndex + 1) % modeOptions.length
  emit('modeChange', modeOptions[nextIndex].value)
}
</script>

<template>
  <section class="workbench-player" aria-label="播放器">
    <audio
      ref="audioRef"
      preload="metadata"
      :src="src"
      crossorigin="use-credentials"
      @play="handlePlay"
      @pause="handlePause"
      @ended="handleEnded"
      @loadedmetadata="handleLoadedMetadata"
      @timeupdate="handleTimeUpdate"
      @error="handleError"
    />
    <div class="player-main">
      <div class="player-info">
        <div class="player-title">{{ title }}</div>
        <div class="player-subtitle">{{ artist }} / {{ album }}</div>
      </div>
      <div class="player-controls">
        <el-tooltip content="上一首" placement="top">
          <el-button
            circle
            class="transport-button"
            :disabled="!canPrevious"
            aria-label="上一首"
            @click="emit('previous')"
          >
            <span class="transport-icon previous-icon" aria-hidden="true" />
          </el-button>
        </el-tooltip>
        <el-button
          class="transport-button play-button"
          circle
          :aria-label="playing ? '暂停' : '播放'"
          @click="togglePlay"
        >
          <span v-if="playing" class="pause-icon" aria-hidden="true">
            <span />
            <span />
          </span>
          <span v-else class="play-icon" aria-hidden="true" />
        </el-button>
        <el-tooltip content="下一首" placement="top">
          <el-button
            circle
            class="transport-button"
            :disabled="!canNext"
            aria-label="下一首"
            @click="emit('next')"
          >
            <span class="transport-icon next-icon" aria-hidden="true" />
          </el-button>
        </el-tooltip>
      </div>
      <div class="player-track">
        <el-slider
          :model-value="sliderValue"
          :min="0"
          :max="progressMax"
          :step="0.1"
          :show-tooltip="false"
          @input="handleSliderInput"
          @change="handleSliderChange"
        />
      </div>
      <div class="player-time">{{ formatTime(currentTime) }} / {{ formatTime(duration) }}</div>
      <div class="player-extra">
        <el-tooltip :content="currentMode.label" placement="top">
          <el-button text circle class="mode-button" @click="cycleMode">
            <el-icon><component :is="currentMode.icon" /></el-icon>
            <span v-if="mode === 'repeat-one'" class="mode-one">1</span>
          </el-button>
        </el-tooltip>
        <div class="volume-control">
          <el-icon><Mute v-if="volume === 0" /><Headset v-else /></el-icon>
          <el-slider
            v-model="volume"
            :min="0"
            :max="100"
            :show-tooltip="false"
            class="volume-slider"
            aria-label="音量"
          />
        </div>
      </div>
    </div>
  </section>
</template>

<style scoped>
.workbench-player {
  border: 1px solid var(--el-border-color-light);
  border-radius: 0 0 8px 8px;
  padding: 12px 16px;
  background: rgba(255, 255, 255, 0.2);
  backdrop-filter: blur(18px) saturate(1.15);
  box-shadow: 0 -10px 30px rgba(31, 45, 61, 0.08);
}
.player-main {
  display: grid;
  grid-template-columns: 170px 190px minmax(180px, 1fr) 112px 210px;
  align-items: center;
  gap: 18px;
}
.player-info {
  min-width: 0;
}
.player-controls {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 20px;
}
.transport-button {
  width: 46px;
  height: 46px;
  padding: 0;
  color: var(--xy-player-progress, var(--xy-primary, var(--el-color-primary)));
  background: color-mix(
    in srgb,
    var(--xy-player-progress, var(--xy-primary, var(--el-color-primary))) 10%,
    rgba(255, 255, 255, 0.88)
  );
  border-color: color-mix(
    in srgb,
    var(--xy-player-progress, var(--xy-primary, var(--el-color-primary))) 26%,
    var(--el-border-color-light)
  );
}
.transport-button:not(.is-disabled):hover {
  color: var(--xy-player-progress, var(--xy-primary, var(--el-color-primary)));
  background: color-mix(
    in srgb,
    var(--xy-player-progress, var(--xy-primary, var(--el-color-primary))) 16%,
    rgba(255, 255, 255, 0.92)
  );
  border-color: color-mix(
    in srgb,
    var(--xy-player-progress, var(--xy-primary, var(--el-color-primary))) 48%,
    var(--el-border-color)
  );
}
.transport-button :deep(span) {
  display: inline-flex;
  align-items: center;
  justify-content: center;
}
.transport-icon {
  position: relative;
  width: 26px;
  height: 26px;
  color: currentColor;
}
.transport-icon::before,
.transport-icon::after {
  position: absolute;
  top: 50%;
  content: '';
  transform: translateY(-50%);
}
.previous-icon::before,
.next-icon::before {
  width: 5px;
  height: 24px;
  border-radius: 999px;
  background: currentColor;
}
.previous-icon::before {
  left: 3px;
}
.next-icon::before {
  right: 3px;
}
.previous-icon::after {
  left: 8px;
  border-top: 11px solid transparent;
  border-right: 16px solid currentColor;
  border-bottom: 11px solid transparent;
}
.next-icon::after {
  right: 8px;
  border-top: 11px solid transparent;
  border-bottom: 11px solid transparent;
  border-left: 16px solid currentColor;
}
.play-button {
  width: 54px;
  height: 54px;
  color: #fff;
  background: var(--xy-player-progress, var(--xy-primary, var(--el-color-primary)));
  border-color: var(--xy-player-progress, var(--xy-primary, var(--el-color-primary)));
  box-shadow: 0 8px 18px
    color-mix(in srgb, var(--xy-player-progress, var(--xy-primary, var(--el-color-primary))) 28%, transparent);
}
.play-button:not(.is-disabled):hover {
  color: #fff;
  background: color-mix(
    in srgb,
    var(--xy-player-progress, var(--xy-primary, var(--el-color-primary))) 84%,
    white
  );
  border-color: color-mix(
    in srgb,
    var(--xy-player-progress, var(--xy-primary, var(--el-color-primary))) 84%,
    white
  );
}
.play-icon {
  width: 0;
  height: 0;
  margin-left: 4px;
  border-top: 13px solid transparent;
  border-bottom: 13px solid transparent;
  border-left: 19px solid #fff;
}
.pause-icon {
  gap: 7px;
}
.pause-icon span {
  width: 6px;
  height: 26px;
  border-radius: 999px;
  background: #fff;
}
.player-track {
  min-width: 0;
  padding-left: 8px;
}
.player-track :deep(.el-slider__bar),
.volume-slider :deep(.el-slider__bar) {
  background-color: var(--xy-player-progress, var(--xy-primary, var(--el-color-primary)));
}
.player-track :deep(.el-slider__button),
.volume-slider :deep(.el-slider__button) {
  border-color: var(--xy-player-progress, var(--xy-primary, var(--el-color-primary)));
}
.player-title {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--el-text-color-primary);
  font-size: 15px;
  font-weight: 700;
}
.player-subtitle {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  margin-top: 6px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.player-time {
  color: var(--el-text-color-secondary);
  font-size: 13px;
  text-align: right;
}
.player-extra {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
}
.mode-button {
  position: relative;
  color: var(--xy-player-progress, var(--xy-primary, var(--el-text-color-regular)));
}
.mode-button :deep(.el-icon) {
  font-size: 18px;
}
.mode-one {
  position: absolute;
  right: 0;
  bottom: 0;
  display: grid;
  width: 12px;
  height: 12px;
  place-items: center;
  border-radius: 999px;
  background: color-mix(in srgb, var(--xy-player-progress, var(--xy-primary, var(--el-color-primary))) 12%, white);
  font-size: 10px;
  font-weight: 800;
  line-height: 1;
}
.volume-control {
  display: grid;
  grid-template-columns: 18px minmax(96px, 1fr);
  align-items: center;
  gap: 8px;
  width: 150px;
  color: var(--el-text-color-secondary);
}
.volume-slider {
  min-width: 0;
}
@media (max-width: 720px) {
  .player-main {
    grid-template-columns: 1fr;
  }
  .player-controls,
  .player-track,
  .player-time,
  .player-extra {
    width: 100%;
  }
  .player-time {
    text-align: left;
  }
  .player-extra {
    justify-content: space-between;
  }
}
</style>
