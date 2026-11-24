<template>
  <div class="auth-page">
    <div id="userRegisterPage">
      <div class="auth-brand"><span class="brand-dot"></span>AI 应用生成</div>
      <h2 class="title">创建账号</h2>
      <div class="desc">注册即赠送 20 次 AI 生成额度</div>
    <a-form :model="formState" name="basic" autocomplete="off" @finish="handleSubmit">
      <a-form-item name="userAccount" :rules="[{ required: true, message: '请输入账号' }]">
        <a-input v-model:value="formState.userAccount" placeholder="请输入账号" />
      </a-form-item>
      <a-form-item
        name="userPassword"
        :rules="[
          { required: true, message: '请输入密码' },
          { min: 8, message: '密码不能小于 8 位' },
        ]"
      >
        <a-input-password v-model:value="formState.userPassword" placeholder="请输入密码" />
      </a-form-item>
      <a-form-item
        name="checkPassword"
        :rules="[
          { required: true, message: '请确认密码' },
          { min: 8, message: '密码不能小于 8 位' },
          { validator: validateCheckPassword },
        ]"
      >
        <a-input-password v-model:value="formState.checkPassword" placeholder="请确认密码" />
      </a-form-item>
      <div class="tips">
        已有账号？
        <RouterLink to="/user/login">去登录</RouterLink>
      </div>
      <a-form-item>
        <a-button type="primary" html-type="submit" style="width: 100%">注册</a-button>
      </a-form-item>
    </a-form>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import { userRegister } from '@/api/userController.ts'
import { message } from 'ant-design-vue'
import { reactive } from 'vue'

const router = useRouter()

const formState = reactive<API.UserRegisterRequest>({
  userAccount: '',
  userPassword: '',
  checkPassword: '',
})

/**
 * 验证确认密码
 * @param rule
 * @param value
 * @param callback
 */
const validateCheckPassword = (rule: unknown, value: string, callback: (error?: Error) => void) => {
  if (value && value !== formState.userPassword) {
    callback(new Error('两次输入密码不一致'))
  } else {
    callback()
  }
}

/**
 * 提交表单
 * @param values
 */
const handleSubmit = async (values: API.UserRegisterRequest) => {
  const res = await userRegister(values)
  // 注册成功，跳转到登录页面
  if (res.data.code === 0) {
    message.success('注册成功')
    router.push({
      path: '/user/login',
      replace: true,
    })
  } else {
    message.error('注册失败，' + res.data.message)
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

#userRegisterPage {
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
  #userRegisterPage {
    padding: 28px 22px 22px;
    border-radius: var(--radius-lg);
  }
}
</style>
