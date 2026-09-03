<template>
  <div id="userLoginView">
    <div class="lc-card login-card">
      <h2 class="login-title">登录 shanxi-Code</h2>
      <p class="login-subtitle">欢迎回来，开始今天的刷题之旅</p>
      <a-form layout="vertical" :model="form" @submit="handleSubmit">
        <a-form-item field="userAccount" label="账号">
          <a-input v-model="form.userAccount" placeholder="请输入账号" />
        </a-form-item>
        <a-form-item
          field="userPassword"
          tooltip="密码不少于 8 位"
          label="密码"
        >
          <a-input-password
            v-model="form.userPassword"
            placeholder="请输入密码"
          />
        </a-form-item>
        <a-form-item>
          <a-button type="primary" html-type="submit" long class="login-button">
            登录
          </a-button>
        </a-form-item>
      </a-form>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive } from "vue";
import { UserControllerService, UserLoginRequest } from "../../../generated";
import message from "@arco-design/web-vue/es/message";
import { useRoute, useRouter } from "vue-router";
import { useStore } from "vuex";

/**
 * 表单信息
 */
const form = reactive({
  userAccount: "",
  userPassword: "",
} as UserLoginRequest);

const router = useRouter();
const route = useRoute();
const store = useStore();

/**
 * 提交表单
 * @param data
 */
const handleSubmit = async () => {
  const res = await UserControllerService.userLoginUsingPost(form);
  // 登录成功，跳转回 redirect 指定的页面（仅允许站内路径），否则回主页
  if (res.code === 0) {
    await store.dispatch("user/getLoginUser");
    const redirect = route.query.redirect;
    const target =
      typeof redirect === "string" && redirect.startsWith("/") ? redirect : "/";
    router.push({
      path: target,
      replace: true,
    });
  } else {
    message.error("登陆失败，" + res.message);
  }
};
</script>

<style scoped>
#userLoginView {
  width: 100%;
  display: flex;
  justify-content: center;
}

.login-card {
  width: 400px;
  padding: 36px 36px 28px;
  text-align: left;
}

.login-title {
  margin: 0 0 4px;
  font-size: 24px;
  font-weight: 700;
  color: var(--lc-text-primary);
}

.login-subtitle {
  margin: 0 0 24px;
  color: var(--lc-text-secondary);
  font-size: 14px;
}

.login-button {
  margin-top: 8px;
  height: 40px;
  font-size: 15px;
}
</style>
