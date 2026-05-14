<script setup lang="ts">
/**
 * ============================================
 *  入库管理页 — 候选人需要实现（任务1）
 * ============================================
 *
 * 需求：
 * 1. 表单：供应商名称 + 入库明细列表
 * 2. 每行明细：选择商品（下拉搜索）→ 选择仓库 → 选择库位 → 输入数量
 * 3. 支持添加/删除明细行
 * 4. 提交按钮（调用 createInboundOrder API）
 *
 * 建议使用 AI 协作完成此页面，参考 ProductsView.vue 的实现风格
 */
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { createInboundOrder, getProducts, getWarehouses, getLocations, type Product, type Warehouse, type Location } from '@/api'

const supplierName = ref('')
const items = ref<any[]>([])
const submitting = ref(false)

// TODOEND: 候选人实现添加/删除明细行的逻辑
interface InboundItem {
  productId: number | undefined
  warehouseId: number | undefined
  locationCode: string | undefined
  quantity: number
}

interface ProductOption {
  id: number
  name: string
  sku: string
}

const products = ref<ProductOption[]>([])
const warehouses = ref<Warehouse[]>([])
const locationsMap = ref<Map<number, Location[]>>(new Map())

onMounted(async () => {
  await loadProducts()
  await loadWarehouses()
})

//加载可选商品列表 -- 下拉框
const loadProducts = async () => {
  try {
    const res = await getProducts()
    products.value = res.data.map((p: Product) => ({
      id: p.id,
      name: p.name,
      sku: p.sku,
    }))
  } catch (e: any) {
    ElMessage.error('加载商品列表失败: ' + (e.response?.data?.message || e.message))
  }
}

//加载可选仓库列表 -- 下拉框
const loadWarehouses = async () => {
  try {
    const res = await getWarehouses()
    warehouses.value = res.data
  } catch (e: any) {
    ElMessage.error('加载仓库列表失败: ' + (e.response?.data?.message || e.message))
  }
}

//加载可选库位列表 -- 下拉框
const loadLocations = async (warehouseId: number) => {
  if (locationsMap.value.has(warehouseId)) {
    return
  }
  try {
    const res = await getLocations(warehouseId)
    locationsMap.value.set(warehouseId, res.data)
  } catch (e: any) {
    ElMessage.error('加载库位列表失败: ' + (e.response?.data?.message || e.message))
  }
}

//明细-新增
const addItem = () => {
  items.value.push({
    productId: undefined,
    quantity: 1,
    locationCode: '',
  })
}

//明细-移除
const removeItem = (index: number) => {
  items.value.splice(index, 1)
}

//仓库值改变事件——》获取库位列表
const handleWarehouseChange = async (index: number, warehouseId: number) => {
  items.value[index].warehouseId = warehouseId
  items.value[index].locationCode = undefined
  await loadLocations(warehouseId)
}

// TODOEND: 候选人实现提交逻辑
const handleSubmit = async () => {
  if (!supplierName.value.trim()) {
    ElMessage.warning('请输入供应商名称')
    return
  }

  if (items.value.length === 0) {
    ElMessage.warning('请至少添加一条入库明细')
    return
  }

  for (let i = 0; i < items.value.length; i++) {
    const item = items.value[i]
    if (!item.productId) {
      ElMessage.warning(`第 ${i + 1} 行：请选择商品`)
      return
    }
    if (!item.warehouseId) {
      ElMessage.warning(`第 ${i + 1} 行：请选择仓库`)
      return
    }
    if (!item.locationCode) {
      ElMessage.warning(`第 ${i + 1} 行：请选择库位`)
      return
    }
    if (!item.quantity || item.quantity <= 0) {
      ElMessage.warning(`第 ${i + 1} 行：请输入有效的数量`)
      return
    }
  }

  submitting.value = true
  try {
    const requestData = {
      supplierName: supplierName.value,
      items: items.value.map(item => ({
        productId: item.productId!,
        quantity: item.quantity,
        locationCode: item.locationCode!,
      })),
    }
    await createInboundOrder(requestData)
    ElMessage.success('入库单创建成功')

    supplierName.value = ''
    items.value = []
  } catch (e: any) {
    ElMessage.error('创建入库单失败: ' + (e.response?.data?.message || e.message))
  } finally {
    submitting.value = false
  }
}

//获取商品标签——》商品名称（商品SKU）
const getProductLabel = (productId: number | undefined) => {
  if (!productId) return ''
  const product = products.value.find(p => p.id === productId)
  return product ? `${product.name} (${product.sku})` : ''
}
</script>

<template>
  <div class="inbound-view">
    <h3>入库管理</h3>

    <el-card shadow="never" style="margin-bottom: 20px">
      <el-form label-width="100px">
        <el-form-item label="供应商名称" required>
          <el-input
              v-model="supplierName"
              placeholder="请输入供应商名称"
              maxlength="200"
              show-word-limit
              clearable
          />
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" style="margin-bottom: 20px">
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center">
          <span style="font-weight: bold">入库明细</span>
          <el-button type="primary" @click="addItem">+ 添加明细</el-button>
        </div>
      </template>

      <div v-if="items.length === 0" style="text-align: center; padding: 40px 0">
        <el-empty description="暂无入库明细，请点击'添加明细'按钮添加商品" />
      </div>

      <div v-else>
        <el-table :data="items" border stripe style="width: 100%">
          <el-table-column label="商品" min-width="200">
            <template #default="{ row, $index }">
              <el-select
                  v-model="row.productId"
                  placeholder="请选择商品"
                  filterable
                  clearable
                  style="width: 100%"
              >
                <el-option
                    v-for="product in products"
                    :key="product.id"
                    :label="getProductLabel(product.id)"
                    :value="product.id"
                />
              </el-select>
            </template>
          </el-table-column>

          <el-table-column label="仓库" width="180">
            <template #default="{ row, $index }">
              <el-select
                  v-model="row.warehouseId"
                  placeholder="请选择仓库"
                  clearable
                  style="width: 100%"
                  @change="(val: number) => handleWarehouseChange($index, val)"
              >
                <el-option
                    v-for="warehouse in warehouses"
                    :key="warehouse.id"
                    :label="warehouse.name"
                    :value="warehouse.id"
                />
              </el-select>
            </template>
          </el-table-column>

          <el-table-column label="库位" width="180">
            <template #default="{ row }">
              <el-select
                  v-model="row.locationCode"
                  placeholder="请选择库位"
                  clearable
                  style="width: 100%"
                  :disabled="!row.warehouseId"
              >
                <el-option
                    v-for="location in locationsMap.get(row.warehouseId!) || []"
                    :key="location.id"
                    :label="`${location.code} (${location.status})`"
                    :value="location.code"
                />
              </el-select>
            </template>
          </el-table-column>

          <el-table-column label="数量" width="150">
            <template #default="{ row }">
              <el-input-number
                  v-model="row.quantity"
                  :min="1"
                  :max="9999"
                  controls-position="right"
                  style="width: 100%"
              />
            </template>
          </el-table-column>

          <el-table-column label="操作" width="100" fixed="right">
            <template #default="{ $index }">
              <el-button
                  type="danger"
                  size="small"
                  @click="removeItem($index)"
              >
                删除
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </el-card>

    <div style="text-align: right">
      <el-button
          type="success"
          :loading="submitting"
          @click="handleSubmit"
          :disabled="items.length === 0"
          size="large"
      >
        {{ submitting ? '提交中...' : '提交入库单' }}
      </el-button>
    </div>
  </div>
</template>
