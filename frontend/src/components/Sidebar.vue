<template>
<el-sidebar :width="isCollapse ? '64px' : '264px'">
     <el-menu
     :collapse="isCollapse"
     :collapse-transition="false"
        default-active="2"
        class="menu-style"
      >
      
        <div class="brand">
          <el-image v-show="!isCollapse" style="width: 50px;
          height: 50px; margin-right: 10px;" :src="iconUrl" alt="logo" />
          <div v-show="!isCollapse" class="info-card">
            <h1 class="brand-title">心理健康AI助手</h1>
            <p class="brand-subtitle">管理后台</p>
          </div>
        </div>

        <el-menu-item @click="selectMenu" v-for="item in router.options.routes[0].children" :key="item.path" :index="item.path" >
          <el-icon><component :is="item.meta.icon" /></el-icon>
          <span>{{item.meta.title}}</span>
        </el-menu-item>
       
      </el-menu>
    </el-sidebar>
</template> 

<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useAdminStore } from '@/stores/admin'
const adminStore = useAdminStore()
const router = useRouter()

console.log(router)

const iconUrl = new URL('../assets/images/机器人.png', import.meta.url).href

  const selectMenu = (key) => {
    const currentrouter=router.options.routes[0]
    router.push(`${currentrouter.path}/${key.index}`)
  }

  const isCollapse = computed(()=>adminStore.isCollapse)

</script>


<style lang="scss" scoped>
.menu-style {
  height: 100%;
  .brand {
  display: flex;
  align-items: center;
  justify-content: center;
  border-bottom: 1px solid #e5e7ed;
  background-color: #fff;
  margin-right: 20px;
  margin-left: 20px;
  

  .info-card {
   .brand-title {
    font-size: 20px;
    font-weight: bold;
    margin-bottom: 5px;
    margin-top: 10px;
    color: #1f2937;
   }
   .brand-subtitle {
    font-size: 14px;
    color: #6b7280;
    margin-bottom: 10px;
   }
  }
}

}



</style>