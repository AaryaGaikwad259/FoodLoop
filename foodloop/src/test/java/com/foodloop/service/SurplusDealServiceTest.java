package com.foodloop.service;

import com.foodloop.dto.DealDtos.DealRequest;
import com.foodloop.dto.DealDtos.DealResponse;
import com.foodloop.entity.Category;
import com.foodloop.entity.FoodItem;
import com.foodloop.entity.SurplusDeal;
import com.foodloop.exception.BadRequestException;
import com.foodloop.repository.FoodItemRepository;
import com.foodloop.repository.OrderItemRepository;
import com.foodloop.repository.SurplusDealRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SurplusDealServiceTest {

    @Mock SurplusDealRepository dealRepository;
    @Mock FoodItemRepository foodItemRepository;
    @Mock OrderItemRepository orderItemRepository;

    SurplusDealService service;
    final LocalDateTime now = LocalDateTime.now();

    @BeforeEach
    void setUp() {
        service = new SurplusDealService(dealRepository, foodItemRepository, orderItemRepository);
        Category c = new Category();
        c.setId(1L);
        c.setName("Main");
        FoodItem f = new FoodItem();
        f.setId(10L);
        f.setName("Pizza");
        f.setPrice(new BigDecimal("200.00"));
        f.setCategory(c);
        when(foodItemRepository.findById(10L)).thenReturn(Optional.of(f));
        when(dealRepository.save(any(SurplusDeal.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void percentDiscount_isCalculatedByServer() {
        DealResponse r = service.create(new DealRequest(10L, null, new BigDecimal("25"),
                now.minusHours(1), now.plusHours(5), 10, true));
        assertEquals(0, new BigDecimal("150.00").compareTo(r.discountedPrice()));
        assertEquals(0, new BigDecimal("200.00").compareTo(r.originalPrice()));
        assertTrue(r.currentlyValid());
    }

    @Test
    void priceDiscount_computesPercent() {
        DealResponse r = service.create(new DealRequest(10L, new BigDecimal("100.00"), null,
                now.minusHours(1), now.plusHours(5), null, true));
        assertEquals(0, new BigDecimal("50.00").compareTo(r.discountPercent()));
    }

    @Test
    void discountedPriceNotBelowRegular_isRejected() {
        assertThrows(BadRequestException.class, () -> service.create(new DealRequest(10L, new BigDecimal("200.00"), null,
                now.minusHours(1), now.plusHours(5), null, true)));
    }

    @Test
    void bothOrNeitherDiscountField_isRejected() {
        assertThrows(BadRequestException.class, () -> service.create(new DealRequest(10L, new BigDecimal("100"), new BigDecimal("10"),
                now.minusHours(1), now.plusHours(5), null, true)));
        assertThrows(BadRequestException.class, () -> service.create(new DealRequest(10L, null, null,
                now.minusHours(1), now.plusHours(5), null, true)));
    }

    @Test
    void invalidOrExpiredTimeRange_isRejected() {
        assertThrows(BadRequestException.class, () -> service.create(new DealRequest(10L, null, new BigDecimal("20"),
                now.plusHours(5), now.plusHours(1), null, true)));          // end before start
        assertThrows(BadRequestException.class, () -> service.create(new DealRequest(10L, null, new BigDecimal("20"),
                now.minusHours(5), now.minusHours(1), null, true)));        // already expired
    }

    @Test
    void expiredDeal_reportsNotCurrentlyValid_evenIfStillActive() {
        SurplusDeal d = new SurplusDeal();
        d.setActive(true);
        d.setStartTime(now.minusHours(5));
        d.setEndTime(now.minusMinutes(1));
        assertFalse(d.isCurrentlyValid(now));
    }

    @Test
    void soldOutDeal_isNotCurrentlyValid() {
        SurplusDeal d = new SurplusDeal();
        d.setActive(true);
        d.setStartTime(now.minusHours(1));
        d.setEndTime(now.plusHours(1));
        d.setQuantityLimit(3);
        d.setQuantitySold(3);
        assertFalse(d.isCurrentlyValid(now));
    }
}
