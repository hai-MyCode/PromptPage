<template>
  <span class="user-avatar" :class="`is-${shape}`" :style="rootStyle" :title="displayName">
    <img v-if="src && !broken" :src="src" alt="" @error="broken = true" />
    <span v-else class="letter">{{ letter }}</span>
  </span>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'

interface Props {
  src?: string | null
  name?: string | null
  size?: number
  shape?: 'circle' | 'square'
}

const props = withDefaults(defineProps<Props>(), {
  src: '',
  name: '',
  size: 32,
  shape: 'circle',
})

// 图片加载失败时回退到文字头像
const broken = ref(false)
watch(
  () => props.src,
  () => {
    broken.value = false
  },
)

const displayName = computed(() => (props.name || '').trim() || '用户')

const letter = computed(() => {
  const chars = Array.from(displayName.value)
  return (chars[0] || 'U').toUpperCase()
})

// 根据昵称生成固定颜色，保证同一用户每次颜色一致
const PALETTE = [
  '#5b5bd6',
  '#7c3aed',
  '#0ea5e9',
  '#0d9488',
  '#10b981',
  '#f59e0b',
  '#ef4444',
  '#ec4899',
]

const color = computed(() => {
  let hash = 0
  for (const ch of displayName.value) {
    hash = (hash * 31 + (ch.codePointAt(0) ?? 0)) >>> 0
  }
  return PALETTE[hash % PALETTE.length]
})

const rootStyle = computed(() => {
  const size = props.size
  return {
    width: `${size}px`,
    height: `${size}px`,
    fontSize: `${Math.max(11, Math.round(size * 0.42))}px`,
    background: props.src && !broken.value ? '#f2f3f8' : color.value,
  }
})
</script>

<style scoped>
.user-avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  overflow: hidden;
  color: #fff;
  font-weight: 600;
  line-height: 1;
  user-select: none;
}

.user-avatar.is-circle {
  border-radius: 50%;
}

.user-avatar.is-square {
  border-radius: 8px;
}

.user-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.letter {
  transform: translateY(0.5px);
}
</style>
