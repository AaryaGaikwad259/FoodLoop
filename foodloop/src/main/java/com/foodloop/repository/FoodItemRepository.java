package com.foodloop.repository;

import com.foodloop.entity.FoodItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface FoodItemRepository extends JpaRepository<FoodItem, Long> {

    boolean existsByCategoryId(Long categoryId);

    @Query("select f from FoodItem f " +
            "where lower(f.name) like lower(concat('%', :name, '%')) " +
            "and (:categoryId is null or f.category.id = :categoryId) " +
            "and (:available is null or f.available = :available) " +
            "order by f.name")
    List<FoodItem> search(@Param("name") String name,
                          @Param("categoryId") Long categoryId,
                          @Param("available") Boolean available);
}
