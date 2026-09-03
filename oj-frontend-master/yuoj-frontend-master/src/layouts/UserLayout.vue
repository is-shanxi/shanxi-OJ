<template>
  <div id="userLayout">
    <a-layout style="min-height: 100vh">
      <a-layout-header class="header">
        <a-space @click="$router.push('/')">
          <img src="../assets/shanxi-logo.png" class="logo" />
          <div class="title">shanxi-Code</div>
        </a-space>
      </a-layout-header>
      <a-layout-content class="content">
        <router-view />
      </a-layout-content>
    </a-layout>
  </div>
</template>

<style scoped>
#userLayout {
  text-align: center;
  background: var(--lc-bg);
}

#userLayout .logo {
  width: 48px;
  height: 48px;
  object-fit: cover;
  border-radius: var(--lc-radius-sm);
}

#userLayout .title {
  color: var(--lc-text-primary);
  font-size: 20px;
  font-weight: 700;
}

#userLayout .header {
  background: transparent;
  margin-top: 24px;
}

#userLayout .header :deep(.arco-space) {
  cursor: pointer;
}

#userLayout .content {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px 20px;
}
</style>
<script></script>
