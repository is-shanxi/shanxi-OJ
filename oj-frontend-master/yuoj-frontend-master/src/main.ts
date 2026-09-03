import { createApp } from "vue";
import App from "./App.vue";
import ArcoVue from "@arco-design/web-vue";
import "@arco-design/web-vue/dist/arco.css";
import router from "./router";
import store from "./store";
import "@/plugins/axios";
import "@/access";
import "bytemd/dist/index.css";
import "@/styles/global.css";
import { OpenAPI } from "../generated";

// 配置后端接口地址（当前运行的是单体后端，端口 8121；生成的请求路径已含 /api 前缀）
OpenAPI.BASE = "http://localhost:8121";

//后端微服务接口地址
// OpenAPI.BASE = "http://localhost:8101";
createApp(App).use(ArcoVue).use(store).use(router).mount("#app");
