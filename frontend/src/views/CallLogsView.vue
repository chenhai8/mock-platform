<script setup>
import {computed} from 'vue';
import {ElConfigProvider, ElInput, ElOption, ElPagination, ElSelect} from 'element-plus';
import zhCn from 'element-plus/es/locale/lang/zh-cn';
import 'element-plus/es/components/input/style/css';
import 'element-plus/es/components/select/style/css';
import 'element-plus/es/components/pagination/style/css';
import TimeRangePicker from '../components/TimeRangePicker.vue';
import '../record-ui.css';

const props = defineProps({
  page: {type: Object, required: true},
  filters: {type: Object, required: true},
  endpoints: {type: Array, required: true},
  sourceNames: {type: Object, required: true},
  formatDate: {type: Function, required: true},
  loading: {type: Boolean, default: false}
});
const emit = defineEmits(['update:filters', 'query', 'detail']);
const publishedEndpoints = computed(() => props.endpoints.filter(endpoint => endpoint.published && !endpoint.archived));
const selectedEndpoint = computed(() => publishedEndpoints.value.find(endpoint => endpoint.id === props.filters.endpointId));
const scenes = computed(() => [...new Set((selectedEndpoint.value?.published.rules || []).map(rule => rule.name))]);
const invalidRange = computed(() => Boolean(props.filters.from && props.filters.to && props.filters.from > props.filters.to));

function updateFilter(field, value) {
  emit('update:filters', {[field]: value ?? ''});
}

function queryPage(page = 1) {
  if (!invalidRange.value && !props.loading) emit('query', page);
}

function endpointName(entry) {
  const endpoint = props.endpoints.find(endpoint => endpoint.id === entry.endpointId);
  const config = entry.source === 'DRAFT_DEBUG' ? endpoint?.draft : endpoint?.published;
  return config?.name || endpoint?.draft?.name || (entry.endpointId ? '接口配置不可用' : '未匹配接口');
}
</script>

<template>
  <ElConfigProvider :locale="zhCn">
    <section class="standalone record-page" :aria-busy="loading" aria-label="调用记录">
      <div class="record-heading">
        <div><h2>调用记录</h2><p>当前系统的请求与响应，按接口和场景快速定位</p></div>
        <button class="record-refresh" :disabled="loading || invalidRange" @click="queryPage(page.current)">刷新</button>
      </div>
      <form class="record-filter-card" @submit.prevent="queryPage(1)">
        <div class="record-filter-grid">
          <label class="record-field record-endpoint-field"><span class="record-field-label">已发布接口</span>
            <ElSelect :model-value="filters.endpointId || ''" filterable clearable :value-on-clear="''"
                      aria-label="接口路径" placeholder="全部接口" popper-class="record-select-popper record-endpoint-popper"
                      :title="selectedEndpoint?.published.path || '可选接口来自已发布配置'" no-data-text="当前系统暂无已发布接口"
                      @update:model-value="updateFilter('endpointId', $event)">
              <ElOption v-for="endpoint in publishedEndpoints" :key="endpoint.id" :value="endpoint.id"
                        :label="`${endpoint.published.name} · ${endpoint.published.method} ${endpoint.published.path}`">
                <span class="record-option-name">{{ endpoint.published.name }}</span>
                <span class="record-option-path"><b>{{ endpoint.published.method }}</b>{{ endpoint.published.path }}</span>
              </ElOption>
            </ElSelect>
          </label>
          <label class="record-field"><span class="record-field-label">响应场景</span>
            <ElSelect :model-value="filters.scene" filterable clearable :value-on-clear="''" aria-label="场景名称"
                      :disabled="!filters.endpointId" :placeholder="filters.endpointId ? '全部场景' : '请先选择接口'"
                      popper-class="record-select-popper" @update:model-value="updateFilter('scene', $event)">
              <ElOption v-for="scene in scenes" :key="scene" :label="scene" :value="scene" />
            </ElSelect>
          </label>
          <label class="record-field"><span class="record-field-label">调用来源</span>
            <ElSelect :model-value="filters.source" clearable :value-on-clear="''" aria-label="调用来源" placeholder="全部来源"
                      popper-class="record-select-popper" @update:model-value="updateFilter('source', $event)">
              <ElOption v-for="(name, key) in sourceNames" :key="key" :value="key" :label="name" />
            </ElSelect>
          </label>
          <label class="record-field"><span class="record-field-label">HTTP 状态码</span>
            <ElInput :model-value="filters.status" clearable aria-label="HTTP 状态码" placeholder="例如 200" type="number"
                     min="100" max="599" @update:model-value="updateFilter('status', $event)" />
          </label>
          <label class="record-field"><span class="record-field-label">平台错误码</span>
            <ElInput :model-value="filters.errorCode" clearable aria-label="平台错误码" placeholder="输入错误码"
                     @update:model-value="updateFilter('errorCode', $event)" />
          </label>
        </div>
        <div class="record-filter-bottom">
          <TimeRangePicker :from="filters.from" :to="filters.to" :invalid="invalidRange"
                           @change="emit('update:filters', $event)" />
          <button class="primary record-query" type="submit" :disabled="loading || invalidRange">{{ loading ? '查询中…' : '筛选' }}</button>
        </div>
        <p class="record-filter-hint">未指定时间时查询最近一个月 · 每页 20 条 · 诊断正文需在部署时开启</p>
      </form>
      <div class="record-results" :class="{'is-loading': loading}">
        <div v-if="loading" class="record-loading" role="status">正在加载调用记录…</div>
        <div class="table-wrap">
          <table class="call-log-table">
            <thead><tr><th>时间 / 来源</th><th>请求</th><th>结果</th><th>场景 / 版本</th><th>耗时</th><th>报文查看</th></tr></thead>
            <tbody>
              <tr v-for="entry in page.records" :key="entry.id">
                <td>{{ formatDate(entry.createdAt) }}<small>{{ sourceNames[entry.source] }}</small></td>
                <td><span class="record-request-name">{{ endpointName(entry) }}</span><small><b class="method-tag">{{ entry.requestMethod }}</b>{{ entry.requestPath }}</small></td>
                <td><span class="status-pill" :class="{bad: entry.errorCode}">{{ entry.status }}</span><small>{{ entry.errorCode || '配置响应' }}</small></td>
                <td>{{ entry.scene || '—' }}<small>版本/修订 {{ entry.configVersion }}</small></td>
                <td class="record-duration">{{ entry.durationMs }} ms</td>
                <td><div class="actions log-actions">
                  <button class="text-button" @click="emit('detail', entry)">详情</button>
                  <button class="text-button" @click="emit('detail', entry, 'request')">请求报文</button>
                  <button class="text-button" @click="emit('detail', entry, 'response')">返回报文</button>
                </div></td>
              </tr>
            </tbody>
          </table>
        </div>
        <div v-if="!page.records.length && !loading" class="record-empty"><b>还没有匹配的调用记录</b><span>试试其他接口、场景或时间范围</span></div>
      </div>
      <div class="record-pagination">
        <span>共 {{ page.total }} 条 <small>每页 20 条</small></span>
        <ElPagination :current-page="page.current" :page-size="20" :total="page.total" :pager-count="5"
                      layout="prev, pager, next" background :disabled="loading || invalidRange"
                      prev-text="上一页" next-text="下一页" @current-change="queryPage" />
      </div>
    </section>
  </ElConfigProvider>
</template>
