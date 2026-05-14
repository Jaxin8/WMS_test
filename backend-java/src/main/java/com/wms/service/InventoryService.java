package com.wms.service;

import com.wms.dto.InboundItemRequest;
import com.wms.dto.InboundOrderCreateRequest;
import com.wms.dto.InboundOrderItemResponse;
import com.wms.dto.InboundOrderResponse;
import com.wms.dto.InventoryResponse;
import com.wms.entity.InboundOrder;
import com.wms.entity.InboundOrderItem;
import com.wms.entity.Inventory;
import com.wms.entity.Location;
import com.wms.entity.Product;
import com.wms.entity.Warehouse;
import com.wms.repository.InboundOrderItemRepository;
import com.wms.repository.InboundOrderRepository;
import com.wms.repository.InventoryRepository;
import com.wms.repository.LocationRepository;
import com.wms.repository.ProductRepository;
import com.wms.repository.WarehouseRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * ============================================
 *  候选人需要实现以下两个方法：
 * ============================================
 *
 * 1. createInboundOrder() — 入库单创建（任务1）
 *    要求：
 *    - 生成入库单号（格式 IN-YYYYMMDD-XXX）
 *    - 校验商品和库位是否存在
 *    - 在事务中同时创建入库单和更新库存
 *    - 参数校验已在 DTO 层通过 @Valid 处理
 *
 * 2. queryInventory() — 库存查询（任务2）
 *    要求：
 *    - 支持按商品名称/SKU模糊搜索
 *    - 支持按仓库筛选
 *    - 支持分页
 *    - 返回关联的商品名称和仓库名称
 *    - 注意性能：使用 JOIN 查询而非 N+1
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryService {

    // === 候选人需注入以下 Repository ===
    private final InventoryRepository inventoryRepository;
    private final InboundOrderRepository inboundOrderRepository;
    private final InboundOrderItemRepository inboundOrderItemRepository;
    private final ProductRepository productRepository;
    private final LocationRepository locationRepository;
    private final WarehouseRepository warehouseRepository;

    /**
     * 入库单创建 — 候选人实现
     */
    @Transactional
    public Object createInboundOrder(InboundOrderCreateRequest request) {
        // TODOEND: 候选人实现
        //生成入库单号（格式 IN-YYYYMMDD-XXX）
        String orderNo = generateOrderNo();

        //构建入库单
        InboundOrder order = InboundOrder.builder()
                .orderNo(orderNo)
                .supplierName(request.getSupplierName())
                .status("PENDING")
                .build();

        //保存入库单
        InboundOrder savedOrder = inboundOrderRepository.save(order);

        for (InboundItemRequest item : request.getItems()) {
            //校验商品是否存在
            if (!productRepository.existsById(item.getProductId())) {
                throw new EntityNotFoundException("商品不存在: ID=" + item.getProductId());
            }

            //校验库位是否存在
            if (!locationRepository.existsByCode(item.getLocationCode())) {
                throw new EntityNotFoundException("库位不存在: " + item.getLocationCode());
            }

            //构建入库单明细 - 关联数据
            InboundOrderItem orderItem = InboundOrderItem.builder()
                    .orderId(savedOrder.getId())
                    .productId(item.getProductId())
                    .quantity(item.getQuantity())
                    .locationCode(item.getLocationCode())
                    .build();

            //保存入库单明细 - 关联数据
            inboundOrderItemRepository.save(orderItem);

            // 更新或创建库存记录
            inventoryRepository.findByProductIdAndLocationCode(
                            item.getProductId(), item.getLocationCode())
                    .ifPresentOrElse(
                            inventory -> {
                                // 库存已存在，累加数量
                                inventory.setQuantity(inventory.getQuantity() + item.getQuantity());
                                inventoryRepository.save(inventory);
                            },
                            () -> {
                                // 库存不存在，创建新记录
                                Inventory newInventory = Inventory.builder()
                                        .productId(item.getProductId())
                                        .locationCode(item.getLocationCode())
                                        .quantity(item.getQuantity())
                                        .build();
                                inventoryRepository.save(newInventory);
                            }
                    );
        }

        return savedOrder;
    }


    /**
     * 库存查询 — 候选人实现
     */
    public Page<InventoryResponse> queryInventory(String keyword, Long warehouseId,
                                                   int page, int pageSize) {
        // TODO: 候选人实现
//        throw new UnsupportedOperationException("请实现库存查询功能（任务2）");
        Pageable pageable = PageRequest.of(page - 1, pageSize);

//        return inventoryRepository.findAll(pageable).getContent().stream()
//                .map(this::convertToInventoryResponse)
//                .filter(response -> filterByKeyword(response, keyword))
//                .filter(response -> filterByWarehouse(response, warehouseId))
//                .collect(Collectors.toList());
        return inventoryRepository.searchWithFilters(keyword, warehouseId, pageable);
    }

    public Page<InboundOrderResponse> queryInboundOrders(String keyword, String status,
                                                         int page, int pageSize) {
        Pageable pageable = PageRequest.of(page - 1, pageSize);
        Page<InboundOrder> orderPage = inboundOrderRepository.search(keyword, status, pageable);

        return orderPage.map(this::convertToInboundOrderResponse);
    }


    /**
     * 生成入库单号
     *
     * 单号格式: IN-YYYYMMDD-XXX
     *
     * IN: 入库单前缀 (Inbound)
     * YYYYMMDD: 当前日期
     * XXX: 当天订单序号(从001开始)
     *
     * @return 生成的入库单号,例如: IN-20250115-001
     */
    private String generateOrderNo() {
        LocalDate today = LocalDate.now();
        String dateStr = today.format(DateTimeFormatter.ofPattern("yyyyMMdd"));

        LocalDateTime startOfDay = today.atStartOfDay();
        LocalDateTime endOfDay = today.plusDays(1).atStartOfDay();

        long count = inboundOrderRepository.countByCreatedAtBetween(startOfDay, endOfDay);
        long sequence = count + 1;

        return String.format("IN-%s-%03d", dateStr, sequence);
    }

    private InventoryResponse convertToInventoryResponse(Inventory inventory) {
        Product product = productRepository.findById(inventory.getProductId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "商品不存在: ID=" + inventory.getProductId()));

        Location location = locationRepository.findByCode(inventory.getLocationCode())
                .orElseThrow(() -> new EntityNotFoundException(
                        "库位不存在: " + inventory.getLocationCode()));

        Warehouse warehouse = warehouseRepository.findById(location.getWarehouseId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "仓库不存在: ID=" + location.getWarehouseId()));

        return InventoryResponse.builder()
                .productId(product.getId())
                .productName(product.getName())
                .sku(product.getSku())
                .locationCode(inventory.getLocationCode())
                .warehouseId(String.valueOf(location.getWarehouseId()))
                .warehouseName(warehouse.getName())
                .quantity(inventory.getQuantity())
                .updatedAt(inventory.getUpdatedAt())
                .build();
    }

    private InboundOrderResponse convertToInboundOrderResponse(InboundOrder order) {
        List<InboundOrderItem> items = inboundOrderItemRepository.findByOrderId(order.getId());

        List<InboundOrderItemResponse> itemResponses = items.stream()
                .map(this::convertToItemResponse)
                .collect(Collectors.toList());

        return InboundOrderResponse.builder()
                .id(order.getId())
                .orderNo(order.getOrderNo())
                .supplierName(order.getSupplierName())
                .status(order.getStatus())
                .createdAt(order.getCreatedAt())
                .items(itemResponses)
                .build();
    }

    private InboundOrderItemResponse convertToItemResponse(InboundOrderItem item) {
        Product product = productRepository.findById(item.getProductId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "商品不存在: ID=" + item.getProductId()));

        return InboundOrderItemResponse.builder()
                .id(item.getId())
                .productId(item.getProductId())
                .productName(product.getName())
                .productSku(product.getSku())
                .quantity(item.getQuantity())
                .locationCode(item.getLocationCode())
                .build();
    }

    private boolean filterByKeyword(InventoryResponse response, String keyword) {
        if (keyword == null || keyword.isEmpty()) {
            return true;
        }
        String lowerKeyword = keyword.toLowerCase();
        return response.getProductName().toLowerCase().contains(lowerKeyword) ||
                response.getSku().toLowerCase().contains(lowerKeyword);
    }

    private boolean filterByWarehouse(InventoryResponse response, Long warehouseId) {
        if (warehouseId == null) {
            return true;
        }
        return response.getWarehouseId() != null &&
                response.getWarehouseId().contains(warehouseId.toString());
    }
}
