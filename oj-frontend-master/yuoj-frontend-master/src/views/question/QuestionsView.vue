<template>
  <div id="questionsView">
    <!-- 筛选区 -->
    <div class="lc-card filter-card">
      <div class="filter-row">
        <a-input-search
          v-model="searchParams.title"
          class="filter-search"
          placeholder="搜索题目名称"
          search-button
          @search="doSubmit"
        />
        <a-input-tag
          v-model="searchParams.tags"
          class="filter-tags"
          placeholder="按标签筛选，回车确认"
          allow-clear
          @change="doSubmit"
        />
      </div>
      <div class="filter-difficulty">
        <span
          v-for="item in difficultyOptions"
          :key="item.value"
          class="difficulty-chip"
          :class="[
            `difficulty-chip--${item.value}`,
            {
              'difficulty-chip--active': activeDifficulty === item.value,
            },
          ]"
          @click="doFilterDifficulty(item.value)"
        >
          {{ item.label }}
        </span>
      </div>
    </div>

    <!-- 题目列表 -->
    <div class="lc-card table-card">
      <a-table
        :ref="tableRef"
        :columns="columns"
        :data="dataList"
        :pagination="{
          showTotal: true,
          pageSize: searchParams.pageSize,
          current: searchParams.current,
          total,
        }"
        :bordered="{ wrapper: false, cell: false }"
        @page-change="onPageChange"
      >
        <template #title="{ record }">
          <a class="question-title" @click="toQuestionPage(record)">
            {{ record.id }}. {{ record.title }}
          </a>
        </template>
        <template #difficulty="{ record }">
          <span
            :class="`lc-difficulty lc-difficulty--${
              getDifficulty(record) ?? 'none'
            }`"
          >
            {{ difficultyText(getDifficulty(record)) }}
          </span>
        </template>
        <template #tags="{ record }">
          <a-space wrap>
            <a-tag
              v-for="(tag, index) of record.tags"
              :key="index"
              class="question-tag"
              >{{ tag }}
            </a-tag>
          </a-space>
        </template>
        <template #acceptedRate="{ record }">
          <span :class="acceptedRateClass(record)">
            {{ acceptedRateText(record) }}
          </span>
        </template>
        <template #createTime="{ record }">
          <span class="cell-secondary">
            {{ moment(record.createTime).format("YYYY-MM-DD") }}
          </span>
        </template>
        <template #optional="{ record }">
          <a-button type="primary" size="small" @click="toQuestionPage(record)">
            做题
          </a-button>
        </template>
      </a-table>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watchEffect } from "vue";
import {
  Page_Question_,
  Question,
  QuestionControllerService,
  QuestionQueryRequest,
} from "../../../generated";
import message from "@arco-design/web-vue/es/message";
import { useRouter } from "vue-router";
import moment from "moment";

const tableRef = ref();

const dataList = ref([]);
const total = ref(0);
const searchParams = ref<QuestionQueryRequest>({
  title: "",
  tags: [],
  pageSize: 8,
  current: 1,
});

// 难度标签约定：题目标签中包含"简单/中等/困难"即视为难度标签
const DIFFICULTY_TAG_TEXT: Record<string, string> = {
  easy: "简单",
  medium: "中等",
  hard: "困难",
};

const difficultyOptions = [
  { label: "全部", value: "all" },
  { label: "简单", value: "easy" },
  { label: "中等", value: "medium" },
  { label: "困难", value: "hard" },
];

const activeDifficulty = computed(() => {
  const tags = searchParams.value.tags ?? [];
  for (const [key, text] of Object.entries(DIFFICULTY_TAG_TEXT)) {
    if (tags.includes(text)) {
      return key;
    }
  }
  return "all";
});

const getDifficulty = (record: any): string | null => {
  const tags: string[] = record?.tags ?? [];
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

// 通过率 = 通过数 / 提交数，按百分比展示
const acceptedRateText = (record: any) => {
  if (!record.submitNum) {
    return "—";
  }
  return `${Math.round((record.acceptedNum / record.submitNum) * 100)}% (${
    record.acceptedNum
  }/${record.submitNum})`;
};

const acceptedRateClass = (record: any) => {
  if (!record.submitNum) {
    return "cell-secondary";
  }
  const rate = record.acceptedNum / record.submitNum;
  if (rate >= 0.6) {
    return "rate rate--high";
  }
  if (rate >= 0.3) {
    return "rate rate--mid";
  }
  return "rate rate--low";
};

const loadData = async () => {
  const res = await QuestionControllerService.listQuestionVoByPageUsingPost(
    searchParams.value
  );
  if (res.code === 0) {
    dataList.value = res.data.records;
    total.value = res.data.total;
  } else {
    message.error("加载失败，" + res.message);
  }
};

/**
 * 监听 searchParams 变量，改变时触发页面的重新加载
 */
watchEffect(() => {
  loadData();
});

/**
 * 页面加载时，请求数据
 */
onMounted(() => {
  loadData();
});

const columns = [
  {
    title: "题目",
    slotName: "title",
  },
  {
    title: "难度",
    slotName: "difficulty",
    width: 100,
  },
  {
    title: "标签",
    slotName: "tags",
  },
  {
    title: "通过率",
    slotName: "acceptedRate",
    width: 180,
  },
  {
    title: "创建时间",
    slotName: "createTime",
    width: 120,
  },
  {
    slotName: "optional",
    width: 100,
  },
];

const onPageChange = (page: number) => {
  searchParams.value = {
    ...searchParams.value,
    current: page,
  };
};

const router = useRouter();

/**
 * 跳转到做题页面
 * @param question
 */
const toQuestionPage = (question: Question) => {
  router.push({
    path: `/view/question/${question.id}`,
  });
};

/**
 * 确认搜索，重新加载数据
 */
const doSubmit = () => {
  // 这里需要重置搜索页号
  searchParams.value = {
    ...searchParams.value,
    current: 1,
  };
};

/**
 * 按难度（标签约定）筛选：切换到"全部"时移除难度标签
 */
const doFilterDifficulty = (value: string) => {
  const tags = [...(searchParams.value.tags ?? [])].filter(
    (tag) => !Object.values(DIFFICULTY_TAG_TEXT).includes(tag)
  );
  if (value !== "all") {
    tags.push(DIFFICULTY_TAG_TEXT[value]);
  }
  searchParams.value = {
    ...searchParams.value,
    tags,
    current: 1,
  };
};
</script>

<style scoped>
#questionsView {
  max-width: 1280px;
  margin: 0 auto;
}

.filter-card {
  padding: 16px 20px;
  margin-bottom: 20px;
}

.filter-row {
  display: flex;
  gap: 12px;
}

.filter-search {
  width: 280px;
}

.filter-tags {
  flex: 1;
  min-width: 240px;
}

.filter-difficulty {
  display: flex;
  gap: 8px;
  margin-top: 14px;
}

.difficulty-chip {
  padding: 4px 14px;
  border-radius: 999px;
  font-size: 13px;
  color: var(--lc-text-secondary);
  background: var(--lc-border-light);
  cursor: pointer;
  transition: all 0.2s;
}

.difficulty-chip:hover {
  color: var(--lc-text-primary);
}

.difficulty-chip--easy.difficulty-chip--active {
  color: var(--lc-easy);
  background: var(--lc-easy-bg);
  font-weight: 600;
}

.difficulty-chip--medium.difficulty-chip--active {
  color: #c58f00;
  background: var(--lc-medium-bg);
  font-weight: 600;
}

.difficulty-chip--hard.difficulty-chip--active {
  color: var(--lc-hard);
  background: var(--lc-hard-bg);
  font-weight: 600;
}

.difficulty-chip--all.difficulty-chip--active {
  color: var(--lc-text-primary);
  background: var(--lc-border);
  font-weight: 600;
}

.table-card {
  padding: 8px 20px 20px;
}

.question-title {
  color: var(--lc-text-primary);
  font-weight: 500;
  cursor: pointer;
}

.question-title:hover {
  color: var(--lc-primary);
}

.question-tag {
  background: var(--lc-border-light);
  color: var(--lc-text-secondary);
  border: none;
}

.cell-secondary {
  color: var(--lc-text-secondary);
}

.rate--high {
  color: var(--lc-easy);
}

.rate--mid {
  color: #c58f00;
}

.rate--low {
  color: var(--lc-hard);
}
</style>
