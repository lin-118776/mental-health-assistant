<template>
    <div>
        <pageHead title="咨询记录" />
        <el-table :data="tableData" style="width: 100%; margin-top: 20px;">
           <el-table-column label="用户" width="200">
                <template #default="scope">
                    <div style="display: flex; align-items: center; gap: 10px;">
                        <el-avatar :size="32" :src="scope.row.userAvatar">{{ scope.row.userNickname?.charAt(0) }}</el-avatar>
                        <span>{{ scope.row.userNickname }}</span>
                    </div>
                </template>
           </el-table-column>
            <el-table-column label="情绪日志" min-width="300">
                <template #default="scope">
                    <div class="session-info">
                        <div class="session-title" style="font-weight: bold; margin-bottom: 4px;">{{ scope.row.sessionTitle }}</div>
                        <div class="session-content" style="font-size: 13px; color: #666; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;">
                            {{ scope.row.lastMessageContent }}
                        </div>
                    </div>
                </template>
           </el-table-column>
           <el-table-column prop="messageCount" label="消息数" width="120" align="center" />
           <el-table-column prop="lastMessageTime" label="最近咨询时间" width="200" align="center" />
            <el-table-column label="操作" width="120" fixed="right" align="center">
                <template #default="scope">
                   <el-button type="primary" text @click="viewSessionDetail(scope.row)">详情</el-button>
                </template>
           </el-table-column>
        </el-table>

       <el-pagination 
            style="margin-top: 25px; align-items: center; justify-content: center;"
            :page-size="pagination.size"
            layout="prev, pager, next"
            :total="pagination.total"
            @current-change="handleChange"
            />
            <el-dialog
                v-model="showdetailDialog"
                title="咨询会话详情"
                width="70%"
                :close-on-click-modal="false"
            >
                <div class="session-detail">
                    <div class="detail-header">
                        <div class="detail-row">
                            <div class="detail-label">用户：</div>
                            <div class="detail-value">{{ sessionDetail.userNickname }}</div>
                        </div>
                         <div class="detail-row">
                            <div class="detail-label">最近咨询时间：</div>
                            <div class="detail-value">{{ sessionDetail.lastMessageTime }}</div>
                        </div>
                         <div class="detail-row">
                            <div class="detail-label">消息数：</div>
                            <div class="detail-value">{{ sessionDetail.messageCount }}</div>
                        </div>
                    </div>
                    <div class="messages-container">
                        <div class="messages-header">
                            <h4>对话记录</h4>
                        </div>
                    </div>
                    <div class="messages-list" v-loading="loadingmessage">
                        <div v-for="message in sessionMessages" :key="message.id" class="message-item" :class="message.senderType === 1 ? 'user-message' : 'ai-message'">
                            <div class="message-header">
                                <span class="sender">{{ message.senderType === 1 ? '用户' : 'AI助手' }}</span>
                                <span class="time">{{ message.createdAt }}</span>
                            </div>
                            <div class="message-content">{{ message.content }}</div>
                        </div>
                    </div>
                </div>
                 <template #footer>        
                    <el-button  @click="showdetailDialog = false">关闭</el-button>                  
                </template>
            </el-dialog>
    </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import pageHead from '@/components/PageHead.vue'
import { getConsultations,getConsultationDetail } from '@/api/admin'
 


const pagination = ref({
    currentPage: 1,
    size: 10,
    total: 0
})
const tableData = ref([])

const fetchList = () => {
    const params = {
        page: pagination.value.currentPage,
        size: pagination.value.size
    }
    getConsultations(params).then(res => {
        const { records, total } = res.data
        tableData.value = records
        pagination.value.total = total
    }).catch(err => {
        console.error('获取数据失败:', err)
    })
}

onMounted(() => {
    fetchList()
})

const handleChange = (val) => {
    pagination.value.currentPage = val
    fetchList()
}
//会话详情
const sessionDetail = ref({})
const sessionMessages = ref([])
const loadingmessage = ref(false)

const viewSessionDetail = (row) => {
    loadingmessage.value = true
    showdetailDialog.value = true
    sessionDetail.value = row // 保存当前行数据用于头部展示
    getConsultationDetail(row.id).then(res => {
        console.log(res);
        loadingmessage.value = false
        sessionMessages.value = res.data
    }).catch(err => {
        loadingmessage.value = false
        console.error('获取详情失败:', err)
    })
}

//详情
const showdetailDialog = ref(false)


</script>

<style lang="scss" scoped>
.session-title {
    font-weight: 500;
    color: #333;
    margin-bottom: 4px;
  }
  .session-preview {
    font-size: 13px;
    color: #666;
    margin-bottom: 4px;
    display: -webkit-box;
    -webkit-line-clamp: 2;
    line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;
  }
  .session-detail {
    max-height: 70vh;
    overflow-y: auto;
    .detail-header {
      margin-bottom: 20px;
      padding: 16px;
      background: #9cc3ea;
      border-radius: 8px;
      border: 1px solid #e9ecef;
    }

    .detail-row {
      display: flex;
      align-items: center;
      margin-bottom: 8px;
      :last-child {
        margin-bottom: 0;
      }
      .detail-label {
        font-weight: 500;
        color: #495057;
        min-width: 80px;
        margin-right: 8px;
      }

      .detail-value {
        color: #333;
      }
    }
  }
  .messages-container {
    margin-top: 20px;
    .messages-header {
      margin-bottom: 16px;
      h4 {
        margin: 0;
        color: #333;
        font-size: 16px;
        font-weight: 500;
      }
    }
    .messages-list {
      max-height: 400px;
      overflow-y: auto;
      border: 1px solid #e9ecef;
      border-radius: 8px;
      padding: 16px;
      background: #ffffff;
      .message-item {
        margin-bottom: 12px;
        padding: 12px;
        border-radius: 8px;
        background: #f8f9fa;
        border: 1px solid #e9ecef;
        :last-child {
          margin-bottom: 0;
        }
        &.user-message {
          background: #e8f4fd;
        }

        &.ai-message {
          background: #f0f9f0;
        }
      }
      .message-header {
        display: flex;
        justify-content: space-between;
        align-items: center;
        margin-bottom: 8px;
        .sender {
          font-weight: 500;
          color: #333;
          display: flex;
          align-items: center;
          gap: 4px;
        }

        .time {
          font-size: 12px;
          color: #999;
        }
      }

      .message-content {
        color: #333;
        line-height: 1.6;
        white-space: pre-wrap;
        margin-top: 8px;
        font-size: 14px;
      }
    }
  }
</style>
