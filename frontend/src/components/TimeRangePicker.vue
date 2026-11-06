<script setup>
import {ElDatePicker} from 'element-plus';
import 'element-plus/es/components/date-picker/style/css';

const props = defineProps({
  from: {type: String, default: ''},
  to: {type: String, default: ''},
  startLabel: {type: String, default: '开始时间'},
  endLabel: {type: String, default: '结束时间'},
  invalid: {type: Boolean, default: false}
});
const emit = defineEmits(['change']);
const MINUTE_MS = 60_000;
const DAY_MS = 24 * 60 * MINUTE_MS;

function updateDate(field, value) {
  emit('change', {from: props.from, to: props.to, [field]: value || ''});
}

function localDateTime(date) {
  return new Date(date.getTime() - date.getTimezoneOffset() * MINUTE_MS).toISOString().slice(0, 16);
}

function selectShortcut(range) {
  const end = new Date();
  const start = new Date(end);
  if (range === 'month') {
    start.setMonth(start.getMonth() - 1);
    // 月末向前移动月份可能溢出，回退到目标月的最后一天
    if (start.getDate() !== end.getDate()) start.setDate(0);
  } else {
    start.setTime(end.getTime() - (range === 'week' ? 7 : 1) * DAY_MS);
  }
  emit('change', {from: localDateTime(start), to: localDateTime(end)});
}
</script>

<template>
  <div class="record-time-range" role="group" aria-label="时间范围">
    <div class="record-time-heading">
      <span class="record-field-label">时间范围</span>
      <div class="record-shortcuts" aria-label="快捷时间范围">
        <button type="button" @click="selectShortcut('day')">近 24 小时</button>
        <button type="button" @click="selectShortcut('week')">近 7 天</button>
        <button type="button" @click="selectShortcut('month')">近 1 个月</button>
      </div>
    </div>
    <div class="record-time-fields" :class="{'is-invalid': invalid}">
      <ElDatePicker :model-value="from || null" type="datetime" format="YYYY-MM-DD HH:mm"
                    value-format="YYYY-MM-DDTHH:mm" placeholder="开始时间" :aria-label="startLabel"
                    clearable popper-class="record-picker-popper" :show-confirm="true"
                    @update:model-value="updateDate('from', $event)" />
      <span class="record-time-separator" aria-hidden="true">至</span>
      <ElDatePicker :model-value="to || null" type="datetime" format="YYYY-MM-DD HH:mm"
                    value-format="YYYY-MM-DDTHH:mm" placeholder="结束时间" :aria-label="endLabel"
                    clearable popper-class="record-picker-popper" :show-confirm="true"
                    @update:model-value="updateDate('to', $event)" />
    </div>
    <p v-if="invalid" class="record-range-error" role="alert">开始时间不能晚于结束时间</p>
  </div>
</template>
