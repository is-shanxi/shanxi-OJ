<template>
  <div id="questionSubmitView">
    <div class="lc-card filter-card">
      <a-form :model="searchParams" layout="inline">
        <a-form-item field="questionId" label="题号" style="min-width: 240px">
          <a-input v-model="searchParams.questionId" placeholder="请输入" />
        </a-form-item>
        <a-form-item field="language" label="编程语言" style="min-width: 240px">
          <a-select
            v-model="searchParams.language"
            :style="{ width: '320px' }"
            placeholder="选择编程语言"
          >
            <a-option>java</a-option>
            <a-option>cpp</a-option>
            <a-option>go</a-option>
            <a-option>html</a-option>
          </a-select>
        </a-form-item>
        <a-form-item>
          <a-button type="primary" @click="doSubmit">搜索</a-button>
        </a-form-item>
      </a-form>
    </div>
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
        <template #judgeInfo="{ record }">
          <span
            v-if="record.judgeInfo?.message"
            :class="judgeMessageClass(record.judgeInfo.message)"
          >
            {{ record.judgeInfo.message }}
          </span>
          <span v-else-if="record.judgeInfo?.time" class="cell-secondary">
            耗时 {{ record.judgeInfo.time }} ms
          </span>
          <span v-else class="cell-secondary">—</span>
        </template>
        <template #status="{ record }">
          <span :class="`lc-difficulty ${statusClass(record.status)}`">
            {{ statusText(record.status) }}
          </span>
        </template>
        <template #questionId="{ record }">
          <a
            class="cell-link"
            @click="toQuestionPage({ id: record.questionId })"
          >
            {{ record.questionId }}
          </a>
        </template>
        <template #createTime="{ record }">
          <span class="cell-secondary">
            {{ moment(record.createTime).format("YYYY-MM-DD HH:mm") }}
          </span>
        </template>
      </a-table>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref, watchEffect } from "vue";
import {
  Question,
  QuestionControllerService,
  QuestionSubmitQueryRequest,
} from "../../../generated";
import message from "@arco-design/web-vue/es/message";
import { useRouter } from "vue-router";
import moment from "moment";

const tableRef = ref();

const dataList = ref([]);
const total = ref(0);
const searchParams = ref<QuestionSubmitQueryRequest>({
  questionId: undefined,
  language: undefined,
  pageSize: 10,
  current: 1,
});

const loadData = async () => {
  const res = await QuestionControllerService.listQuestionSubmitByPageUsingPost(
    {
      ...searchParams.value,
      sortField: "createTime",
      sortOrder: "descend",
    }
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

// 判题状态映射（与后端 QuestionSubmitStatusEnum 一致：0 待判题、1 判题中、2 判题完成、3 失败）
// 注意：status=2 仅表示判题流程完成（答错 / 超时也是 2），是否通过看判题信息列
const STATUS_TEXT: Record<number, string> = {
  0: "等待中",
  1: "判题中",
  2: "判题完成",
  3: "失败",
};

const statusText = (status?: number) => {
  return status != null && STATUS_TEXT[status] ? STATUS_TEXT[status] : "未知";
};

const statusClass = (status?: number) => {
  if (status === 2) {
    return "lc-difficulty--easy";
  }
  if (status === 3) {
    return "lc-difficulty--hard";
  }
  if (status === 0 || status === 1) {
    return "lc-difficulty--medium";
  }
  return "lc-difficulty--none";
};

// 判题信息文字着色：通过（Accepted）为绿色，其余错误信息为红色
const judgeMessageClass = (messageText: string) => {
  return messageText.toLowerCase() === "accepted"
    ? "judge-info judge-info--pass"
    : "judge-info judge-info--fail";
};

const columns = [
  {
    title: "提交号",
    dataIndex: "id",
    width: 90,
  },
  {
    title: "题目",
    slotName: "questionId",
    width: 90,
  },
  {
    title: "编程语言",
    dataIndex: "language",
    width: 110,
  },
  {
    title: "判题状态",
    slotName: "status",
    width: 110,
  },
  {
    title: "判题信息",
    slotName: "judgeInfo",
  },
  {
    title: "提交者 id",
    dataIndex: "userId",
    width: 110,
  },
  {
    title: "创建时间",
    slotName: "createTime",
    width: 160,
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
</script>

<style scoped>
#questionSubmitView {
  max-width: 1280px;
  margin: 0 auto;
}

.filter-card {
  padding: 16px 20px;
  margin-bottom: 20px;
}

.table-card {
  padding: 8px 20px 20px;
}

.cell-secondary {
  color: var(--lc-text-secondary);
}

.cell-link {
  color: var(--lc-link);
  cursor: pointer;
}

.judge-info--pass {
  color: var(--lc-easy);
  font-weight: 600;
}

.judge-info--fail {
  color: var(--lc-hard);
  font-weight: 600;
}
</style>
