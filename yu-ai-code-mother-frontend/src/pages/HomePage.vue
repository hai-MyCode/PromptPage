<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
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

// 设置提示词
const setPrompt = (prompt: string) => {
  userPrompt.value = prompt
}

// 优化提示词功能已移除

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
      // 跳转到对话页面，确保ID是字符串类型
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

// 格式化时间函数已移除，不再需要显示创建时间

// 页面加载时获取数据
onMounted(() => {
  loadMyApps()
  loadFeaturedApps()

  // 鼠标跟随光效
  const handleMouseMove = (e: MouseEvent) => {
    const { clientX, clientY } = e
    const { innerWidth, innerHeight } = window
    const x = (clientX / innerWidth) * 100
    const y = (clientY / innerHeight) * 100

    document.documentElement.style.setProperty('--mouse-x', `${x}%`)
    document.documentElement.style.setProperty('--mouse-y', `${y}%`)
  }

  document.addEventListener('mousemove', handleMouseMove)

  // 清理事件监听器
  return () => {
    document.removeEventListener('mousemove', handleMouseMove)
  }
})
</script>

<template>
  <div id="homePage">
    <div class="container">
      <!-- 网站标题和描述 -->
      <div class="hero-section">
        <div class="hero-badge">
          <span class="hero-badge-dot"></span>
          AI 驱动 · 一句话生成完整网站
        </div>
        <h1 class="hero-title">把想法，<span class="gradient-text">变成网站</span></h1>
        <p class="hero-description">
          描述你想要的页面，AI 自动完成设计、编码与部署，从灵感到上线只需要几分钟
        </p>
      </div>

      <!-- 用户提示词输入框 -->
      <div class="input-section">
        <a-textarea
          v-model:value="userPrompt"
          placeholder="帮我创建个人博客网站"
          :rows="4"
          :maxlength="1000"
          class="prompt-input"
        />
        <div class="input-actions">
          <a-button type="primary" size="large" @click="createApp" :loading="creating">
            <template #icon>
              <span>↑</span>
            </template>
          </a-button>
        </div>
        <div class="input-hint">
          支持 HTML 单页、多文件与 Vue 工程三种产出 · Enter 直达
        </div>
      </div>

      <!-- 快捷按钮 -->
      <div class="quick-actions">
        <a-button
          type="default"
          @click="
            setPrompt(
              '创建一个现代化的个人博客网站，包含文章列表、详情页、分类标签、搜索功能、评论系统和个人简介页面。采用简洁的设计风格，支持响应式布局，文章支持Markdown格式，首页展示最新文章和热门推荐。',
            )
          "
          >个人博客网站</a-button
        >
        <a-button
          type="default"
          @click="
            setPrompt(
              '设计一个专业的企业官网，包含公司介绍、产品服务展示、新闻资讯、联系我们等页面。采用商务风格的设计，包含轮播图、产品展示卡片、团队介绍、客户案例展示，支持多语言切换和在线客服功能。',
            )
          "
          >企业官网</a-button
        >
        <a-button
          type="default"
          @click="
            setPrompt(
              '构建一个功能完整的在线商城，包含商品展示、购物车、用户注册登录、订单管理、支付结算等功能。设计现代化的商品卡片布局，支持商品搜索筛选、用户评价、优惠券系统和会员积分功能。',
            )
          "
          >在线商城</a-button
        >
        <a-button
          type="default"
          @click="
            setPrompt(
              '制作一个精美的作品展示网站，适合设计师、摄影师、艺术家等创作者。包含作品画廊、项目详情页、个人简历、联系方式等模块。采用瀑布流或网格布局展示作品，支持图片放大预览和作品分类筛选。',
            )
          "
          >作品展示网站</a-button
        >
      </div>

      <!-- 我的作品 -->
      <div class="section">
        <div class="section-head">
          <div>
            <h2 class="section-title">我的作品</h2>
            <p class="section-sub">你创建过的应用，随时继续对话或再次部署</p>
          </div>
        </div>
        <div class="app-grid">
          <AppCard
            v-for="app in myApps"
            :key="app.id"
            :app="app"
            @view-chat="viewChat"
            @view-work="viewWork"
          />
        </div>
        <div class="pagination-wrapper">
          <a-pagination
            v-model:current="myAppsPage.current"
            v-model:page-size="myAppsPage.pageSize"
            :total="myAppsPage.total"
            :show-size-changer="false"
            :show-total="(total: number) => `共 ${total} 个应用`"
            @change="loadMyApps"
          />
        </div>
      </div>

      <!-- 精选案例 -->
      <div class="section">
        <div class="section-head">
          <div>
            <h2 class="section-title">精选案例</h2>
            <p class="section-sub">看看别人用一句话做出了什么</p>
          </div>
        </div>
        <div class="featured-grid">
          <AppCard
            v-for="app in featuredApps"
            :key="app.id"
            :app="app"
            :featured="true"
            @view-chat="viewChat"
            @view-work="viewWork"
          />
        </div>
        <div class="pagination-wrapper">
          <a-pagination
            v-model:current="featuredAppsPage.current"
            v-model:page-size="featuredAppsPage.pageSize"
            :total="featuredAppsPage.total"
            :show-size-changer="false"
            :show-total="(total: number) => `共 ${total} 个案例`"
            @change="loadFeaturedApps"
          />
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
#homePage {
  position: relative;
  min-height: 100vh;
  padding-bottom: 20px;
  overflow: hidden;
  background:
    radial-gradient(900px 520px at 10% -10%, rgba(91, 91, 214, 0.14), transparent 70%),
    radial-gradient(820px 480px at 92% 0%, rgba(139, 92, 246, 0.14), transparent 70%),
    radial-gradient(700px 520px at 50% 112%, rgba(16, 185, 129, 0.08), transparent 70%),
    var(--bg);
}

/* 极淡的网格底纹 */
#homePage::before {
  content: '';
  position: absolute;
  inset: 0;
  pointer-events: none;
  background-image:
    linear-gradient(rgba(28, 31, 43, 0.035) 1px, transparent 1px),
    linear-gradient(90deg, rgba(28, 31, 43, 0.035) 1px, transparent 1px);
  background-size: 56px 56px;
  mask-image: radial-gradient(circle at 50% 0%, #000 0%, transparent 72%);
  -webkit-mask-image: radial-gradient(circle at 50% 0%, #000 0%, transparent 72%);
}

/* 鼠标跟随光晕 */
#homePage::after {
  content: '';
  position: absolute;
  inset: 0;
  pointer-events: none;
  background: radial-gradient(
    620px circle at var(--mouse-x, 50%) var(--mouse-y, 20%),
    rgba(91, 91, 214, 0.08),
    transparent 65%
  );
  transition: background 0.2s ease;
}

.container {
  position: relative;
  z-index: 1;
  width: 100%;
  max-width: 1240px;
  margin: 0 auto;
  padding: 0 24px 72px;
}

/* ---------- Hero ---------- */
.hero-section {
  padding: 84px 0 36px;
  text-align: center;
}

.hero-badge {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 6px 14px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.78);
  border: 1px solid var(--border);
  box-shadow: var(--shadow-xs);
  backdrop-filter: blur(12px);
  color: var(--text-2);
  font-size: 13px;
}

.hero-badge-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--success);
  box-shadow: 0 0 0 4px rgba(16, 185, 129, 0.14);
}

.hero-title {
  margin: 22px 0 16px;
  font-size: 52px;
  font-weight: 800;
  line-height: 1.16;
  letter-spacing: -0.02em;
  color: var(--text);
}

.hero-description {
  max-width: 620px;
  margin: 0 auto;
  font-size: 16.5px;
  line-height: 1.75;
  color: var(--text-2);
}

/* ---------- 输入区 ---------- */
.input-section {
  position: relative;
  max-width: 820px;
  margin: 36px auto 0;
}

.prompt-input {
  width: 100%;
  padding: 22px 68px 22px 22px;
  border-radius: var(--radius-lg);
  border: 1px solid var(--border);
  background: var(--surface);
  box-shadow: var(--shadow);
  font-size: 15px;
  line-height: 1.7;
  color: var(--text);
  resize: none;
  transition:
    border-color 0.2s ease,
    box-shadow 0.2s ease,
    transform 0.2s ease;
}

.prompt-input::placeholder {
  color: var(--text-3);
}

.prompt-input:hover {
  border-color: var(--border-strong);
}

.prompt-input:focus {
  outline: none;
  border-color: rgba(91, 91, 214, 0.55);
  box-shadow:
    0 0 0 4px rgba(91, 91, 214, 0.1),
    var(--shadow);
}

.input-actions {
  position: absolute;
  right: 14px;
  bottom: 18px;
}

.input-actions :deep(.ant-btn) {
  width: 44px;
  height: 44px;
  padding: 0;
  border-radius: var(--radius-sm);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  background: var(--brand-gradient);
  border: none;
  color: #fff;
  box-shadow: 0 10px 24px -12px rgba(91, 91, 214, 0.9);
}

.input-hint {
  margin-top: 12px;
  text-align: center;
  font-size: 12.5px;
  color: var(--text-3);
}

/* ---------- 快捷模板 ---------- */
.quick-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 10px;
  margin: 22px 0 64px;
}

.quick-actions :deep(.ant-btn) {
  height: 36px;
  padding: 0 18px;
  border-radius: 999px;
  background: var(--surface);
  border: 1px solid var(--border);
  color: var(--text-2);
  box-shadow: var(--shadow-xs);
}

.quick-actions :deep(.ant-btn:hover) {
  color: var(--brand-1);
  border-color: rgba(91, 91, 214, 0.45);
  transform: translateY(-1px);
  box-shadow: 0 10px 22px -16px rgba(91, 91, 214, 0.8);
}

/* ---------- 作品列表 ---------- */
.section {
  margin-bottom: 56px;
}

.section-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 22px;
}

.section-title {
  margin: 0;
  font-size: 22px;
  font-weight: 700;
  color: var(--text);
}

.section-sub {
  margin: 6px 0 0;
  font-size: 13.5px;
  color: var(--text-3);
}

.app-grid,
.featured-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 20px;
}

.pagination-wrapper {
  display: flex;
  justify-content: center;
  margin-top: 28px;
}

@media (max-width: 900px) {
  .hero-section {
    padding: 56px 0 24px;
  }

  .hero-title {
    font-size: 36px;
  }

  .hero-description {
    font-size: 15px;
  }

  .input-section {
    margin-top: 28px;
  }

  .quick-actions {
    margin-bottom: 48px;
  }

  .app-grid,
  .featured-grid {
    grid-template-columns: 1fr;
  }
}
</style>
