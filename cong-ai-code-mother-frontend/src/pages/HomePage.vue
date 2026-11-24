<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { ArrowUpOutlined, ThunderboltOutlined } from '@ant-design/icons-vue'
import { useLoginUserStore } from '@/stores/loginUser'
import { addApp, listMyAppVoByPage, listGoodAppVoByPage } from '@/api/appController'
import { getDeployUrl } from '@/config/env'
import AppCard from '@/components/AppCard.vue'

const router = useRouter()
const loginUserStore = useLoginUserStore()

// 用户提示词
const userPrompt = ref('')
const creating = ref(false)

// 我的应用数据
const myApps = ref<API.AppVO[]>([])
const myAppsPage = reactive({
  current: 1,
  pageSize: 6,
  total: 0,
})

// 精选应用数据
const featuredApps = ref<API.AppVO[]>([])
const featuredAppsPage = reactive({
  current: 1,
  pageSize: 6,
  total: 0,
})

// 快捷模板
const templates = [
  {
    label: '个人博客',
    prompt:
      '创建一个现代化的个人博客网站，包含文章列表、详情页、分类标签、搜索功能、评论系统和个人简介页面。采用简洁的设计风格，支持响应式布局，文章支持 Markdown 格式。',
  },
  {
    label: '企业官网',
    prompt:
      '设计一个专业的企业官网，包含公司介绍、产品服务展示、新闻资讯、联系我们等页面。商务风格，包含轮播图、产品卡片、团队介绍与客户案例。',
  },
  {
    label: '在线商城',
    prompt:
      '构建一个功能完整的在线商城，包含商品展示、购物车、登录注册、订单管理与支付结算。现代化商品卡片布局，支持搜索筛选、评价和优惠券。',
  },
  {
    label: '作品集',
    prompt:
      '制作一个精美的作品展示网站，适合设计师、摄影师。包含作品画廊、项目详情、个人简历与联系方式，采用网格布局并支持图片预览。',
  },
]

const setPrompt = (prompt: string) => {
  userPrompt.value = prompt
}

// 创建应用
const createApp = async () => {
  if (!userPrompt.value.trim()) {
    message.warning('请输入应用描述')
    return
  }

  if (!loginUserStore.loginUser.id) {
    message.warning('请先登录')
    await router.push('/user/login')
    return
  }

  creating.value = true
  try {
    const res = await addApp({
      initPrompt: userPrompt.value.trim(),
    })

    if (res.data.code === 0 && res.data.data) {
      message.success('应用创建成功')
      const appId = String(res.data.data)
      await router.push(`/app/chat/${appId}`)
    } else {
      message.error('创建失败：' + res.data.message)
    }
  } catch (error) {
    console.error('创建应用失败：', error)
    message.error('创建失败，请重试')
  } finally {
    creating.value = false
  }
}

// 加载我的应用
const loadMyApps = async () => {
  if (!loginUserStore.loginUser.id) {
    myApps.value = []
    return
  }

  try {
    const res = await listMyAppVoByPage({
      pageNum: myAppsPage.current,
      pageSize: myAppsPage.pageSize,
      sortField: 'createTime',
      sortOrder: 'desc',
    })

    if (res.data.code === 0 && res.data.data) {
      myApps.value = res.data.data.records || []
      myAppsPage.total = res.data.data.totalRow || 0
    }
  } catch (error) {
    console.error('加载我的应用失败：', error)
  }
}

// 加载精选应用
const loadFeaturedApps = async () => {
  try {
    const res = await listGoodAppVoByPage({
      pageNum: featuredAppsPage.current,
      pageSize: featuredAppsPage.pageSize,
      sortField: 'createTime',
      sortOrder: 'desc',
    })

    if (res.data.code === 0 && res.data.data) {
      featuredApps.value = res.data.data.records || []
      featuredAppsPage.total = res.data.data.totalRow || 0
    }
  } catch (error) {
    console.error('加载精选应用失败：', error)
  }
}

// 查看对话
const viewChat = (appId: string | number | undefined) => {
  if (appId) {
    router.push(`/app/chat/${appId}?view=1`)
  }
}

// 查看作品
const viewWork = (app: API.AppVO) => {
  if (app.deployKey) {
    const url = getDeployUrl(app.deployKey)
    window.open(url, '_blank')
  }
}

// 页面加载时获取数据
onMounted(() => {
  loadMyApps()
  loadFeaturedApps()
})
</script>

<template>
  <div id="home">
    <div class="home-inner">
      <!-- Hero -->
      <section class="hero">
        <span class="badge">
          <ThunderboltOutlined class="badge-icon" />
          AI 驱动 · 一句话生成完整网站
        </span>
        <h1 class="hero-title">
          把想法，<span class="hero-accent">变成网站</span>
        </h1>
        <p class="hero-subtitle">
          描述你想要的页面，AI 自动完成设计、编码与部署，从灵感到上线只需要几分钟
        </p>
      </section>

      <!-- 输入卡片 -->
      <section class="composer">
        <a-textarea
          v-model:value="userPrompt"
          class="composer-input"
          placeholder="例如：帮我做一个咖啡店官网，包含菜单、门店展示和在线预订"
          :rows="3"
          :maxlength="1000"
          :bordered="false"
        />
        <div class="composer-bar">
          <span class="composer-tip">支持 HTML 单页 / 多文件 / Vue 工程 · 最多 1000 字</span>
          <a-button
            type="primary"
            class="composer-submit"
            :loading="creating"
            @click="createApp"
          >
            <template #icon><ArrowUpOutlined /></template>
            生成网站
          </a-button>
        </div>
      </section>

      <!-- 快捷模板 -->
      <div class="chips">
        <span class="chips-label">试试这些</span>
        <button
          v-for="item in templates"
          :key="item.label"
          type="button"
          class="chip"
          @click="setPrompt(item.prompt)"
        >
          {{ item.label }}
        </button>
      </div>

      <!-- 我的作品 -->
      <section class="section">
        <header class="section-head">
          <div class="section-heading">
            <h2>我的作品</h2>
            <p>创建过的应用，随时继续对话或再次部署</p>
          </div>
          <span v-if="myAppsPage.total" class="section-count">{{ myAppsPage.total }} 个</span>
        </header>

        <div v-if="myApps.length" class="app-grid">
          <AppCard
            v-for="app in myApps"
            :key="app.id"
            :app="app"
            @view-chat="viewChat"
            @view-work="viewWork"
          />
        </div>
        <div v-else class="empty">
          <div class="empty-icon">✦</div>
          <p class="empty-title">还没有作品</p>
          <p class="empty-desc">在上方输入一句话，创建你的第一个网站</p>
        </div>

        <div v-if="myAppsPage.total > myAppsPage.pageSize" class="pagination">
          <a-pagination
            v-model:current="myAppsPage.current"
            v-model:page-size="myAppsPage.pageSize"
            :total="myAppsPage.total"
            :show-size-changer="false"
            @change="loadMyApps"
          />
        </div>
      </section>

      <!-- 精选案例 -->
      <section class="section">
        <header class="section-head">
          <div class="section-heading">
            <h2>精选案例</h2>
            <p>看看别人用一句话做出了什么</p>
          </div>
          <span v-if="featuredAppsPage.total" class="section-count">
            {{ featuredAppsPage.total }} 个
          </span>
        </header>

        <div v-if="featuredApps.length" class="app-grid">
          <AppCard
            v-for="app in featuredApps"
            :key="app.id"
            :app="app"
            :featured="true"
            @view-chat="viewChat"
            @view-work="viewWork"
          />
        </div>
        <div v-else class="empty">
          <div class="empty-icon">✦</div>
          <p class="empty-title">暂无精选案例</p>
          <p class="empty-desc">管理员可以在后台把优秀应用设为精选</p>
        </div>

        <div v-if="featuredAppsPage.total > featuredAppsPage.pageSize" class="pagination">
          <a-pagination
            v-model:current="featuredAppsPage.current"
            v-model:page-size="featuredAppsPage.pageSize"
            :total="featuredAppsPage.total"
            :show-size-changer="false"
            @change="loadFeaturedApps"
          />
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
#home {
  min-height: calc(100vh - 64px);
  padding: 56px 24px 80px;
  background:
    radial-gradient(760px 420px at 18% -8%, rgba(91, 91, 214, 0.12), transparent 70%),
    radial-gradient(680px 400px at 84% 0%, rgba(139, 92, 246, 0.1), transparent 70%),
    var(--bg);
}

.home-inner {
  max-width: 1120px;
  margin: 0 auto;
}

/* ---------- Hero ---------- */
.hero {
  text-align: center;
  padding: 8px 0 32px;
}

.badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 5px 14px;
  border-radius: 999px;
  background: var(--surface);
  border: 1px solid var(--border);
  color: var(--text-2);
  font-size: 12.5px;
  box-shadow: var(--shadow-xs);
}

.badge-icon {
  color: var(--brand-1);
  font-size: 13px;
}

.hero-title {
  margin: 20px 0 14px;
  font-size: clamp(32px, 4.4vw, 52px);
  font-weight: 800;
  line-height: 1.15;
  letter-spacing: -0.025em;
  color: var(--text);
}

.hero-accent {
  background: var(--brand-gradient);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}

.hero-subtitle {
  max-width: 560px;
  margin: 0 auto;
  color: var(--text-2);
  font-size: 15px;
  line-height: 1.8;
}

/* ---------- 输入卡片 ---------- */
.composer {
  max-width: 760px;
  margin: 0 auto;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-xl);
  box-shadow: var(--shadow);
  overflow: hidden;
  transition:
    border-color 0.2s ease,
    box-shadow 0.2s ease;
}

.composer:focus-within {
  border-color: rgba(91, 91, 214, 0.45);
  box-shadow:
    0 0 0 4px rgba(91, 91, 214, 0.08),
    var(--shadow);
}

.composer-input {
  padding: 20px 22px 8px;
  font-size: 15px;
  line-height: 1.7;
  color: var(--text);
  resize: none;
  background: transparent;
}

.composer-input::placeholder {
  color: var(--text-3);
}

.composer-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 16px 14px 22px;
  border-top: 1px solid #f3f4f9;
}

.composer-tip {
  font-size: 12px;
  color: var(--text-3);
}

.composer-submit {
  height: 38px;
  padding: 0 20px;
  border-radius: 12px;
  font-weight: 600;
  background: var(--brand-gradient);
  border: none;
  box-shadow: 0 12px 26px -14px rgba(91, 91, 214, 0.9);
}

/* ---------- 快捷模板 ---------- */
.chips {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-wrap: wrap;
  gap: 8px;
  margin: 22px auto 64px;
}

.chips-label {
  margin-right: 4px;
  font-size: 12.5px;
  color: var(--text-3);
}

.chip {
  padding: 6px 14px;
  border-radius: 999px;
  border: 1px solid var(--border);
  background: var(--surface);
  color: var(--text-2);
  font-size: 13px;
  cursor: pointer;
  transition:
    color 0.18s ease,
    border-color 0.18s ease,
    transform 0.18s ease;
}

.chip:hover {
  color: var(--brand-1);
  border-color: rgba(91, 91, 214, 0.45);
  transform: translateY(-1px);
}

/* ---------- 区块 ---------- */
.section {
  margin-bottom: 56px;
}

.section-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 18px;
}

.section-heading h2 {
  margin: 0;
  font-size: 20px;
  font-weight: 700;
  color: var(--text);
}

.section-heading p {
  margin: 5px 0 0;
  font-size: 13px;
  color: var(--text-3);
}

.section-count {
  padding: 2px 10px;
  border-radius: 999px;
  background: var(--surface);
  border: 1px solid var(--border);
  color: var(--text-2);
  font-size: 12px;
}

.app-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 20px;
}

/* ---------- 空状态 ---------- */
.empty {
  padding: 48px 24px;
  text-align: center;
  background: var(--surface);
  border: 1px dashed var(--border-strong);
  border-radius: var(--radius-lg);
}

.empty-icon {
  font-size: 26px;
  color: var(--brand-1);
  opacity: 0.6;
}

.empty-title {
  margin: 10px 0 4px;
  font-size: 14px;
  font-weight: 600;
  color: var(--text);
}

.empty-desc {
  margin: 0;
  font-size: 13px;
  color: var(--text-3);
}

.pagination {
  display: flex;
  justify-content: center;
  margin-top: 24px;
}

@media (max-width: 768px) {
  #home {
    padding: 32px 16px 56px;
  }

  .hero {
    padding-bottom: 24px;
  }

  .composer-bar {
    flex-direction: column;
    align-items: stretch;
  }

  .composer-submit {
    width: 100%;
  }

  .chips {
    margin-bottom: 44px;
  }

  .app-grid {
    grid-template-columns: 1fr;
  }
}
</style>
