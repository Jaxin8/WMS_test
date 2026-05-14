package com.wms.repository;

import com.wms.dto.InventoryResponse;
import com.wms.entity.Inventory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 库存 Repository — 候选人需要实现库存查询（任务2）
 * 提示：你可能需要添加自定义查询方法
 */
@Repository
public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    Optional<Inventory> findByProductIdAndLocationCode(Long productId, String locationCode);

    // TODO: 候选人添加自定义查询方法，支持按 product/sku/location 筛选和分页
    // 提示：可以使用 @Query 写 JPQL，或使用 Specification 动态查询
    @Query("SELECT new com.wms.dto.InventoryResponse(" +
            "p.id, p.name, p.sku, l.code, CAST(w.id AS string), w.name, i.quantity, i.updatedAt) " +
            "FROM Inventory i " +
            "JOIN Product p ON i.productId = p.id " +
            "JOIN Location l ON i.locationCode = l.code " +
            "JOIN Warehouse w ON l.warehouseId = w.id " +
            "WHERE (:keyword IS NULL OR p.name LIKE %:keyword% OR p.sku LIKE %:keyword%) " +
            "AND (:warehouseId IS NULL OR l.warehouseId = :warehouseId)")
    Page<InventoryResponse> searchWithFilters(@Param("keyword") String keyword,
                                              @Param("warehouseId") Long warehouseId,
                                              Pageable pageable);

}
