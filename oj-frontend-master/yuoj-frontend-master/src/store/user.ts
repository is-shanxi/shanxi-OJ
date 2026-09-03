// initial state
import { StoreOptions } from "vuex";
import ACCESS_ENUM from "@/access/accessEnum";
import { UserControllerService } from "../../generated";

export default {
  namespaced: true,
  state: () => ({
    loginUser: {
      userName: "未登录",
    },
  }),
  actions: {
    async getLoginUser({ commit, state }, payload) {
      try {
        // 从远程请求获取登录信息
        const res = await UserControllerService.getLoginUserUsingGet();
        if (res.code === 0) {
          commit("updateUser", res.data);
        } else {
          commit("updateUser", {
            ...state.loginUser,
            userRole: ACCESS_ENUM.NOT_LOGIN,
          });
        }
      } catch (e) {
        // 后端异常/网络错误时不抛错，避免路由守卫中断导致整站白屏；按未登录处理
        console.error("获取登录用户失败", e);
        commit("updateUser", {
          ...state.loginUser,
          userRole: ACCESS_ENUM.NOT_LOGIN,
        });
      }
    },
    async logout({ commit }) {
      const res = await UserControllerService.userLogoutUsingPost();
      if (res.code === 0) {
        commit("updateUser", {
          userName: "未登录",
          userRole: ACCESS_ENUM.NOT_LOGIN,
        });
      }
      return res;
    },
  },
  mutations: {
    updateUser(state, payload) {
      state.loginUser = payload;
    },
  },
} as StoreOptions<any>;
