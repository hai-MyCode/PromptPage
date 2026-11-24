<template>
  <a-layout-footer class="footer">
    <div class="footer-content">
      <p class="copyright">AI 应用生成平台 · 仅供学习交流</p>
    </div>
  </a-layout-footer>
</template>

<script setup lang="ts">
// 无需额外的响应式数据
</script>

<style scoped>
.footer {
  padding: 26px 24px;
  margin-top: 24px;
  background: transparent;
  border-top: 1px solid var(--border);
  text-align: center;
}

.copyright {
  margin: 0;
  color: var(--text-3);
  font-size: 13px;
}
</style>
