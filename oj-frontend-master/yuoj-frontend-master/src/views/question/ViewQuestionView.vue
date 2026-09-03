<template>
  <div id="viewQuestionView">
    <a-row :gutter="[20, 20]" :wrap="false" class="detail-row">
      <!-- 左栏：题目描述 -->
      <a-col :md="12" :xs="24" class="detail-left">
        <div class="lc-card left-card">
          <a-tabs default-active-key="question">
            <a-tab-pane key="question" title="描述">
              <div v-if="question" class="question-content">
                <div class="question-head">
                  <h2 class="lc-page-title">
                    {{ question.id }}. {{ question.title }}
                  </h2>
                  <div class="question-meta">
                    <span
                      :class="`lc-difficulty lc-difficulty--${
                        getDifficulty() ?? 'none'
                      }`"
                    >
                      {{ difficultyText(getDifficulty()) }}
                    </span>
                    <a-space wrap>
                      <a-tag
                        v-for="(tag, index) of question.tags"
                        :key="index"
                        class="question-tag"
                        >{{ tag }}
                      </a-tag>
                    </a-space>
                  </div>
                </div>
                <MdViewer :value="question.content || ''" />
                <a-divider />
                <a-descriptions
                  title="判题条件"
                  :column="{ xs: 1, md: 2, lg: 3 }"
                  class="judge-desc"
                >
                  <a-descriptions-item label="时间限制">
                    {{ question.judgeConfig.timeLimit ?? 0 }} ms
                  </a-descriptions-item>
                  <a-descriptions-item label="内存限制">
                    {{ question.judgeConfig.memoryLimit ?? 0 }} MB
                  </a-descriptions-item>
                  <a-descriptions-item label="堆栈限制">
                    {{ question.judgeConfig.stackLimit ?? 0 }} KB
                  </a-descriptions-item>
                </a-descriptions>
              </div>
            </a-tab-pane>
            <a-tab-pane key="comment" title="评论" disabled>评论区</a-tab-pane>
            <a-tab-pane key="answer" title="题解"> 暂时无法查看题解</a-tab-pane>
          </a-tabs>
        </div>
      </a-col>
      <!-- 右栏：代码编辑区 -->
      <a-col :md="12" :xs="24" class="detail-right">
        <div class="lc-card editor-card">
          <div class="editor-toolbar">
            <a-select
              v-model="form.language"
              :style="{ width: '160px' }"
              size="small"
              placeholder="选择编程语言"
            >
              <a-option>java</a-option>
              <a-option>cpp</a-option>
              <a-option>go</a-option>
              <a-option>html</a-option>
            </a-select>
            <a-space class="editor-actions">
              <a-button size="small" @click="doRun">运行</a-button>
              <a-button type="primary" size="small" @click="doSubmit">
                提交
              </a-button>
            </a-space>
          </div>
          <CodeEditor
            :value="form.code as string"
            :language="form.language"
            :handle-change="changeCode"
          />
        </div>
      </a-col>
    </a-row>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref, withDefaults, defineProps } from "vue";
import message from "@arco-design/web-vue/es/message";
import CodeEditor from "@/components/CodeEditor.vue";
import MdViewer from "@/components/MdViewer.vue";
import {
  QuestionControllerService,
  QuestionSubmitAddRequest,
  QuestionVO,
} from "../../../generated";

interface Props {
  id: string;
}

const props = withDefaults(defineProps<Props>(), {
  id: () => "",
});

const question = ref<QuestionVO>();

// 难度标签约定：题目标签中包含"简单/中等/困难"即视为难度标签（与列表页一致）
const DIFFICULTY_TAG_TEXT: Record<string, string> = {
  easy: "简单",
  medium: "中等",
  hard: "困难",
};

const getDifficulty = (): string | null => {
  const tags: string[] = question.value?.tags ?? [];
  for (const [key, text] of Object.entries(DIFFICULTY_TAG_TEXT)) {
    if (tags.includes(text)) {
      return key;
    }
  }
  return null;
};

const difficultyText = (difficulty: string | null) => {
  return difficulty ? DIFFICULTY_TAG_TEXT[difficulty] : "—";
};

const loadData = async () => {
  const res = await QuestionControllerService.getQuestionVoByIdUsingGet(
    props.id as any
  );
  if (res.code === 0) {
    question.value = res.data;
  } else {
    message.error("加载失败，" + res.message);
  }
};

const form = ref<QuestionSubmitAddRequest>({
  language: "java",
  code: "",
});

/**
 * 运行代码（当前后端暂无在线运行接口，仅作占位提示）
 */
const doRun = () => {
  message.info("当前版本暂不支持在线运行，请直接提交评测");
};

/**
 * 提交代码
 */
const doSubmit = async () => {
  if (!question.value?.id) {
    return;
  }

  const res = await QuestionControllerService.doQuestionSubmitUsingPost({
    ...form.value,
    questionId: question.value.id,
  });
  if (res.code === 0) {
    message.success("提交成功");
  } else {
    message.error("提交失败," + res.message);
  }
};

/**
 * 页面加载时，请求数据
 */
onMounted(() => {
  loadData();
});

const changeCode = (value: string) => {
  form.value.code = value;
};
</script>

<style>
#viewQuestionView {
  max-width: 1400px;
  margin: 0 auto;
}

#viewQuestionView .detail-row {
  height: calc(100vh - 128px);
}

#viewQuestionView .detail-left,
#viewQuestionView .detail-right {
  height: 100%;
}

#viewQuestionView .left-card,
#viewQuestionView .editor-card {
  height: 100%;
  overflow: auto;
}

#viewQuestionView .editor-card {
  display: flex;
  flex-direction: column;
  padding: 12px 16px;
}

#viewQuestionView .question-content {
  padding: 4px 8px 16px;
}

#viewQuestionView .question-head {
  margin-bottom: 12px;
}

#viewQuestionView .question-meta {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 10px;
}

#viewQuestionView .question-tag {
  background: var(--lc-border-light);
  color: var(--lc-text-secondary);
  border: none;
}

#viewQuestionView .editor-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-bottom: 10px;
  border-bottom: 1px solid var(--lc-border-light);
}

#viewQuestionView .judge-desc .arco-descriptions-title {
  font-size: 14px;
  color: var(--lc-text-secondary);
}

/* 覆盖 Arco space 的默认底边距，避免工具栏错位 */
#viewQuestionView .arco-space-horizontal .arco-space-item {
  margin-bottom: 0 !important;
}
</style>
