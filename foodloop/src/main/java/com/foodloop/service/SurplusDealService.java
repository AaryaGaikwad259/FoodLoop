package com.foodloop.service;

import com.foodloop.dto.DealDtos.DealRequest;
import com.foodloop.dto.DealDtos.DealResponse;
import com.foodloop.entity.FoodItem;
import com.foodloop.entity.SurplusDeal;
import com.foodloop.exception.BadRequestException;
import com.foodloop.exception.ConflictException;
import com.foodloop.exception.ResourceNotFoundException;
import com.foodloop.repository.FoodItemRepository;
import com.foodloop.repository.OrderItemRepository;
import com.foodloop.repository.SurplusDealRepository;
import com.foodloop.util.Mapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class SurplusDealService {

    private final SurplusDealRepository dealRepository;
    private final FoodItemRepository foodItemRepository;
    private final OrderItemRepository orderItemRepository;

    public SurplusDealService(SurplusDealRepository dealRepository, FoodItemRepository foodItemRepository,
                              OrderItemRepository orderItemRepository) {
        this.dealRepository = dealRepository;
        this.foodItemRepository = foodItemRepository;
        this.orderItemRepository = orderItemRepository;
    }

    /** Customers: only deals that are valid RIGHT NOW (active, inside the time window, not sold out). */
    @Transactional(readOnly = true)
    public List<DealResponse> listCurrentlyValid() {
        return dealRepository.findAllCurrentlyValid(LocalDateTime.now()).stream().map(Mapper::toDeal).toList();
    }

    @Transactional(readOnly = true)
    public List<DealResponse> listAll() {
        return dealRepository.findAllByOrderByStartTimeDesc().stream().map(Mapper::toDeal).toList();
    }

    @Transactional(readOnly = true)
    public DealResponse get(Long id) {
        return Mapper.toDeal(findOrThrow(id));
    }

    @Transactional
    public DealResponse create(DealRequest request) {
        SurplusDeal deal = new SurplusDeal();
        apply(deal, request);
        return Mapper.toDeal(dealRepository.save(deal));
    }

    @Transactional
    public DealResponse update(Long id, DealRequest request) {
        SurplusDeal deal = findOrThrow(id);
        apply(deal, request);
        return Mapper.toDeal(dealRepository.save(deal));
    }

    @Transactional
    public DealResponse setActive(Long id, boolean active) {
        SurplusDeal deal = findOrThrow(id);
        deal.setActive(active);
        return Mapper.toDeal(dealRepository.save(deal));
    }

    /** A deal that has been used by an order must be kept for history - deactivate it instead of deleting. */
    @Transactional
    public void delete(Long id) {
        SurplusDeal deal = findOrThrow(id);
        if (orderItemRepository.existsByDealId(id)) {
            throw new ConflictException("This deal was used in orders and cannot be deleted. Deactivate it instead.");
        }
        dealRepository.delete(deal);
    }

    /** Validates the request and fills in the entity. The server calculates the final price - never the client. */
    private void apply(SurplusDeal deal, DealRequest r) {
        FoodItem food = foodItemRepository.findById(r.foodItemId())
                .orElseThrow(() -> new BadRequestException("Food item not found with id " + r.foodItemId()));

        if (!r.endTime().isAfter(r.startTime())) {
            throw new BadRequestException("endTime must be after startTime");
        }
        if (!r.endTime().isAfter(LocalDateTime.now())) {
            throw new BadRequestException("endTime must be in the future - a deal that has already expired cannot be saved as new");
        }
        if ((r.discountedPrice() == null) == (r.discountPercent() == null)) {
            throw new BadRequestException("Provide exactly one of discountPercent or discountedPrice");
        }

        BigDecimal regular = food.getPrice();
        BigDecimal discounted;
        BigDecimal percent;
        if (r.discountPercent() != null) {
            percent = r.discountPercent().setScale(2, RoundingMode.HALF_UP);
            discounted = regular.multiply(BigDecimal.valueOf(100).subtract(percent))
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        } else {
            discounted = r.discountedPrice().setScale(2, RoundingMode.HALF_UP);
            if (discounted.compareTo(regular) >= 0) {
                throw new BadRequestException("Discounted price must be lower than the regular price (" + regular + ")");
            }
            percent = regular.subtract(discounted).multiply(BigDecimal.valueOf(100))
                    .divide(regular, 2, RoundingMode.HALF_UP);
        }
        if (discounted.compareTo(new BigDecimal("0.01")) < 0) {
            throw new BadRequestException("Discounted price is too low");
        }
        if (r.quantityLimit() != null && r.quantityLimit() < deal.getQuantitySold()) {
            throw new BadRequestException("Quantity limit cannot be lower than the units already sold (" + deal.getQuantitySold() + ")");
        }

        deal.setFoodItem(food);
        deal.setOriginalPrice(regular);
        deal.setDiscountedPrice(discounted);
        deal.setDiscountPercent(percent);
        deal.setStartTime(r.startTime());
        deal.setEndTime(r.endTime());
        deal.setQuantityLimit(r.quantityLimit());
        deal.setActive(r.active() == null || r.active());
    }

    private SurplusDeal findOrThrow(Long id) {
        return dealRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Deal not found with id " + id));
    }
}
