<script setup lang="ts">
import Sidebar from './Sidebar.vue';
import Navbar from './Navbar.vue';


</script>

<template>
     <div class="common-layout">
    <el-container class="main-container">
      <sidebar />
      <el-container>
        <el-header>
            <Navbar />
        </el-header>
        <el-main class="main-content">
            <router-view class="content-container"></router-view>
        </el-main>
      </el-container>
    </el-container>
  </div>
    
</template>


<style lang="scss" scoped>
.common-layout {
  height: 100vh;
  .el-header {
    height: 74px !important;
  }
  .main-container {
    height: 100%;
    .content-container {
        padding: 20px;
        height: calc(100% - 74px);
        background-color: #fff;
    }
  }
}
</style>
