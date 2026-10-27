package com.foodloop.repository;

import com.foodloop.entity.SurplusDeal;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SurplusDealRepository extends JpaRepository<SurplusDeal, Long> {

    String VALID_DEAL_FOR_FOOD =
            "select d from SurplusDeal d where d.foodItem.id = :foodId and d.active = true " +
            "and d.startTime <= :now and d.endTime > :now " +
            "and (d.quantityLimit is null or d.quantitySold < d.quantityLimit) " +
            "order by d.discountedPrice asc";

    /** Read-only lookup (used to show prices in the cart and deal lists). */
    @Query(VALID_DEAL_FOR_FOOD)
    List<SurplusDeal> findValidDealsForFood(@Param("foodId") Long foodId, @Param("now") LocalDateTime now);

    /** Same lookup but locks the rows (SELECT ... FOR UPDATE) so two orders cannot oversell a limited deal. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(VALID_DEAL_FOR_FOOD)
    List<SurplusDeal> findValidDealsForFoodForUpdate(@Param("foodId") Long foodId, @Param("now") LocalDateTime now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from SurplusDeal d where d.id = :id")
    Optional<SurplusDeal> findByIdForUpdate(@Param("id") Long id);

    @Query("select d from SurplusDeal d where d.active = true and d.startTime <= :now and d.endTime > :now " +
            "and (d.quantityLimit is null or d.quantitySold < d.quantityLimit) order by d.endTime asc")
    List<SurplusDeal> findAllCurrentlyValid(@Param("now") LocalDateTime now);

    List<SurplusDeal> findAllByOrderByStartTimeDesc();

    void deleteByFoodItemId(Long foodItemId);
}
