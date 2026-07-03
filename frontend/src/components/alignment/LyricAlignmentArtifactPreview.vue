<script setup lang="ts">
interface ArtifactState {
  loading: boolean
  loaded: boolean
  content: string
  error: string
}

defineProps<{
  title: string
  state: ArtifactState
  content: string
  json?: boolean
}>()

defineEmits<{
  (event: 'refresh'): void
}>()
</script>

<template>
  <div v-loading="state.loading" class="artifact-panel">
    <div class="artifact-header">
      <span>{{ title }}</span>
      <el-button size="small" @click="$emit('refresh')">刷新</el-button>
    </div>
    <el-alert v-if="state.error" type="warning" show-icon :title="state.error" />
    <pre v-else-if="content" class="text-preview" :class="{ json }">{{ content }}</pre>
    <el-empty v-else-if="state.loaded" description="结果文件为空" :image-size="90" />
    <el-empty v-else description="点击标签后加载结果文件" :image-size="90" />
  </div>
</template>

<style scoped>
.artifact-panel {
  display: flex;
  flex-direction: column;
  min-height: 360px;
  gap: 10px;
}
.artifact-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  color: var(--el-text-color-primary);
  font-size: 14px;
  font-weight: 700;
}
.text-preview {
  flex: 1;
  min-height: 320px;
  max-height: min(62vh, 680px);
  margin: 0;
  overflow: auto;
  padding: 14px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  background: color-mix(in srgb, var(--el-fill-color-lighter) 72%, transparent);
  color: var(--el-text-color-primary);
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 12px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
}
.text-preview.json {
  white-space: pre;
}
</style>
