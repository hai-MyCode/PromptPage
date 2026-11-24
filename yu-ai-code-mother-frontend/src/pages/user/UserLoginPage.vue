<template>
  <div class="auth-page">
    <div id="userLoginPage">
      <div class="auth-brand"><span class="brand-dot"></span>AI 应用生成</div>
      <h2 class="title">欢迎回来</h2>
      <div class="desc">登录后继续用一句话构建你的网站</div>
    <a-form :model="formState" name="basic" autocomplete="off" @finish="handleSubmit">
      <a-form-item name="userAccount" :rules="[{ required: true, message: '请输入账号' }]">
        <a-input v-model:value="formState.userAccount" placeholder="请输入账号" />
      </a-form-item>
      <a-form-item
        name="userPassword"
        :rules="[
          { required: true, message: '请输入密码' },
          { min: 8, message: '密码长度不能小于 8 位' },
        ]"
      >
        <a-input-password v-model:value="formState.userPassword" placeholder="请输入密码" />
      </a-form-item>
      <div class="tips">
        没有账号
        <RouterLink to="/user/register">去注册</RouterLink>
      </div>
      <a-form-item>
        <a-button type="primary" html-type="submit" style="width: 100%">登录</a-button>
      </a-form-item>
    </a-form>
    </div>
  </div>
</template>
<script lang="ts" setup>
import { reactive } from 'vue'
import { userLogin } from '@/api/userController.ts'
import { useLoginUserStore } from '@/stores/loginUser.ts'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'

const formState = reactive<API.UserLoginRequest>({
  userAccount: '',
  userPassword: '',
})

const router = useRouter()
const loginUserStore = useLoginUserStore()

/**
 * 提交表单
 * @param values
 */
const handleSubmit = async (values: any) => {
  const res = await userLogin(values)
  // 登录成功，把登录态保存到全局状态中
  if (res.data.code === 0 && res.data.data) {
    await loginUserStore.fetchLoginUser()
    message.success('登录成功')
    router.push({
      path: '/',
      replace: true,
    })
  } else {
    message.error('登录失败，' + res.data.message)
  }
}
</script>

<style scoped>
.auth-page {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: calc(100vh - 64px);
  padding: 40px 20px 72px;
  background:
    radial-gradient(720px 420px at 15% 0%, rgba(91, 91, 214, 0.12), transparent 70%),
    radial-gradient(680px 420px at 85% 8%, rgba(139, 92, 246, 0.12), transparent 70%),
    var(--bg);
}

#userLoginPage {
  width: 100%;
  max-width: 430px;
  padding: 38px 36px 30px;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-xl);
  box-shadow: var(--shadow-lg);
}

.auth-brand {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 4px 12px;
  border-radius: 999px;
  background: var(--brand-soft);
  color: var(--brand-1);
  font-size: 12px;
  font-weight: 500;
}

.brand-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--brand-1);
}

.title {
  margin: 18px 0 6px;
  font-size: 24px;
  font-weight: 700;
  color: var(--text);
}

.desc {
  margin-bottom: 26px;
  color: var(--text-3);
  font-size: 13.5px;
}

.tips {
  margin: 2px 0 18px;
  text-align: right;
  color: var(--text-3);
  font-size: 13px;
}

.tips a {
  color: var(--brand-1);
  font-weight: 500;
}

:deep(.ant-form-item) {
  margin-bottom: 18px;
}

:deep(.ant-input),
:deep(.ant-input-affix-wrapper) {
  height: 44px;
  border-radius: 12px;
  font-size: 14px;
}

:deep(.ant-input-affix-wrapper .ant-input) {
  height: auto;
}

:deep(.ant-form-item .ant-btn) {
  height: 44px;
  border-radius: 12px;
  font-weight: 600;
  border: none;
  background: var(--brand-gradient);
}

:deep(.ant-form-item .ant-btn:hover) {
  opacity: 0.94;
}

@media (max-width: 480px) {
  #userLoginPage {
    padding: 28px 22px 22px;
    border-radius: var(--radius-lg);
  }
}
</style>
