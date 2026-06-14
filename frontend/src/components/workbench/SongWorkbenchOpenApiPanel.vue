<script setup lang="ts">
import { computed } from 'vue'
import type { OpenApiPreview } from '../../api/music'

const props = defineProps<{ preview: OpenApiPreview | null; error: string }>()
const formatted = computed(() => props.preview ? JSON.stringify(props.preview, null, 2) : '')
</script>

<template>
  <el-alert
    v-if="error"
    type="error"
    show-icon
    :title="error"
  />
  <pre v-else class="json-preview">{{ formatted }}</pre>
</template>

<style scoped>
.json-preview {
  min-height: 360px;
  margin: 0;
  padding: 16px;
  overflow: auto;
  border-radius: 8px;
  border: 1px solid var(--el-border-color-light);
  background: var(--el-fill-color-lighter);
  color: var(--el-text-color-primary);
  font-size: 13px;
  line-height: 1.6;
}
</style>
