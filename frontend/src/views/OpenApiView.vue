<script setup lang="ts">
import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { fetchServerInfo, fetchSyncState, type ServerInfo, type SyncState } from '../api/openapi'

const serverInfo = ref<ServerInfo | null>(null)
const serverInfoLoading = ref(false)
const serverInfoError = ref(false)

const syncState = ref<SyncState | null>(null)
const syncStateLoading = ref(false)
const syncStateError = ref(false)

const endpoints = [
  { method: 'GET', path: '/api/open/v1/server/info', description: '获取服务器信息', auth: true },
  { method: 'GET', path: '/api/open/v1/sync/state', description: '获取音乐库同步状态', auth: true },
  { method: 'GET', path: '/api/open/v1/tracks', description: '获取歌曲列表（分页/筛选/排序）', auth: true },
  { method: 'GET', path: '/api/open/v1/tracks/:id', description: '获取歌曲详情', auth: true },
  { method: 'GET', path: '/api/open/v1/tracks/:id/lyrics', description: '获取歌词全文', auth: true },
  { method: 'GET', path: '/api/open/v1/tracks/:id/lyrics/meta', description: '获取歌词元数据', auth: true },
  { method: 'GET', path: '/api/open/v1/tracks/:id/artwork', description: '获取封面图片（二进制）', auth: true },
  { method: 'GET', path: '/api/open/v1/tracks/:id/artwork/meta', description: '获取封面元数据', auth: true },
  { method: 'GET', path: '/api/open/v1/artists', description: '获取歌手列表（含统计）', auth: true },
  { method: 'GET', path: '/api/open/v1/albums', description: '获取专辑列表（含统计）', auth: true },
  { method: 'GET', path: '/api/open/v1/match/track', description: '模糊匹配歌曲', auth: true },
]

const securityTable = [
  { status: '401', code: 'OPENAPI_UNAUTHORIZED', description: '签名无效或凭证不存在' },
  { status: '401', code: 'OPENAPI_CREDENTIAL_DISABLED', description: '凭证已被禁用' },
  { status: '401', code: 'OPENAPI_CREDENTIAL_EXPIRED', description: '凭证已过期' },
  { status: '429', code: 'OPENAPI_RATE_LIMITED', description: '超出每分钟请求频率限制' },
]

const securityExamples = [
  {
    title: '认证：HMAC-SHA256 签名（v1.1.3 起推荐）',
    request: `GET /api/open/v1/tracks?page=0&pageSize=10
X-Xingyu-Access-Key: ak_xxxxxxxxxxxx
X-Xingyu-Timestamp: 1718000000000
X-Xingyu-Nonce: random-uuid-string
X-Xingyu-Signature: <HMAC-SHA256 签名结果>`,
    response: `HTTP 200 OK
Content-Type: application/json

{
  "items": [...],
  "page": 0,
  "pageSize": 10,
  "total": 150
}`,
  },
  {
    title: '错误：401 签名无效',
    request: `GET /api/open/v1/tracks?page=0&pageSize=10
X-Xingyu-Access-Key: ak_xxxxxxxxxxxx
X-Xingyu-Timestamp: 1718000000000
X-Xingyu-Nonce: random-uuid-string
X-Xingyu-Signature: invalid-signature`,
    response: `HTTP 401 Unauthorized
Content-Type: application/json

{
  "code": "OPENAPI_UNAUTHORIZED",
  "message": "Invalid HMAC signature",
  "traceId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "details": {}
}`,
  },
  {
    title: '错误：401 凭证已禁用或过期',
    request: `GET /api/open/v1/tracks?page=0&pageSize=10
X-Xingyu-Access-Key: ak_disabled_or_expired
X-Xingyu-Timestamp: 1718000000000
X-Xingyu-Nonce: random-uuid-string
X-Xingyu-Signature: <HMAC-SHA256 签名结果>`,
    response: `HTTP 401 Unauthorized
Content-Type: application/json

{
  "code": "OPENAPI_CREDENTIAL_DISABLED",
  "message": "OpenAPI credential is disabled",
  "traceId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "details": {}
}`,
  },
  {
    title: '错误：429 请求频率限制',
    request: `GET /api/open/v1/tracks?page=0&pageSize=10
X-Xingyu-Access-Key: ak_xxxxxxxxxxxx
X-Xingyu-Timestamp: 1718000000000
X-Xingyu-Nonce: random-uuid-string
X-Xingyu-Signature: <HMAC-SHA256 签名结果>

（超过每分钟请求上限后触发）`,
    response: `HTTP 429 Too Many Requests
Content-Type: application/json

{
  "code": "OPENAPI_RATE_LIMITED",
  "message": "Too many OpenAPI requests",
  "traceId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "details": {
    "limit": 120,
    "windowSeconds": 60
  }
}`,
  },
]

const serverInfoEntries = computed(() => {
  if (!serverInfo.value) return []
  return Object.entries(serverInfo.value).map(([key, value]) => ({
    label: key,
    value: typeof value === 'object' ? JSON.stringify(value, null, 2) : String(value),
  }))
})

const syncStateEntries = computed(() => {
  if (!syncState.value) return []
  return Object.entries(syncState.value).map(([key, value]) => ({
    label: key,
    value: typeof value === 'object' ? JSON.stringify(value, null, 2) : String(value),
  }))
})

async function loadServerInfo() {
  serverInfoLoading.value = true
  serverInfoError.value = false
  try {
    serverInfo.value = await fetchServerInfo()
  } catch {
    serverInfoError.value = true
    ElMessage.warning('无法获取服务器信息，请使用已签名的 OpenAPI 请求验证')
  } finally {
    serverInfoLoading.value = false
  }
}

async function loadSyncState() {
  syncStateLoading.value = true
  syncStateError.value = false
  try {
    syncState.value = await fetchSyncState()
  } catch {
    syncStateError.value = true
  } finally {
    syncStateLoading.value = false
  }
}

function methodTagType(method: string): '' | 'success' | 'warning' | 'danger' | 'info' {
  const map: Record<string, '' | 'success' | 'warning' | 'danger' | 'info'> = {
    GET: '',
    POST: 'success',
    PUT: 'warning',
    DELETE: 'danger',
  }
  return map[method] || 'info'
}

function toLabel(key: string): string {
  const map: Record<string, string> = {
    serviceName: '服务名称',
    serviceVersion: '服务版本',
    apiVersion: 'API 版本',
    readOnly: '只读模式',
    features: '功能特性',
    trackCount: '歌曲总数',
    artistCount: '歌手总数',
    albumCount: '专辑总数',
    lyricsCount: '歌词数量',
    artworkCount: '封面数量',
    lastUpdatedAt: '最后更新时间',
  }
  return map[key] || key
}
</script>

<template>
  <div class="openapi-page">
    <el-alert
      type="info"
      :closable="false"
      show-icon
      class="page-alert"
    >
      本页展示 OpenAPI MVP 状态和常用接口示例，便于开发者和客户端对接。接口路径前缀为 <code>/api/open/v1</code>。
    </el-alert>

    <div class="openapi-layout">
      <div class="openapi-main">
        <div class="status-row">
          <el-card v-loading="serverInfoLoading" class="status-card">
            <template #header>
              <div class="card-header">
                <span>服务信息</span>
                <div style="display: flex; align-items: center; gap: 8px">
                  <el-tag :type="serverInfoError ? 'danger' : 'success'" size="small">
                    {{ serverInfoError ? '不可用' : '正常' }}
                  </el-tag>
                  <el-button size="small" text @click="loadServerInfo">刷新</el-button>
                </div>
              </div>
            </template>
            <el-empty v-if="serverInfoError" description="无法连接到服务器" :image-size="60" />
            <el-descriptions v-else-if="serverInfo" :column="1" size="small" border>
              <el-descriptions-item
                v-for="entry in serverInfoEntries"
                :key="entry.label"
                :label="toLabel(entry.label)"
              >
                {{ entry.value }}
              </el-descriptions-item>
            </el-descriptions>
            <div v-else style="text-align: center; color: #909399; padding: 20px">
              使用 AK/SK 签名请求验证，或点击刷新查看未签名请求的错误响应。
            </div>
          </el-card>

          <el-card v-loading="syncStateLoading" class="status-card">
            <template #header>
              <div class="card-header">
                <span>音乐库状态</span>
                <div style="display: flex; align-items: center; gap: 8px">
                  <el-tag :type="syncStateError ? 'danger' : 'success'" size="small">
                    {{ syncStateError ? '不可用' : '正常' }}
                  </el-tag>
                  <el-button size="small" text @click="loadSyncState">刷新</el-button>
                </div>
              </div>
            </template>
            <el-empty v-if="syncStateError" description="无法获取同步状态" :image-size="60" />
            <el-descriptions v-else-if="syncState" :column="1" size="small" border>
              <el-descriptions-item
                v-for="entry in syncStateEntries"
                :key="entry.label"
                :label="toLabel(entry.label)"
              >
                {{ entry.value }}
              </el-descriptions-item>
            </el-descriptions>
            <div v-else style="text-align: center; color: #909399; padding: 20px">
              使用 AK/SK 签名请求验证，或点击刷新查看未签名请求的错误响应。
            </div>
          </el-card>
        </div>

        <el-card class="section-card">
          <template #header>
            <span>接口清单</span>
          </template>
          <el-table :data="endpoints" size="small" stripe>
            <el-table-column label="方法" width="80">
              <template #default="{ row }">
                <el-tag :type="methodTagType(row.method)" size="small" effect="dark">
                  {{ row.method }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="path" label="路径" min-width="250" />
            <el-table-column prop="description" label="说明" />
            <el-table-column label="认证" width="80" align="center">
              <template #default="{ row }">
                <el-tag :type="row.auth ? 'warning' : 'success'" size="small">
                  {{ row.auth ? 'HMAC' : '公开' }}
                </el-tag>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </div>

      <div class="openapi-side">
        <el-card class="section-card">
        <template #header>
          <span>安全与访问控制</span>
        </template>

        <el-alert
          type="info"
          :closable="false"
          show-icon
          style="margin-bottom: 12px"
        >
          v1.1.3 起 OpenAPI 使用 <strong>AK/SK + HMAC-SHA256 签名认证</strong>，旧静态 Token 不再作为推荐认证方式。请在「OpenAPI 凭证」页面创建 AK/SK 凭证，客户端使用 Access Key 和 Secret Key 生成签名。Secret Key 仅用于签名计算，不直接传输。详细签名规范请查看 OpenAPI 文档。
        </el-alert>

        <el-table :data="securityTable" size="small" stripe style="margin-bottom: 16px">
          <el-table-column prop="status" label="状态码" width="84" />
          <el-table-column prop="code" label="错误码" min-width="170" />
          <el-table-column prop="description" label="说明" min-width="150" />
        </el-table>

        <el-collapse accordion>
          <el-collapse-item
            v-for="item in securityExamples"
            :key="item.title"
            :title="item.title"
          >
            <div class="example-section">
              <div class="example-label">请求</div>
              <pre class="code-block">{{ item.request }}</pre>
            </div>
            <div class="example-section">
              <div class="example-label">响应</div>
              <pre class="code-block">{{ item.response }}</pre>
            </div>
          </el-collapse-item>
        </el-collapse>
      </el-card>

      <el-card class="section-card">
        <template #header>
          <span>客户端接入建议</span>
        </template>
        <div class="integration-tips">
          <el-alert type="info" :closable="false" show-icon>
            <p>1. 在「OpenAPI 凭证」页面创建 AK/SK 凭证，作为客户端身份凭证。</p>
            <p>2. 所有接口使用 HMAC-SHA256 签名认证，请求头携带 <code>X-Xingyu-Access-Key</code>、<code>X-Xingyu-Timestamp</code>、<code>X-Xingyu-Nonce</code>、<code>X-Xingyu-Signature</code>。</p>
            <p>3. Secret Key 只在创建时显示一次，请务必妥善保存。</p>
            <p>4. 分页接口使用 <code>page</code>（从 0 开始）和 <code>pageSize</code> 参数，响应中包含 <code>total</code>。</p>
            <p>5. 封面图片通过 <code>GET /api/open/v1/tracks/{id}/artwork</code> 获取二进制文件流，响应头含 <code>ETag</code> + <code>Cache-Control</code>。必要字段可通过 <code>GET .../artwork/meta</code> 获取。</p>
            <p>6. 建议客户端实现本地缓存策略，减少重复请求。</p>
          </el-alert>
        </div>
      </el-card>
      </div>
    </div>
  </div>
</template>

<style scoped>
.openapi-page {
  display: flex;
  flex-direction: column;
  gap: 12px;
  height: calc(100vh - 126px);
  max-width: none;
  overflow: hidden;
}

.page-alert {
  flex: 0 0 auto;
}

.openapi-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(360px, 420px);
  gap: 16px;
  min-height: 0;
  flex: 1;
  overflow: hidden;
}

.openapi-main,
.openapi-side {
  min-height: 0;
  overflow-y: auto;
  padding-right: 4px;
}

.status-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  margin-bottom: 16px;
}

@media (max-width: 768px) {
  .openapi-page {
    height: auto;
    overflow: visible;
  }

  .openapi-layout {
    grid-template-columns: 1fr;
    overflow: visible;
  }

  .openapi-main,
  .openapi-side {
    overflow: visible;
    padding-right: 0;
  }

  .status-row {
    grid-template-columns: 1fr;
  }
}

.status-card {
  min-height: 160px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.section-card {
  margin-bottom: 16px;
}

.example-section {
  margin-bottom: 16px;
}

.example-label {
  font-size: 13px;
  font-weight: 600;
  color: #606266;
  margin-bottom: 8px;
}

.code-block {
  background: #1e1e2e;
  color: #c0c0d0;
  padding: 16px;
  border-radius: 8px;
  font-size: 13px;
  line-height: 1.7;
  overflow-x: auto;
  font-family: Menlo, Monaco, 'Courier New', monospace;
  margin: 0;
  white-space: pre;
}

.integration-tips {
  line-height: 2;
}

.integration-tips p {
  margin: 0;
}

code {
  background: #e8eaed;
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 13px;
}
</style>
