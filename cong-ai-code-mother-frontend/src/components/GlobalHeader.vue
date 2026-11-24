<template>
  <header class="site-header">
    <div class="header-inner">
      <!-- Logo -->
      <RouterLink to="/" class="brand">
        <img class="logo" src="@/assets/logo.jpg" alt="Logo" />
        <span class="site-title">AI 应用生成</span>
      </RouterLink>

      <!-- 导航 -->
      <nav class="nav">
        <RouterLink
          v-for="item in navItems"
          :key="item.path"
          :to="item.path"
          class="nav-link"
          :class="{ 'is-active': isActive(item.path) }"
        >
          {{ item.label }}
        </RouterLink>
      </nav>

      <!-- 用户区 -->
      <div class="header-right">
        <template v-if="loginUserStore.loginUser.id">
          <span class="quota-pill">额度 {{ loginUserStore.loginUser.quota ?? 0 }}</span>

          <a-dropdown placement="bottomRight">
            <button class="user-btn" type="button">
              <UserAvatar
                :size="28"
                :src="loginUserStore.loginUser.userAvatar"
                :name="loginUserStore.loginUser.userName"
              />
              <span class="user-name">{{ loginUserStore.loginUser.userName ?? '无名' }}</span>
            </button>
            <template #overlay>
              <a-menu>
                <a-menu-item key="logout" @click="doLogout">
                  <LogoutOutlined />
                  <span class="logout-text">退出登录</span>
                </a-menu-item>
              </a-menu>
            </template>
          </a-dropdown>
        </template>

        <RouterLink v-else to="/user/login">
          <a-button type="primary" size="small" class="login-btn">登录</a-button>
        </RouterLink>
      </div>
    </div>
  </header>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { useLoginUserStore } from '@/stores/loginUser.ts'
import { userLogout } from '@/api/userController.ts'
import { LogoutOutlined } from '@ant-design/icons-vue'
import UserAvatar from '@/components/UserAvatar.vue'

const loginUserStore = useLoginUserStore()
const router = useRouter()
const route = useRoute()

// 导航项：管理页面仅管理员可见
const navItems = computed(() => {
  const items = [{ path: '/', label: '主页' }]
  if (loginUserStore.loginUser.userRole === 'admin') {
    items.push({ path: '/admin/userManage', label: '用户管理' })
    items.push({ path: '/admin/appManage', label: '应用管理' })
  }
  return items
})

const isActive = (path: string) => {
  if (path === '/') {
    return route.path === '/'
  }
  return route.path.startsWith(path)
}

// 退出登录
const doLogout = async () => {
  const res = await userLogout()
  if (res.data.code === 0) {
    loginUserStore.setLoginUser({
      userName: '未登录',
    })
    message.success('退出登录成功')
    await router.push('/user/login')
  } else {
    message.error('退出登录失败，' + res.data.message)
  }
}
</script>

<style scoped>
.site-header {
  position: sticky;
  top: 0;
  z-index: 100;
  height: 64px;
  background: rgba(255, 255, 255, 0.85);
  backdrop-filter: blur(16px);
  border-bottom: 1px solid var(--border);
}

.header-inner {
  display: flex;
  align-items: center;
  gap: 32px;
  width: 100%;
  max-width: 1200px;
  height: 100%;
  margin: 0 auto;
  padding: 0 24px;
}

/* Logo */
.brand {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
  color: inherit;
}

.logo {
  width: 32px;
  height: 32px;
  border-radius: 9px;
  object-fit: cover;
  box-shadow: var(--shadow-xs);
}

.site-title {
  font-size: 15.5px;
  font-weight: 700;
  letter-spacing: -0.01em;
  color: var(--text);
  white-space: nowrap;
}

/* 导航 */
.nav {
  display: flex;
  align-items: center;
  gap: 4px;
  flex: 1;
  min-width: 0;
}

.nav-link {
  position: relative;
  padding: 7px 14px;
  border-radius: 999px;
  color: var(--text-2);
  font-size: 14px;
  line-height: 1.2;
  white-space: nowrap;
  transition:
    color 0.18s ease,
    background 0.18s ease;
}

.nav-link:hover {
  color: var(--text);
  background: #f4f5fa;
}

.nav-link.is-active {
  color: var(--brand-1);
  background: var(--brand-soft);
  font-weight: 600;
}

/* 右侧 */
.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-shrink: 0;
}

.quota-pill {
  display: inline-flex;
  align-items: center;
  height: 26px;
  padding: 0 12px;
  border-radius: 999px;
  background: var(--brand-soft);
  border: 1px solid rgba(91, 91, 214, 0.16);
  color: var(--brand-1);
  font-size: 12px;
  font-weight: 500;
  white-space: nowrap;
}

.user-btn {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 3px 10px 3px 3px;
  border: 1px solid transparent;
  border-radius: 999px;
  background: transparent;
  color: var(--text);
  cursor: pointer;
  transition:
    background 0.18s ease,
    border-color 0.18s ease;
}

.user-btn:hover {
  background: #f7f8fc;
  border-color: var(--border);
}

.user-name {
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13.5px;
  color: var(--text-2);
}

.login-btn {
  height: 32px;
  padding: 0 18px;
  border-radius: 999px;
  font-weight: 500;
}

.logout-text {
  margin-left: 6px;
}

@media (max-width: 860px) {
  .header-inner {
    gap: 12px;
    padding: 0 16px;
  }

  .site-title,
  .quota-pill,
  .user-name {
    display: none;
  }

  .nav {
    justify-content: flex-end;
  }
}
</style>
