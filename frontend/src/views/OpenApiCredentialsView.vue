<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { CopyDocument, Key, Refresh } from '@element-plus/icons-vue'
import {
  fetchCredentials,
  createCredential,
  updateCredentialEnabled,
  deleteCredential,
  type OpenApiCredential,
  type CreateCredentialRequest,
  type CreateCredentialResponse,
} from '../api/openapiCredentials'

const credentials = ref<OpenApiCredential[]>([])
const loading = ref(false)

const createDialogVisible = ref(false)
const createForm = ref<CreateCredentialRequest>({
  name: '',
  description: '',
  scopes: ['OPENAPI_READ'],
  expiresAt: null,
})
const createSubmitting = ref(false)
const scopeValue = ref<'OPENAPI_READ' | 'OPENAPI_READ_WRITE'>('OPENAPI_READ')

const secretDialogVisible = ref(false)
const createdCredential = ref<CreateCredentialResponse | null>(null)
const secretConfirmed = ref(false)

onMounted(() => {
  loadCredentials()
})

async function loadCredentials() {
  loading.value = true
  try {
    credentials.value = await fetchCredentials()
  } catch {
    ElMessage.error('加载凭证列表失败')
  } finally {
    loading.value = false
  }
}

function openCreateDialog() {
  createForm.value = {
    name: '',
    description: '',
    scopes: ['OPENAPI_READ'],
    expiresAt: null,
  }
  scopeValue.value = 'OPENAPI_READ'
  createDialogVisible.value = true
}

async function handleCreate() {
  if (!createForm.value.name.trim()) {
    ElMessage.warning('请输入凭证名称')
    return
  }
  createSubmitting.value = true
  try {
    createForm.value.scopes = scopeValue.value === 'OPENAPI_READ_WRITE'
      ? ['OPENAPI_READ', 'OPENAPI_WRITE']
      : ['OPENAPI_READ']
    if (createForm.value.description === '') {
      delete createForm.value.description
    }
    if (!createForm.value.expiresAt) {
      createForm.value.expiresAt = undefined
    }
    const result = await createCredential(createForm.value)
    createdCredential.value = result
    secretConfirmed.value = false
    createDialogVisible.value = false
    secretDialogVisible.value = true
    ElMessage.success('凭证创建成功')
    loadCredentials()
  } catch {
    ElMessage.error('创建凭证失败')
  } finally {
    createSubmitting.value = false
  }
}

function handleSecretConfirmClose() {
  if (!secretConfirmed.value) {
    ElMessage.warning('请先勾选确认已保存 Secret Key')
    return
  }
  secretDialogVisible.value = false
  createdCredential.value = null
}

async function copyToClipboard(text: string, label: string) {
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success(`${label} 已复制到剪贴板`)
  } catch {
    ElMessage.error('复制失败，请手动复制')
  }
}

async function handleToggleEnabled(row: OpenApiCredential) {
  const nextEnabled = !row.enabled
  const action = nextEnabled ? '启用' : '禁用'
  const confirmMessage = nextEnabled
    ? '启用后该客户端将恢复访问 OpenAPI。是否继续？'
    : '禁用后该客户端将暂时无法访问 OpenAPI，可稍后重新启用。是否继续？'
  try {
    await ElMessageBox.confirm(confirmMessage, `确认${action}凭证`, {
      confirmButtonText: action,
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch {
    return
  }
  try {
    await updateCredentialEnabled(row.id, { enabled: nextEnabled })
    row.enabled = nextEnabled
    ElMessage.success(`凭证已${action}`)
  } catch {
    ElMessage.error(`${action}凭证失败`)
  }
}

async function handleDelete(row: OpenApiCredential) {
  try {
    await ElMessageBox.confirm(
      '删除后该客户端将无法继续访问 OpenAPI。是否继续？',
      '确认删除凭证',
      {
        confirmButtonText: '删除',
        cancelButtonText: '取消',
        type: 'warning',
      },
    )
  } catch {
    return
  }
  try {
    await deleteCredential(row.id)
    ElMessage.success('凭证已删除')
    loadCredentials()
  } catch {
    ElMessage.error('删除凭证失败')
  }
}

function scopesLabel(scopes: string[]): string {
  if (!scopes || scopes.length === 0) return '无'
  const hasRead = scopes.includes('OPENAPI_READ')
  const hasWrite = scopes.includes('OPENAPI_WRITE')
  if (hasRead && hasWrite) return '读写'
  if (hasRead) return '只读'
  return scopes.join(', ')
}

function scopesTagType(scopes: string[]): 'info' | 'warning' {
  const hasWrite = scopes.includes('OPENAPI_WRITE')
  return hasWrite ? 'warning' : 'info'
}

function formatDateTime(value: string | null): string {
  if (!value) return '—'
  return value.replace('T', ' ').substring(0, 19)
}

function expiresLabel(value: string | null): string {
  if (!value) return '永不过期'
  return formatDateTime(value)
}
</script>

<template>
  <div class="credentials-page">
    <div class="page-header">
      <div class="page-header-left">
        <h2 class="page-title">OpenAPI 凭证管理</h2>
        <p class="page-subtitle">管理 OpenAPI 客户端的 AK/SK 凭证，用于 HMAC 签名认证</p>
      </div>
      <div class="page-header-right">
        <el-button :icon="Refresh" @click="loadCredentials" :loading="loading">刷新</el-button>
        <el-button type="primary" :icon="Key" @click="openCreateDialog">创建凭证</el-button>
      </div>
    </div>

    <el-card class="hmac-info-card">
      <el-alert type="info" :closable="false" show-icon>
        <template #title>
          HMAC-SHA256 签名认证说明
        </template>
        <p>v1.1.3 起 OpenAPI 使用 AK/SK + HMAC-SHA256 签名认证，旧静态 Token 不再作为推荐认证方式。</p>
        <p>客户端需使用 Access Key 和 Secret Key 生成签名，请求需携带 <code>X-Xingyu-Access-Key</code>、<code>X-Xingyu-Timestamp</code>、<code>X-Xingyu-Nonce</code>、<code>X-Xingyu-Signature</code> 头。</p>
        <p>Secret Key 不直接传输，仅用于签名计算。签名版本：v1。详细签名规范请查看 OpenAPI 文档。</p>
      </el-alert>
    </el-card>

    <el-card>
      <el-table :data="credentials" v-loading="loading" stripe empty-text="暂无凭证，请点击「创建凭证」按钮创建">
        <el-table-column prop="name" label="名称" min-width="140" />
        <el-table-column label="Access Key" min-width="200">
          <template #default="{ row }">
            <div class="cell-with-copy">
              <code>{{ row.accessKey }}</code>
              <el-button
                :icon="CopyDocument"
                size="small"
                text
                @click="copyToClipboard(row.accessKey, 'Access Key')"
              />
            </div>
          </template>
        </el-table-column>
        <el-table-column label="Secret 指纹" width="200">
          <template #default="{ row }">
            <code class="fingerprint">{{ row.secretFingerprint }}</code>
          </template>
        </el-table-column>
        <el-table-column label="权限" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="scopesTagType(row.scopes)" size="small">
              {{ scopesLabel(row.scopes) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.enabled ? 'success' : 'info'" size="small">
              {{ row.enabled ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="170">
          <template #default="{ row }">
            {{ formatDateTime(row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column label="过期时间" width="170">
          <template #default="{ row }">
            <el-tag v-if="!row.expiresAt" type="success" size="small">永不过期</el-tag>
            <span v-else>{{ formatDateTime(row.expiresAt) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="最后使用时间" width="170">
          <template #default="{ row }">
            {{ formatDateTime(row.lastUsedAt) }}
          </template>
        </el-table-column>
        <el-table-column label="最后使用 IP" width="140">
          <template #default="{ row }">
            {{ row.lastUsedIp || '—' }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right" align="center">
          <template #default="{ row }">
            <el-button
              :type="row.enabled ? 'warning' : 'success'"
              size="small"
              @click="handleToggleEnabled(row)"
            >
              {{ row.enabled ? '禁用' : '启用' }}
            </el-button>
            <el-button type="danger" size="small" @click="handleDelete(row)">
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="createDialogVisible" title="创建 OpenAPI 凭证" width="520px" destroy-on-close>
      <el-form label-width="80px" :model="createForm">
        <el-form-item label="名称" required>
          <el-input
            v-model="createForm.name"
            placeholder="例如：iPhone 星语音乐盒"
            maxlength="128"
            show-word-limit
          />
        </el-form-item>
        <el-form-item label="描述">
          <el-input
            v-model="createForm.description"
            type="textarea"
            placeholder="可选，描述此凭证的用途"
            maxlength="256"
            show-word-limit
          />
        </el-form-item>
        <el-form-item label="权限">
          <el-radio-group v-model="scopeValue">
            <el-radio value="OPENAPI_READ">
              <span style="font-weight:500">只读</span>
              <span style="color:#909399;font-size:12px;margin-left:4px">OPENAPI_READ</span>
            </el-radio>
            <el-radio value="OPENAPI_READ_WRITE">
              <span style="font-weight:500">读写</span>
              <span style="color:#909399;font-size:12px;margin-left:4px">OPENAPI_READ + OPENAPI_WRITE</span>
            </el-radio>
          </el-radio-group>
          <div class="scope-hint">
            <p>只读：适合当前音乐盒读取曲目、歌词、封面、元数据。</p>
            <p>读写：为后续候选确认、元数据写入等能力预留。</p>
          </div>
        </el-form-item>
        <el-form-item label="过期时间">
          <el-date-picker
            v-model="createForm.expiresAt"
            type="datetime"
            placeholder="可选，不填则永不过期"
            value-format="YYYY-MM-DDTHH:mm:ss"
            style="width: 100%"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="createSubmitting" @click="handleCreate">
          创建
        </el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="secretDialogVisible"
      title="凭证创建成功 — Secret Key 仅显示一次"
      width="560px"
      :close-on-click-modal="false"
      :close-on-press-escape="false"
      destroy-on-close
    >
      <div v-if="createdCredential" class="secret-display">
        <el-alert
          type="warning"
          :closable="false"
          show-icon
          style="margin-bottom: 20px"
        >
          <strong>Secret Key 只会显示这一次。</strong>关闭弹窗后无法再次查看，请立即复制并妥善保存。
        </el-alert>

        <div class="secret-item">
          <div class="secret-label">Access Key</div>
          <div class="secret-value-row">
            <code class="secret-value">{{ createdCredential.accessKey }}</code>
            <el-button
              :icon="CopyDocument"
              size="small"
              @click="copyToClipboard(createdCredential.accessKey, 'Access Key')"
            >
              复制
            </el-button>
          </div>
        </div>

        <div class="secret-item">
          <div class="secret-label">Secret Key</div>
          <div class="secret-value-row">
            <code class="secret-value secret-key">{{ createdCredential.secretKey }}</code>
            <el-button
              :icon="CopyDocument"
              size="small"
              type="primary"
              @click="copyToClipboard(createdCredential.secretKey, 'Secret Key')"
            >
              复制
            </el-button>
          </div>
        </div>

        <div class="secret-meta">
          <el-descriptions :column="1" size="small" border>
            <el-descriptions-item label="签名版本">v1 (HMAC-SHA256)</el-descriptions-item>
            <el-descriptions-item label="权限">{{ scopesLabel(createdCredential.scopes) }}</el-descriptions-item>
            <el-descriptions-item label="过期时间">{{ expiresLabel(createdCredential.expiresAt) }}</el-descriptions-item>
          </el-descriptions>
        </div>

        <div class="secret-tips">
          <p><strong>接入提示：</strong></p>
          <ol>
            <li>客户端需使用 AK/SK 计算 HMAC-SHA256 签名</li>
            <li>Secret Key 仅用于签名计算，不直接传输</li>
            <li>请求需携带 <code>X-Xingyu-Access-Key</code>、<code>X-Xingyu-Timestamp</code>、<code>X-Xingyu-Nonce</code>、<code>X-Xingyu-Signature</code> 头</li>
            <li>详细签名规范请查看 OpenAPI 文档</li>
          </ol>
        </div>

        <el-divider />

        <el-checkbox v-model="secretConfirmed">
          我已复制并妥善保存 Secret Key，确认后可关闭此弹窗
        </el-checkbox>
      </div>
      <template #footer>
        <el-button type="primary" :disabled="!secretConfirmed" @click="handleSecretConfirmClose">
          我已保存，关闭
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.credentials-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  flex-wrap: wrap;
  gap: 12px;
}

.page-title {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
  color: #303133;
}

.page-subtitle {
  margin: 4px 0 0;
  font-size: 13px;
  color: #909399;
}

.page-header-right {
  display: flex;
  gap: 8px;
  align-items: center;
}

.hmac-info-card p {
  margin: 4px 0;
  line-height: 1.7;
}

.cell-with-copy {
  display: flex;
  align-items: center;
  gap: 4px;
}

.cell-with-copy code,
.fingerprint {
  font-family: Menlo, Monaco, 'Courier New', monospace;
  font-size: 12px;
  background: #f0f2f5;
  padding: 2px 6px;
  border-radius: 4px;
  word-break: break-all;
}

.scope-hint {
  margin-top: 8px;
  padding: 8px 12px;
  background: #f5f7fa;
  border-radius: 6px;
  font-size: 12px;
  color: #909399;
  line-height: 1.6;
}

.scope-hint p {
  margin: 2px 0;
}

.secret-display {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.secret-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.secret-label {
  font-size: 13px;
  font-weight: 600;
  color: #606266;
}

.secret-value-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.secret-value {
  flex: 1;
  font-family: Menlo, Monaco, 'Courier New', monospace;
  font-size: 13px;
  background: #f0f2f5;
  padding: 8px 12px;
  border-radius: 6px;
  word-break: break-all;
  user-select: all;
}

.secret-key {
  background: #fef0f0;
  color: #c45656;
  border: 1px dashed #f89898;
}

.secret-meta {
  margin-top: 4px;
}

.secret-tips {
  padding: 12px 16px;
  background: #f5f7fa;
  border-radius: 8px;
  font-size: 13px;
  color: #606266;
  line-height: 1.8;
}

.secret-tips ol {
  margin: 4px 0 0;
  padding-left: 20px;
}

.secret-tips li {
  margin-bottom: 4px;
}

code {
  background: #e8eaed;
  padding: 1px 5px;
  border-radius: 3px;
  font-size: 12px;
  font-family: Menlo, Monaco, 'Courier New', monospace;
}
</style>
