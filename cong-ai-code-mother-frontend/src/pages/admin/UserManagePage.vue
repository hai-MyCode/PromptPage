<template>
  <div id="userManagePage">
    <!-- 搜索表单 -->
    <a-form layout="inline" :model="searchParams" @finish="doSearch">
      <a-form-item label="账号">
        <a-input v-model:value="searchParams.userAccount" placeholder="输入账号" />
      </a-form-item>
      <a-form-item label="用户名">
        <a-input v-model:value="searchParams.userName" placeholder="输入用户名" />
      </a-form-item>
      <a-form-item>
        <a-button type="primary" html-type="submit">搜索</a-button>
      </a-form-item>
    </a-form>
    <a-divider />
    <!-- 表格 -->
    <a-table
      :columns="columns"
      :data-source="data"
      :pagination="pagination"
      @change="doTableChange"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.dataIndex === 'userAvatar'">
          <UserAvatar :size="36" :src="record.userAvatar" :name="record.userName" />
        </template>
        <template v-else-if="column.dataIndex === 'userRole'">
          <div v-if="record.userRole === 'admin'">
            <a-tag color="green">管理员</a-tag>
          </div>
          <div v-else>
            <a-tag color="blue">普通用户</a-tag>
          </div>
        </template>
        <template v-else-if="column.dataIndex === 'createTime'">
          {{ dayjs(record.createTime).format('YYYY-MM-DD HH:mm:ss') }}
        </template>
        <template v-else-if="column.dataIndex === 'quota'">
          <a-tag :color="record.quota > 0 ? 'orange' : 'red'">{{ record.quota ?? 0 }}</a-tag>
        </template>
        <template v-else-if="column.key === 'action'">
          <a-space>
            <a-button type="primary" @click="openQuotaModal(record)">调整额度</a-button>
            <a-button danger @click="doDelete(record.id)">删除</a-button>
          </a-space>
        </template>
      </template>
    </a-table>

    <!-- 调整额度弹窗 -->
    <a-modal
      v-model:open="quotaModalOpen"
      title="调整生成额度"
      :confirm-loading="quotaSubmitting"
      @ok="doUpdateQuota"
    >
      <a-form layout="vertical">
        <a-form-item label="账号">
          <a-input :value="quotaForm.userAccount" disabled />
        </a-form-item>
        <a-form-item label="剩余额度（次数）">
          <a-input-number v-model:value="quotaForm.quota" :min="0" :max="100000" style="width: 200px" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>
<script lang="ts" setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { deleteUser, listUserVoByPage, updateUserQuota } from '@/api/userController.ts'
import UserAvatar from '@/components/UserAvatar.vue'
import { message } from 'ant-design-vue'
import dayjs from 'dayjs'

const columns = [
  {
    title: 'id',
    dataIndex: 'id',
  },
  {
    title: '账号',
    dataIndex: 'userAccount',
  },
  {
    title: '用户名',
    dataIndex: 'userName',
  },
  {
    title: '头像',
    dataIndex: 'userAvatar',
  },
  {
    title: '简介',
    dataIndex: 'userProfile',
  },
  {
    title: '用户角色',
    dataIndex: 'userRole',
  },
  {
    title: '剩余额度',
    dataIndex: 'quota',
    width: 100,
  },
  {
    title: '创建时间',
    dataIndex: 'createTime',
  },
  {
    title: '操作',
    key: 'action',
  },
]

// 展示的数据
const data = ref<API.UserVO[]>([])
const total = ref(0)

// 搜索条件
const searchParams = reactive<API.UserQueryRequest>({
  pageNum: 1,
  pageSize: 10,
})

// 获取数据
const fetchData = async () => {
  const res = await listUserVoByPage({
    ...searchParams,
  })
  if (res.data.data) {
    data.value = res.data.data.records ?? []
    total.value = res.data.data.totalRow ?? 0
  } else {
    message.error('获取数据失败，' + res.data.message)
  }
}

// 分页参数
const pagination = computed(() => {
  return {
    current: searchParams.pageNum ?? 1,
    pageSize: searchParams.pageSize ?? 10,
    total: total.value,
    showSizeChanger: true,
    showTotal: (total: number) => `共 ${total} 条`,
  }
})

// 表格分页变化时的操作
const doTableChange = (page: { current: number; pageSize: number }) => {
  searchParams.pageNum = page.current
  searchParams.pageSize = page.pageSize
  fetchData()
}

// 搜索数据
const doSearch = () => {
  // 重置页码
  searchParams.pageNum = 1
  fetchData()
}

// 删除数据
const doDelete = async (id: number) => {
  if (!id) {
    return
  }
  const res = await deleteUser({ id })
  if (res.data.code === 0) {
    message.success('删除成功')
    // 刷新数据
    fetchData()
  } else {
    message.error('删除失败')
  }
}

// 页面加载时请求一次
onMounted(() => {
  fetchData()
})

// 调整额度
const quotaModalOpen = ref(false)
const quotaSubmitting = ref(false)
const quotaForm = reactive<{ id?: number; userAccount?: string; quota: number }>({
  quota: 0,
})

const openQuotaModal = (record: API.UserVO) => {
  quotaForm.id = record.id
  quotaForm.userAccount = record.userAccount
  quotaForm.quota = record.quota ?? 0
  quotaModalOpen.value = true
}

const doUpdateQuota = async () => {
  if (quotaForm.id === undefined) {
    return
  }
  quotaSubmitting.value = true
  try {
    const res = await updateUserQuota({ id: quotaForm.id, quota: quotaForm.quota })
    if (res.data.code === 0) {
      message.success('额度更新成功')
      quotaModalOpen.value = false
      fetchData()
    } else {
      message.error('更新失败：' + res.data.message)
    }
  } catch (error) {
    console.error('更新额度失败：', error)
    message.error('更新失败，请重试')
  } finally {
    quotaSubmitting.value = false
  }
}
</script>

<style scoped>
#userManagePage {
  max-width: 1240px;
  margin: 24px auto 64px;
  padding: 24px;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-xs);
}
</style>
