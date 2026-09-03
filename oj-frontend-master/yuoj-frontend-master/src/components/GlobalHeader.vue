<template>
  <a-row id="globalHeader" align="center" :wrap="false">
    <a-col flex="auto">
      <div class="header-inner">
        <div class="title-bar" @click="doMenuClick('/')">
          <img class="logo" src="../assets/shanxi-logo.png" />
          <div class="title">shanxi-Code</div>
        </div>
        <a-menu
          mode="horizontal"
          class="header-menu"
          :selected-keys="selectedKeys"
          @menu-item-click="doMenuClick"
        >
          <a-menu-item v-for="item in visibleRoutes" :key="item.path">
            {{ item.name }}
          </a-menu-item>
        </a-menu>
      </div>
    </a-col>
    <a-col flex="120px" :style="{ textAlign: 'right' }">
      <div>
        <template v-if="loginUser?.id">
          <a-dropdown trigger="click" @select="doDropdownSelect">
            <a-avatar
              :style="{ backgroundColor: '#ffa116', cursor: 'pointer' }"
            >
              {{ loginUser.userName?.charAt(0) ?? "用" }}
            </a-avatar>
            <template #content>
              <a-doption disabled>{{ loginUser.userName }}</a-doption>
              <a-doption value="logout">登出</a-doption>
            </template>
          </a-dropdown>
        </template>
        <a-button v-else type="primary" size="small" @click="doLogin">
          登录
        </a-button>
      </div>
    </a-col>
  </a-row>
</template>

<script setup lang="ts">
import { routes } from "../router/routes";
import { useRoute, useRouter } from "vue-router";
import { computed, ref } from "vue";
import { useStore } from "vuex";
import checkAccess from "@/access/checkAccess";
import message from "@arco-design/web-vue/es/message";

const router = useRouter();
const route = useRoute();
const store = useStore();

// 展示在菜单的路由数组
const visibleRoutes = computed(() => {
  return routes.filter((item) => {
    if (item.meta?.hideInMenu) {
      return false;
    }
    // 根据权限过滤菜单
    if (
      !checkAccess(store.state.user.loginUser, item?.meta?.access as string)
    ) {
      return false;
    }
    return true;
  });
});

// 默认主页
const selectedKeys = ref(["/"]);

// 路由跳转后，更新选中的菜单项
router.afterEach((to) => {
  selectedKeys.value = [to.path];
});

const doMenuClick = (key: string) => {
  router.push({
    path: key,
  });
};

// 当前登录用户
const loginUser = computed(() => store.state.user?.loginUser);

const doLogin = () => {
  router.push({
    path: "/user/login",
    query: {
      redirect: route.fullPath,
    },
  });
};

// 头像下拉菜单：登出
const doDropdownSelect = async (key: string | number | object) => {
  if (key === "logout") {
    const res = await store.dispatch("user/logout");
    if (res.code === 0) {
      message.success("登出成功");
      router.push({
        path: "/",
        replace: true,
      });
    } else {
      message.error("登出失败，" + res.message);
    }
  }
};
</script>

<style scoped>
#globalHeader {
  height: 56px;
  max-width: 1440px;
  margin: 0 auto;
  padding: 0 24px;
}

.header-inner {
  display: flex;
  align-items: center;
  height: 56px;
}

.title-bar {
  display: flex;
  align-items: center;
  cursor: pointer;
}

.title {
  color: var(--lc-text-primary);
  font-size: 18px;
  font-weight: 700;
  margin-left: 8px;
}

.logo {
  height: 40px;
  width: 40px;
  object-fit: cover;
  border-radius: var(--lc-radius-sm);
}

.header-menu {
  flex: 1;
  max-width: 640px;
  border-bottom: none;
  background: transparent;
  margin-left: 24px;
}

.header-menu :deep(.arco-menu-item) {
  font-size: 14px;
  color: var(--lc-text-secondary);
  line-height: 56px;
}

.header-menu :deep(.arco-menu-item:hover) {
  color: var(--lc-text-primary);
  background: transparent;
}

.header-menu :deep(.arco-menu-selected-item) {
  color: var(--lc-text-primary);
  font-weight: 600;
  background: transparent !important;
}
</style>
