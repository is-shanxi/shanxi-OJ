<template>
  <div id="userRegisterView">
    <div class="lc-card register-card">
      <h2 class="register-title">注册 shanxi-Code</h2>
      <p class="register-subtitle">创建账号，开始你的刷题之旅</p>
      <a-form
        layout="vertical"
        :model="form"
        :rules="rules"
        @submit-success="handleSubmit"
      >
        <a-form-item field="userAccount" label="账号">
          <a-input
            v-model="form.userAccount"
            placeholder="请输入账号（不少于 4 位）"
          />
        </a-form-item>
        <a-form-item field="userPassword" label="密码">
          <a-input-password
            v-model="form.userPassword"
            placeholder="请输入密码（不少于 8 位）"
          />
        </a-form-item>
        <a-form-item field="checkPassword" label="确认密码">
          <a-input-password
            v-model="form.checkPassword"
            placeholder="请再次输入密码"
          />
        </a-form-item>
        <a-form-item>
          <a-button
            type="primary"
            html-type="submit"
            long
            class="register-button"
          >
            注册
          </a-button>
        </a-form-item>
      </a-form>
      <div class="register-footer">
        已有账号？
        <router-link to="/user/login" class="login-link">去登录</router-link>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive } from "vue";
import { UserControllerService, UserRegisterRequest } from "../../../generated";
import message from "@arco-design/web-vue/es/message";
import { useRouter } from "vue-router";

/**
 * 表单信息
 */
const form = reactive({
  userAccount: "",
  userPassword: "",
  checkPassword: "",
} as UserRegisterRequest);

/**
 * 表单校验规则（与后端校验规则保持一致：账号不少于 4 位、密码不少于 8 位、两次密码一致）
 */
const rules = {
  userAccount: [
    { required: true, message: "请输入账号" },
    { minLength: 4, message: "账号不少于 4 位" },
  ],
  userPassword: [
    { required: true, message: "请输入密码" },
    { minLength: 8, message: "密码不少于 8 位" },
  ],
  checkPassword: [
    { required: true, message: "请再次输入密码" },
    {
      validator: (
        value: string | undefined,
        callback: (error?: string) => void
      ) => {
        if (value !== form.userPassword) {
          callback("两次输入的密码不一致");
        } else {
          callback();
        }
      },
    },
  ],
};

const router = useRouter();

/**
 * 提交表单（校验通过后触发），注册成功后跳转到登录页
 */
const handleSubmit = async () => {
  const res = await UserControllerService.userRegisterUsingPost(form);
  if (res.code === 0) {
    message.success("注册成功，请登录");
    await router.push({
      path: "/user/login",
    });
  } else {
    message.error("注册失败，" + res.message);
  }
};
</script>

<style scoped>
#userRegisterView {
  width: 100%;
  display: flex;
  justify-content: center;
}

.register-card {
  width: 400px;
  padding: 36px 36px 28px;
  text-align: left;
}

.register-title {
  margin: 0 0 4px;
  font-size: 24px;
  font-weight: 700;
  color: var(--lc-text-primary);
}

.register-subtitle {
  margin: 0 0 24px;
  color: var(--lc-text-secondary);
  font-size: 14px;
}

.register-button {
  margin-top: 8px;
  height: 40px;
  font-size: 15px;
}

.register-footer {
  margin-top: 4px;
  text-align: center;
  color: var(--lc-text-secondary);
  font-size: 14px;
}

.login-link {
  color: var(--lc-primary);
  font-weight: 600;
  text-decoration: none;
}
</style>
