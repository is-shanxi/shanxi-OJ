<template>
  <div id="basicLayout">
    <a-layout style="min-height: 100vh">
      <a-layout-header class="header">
        <GlobalHeader />
      </a-layout-header>
      <a-layout>
        <a-layout-sider class="sider" :width="72">
          <div class="side-nav">
            <a-tooltip content="题库" position="right">
              <div
                class="side-nav__item"
                :class="{ 'side-nav__item--active': isQuestionsActive }"
                @click="$router.push('/')"
              >
                <icon-code />
                <span>题库</span>
              </div>
            </a-tooltip>
            <a-tooltip content="敬请期待" position="right">
              <div class="side-nav__item side-nav__item--disabled">
                <icon-trophy />
                <span>竞赛</span>
              </div>
            </a-tooltip>
            <a-tooltip content="敬请期待" position="right">
              <div class="side-nav__item side-nav__item--disabled">
                <icon-message />
                <span>讨论</span>
              </div>
            </a-tooltip>
            <a-tooltip content="敬请期待" position="right">
              <div class="side-nav__item side-nav__item--disabled">
                <icon-rocket />
                <span>进步</span>
              </div>
            </a-tooltip>
          </div>
        </a-layout-sider>
        <a-layout-content class="content">
          <router-view />
        </a-layout-content>
      </a-layout>
    </a-layout>
  </div>
</template>

<style scoped>
#basicLayout .header {
  background: #fff;
  border-bottom: 1px solid var(--lc-border);
  padding: 0;
  height: 56px;
  line-height: normal;
  position: sticky;
  top: 0;
  z-index: 100;
}

#basicLayout .sider {
  background: transparent;
  width: 72px;
  min-width: 72px;
  max-width: 72px;
}

#basicLayout .side-nav {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding-top: 16px;
}

#basicLayout .side-nav__item {
  width: 56px;
  padding: 10px 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  border-radius: var(--lc-radius-sm);
  color: var(--lc-text-secondary);
  font-size: 12px;
  cursor: pointer;
  transition: background 0.2s, color 0.2s;
}

#basicLayout .side-nav__item :deep(.arco-icon) {
  font-size: 20px;
}

#basicLayout .side-nav__item:hover {
  background: var(--lc-border-light);
  color: var(--lc-text-primary);
}

#basicLayout .side-nav__item--active {
  background: var(--lc-primary-bg);
  color: var(--lc-primary);
  font-weight: 600;
}

#basicLayout .side-nav__item--disabled {
  cursor: not-allowed;
  color: var(--lc-text-tertiary);
}

#basicLayout .content {
  background: var(--lc-bg);
  padding: 24px;
}
</style>
<script>
import GlobalHeader from "@/components/GlobalHeader";
import {
  IconCode,
  IconTrophy,
  IconMessage,
  IconRocket,
} from "@arco-design/web-vue/es/icon";

export default {
  components: { GlobalHeader, IconCode, IconTrophy, IconMessage, IconRocket },
  computed: {
    isQuestionsActive() {
      return (
        this.$route.path === "/" || this.$route.path.startsWith("/questions")
      );
    },
  },
};
</script>
