<template>
    <section class="personal-trend" v-loading="loading">
        <div class="trend-heading">
            <h2>我的情绪趋势</h2>
            <el-radio-group v-model="days" @change="refresh">
                <el-radio-button :value="7">近七天</el-radio-button>
                <el-radio-button :value="30">近一个月（30天）</el-radio-button>
            </el-radio-group>
        </div>
        <p class="trend-note">{{ rangeText }} · 按北京时间统计，包含今天。同日多条日志取均值，缺少记录的日期留空。</p>
        <el-alert v-if="error" title="趋势加载失败，请重试" type="error" :closable="false" />
        <el-button v-if="error" @click="refresh">重试</el-button>
        <el-empty v-else-if="!loading && !hasRecords" description="这段时间还没有日志，记录心情后即可查看趋势" />
        <div v-show="!error && hasRecords">
            <div v-for="metric in metrics" :key="metric.key" class="trend-metric">
                <h3>{{ metric.title }}</h3>
                <p class="trend-note">{{ metric.description }}</p>
                <div v-if="!loading && !hasValues(metric.key)" class="metric-empty">这段时间未填写{{ metric.title }}</div>
                <div :ref="el => setChartElement(metric.key, el)" class="trend-chart" :aria-label="`${metric.title}变化折线图`" role="img"></div>
            </div>
        </div>
    </section>
</template>

<script setup>
import { ref, computed, nextTick, onMounted, onBeforeUnmount } from 'vue'
import * as echarts from 'echarts/core'
import { LineChart } from 'echarts/charts'
import { GridComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { getMyEmotionTrend } from '@/api/frontend'

echarts.use([LineChart, GridComponent, TooltipComponent, CanvasRenderer])
const days = ref(7)
const rows = ref([])
const rangeText = ref('')
const loading = ref(false)
const error = ref(false)
const hasRecords = computed(() => rows.value.some(row => row.diaryCount > 0))
const metrics = [
    { key: 'moodScore', title: '情绪评分', max: 10, color: '#4b9c8c', description: '1–10 分，越高表示心情越积极' },
    { key: 'sleepQuality', title: '睡眠质量', max: 5, color: '#638ad6', description: '1–5 分，越高表示睡眠质量越好' },
    { key: 'stressLevel', title: '压力水平', max: 5, color: '#da9a40', description: '1–5 分，越高表示压力越大' }
]
const elements = new Map()
const charts = new Map()
let requestId = 0
let disposed = false
let resizeObserver

const setChartElement = (key, el) => {
    if (el) elements.set(key, el)
    else elements.delete(key)
}
const hasValues = key => rows.value.some(row => row[key] !== null && row[key] !== undefined)
const resizeCharts = () => charts.forEach(chart => chart.resize())
const renderCharts = () => {
    if (disposed || !hasRecords.value || error.value) return
    for (const metric of metrics) {
        const element = elements.get(metric.key)
        if (!element) continue
        let chart = charts.get(metric.key)
        if (!chart) {
            chart = echarts.init(element)
            charts.set(metric.key, chart)
        }
        chart.setOption({
            color: [metric.color],
            tooltip: { trigger: 'axis', valueFormatter: value => value == null ? '未记录' : `${value} 分` },
            grid: { top: 16, left: 40, right: 20, bottom: 32 },
            xAxis: { type: 'category', data: rows.value.map(row => row.date), axisLabel: { formatter: date => date.slice(5) } },
            yAxis: { type: 'value', min: 1, max: metric.max, minInterval: 1 },
            series: [{ name: metric.title, type: 'line', connectNulls: false,
                data: rows.value.map(row => row[metric.key] ?? null), symbolSize: 7 }]
        }, true)
        chart.resize()
    }
}

const refresh = async () => {
    if (disposed) return
    const id = ++requestId
    loading.value = true
    error.value = false
    // 切换周期后不展示上一个周期的曲线。
    rows.value = []
    rangeText.value = ''
    try {
        const res = await getMyEmotionTrend(days.value)
        if (disposed || id !== requestId) return
        rows.value = res.data.dailyTrend
        rangeText.value = `${res.data.startDate} 至 ${res.data.endDate}`
        await nextTick()
        if (!disposed && id === requestId) renderCharts()
    } catch {
        if (!disposed && id === requestId) error.value = true
    } finally {
        if (!disposed && id === requestId) loading.value = false
    }
}

onMounted(() => {
    if (typeof ResizeObserver !== 'undefined') {
        resizeObserver = new ResizeObserver(resizeCharts)
        elements.forEach(element => resizeObserver.observe(element))
    }
    window.addEventListener('resize', resizeCharts)
    refresh()
})
onBeforeUnmount(() => {
    disposed = true
    requestId++
    resizeObserver?.disconnect()
    window.removeEventListener('resize', resizeCharts)
    charts.forEach(chart => chart.dispose())
    charts.clear()
})
defineExpose({ refresh })
</script>

<style scoped>
.personal-trend { background: white; border-radius: 10px; padding: 20px; margin-bottom: 20px; box-shadow: 0 4px 6px rgba(0, 0, 0, 0.05); }
.trend-heading { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 12px; }
.trend-heading h2 { margin: 0; font-size: 25px; color: #374151; }
.trend-metric h3 { margin: 20px 0 4px; color: #374151; }
.trend-note, .metric-empty { color: #6b7280; font-size: 13px; }
.trend-chart { height: 220px; width: 100%; }
</style>
