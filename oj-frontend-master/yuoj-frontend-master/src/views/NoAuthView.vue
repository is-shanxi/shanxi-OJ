<template>
  <div class="no-auth-view">
    <div class="lc-card no-auth-card">
      <h2 class="no-auth-title">暂无访问权限</h2>
      <p class="no-auth-text">你还没有权限访问该页面</p>
      <a-button type="primary" @click="$router.push('/')">返回首页</a-button>
    </div>
  </div>
</template>

<style scoped>
.no-auth-view {
  width: 100%;
  display: flex;
  justify-content: center;
  padding-top: 80px;
}

.no-auth-card {
  width: 400px;
  padding: 40px;
  text-align: center;
}

.no-auth-title {
  margin: 0 0 8px;
  font-size: 22px;
  font-weight: 700;
  color: var(--lc-text-primary);
}

.no-auth-text {
  margin: 0 0 20px;
  color: var(--lc-text-secondary);
}
</style>
