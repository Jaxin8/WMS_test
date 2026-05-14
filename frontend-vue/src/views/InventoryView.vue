<script setup lang="ts">
/**
 * ============================================
 *  库存查询页 — 候选人需要实现（任务2）
 * ============================================
 *
 * 需求：
 * 1. 搜索栏：商品名称/SKU 模糊搜索 + 仓库下拉筛选
 * 2. 表格展示：商品名称、SKU、库位编码、仓库名、库存数量、更新时间
 * 3. 库存数量 < 10 的行高亮为红色
 * 4. 支持分页
 *
 * 建议使用 AI 协作完成此页面，参考 ProductsView.vue 的实现风格
 */
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getInventory, getWarehouses, type InventoryItem, type Warehouse } from '@/api'

const keyword = ref('')
const warehouseId = ref<number | undefined>()
const loading = ref(false)
const inventoryList = ref<any[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)
const warehouses = ref<Warehouse[]>([])

onMounted(async () => {
  await loadWarehouses()
  await loadInventory()
})

const loadWarehouses = async () => {
  try {
    const res = await getWarehouses()
    warehouses.value = res.data
  } catch (e: any) {
    ElMessage.error('加载仓库列表失败: ' + (e.response?.data?.message || e.message))
  }
}

// TODOEND: 候选人实现 loadInventory 函数
const loadInventory = async () => {
  // 提示：调用 getInventory({ keyword, warehouseId, page, pageSize })
  loading.value = true
  try {
    const res = await getInventory({
      keyword: keyword.value || undefined,
      warehouseId: warehouseId.value,
      page: page.value,
      pageSize: pageSize.value,
    })
    inventoryList.value = res.data.content || []
    total.value = res.data.totalElements || 0
  } catch (e: any) {
    ElMessage.error('加载库存数据失败: ' + (e.response?.data?.message || e.message))
    inventoryList.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

// TODOEND: 候选人实现表格行样式
const getRowStyle = (row: any) => {
  // 提示：当 row.quantity < 10 时返回红色样式
  if (row.quantity < 10) {
    return { backgroundColor: '#fef0f0' }
  }
  return {}
}

//日期格式化
const formatTime = (timeStr: string) => {
  if (!timeStr) return '-'
  const date = new Date(timeStr)
  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  })
}
</script>

<template>
  <div>
    <h3> 库存查询</h3>

    <!-- 搜索栏 — 候选人实现 -->
    <div style="display: flex; gap: 12px; margin-bottom: 16px">
      <el-input v-model="keyword" placeholder="搜索商品名称/SKU..." style="width: 300px" clearable />
      <el-select v-model="warehouseId" placeholder="选择仓库" clearable style="width: 200px">
        <el-option
            v-for="warehouse in warehouses"
            :key="warehouse.id"
            :label="warehouse.name"
            :value="warehouse.id"
        />
      </el-select>
      <el-button type="primary" @click="loadInventory">查询</el-button>
    </div>

    <!-- 表格 — 候选人实现 -->
    <el-table :data="inventoryList" v-loading="loading" border stripe :row-style="getRowStyle">
      <el-table-column prop="productName" label="商品名称" />
      <el-table-column prop="sku" label="SKU" width="150" />
      <el-table-column prop="locationCode" label="库位编码" width="150" />
      <el-table-column prop="warehouseName" label="仓库" width="120" />
      <el-table-column prop="quantity" label="库存数量" width="100">
        <template #default="{ row }">
          <el-tag
              :type="row.quantity < 10 ? 'danger' : row.quantity < 50 ? 'warning' : 'success'"
              effect="light"
          >
            {{ row.quantity }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="updatedAt" label="更新时间" width="180">
        <template #default="{ row }">
          {{ formatTime(row.updatedAt) }}
        </template>
      </el-table-column>
    </el-table>

    <!-- 分页 — 候选人实现 -->
    <div style="margin-top: 16px; text-align: right">
      <el-pagination
          v-model:current-page="page"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="loadInventory"
          @current-change="loadInventory"
      />
    </div>

    <el-empty v-if="!loading && inventoryList.length === 0" description="暂无库存数据，请先完成入库操作" />
  </div>
</template>
