package com.wms.controller;

import com.wms.common.ApiResponse;
import com.wms.dto.InboundOrderCreateRequest;
import com.wms.dto.InboundOrderResponse;
import com.wms.dto.InventoryResponse;
import com.wms.entity.Inventory;
import com.wms.service.InventoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * ============================================
 *  候选人需要实现以下接口：
 * ============================================
 *
 * POST /api/inbound-orders         — 创建入库单（任务1）
 * GET  /api/inventory              — 库存查询（任务2）
 *
 * 候选人在 InventoryService 中实现业务逻辑后，
 * 在此 Controller 中补全对应的接口方法。
 */
@Tag(name = "库存管理", description = "入库单创建与库存查询接口")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;

    /**
     * 创建入库单 — 候选人实现
     */
    @Operation(summary = "创建入库单", description = "提交入库申请，系统自动生成单号并更新库存")
    @PostMapping("/inbound-orders")
    public ApiResponse<?> createInboundOrder(@Valid @RequestBody InboundOrderCreateRequest request) {
        // TODOEND: 调用 inventoryService.createInboundOrder(request)
//        return ApiResponse.error(501, "请实现入库单创建功能（任务1）");
        return ApiResponse.success("入库单创建成功", inventoryService.createInboundOrder(request));
    }


    /**
     * 库存查询 — 候选人实现
     */
    @Operation(summary = "库存分页查询", description = "支持按商品名称/SKU模糊搜索及按仓库筛选")
    @GetMapping("/inventory")
    public ApiResponse<Page<InventoryResponse>> queryInventory(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        // TODOEND: 调用 inventoryService.queryInventory(...)
//        return ApiResponse.error(501, "请实现库存查询功能（任务2）");
        return ApiResponse.success(inventoryService.queryInventory(keyword, warehouseId, page, pageSize));
    }

    @Operation(summary = "入库单分页查询", description = "查询入库单列表，支持按状态和关键词筛选")
    @GetMapping("/inbound-orders")
    public ApiResponse<Page<InboundOrderResponse>> queryInboundOrders(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        return ApiResponse.success(inventoryService.queryInboundOrders(keyword, status, page, pageSize));
    }

}


