<script setup>
import {computed} from 'vue';
import {ElConfigProvider, ElPagination} from 'element-plus';
import zhCn from 'element-plus/es/locale/lang/zh-cn';
import 'element-plus/es/components/pagination/style/css';
import TimeRangePicker from '../components/TimeRangePicker.vue';
import '../record-ui.css';

const props = defineProps({
  page: {type: Object, required: true},
  filters: {type: Object, required: true},
  formatDate: {type: Function, required: true},
  loading: {type: Boolean, default: false}
});
const emit = defineEmits(['update:filters', 'query']);
const invalidRange = computed(() => Boolean(props.filters.from && props.filters.to && props.filters.from > props.filters.to));

function queryPage(page = 1) {
  if (!invalidRange.value && !props.loading) emit('query', page);
}
</script>

<template>
  <ElConfigProvider :locale="zhCn">
    <section class="standalone record-page" :aria-busy="loading" aria-label="操作记录">
      <div class="record-heading">
        <div><h2>操作记录</h2><p>查看配置变更与关键操作，追溯系统的每一次调整</p></div>
        <button class="record-refresh" :disabled="loading || invalidRange" @click="queryPage(page.current)">刷新</button>
      </div>
      <div class="record-audit-note">未启用身份认证，记录操作时间和服务端观测来源，不代表实名审计。</div>
      <form class="record-filter-card" @submit.prevent="queryPage(1)">
        <div class="record-filter-bottom">
          <TimeRangePicker :from="filters.from" :to="filters.to" :invalid="invalidRange"
                           start-label="操作开始时间" end-label="操作结束时间" @change="emit('update:filters', $event)" />
          <button class="primary record-query" type="submit" :disabled="loading || invalidRange">{{ loading ? '查询中…' : '筛选' }}</button>
        </div>
        <p class="record-filter-hint">未指定时间时查询最近一个月 · 每页 20 条</p>
      </form>
      <div class="record-results" :class="{'is-loading': loading}">
        <div v-if="loading" class="record-loading" role="status">正在加载操作记录…</div>
        <div v-for="operation in page.records" :key="operation.id" class="record-operation">
          <span class="record-operation-marker" aria-hidden="true"></span>
          <div class="record-operation-content">
            <div class="record-operation-title"><b>{{ operation.action }}</b><time>{{ formatDate(operation.createdAt) }}</time></div>
            <div class="record-operation-detail">{{ operation.detail }}</div>
            <div class="record-operation-meta"><span>{{ operation.targetType }} #{{ operation.targetId }}</span><span>来源 {{ operation.sourceAddress || '—' }}</span><span>未认证</span></div>
          </div>
        </div>
        <div v-if="!page.records.length && !loading" class="record-empty"><b>暂无操作记录</b><span>可以调整时间范围后重新查询</span></div>
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
