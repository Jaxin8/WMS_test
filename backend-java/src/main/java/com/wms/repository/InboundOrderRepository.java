package com.wms.repository;

import com.wms.entity.InboundOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface InboundOrderRepository extends JpaRepository<InboundOrder, Long> {

    @Query("SELECT COUNT(io) FROM InboundOrder io WHERE io.createdAt >= :startTime AND io.createdAt < :endTime")
    long countByCreatedAtBetween(@Param("startTime") LocalDateTime startTime, 
                                  @Param("endTime") LocalDateTime endTime);

    @Query("SELECT io FROM InboundOrder io WHERE " +
           "(:keyword IS NULL OR io.orderNo LIKE %:keyword% OR io.supplierName LIKE %:keyword%) " +
           "AND (:status IS NULL OR io.status = :status)")
    Page<InboundOrder> search(@Param("keyword") String keyword,
                              @Param("status") String status,
                              Pageable pageable);
}
