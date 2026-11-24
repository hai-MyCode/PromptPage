<template>
  <div class="user-info">
    <UserAvatar :src="user?.userAvatar" :name="user?.userName" :size="avatarSize" />
    <span v-if="showName" class="user-name">{{ user?.userName || '未知用户' }}</span>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import UserAvatar from '@/components/UserAvatar.vue'

interface Props {
  user?: API.UserVO
  size?: number | 'small' | 'default' | 'large'
  showName?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  size: 'default',
  showName: true,
})

const SIZE_MAP: Record<string, number> = {
  small: 24,
  default: 32,
  large: 40,
}

const avatarSize = computed(() =>
  typeof props.size === 'number' ? props.size : (SIZE_MAP[props.size] ?? 32),
)
</script>

<style scoped>
.user-info {
  display: flex;
  align-items: center;
  gap: 8px;
}

.user-name {
  font-size: 14px;
  color: var(--text);
}
</style>
