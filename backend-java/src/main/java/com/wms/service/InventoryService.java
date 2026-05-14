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
import org.springframework.transaction.annotation.Isolation;
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
     * 入库单创建 — 重构版
     */
    @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public InboundOrderResponse createInboundOrder(InboundOrderCreateRequest request) {
        // 1. 校验所有明细项
        validateItems(request.getItems());

        // 2. 创建入库单主表
        InboundOrder savedOrder = createInboundOrderEntity(request.getSupplierName());

        // 3. 批量处理入库明细
        processInboundItemsBatch(savedOrder.getId(), request.getItems());

        // 4. 返回完整响应
        return convertToInboundOrderResponse(savedOrder);
    }

    /**
     * 批量处理入库明细（解决 N+1 性能问题）
     */
    private void processInboundItemsBatch(Long orderId, List<InboundItemRequest> items) {
        // 3.1 构建明细对象列表
        List<InboundOrderItem> orderItems = items.stream()
                .map(item -> InboundOrderItem.builder()
                        .orderId(orderId)
                        .productId(item.getProductId())
                        .quantity(item.getQuantity())
                        .locationCode(item.getLocationCode())
                        .build())
                .collect(Collectors.toList());

        // 3.2 批量保存明细
        inboundOrderItemRepository.saveAll(orderItems);

        // 3.3 批量更新库存（使用原子操作解决并发问题）
        for (InboundItemRequest item : items) {
            updateOrCreateInventoryAtomic(item.getProductId(), item.getLocationCode(), item.getQuantity());
        }
    }

    /**
     * 原子性更新或创建库存
     */
    private void updateOrCreateInventoryAtomic(Long productId, String locationCode, Integer quantity) {
        // 尝试原子累加
        int updated = inventoryRepository.addQuantity(productId, locationCode, quantity);

        if (updated == 0) {
            // 如果累加失败（记录不存在），则创建新记录
            // 注意：在高并发下这里可能抛出唯一索引冲突异常，需根据业务需求决定是否重试
            Inventory newInventory = Inventory.builder()
                    .productId(productId)
                    .locationCode(locationCode)
                    .quantity(quantity)
                    .build();

            try {
                inventoryRepository.save(newInventory);
            } catch (Exception e) {
                // 简单的冲突处理：如果创建失败，再次尝试累加（假设是并发插入导致）
                log.warn("库存创建冲突，尝试重新累加: productId={}", productId);
                inventoryRepository.addQuantity(productId, locationCode, quantity);
            }
        }
    }


    /**
     * 库存查询 — 候选人实现
     */
    public Page<InventoryResponse> queryInventory(String keyword, Long warehouseId,
                                                   int page, int pageSize) {
        // TODO: 候选人实现
//        throw new UnsupportedOperationException("请实现库存查询功能（任务2）");
        Pageable pageable = PageRequest.of(page - 1, pageSize);
        return inventoryRepository.searchWithFilters(keyword, warehouseId, pageable);
    }

    public Page<InboundOrderResponse> queryInboundOrders(String keyword, String status,
                                                         int page, int pageSize) {
        Pageable pageable = PageRequest.of(page - 1, pageSize);
        Page<InboundOrder> orderPage = inboundOrderRepository.search(keyword, status, pageable);

        return orderPage.map(this::convertToInboundOrderResponse);
    }

    /**
     * 校验入库明细中的商品和库位是否存在
     */
    private void validateItems(List<InboundItemRequest> items) {
        for (InboundItemRequest item : items) {
            if (!productRepository.existsById(item.getProductId())) {
                throw new EntityNotFoundException("商品不存在: ID=" + item.getProductId());
            }

            if (!locationRepository.existsByCode(item.getLocationCode())) {
                throw new EntityNotFoundException("库位不存在: " + item.getLocationCode());
            }
        }
    }

    /**
     * 创建入库单主表记录
     */
    private InboundOrder createInboundOrderEntity(String supplierName) {
        String orderNo = generateOrderNo();

        InboundOrder order = InboundOrder.builder()
                .orderNo(orderNo)
                .supplierName(supplierName)
                .status("PENDING")
                .build();

        return inboundOrderRepository.save(order);
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

}
