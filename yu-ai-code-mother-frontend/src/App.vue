<script setup lang="ts">
import BasicLayout from '@/layouts/BasicLayout.vue'

// Ant Design 主题令牌：与 styles/theme.css 中的设计变量保持一致
const themeConfig = {
  token: {
    colorPrimary: '#5b5bd6',
    colorInfo: '#5b5bd6',
    colorSuccess: '#10b981',
    colorWarning: '#f59e0b',
    colorError: '#ef4444',
    borderRadius: 10,
    borderRadiusLG: 14,
    fontSize: 14,
    controlHeight: 38,
    wireframe: false,
    fontFamily:
      "'Inter', -apple-system, BlinkMacSystemFont, 'PingFang SC', 'Microsoft YaHei', 'Segoe UI', Roboto, sans-serif",
  },
}
</script>

<template>
  <a-config-provider :theme="themeConfig">
    <BasicLayout />
  </a-config-provider>
</template>
