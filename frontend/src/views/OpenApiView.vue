<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
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

const examples = [
  {
    title: '获取歌曲列表',
    request: `GET /api/open/v1/tracks?page=0&pageSize=20&sort=updatedAt&order=desc
Authorization: Bearer <your-token>`,
    response: `{
  "items": [
    {
      "id": 42,
      "title": "晴天",
      "artist": "周杰伦",
      "album": "叶惠美",
      "albumArtist": "周杰伦",
      "durationMs": 269000,
      "year": 2003,
      "trackNo": 3,
      "genre": "流行",
      "metadataStatus": "synced",
      "lyricsStatus": "BOUND",
      "artworkStatus": "BOUND",
      "fileName": "晴天.flac",
      "fileExtension": "flac",
      "fileSize": 28901234,
      "lyricsAvailable": true,
      "lyricId": 15,
      "artworkAvailable": true,
      "artworkId": 8,
      "artworkUrl": "/api/open/v1/tracks/42/artwork",
      "createdAt": "2025-01-15T08:30:00",
      "updatedAt": "2025-03-20T14:22:00"
    }
  ],
  "page": 0,
  "pageSize": 20,
  "total": 150
}`,
  },
  {
    title: '获取歌词',
    request: `GET /api/open/v1/tracks/42/lyrics
Authorization: Bearer <your-token>`,
    response: `{
  "trackId": 42,
  "lyricId": 15,
  "format": "LRC",
  "content": "[00:00.00]作词 : 周杰伦\\n[00:01.00]作曲 : 周杰伦\\n[00:15.00]...",
  "hash": "abc123def456...",
  "updatedAt": "2025-01-15T08:35:00"
}`,
  },
  {
    title: '获取封面元数据',
    request: `GET /api/open/v1/tracks/42/artwork/meta
Authorization: Bearer <your-token>`,
    response: `{
  "trackId": 42,
  "available": true,
  "artworkId": 8,
  "mimeType": "image/jpeg",
  "fileSize": 245678,
  "width": 800,
  "height": 800,
  "hash": "def789abc012...",
  "etag": "\\"artwork-42-def789abc012...\\"",
  "updatedAt": "2025-01-15T08:32:00"
}`,
  },
  {
    title: '获取封面图片（二进制）',
    request: `GET /api/open/v1/tracks/42/artwork
Authorization: Bearer <your-token>`,
    response: `HTTP 200 OK
Content-Type: image/jpeg
ETag: "abc123"
Cache-Control: max-age=3600

<binary image data>`,
  },
  {
    title: 'match/track 匹配歌曲',
    request: `GET /api/open/v1/match/track?title=晴天&artist=周杰伦&album=叶惠美&durationMs=269000
Authorization: Bearer <your-token>`,
    response: `{
  "matched": true,
  "score": 95,
  "reason": "title exact match; artist exact match; album exact match",
  "track": {
    "id": 42,
    "title": "晴天",
    "artist": "周杰伦",
    "album": "叶惠美",
    "albumArtist": "周杰伦",
    "durationMs": 269000,
    "year": 2003,
    "trackNo": 3,
    "genre": "流行",
    "metadataStatus": "synced",
    "lyricsStatus": "BOUND",
    "artworkStatus": "BOUND",
    "fileName": "晴天.flac",
    "fileExtension": "flac",
    "fileSize": 28901234,
    "lyricsAvailable": true,
    "lyricId": 15,
    "artworkAvailable": true,
    "artworkId": 8,
    "artworkUrl": "/api/open/v1/tracks/42/artwork",
    "createdAt": "2025-01-15T08:30:00",
    "updatedAt": "2025-03-20T14:22:00"
  }
}`,
  },
]

const securityTable = [
  { status: '401', code: 'OPENAPI_UNAUTHORIZED', description: 'Token 缺失或无效' },
  { status: '429', code: 'OPENAPI_RATE_LIMITED', description: '超出每分钟请求频率限制' },
]

const securityExamples = [
  {
    title: '认证：Authorization Bearer（推荐）',
    request: `GET /api/open/v1/tracks?page=0&pageSize=10
Authorization: Bearer <your-openapi-token>`,
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
    title: '认证：X-Xingyu-Api-Token（备选）',
    request: `GET /api/open/v1/tracks?page=0&pageSize=10
X-Xingyu-Api-Token: <your-openapi-token>`,
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
    title: '错误：401 认证失败',
    request: `GET /api/open/v1/tracks?page=0&pageSize=10
Authorization: Bearer invalid-token`,
    response: `HTTP 401 Unauthorized
Content-Type: application/json

{
  "code": "OPENAPI_UNAUTHORIZED",
  "message": "Missing or invalid OpenAPI token",
  "traceId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "details": {}
}`,
  },
  {
    title: '错误：429 请求频率限制',
    request: `GET /api/open/v1/tracks?page=0&pageSize=10
Authorization: Bearer <your-openapi-token>

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

onMounted(() => {
  loadServerInfo()
  loadSyncState()
})

async function loadServerInfo() {
  serverInfoLoading.value = true
  serverInfoError.value = false
  try {
    serverInfo.value = await fetchServerInfo()
  } catch {
    serverInfoError.value = true
    ElMessage.warning('无法获取服务器信息，请确认后端服务已启动')
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
      style="margin-bottom: 16px"
    >
      本页展示 OpenAPI MVP 状态和常用接口示例，便于开发者和客户端对接。接口路径前缀为 <code>/api/open/v1</code>。
    </el-alert>

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
          正在加载...
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
          正在加载...
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
              {{ row.auth ? 'Token' : '公开' }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

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
        OpenAPI 认证默认关闭（<code>xingyu.openapi.auth.enabled=false</code>），部署方可设为 <code>true</code> 启用认证。支持两种认证方式：<strong>Authorization Bearer</strong>（推荐）和 <strong>X-Xingyu-Api-Token</strong>（备选）。Token 通过后端配置项 <code>xingyu.openapi.auth.token</code> 设置。
      </el-alert>

      <el-table :data="securityTable" size="small" stripe style="margin-bottom: 16px">
        <el-table-column prop="status" label="状态码" width="100" />
        <el-table-column prop="code" label="错误码" width="240" />
        <el-table-column prop="description" label="说明" />
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
        <span>请求示例</span>
      </template>
      <el-collapse accordion>
        <el-collapse-item v-for="example in examples" :key="example.title" :title="example.title">
          <div class="example-section">
            <div class="example-label">请求</div>
            <pre class="code-block">{{ example.request }}</pre>
          </div>
          <div class="example-section">
            <div class="example-label">响应示例</div>
            <pre class="code-block">{{ example.response }}</pre>
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
          <p>1. 在「设置」页面生成或配置 <strong>API Token</strong>，作为客户端身份凭证。</p>
          <p>2. 所有接口在请求头中携带 <code>Authorization: Bearer &lt;token&gt;</code>（推荐），或使用 <code>X-Xingyu-Api-Token: &lt;token&gt;</code>（备选）。</p>
          <p>3. 分页接口使用 <code>page</code>（从 0 开始）和 <code>pageSize</code> 参数，响应中包含 <code>total</code>。</p>
          <p>4. 封面图片通过 <code>GET /api/open/v1/tracks/{id}/artwork</code> 获取二进制文件流，响应头含 <code>ETag</code> + <code>Cache-Control</code>。必要字段可通过 <code>GET .../artwork/meta</code> 获取。</p>
          <p>5. 建议客户端实现本地缓存策略，减少重复请求。</p>
        </el-alert>
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.openapi-page {
  max-width: 1200px;
}

.status-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  margin-bottom: 16px;
}

@media (max-width: 768px) {
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
