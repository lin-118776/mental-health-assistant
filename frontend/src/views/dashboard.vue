<template>
    <div class="dashboard-container" v-loading="loading">
        <el-alert v-if="loadError" title="统计加载失败，请重试" type="error" :closable="false" />
        <el-button v-if="loadError" @click="loadOverview">重试</el-button>
        <p class="statistics-note">按北京时间统计。近7天活跃用户指新建日志或发送消息的去重用户；今日新增按创建时间计算。</p>
        <el-row :gutter="20">
            <el-col :span="6">
               <el-card>
                    <div class="card-content">
                        <div class="avatar users">
                            <el-image style="width: 40px; height: 40px;"  :src="iconUrl1"></el-image>
                        </div>
                        <div class="info">
                            <p class="title">总用户数</p>
                            <p class="number">{{aiData?.systemOverview?.totalUsers ?? '--'}}</p>
                            <p class="subtitle-title">近7天活跃：{{aiData?.systemOverview?.activeUsers ?? '--'}}</p>
                        </div>
                    </div>
               </el-card>
            </el-col>

            <el-col :span="6">
               <el-card>
                    <div class="card-content">
                        <div class="avatar like">
                            <el-image style="width: 40px; height: 40px;"  :src="iconUrl2"></el-image>
                        </div>
                        <div class="info">
                            <p class="title">情绪日志</p>
                            <p class="number">{{aiData?.systemOverview?.totalDiaries ?? '--'}}</p>
                            <p class="subtitle-title">今日新增：{{aiData?.systemOverview?.todayDiaries ?? '--'}}</p>
                        </div>
                    </div>
               </el-card>
            </el-col>

            <el-col :span="6">
               <el-card>
                    <div class="card-content">
                        <div class="avatar comments">
                            <el-image style="width: 40px; height: 40px;"  :src="iconUrl3"></el-image>
                        </div>
                        <div class="info">
                            <p class="title">咨询会话</p>
                            <p class="number">{{aiData?.systemOverview?.totalSesss ?? '--'}}</p>
                            <p class="subtitle-title">今日新增：{{aiData?.systemOverview?.todaySesss ?? '--'}}</p>
                        </div>
                    </div>
               </el-card>
            </el-col>

            <el-col :span="6">
               <el-card>
                    <div class="card-content">
                        <div class="avatar smile">
                            <el-image style="width: 40px; height: 40px;"  :src="iconUrl4"></el-image>
                        </div>
                        <div class="info">
                            <p class="title">平均情绪</p>
                            <p class="number">{{aiData?.systemOverview?.avgMoodScore ?? '--'}}/10</p>
                            <p class="subtitle-title">全部日志评分均值</p>
                        </div>
                    </div>
               </el-card>
            </el-col>           
        </el-row>

        <el-row :gutter="20" style="margin-top: 20px;"> 
          <el-col :span="12">
            <el-card style="width: 100%;">
              <template #header>
                <div class="card-header"> 
                  情绪趋势分析
                </div>
              </template>
                <div ref="emotionChartRef" style="width: 100%; height: 400px;"></div>
            </el-card>
          </el-col>

           <el-col :span="12">
            <el-card style="width: 100%;">
              <template #header>
                <div class="card-header"> 
                  咨询会话统计
                </div>
              </template>
              <div class="chart-content">
                <div class="consultation-stats">
                  <div class="stat-item">
                    <div class="stat-label">总会话数</div>
                    <div class="stat-value">{{aiData?.consultationStats?.totalSessions ?? '--'}}</div>
                  </div>
                  <div class="stat-item">
                    <div class="stat-label" title="有用户发言的会话从首条到末条消息的平均时间差，不代表在线使用时长">平均消息跨度</div>
                    <div class="stat-value">{{aiData?.consultationStats?.avgDurationMinutes ?? '--'}}分钟</div>
                  </div>
                  <div class="stat-item">
                    <div class="stat-label">近7天活跃用户</div>
                    <div class="stat-value">{{aiData?.systemOverview?.activeUsers ?? '--'}}</div>
                  </div>
                </div>
              </div>
                <p class="statistics-note">总会话数含尚未发言的会话；下图按每天有用户发言的会话统计。消息跨度不代表在线使用时长。</p>
                <div ref="consultationChartRef" style="width: 100%; height: 293px;"></div>
            </el-card>
          </el-col>
        </el-row>

        <el-row style="margin-top: 20px;">         
            <el-card style="width: 100%;">
              <template #header>
                <div class="card-header"> 
                  用户活跃度趋势
                </div>
              </template>
              <div class="chart-content">
                <div ref="userActiveChartRef" style="width: 100%; height: 400px;"></div>
              </div>
            </el-card> 
        </el-row>
    </div>
</template>

<script setup>
import { getAnalyticsOverview } from '@/api/admin'
import { onMounted,ref } from 'vue'
import * as echarts from 'echarts'
import { Position } from '@element-plus/icons-vue'
//图片引入
const iconUrl1=new URL('@/assets/images/users.png',import.meta.url).href
const iconUrl2=new URL('@/assets/images/like.png',import.meta.url).href
const iconUrl3=new URL('@/assets/images/comments.png',import.meta.url).href
const iconUrl4=new URL('@/assets/images/smile.png',import.meta.url).href



const aiData=ref({})
const loading = ref(false)
const loadError = ref(false)



//初始化图标
const initCharts =()=>{
  initEmotionChart()
  initConsultationChart()
  initUserActiveChart()
}

//初始化咨询绘画统计图标
let consultationChart=null
const consultationChartRef=ref(null)
//初始化咨询绘画统计图标
const initConsultationChart =()=>{
  if(!consultationChartRef.value) return
  //销毁现有图标
  if(consultationChart) {
    consultationChart.dispose()
  }
  //创建echarts实例
    consultationChart=echarts.init(consultationChartRef.value)  

    //获取数据

    const dailyTrend = aiData.value.consultationStats.dailyTrend

    const option = {
      title: {
        text: '咨询活动统计',
        textStyle: {
          fontSize: 16,
          fontWeight: 600,
          color: '#2d3436'
        },
        left: 'center',
        top: 10
      },
      tooltip: {
        trigger: 'axis',
        backgroundColor: 'rgba(255, 255, 255, 0.95)',
        borderColor: '#fab1a0',
        borderWidth: 1,
        textStyle: {
          color: '#2d3436'
        }
      },
      legend: {
        data: ['会话数量', '参与用户数'],
        top: 40,
        textStyle: {
          color: '#636e72'
        }
      },
      grid: {
        left: '3%',
        right: '4%',
        bottom: '3%',
        top: 80,
        containLabel: true
      },
      xAxis: {
        type: 'category',
        data: dailyTrend.map(item =>item.date),
        axisLine: {
          lineStyle: {
            color: 'rgba(244, 162, 97, 0.3)'
          }
        },
        axisLabel: {
          color: '#636e72'
        }
      },
      yAxis: {
        type: 'value',
        min: 0,
        minInterval: 1,
        axisLabel: {
          color: '#636e72'
        },
        axisLine: {
          lineStyle: {
            color: 'rgba(244, 162, 97, 0.3)'
          }
        },
        splitLine: {
          lineStyle: {
            color: 'rgba(244, 162, 97, 0.1)'
          }
        }
      },
      series: [
        {
          name: '会话数量',
          type: 'bar',
          data: dailyTrend.map(item =>item.sessionCount),
          itemStyle: {
            color: {
              type: 'linear',
              x: 0,
              y: 0,
              x2: 0,
              y2: 1,
              colorStops: [
                { offset: 0, color: '#74b9ff' },
                { offset: 1, color: '#0984e3' }
              ]
            }
          },
          barWidth: '40%'
        },
        {
          name: '参与用户数',
          type: 'bar',
          data: dailyTrend.map(item =>item.userCount),
          itemStyle: {
            color: {
              type: 'linear',
              x: 0,
              y: 0,
              x2: 0,
              y2: 1,
              colorStops: [
                { offset: 0, color: '#fdcb6e' },
                { offset: 1, color: '#f39c12' }
              ]
            }
          },
          barWidth: '40%'
        }
      ]
    }
    consultationChart.setOption(option)
}

//初始化用户活跃度趋势图标
let userActiveChart=null
const userActiveChartRef=ref(null)
//初始化用户活跃度趋势图标
const initUserActiveChart =()=>{
  if(!userActiveChartRef.value) return
  //销毁现有图标
  if(userActiveChart) {
    userActiveChart.dispose()
  }
  //创建echarts实例
    userActiveChart=echarts.init(userActiveChartRef.value)  

    const userActiveData = aiData.value.userActivity || aiData.value.userActivityity || []

    const option = {
      title: {
        text: '用户活跃度趋势',
        textStyle: {
          fontSize: 16,
          fontWeight: 600,
          color: '#2d3436'
        },
        left: 'center',
        top: 10
      },
      tooltip: {
        trigger: 'axis',
        backgroundColor: 'rgba(255, 255, 255, 0.95)',
        borderColor: '#fab1a0',
        borderWidth: 1,
        textStyle: {
          color: '#2d3436'
        }
      },
      legend: {
        data: ['活跃用户', '新增用户', '日记用户', '咨询用户'],
        top: 40,
        textStyle: {
          color: '#636e72'
        }
      },
      grid: {
        left: '3%',
        right: '4%',
        bottom: '3%',
        top: 80,
        containLabel: true
      },
      xAxis: {
        type: 'category',
        data: userActiveData.map(item =>item.date),
        axisLine: {
          lineStyle: {
            color: 'rgba(244, 162, 97, 0.3)'
          }
        },
        axisLabel: {
          color: '#636e72'
        }
      },
      yAxis: {
        type: 'value',
        axisLabel: {
          color: '#636e72'
        },
        axisLine: {
          lineStyle: {
            color: 'rgba(244, 162, 97, 0.3)'
          }
        },
        splitLine: {
          lineStyle: {
            color: 'rgba(244, 162, 97, 0.1)'
          }
        }
      },
      series: [
        {
          name: '活跃用户',
          type: 'line',
          data: userActiveData.map(item =>item.activeUsers),
          smooth: true,
          lineStyle: {
            width: 3,
            color: '#a29bfe'
          },
          itemStyle: {
            color: '#a29bfe'
          },
          areaStyle: {
            color: {
              type: 'linear',
              x: 0,
              y: 0,
              x2: 0,
              y2: 1,
              colorStops: [
                { offset: 0, color: 'rgba(162, 155, 254, 0.4)' },
                { offset: 1, color: 'rgba(162, 155, 254, 0.1)' }
              ]
            }
          }
        },
        {
          name: '新增用户',
          type: 'line',
          data: userActiveData.map(item =>item.newUsers),
          smooth: true,
          lineStyle: {
            width: 3,
            color: '#fdcb6e'
          },
          itemStyle: {
            color: '#fdcb6e'
          }
        },
        {
          name: '日记用户',
          type: 'line',
          data: userActiveData.map(item =>item.diaryUsers),
          smooth: true,
          lineStyle: {
            width: 3,
            color: '#00b894'
          },
          itemStyle: {
            color: '#00b894'
          }
        },
        {
          name: '咨询用户',
          type: 'line',
          data: userActiveData.map(item =>item.consultationUsers),
          smooth: true,
          lineStyle: {
            width: 3,
            color: '#fab1a0'
          },
          itemStyle: {
            color: '#fab1a0'
          }
        }
      ]
    }

    userActiveChart.setOption(option)
}
//获取数据








//情绪趋势
let emotionChart=null
const emotionChartRef=ref(null)
//初始化情绪趋势图标
const initEmotionChart =()=>{
  if(!emotionChartRef.value) return
  //销毁现有图标
  if(emotionChart) {
    emotionChart.dispose()
  }
  //
  //创建echarts实例
    emotionChart=echarts.init(emotionChartRef.value)  
    //获取情绪趋势数据
    const trendData = aiData.value.emotionTrend || []
    //设置图标配置
    const option={
      title: {
        text: '情绪趋势分析',
        textStyle: {
          fontSize: 16,
          color: '#2d3436',
          fontWeight: 600
        },
        left: 'center',
        top: 10
      },
      tooltip:{
        trigger:'axis',
        borderColor:'#fab1a0',
        borderWidth:1,
        textStyle:{
          color: '#2d3436',        
        }
      },
      legend:{
        data:['平均情绪评分','记录数量'],
        top:40
        },
      grid:{
        top: 100,
        bottom: 60,
        left: '5%',
        right: '5%'
      },
      xAxis: {
        type: 'category',
        data: trendData.map(item=>item.date),
        axisLabel:{
          color: '#2d3436'
        }
      },
      yAxis: [{
        type: 'value',
        min: 1,
        max: 10,
        name: '情绪评分',
        position: 'left',
        axisLine:{
          lineStyle:{
            color: '#2d3436', 
          }
        }
      },{
        type: 'value',
        min: 0,
        minInterval: 1,
        name: '记录数量',
        position: 'right',
        axisLine:{
          lineStyle:{
            color: '#2d3436', 
          }
        }
      }],
      series: [{
        name: '平均情绪评分',
        type: 'line',
        data: trendData.map(item=>item.avgMoodScore),
        connectNulls: false,
        smooth: true,
        lineStyle:{
          color: '#facbaf',
          width: 3
        },
        itemStyle:{
          color: '#facbaf',
        }
      },
    {
      name: '记录数量',
      type: 'line',
      data: trendData.map(item=>item.sessionCount),
      yAxisIndex: 1,
      smooth: true,
      lineStyle:{
        color: '#eeb5a3',
        width: 3
      },
      itemStyle:{
        color: '#eeb5a3',
      }
    }]
    }

    emotionChart.setOption(option)
}






const loadOverview = async () => {
    if (loading.value) return
    loading.value = true
    loadError.value = false
    try {
        const res = await getAnalyticsOverview()
        aiData.value = res.data
        initCharts()
    } catch {
        aiData.value = {}
        loadError.value = true
        ;[emotionChart, consultationChart, userActiveChart].forEach(chart => chart?.clear())
    } finally {
        loading.value = false
    }
}
onMounted(loadOverview)
</script>

<style lang="scss" scoped>
.statistics-note { color: #6b7280; font-size: 13px; margin-bottom: 16px; }
.dashboard-container {
    .card-content {
      display: flex;
      align-items: center;
      .avatar {
        margin-right: 12px;
        width: 60px;
        height: 60px;
        border-radius: 12px;
        display: flex;
        align-items: center;
        justify-content: center;
        &.users {
          background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
        }
        &.like {
          background: linear-gradient(135deg, #f093fb 0%, #f5576c 100%);
        }
        &.comments {
          background: linear-gradient(135deg, #4facfe 0%, #00f2fe 100%);
        }
        &.smile {
          background: linear-gradient(135deg, #43e97b 0%, #38f9d7 100%);
        }
      }
      .info {
        .title {
          font-size: 14px;
          color: #7f8c8d;
          margin-bottom: 4px;
        }
        .value {
          font-size: 24px;
          font-weight: 700;
          color: #2c3e50;
          margin-bottom: 4px
        }
        .subtitle-title {
          font-size: 12px;
          color: #95a5a6;
        }
      }
    }
    .chart-content {
      padding: 20px;
      // height: 300px;
      position: relative;

      canvas {
        width: 100% !important;
        height: 100% !important;
      }

      .consultation-stats {
        display: flex;
        justify-content: space-around;
        margin-bottom: 20px;

        .stat-item {
          text-align: center;

          .stat-label {
            font-size: 12px;
            color: #7f8c8d;
            margin-bottom: 4px;
          }

          .stat-value {
            font-size: 18px;
            font-weight: 600;
            color: #2c3e50;
          }
        }
      }
    }
  }
</style>
