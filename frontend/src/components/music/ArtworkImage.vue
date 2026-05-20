<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { PictureFilled } from '@element-plus/icons-vue'

const props = withDefaults(defineProps<{
  src?: string | null
  fileExists?: boolean | null
  alt?: string
  size?: number
  radius?: number
}>(), {
  alt: '',
  size: 72,
  radius: 6,
})

const failed = ref(false)

const canShowImage = computed(() => Boolean(props.src && props.fileExists !== false && !failed.value))

const boxStyle = computed(() => ({
  width: `${props.size}px`,
  height: `${props.size}px`,
  borderRadius: `${props.radius}px`,
}))

watch(() => props.src, () => {
  failed.value = false
})
</script>

<template>
  <div class="artwork-image" :style="boxStyle">
    <img
      v-if="canShowImage"
      :src="src || ''"
      :alt="alt"
      class="artwork-img"
      @error="failed = true"
    />
    <div v-else class="artwork-placeholder">
      <el-icon :size="Math.max(20, Math.floor(size * 0.38))"><PictureFilled /></el-icon>
    </div>
  </div>
</template>

<style scoped>
.artwork-image {
  overflow: hidden;
  background: #f0f2f5;
  border: 1px solid #e4e7ed;
  flex: none;
}
.artwork-img,
.artwork-placeholder {
  width: 100%;
  height: 100%;
}
.artwork-img {
  display: block;
  object-fit: cover;
}
.artwork-placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  color: #a8abb2;
  background: linear-gradient(135deg, #f5f7fa 0%, #ebeef5 100%);
}
</style>
