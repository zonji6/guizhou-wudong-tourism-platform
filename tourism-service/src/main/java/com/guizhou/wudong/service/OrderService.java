package com.guizhou.wudong.service;

import com.guizhou.wudong.api.ApiRequestException;
import com.guizhou.wudong.api.OrderRequests;
import com.guizhou.wudong.api.OrderViews;
import com.guizhou.wudong.domain.FoodOrder;
import com.guizhou.wudong.domain.FoodOrderStatus;
import com.guizhou.wudong.domain.ProductOrder;
import com.guizhou.wudong.domain.ProductOrderStatus;
import com.guizhou.wudong.domain.StayBooking;
import com.guizhou.wudong.domain.StayBookingStatus;
import com.guizhou.wudong.repository.OrderRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class OrderService {
    private static final String PUBLISHED = "PUBLISHED";
    private static final Set<ProductOrderStatus> PRODUCT_PENDING_TARGETS = Set.of(
            ProductOrderStatus.PICKED_UP, ProductOrderStatus.CANCELLED);
    private static final Set<FoodOrderStatus> FOOD_PENDING_TARGETS = Set.of(
            FoodOrderStatus.COMPLETED, FoodOrderStatus.CANCELLED);
    private static final Set<StayBookingStatus> STAY_PENDING_TARGETS = Set.of(
            StayBookingStatus.CONFIRMED, StayBookingStatus.CANCELLED);
    private static final Set<StayBookingStatus> STAY_CONFIRMED_TARGETS = Set.of(
            StayBookingStatus.COMPLETED, StayBookingStatus.CANCELLED);

    private final OrderRepository repository;

    public OrderService(OrderRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public OrderViews.ProductPublicView createProduct(String visitorId, OrderRequests.ProductCreate request) {
        OrderRepository.ProductCatalog product = repository.findProductCatalog(request.productId())
                .orElseThrow(OrderService::resourceNotFound);
        requirePublished(product.catalogStatus(), product.merchantStatus());
        if (!product.pickupPoint().equals(request.pickupPoint())) {
            throw new IllegalArgumentException("取货点必须与公开目录一致");
        }
        String id = UUID.randomUUID().toString();
        repository.insertProductOrder(id, visitorId, request.productId(), request.quantity(), request.pickupPoint(),
                request.contactName(), request.contactPhone(), request.note(), ProductOrderStatus.PENDING_PICKUP.name(),
                request.sourceThreadId());
        return OrderViews.ProductPublicView.from(requireProductOrder(id));
    }

    @Transactional
    public OrderViews.FoodPublicView createFood(String visitorId, OrderRequests.FoodCreate request) {
        OrderRepository.FoodCatalog food = repository.findFoodCatalog(request.foodItemId())
                .orElseThrow(OrderService::resourceNotFound);
        requirePublished(food.catalogStatus(), food.merchantStatus());
        String id = UUID.randomUUID().toString();
        repository.insertFoodOrder(id, visitorId, request.foodItemId(), request.visitAt(), request.peopleCount(),
                request.contactName(), request.contactPhone(), request.note(), FoodOrderStatus.PENDING_VISIT.name(),
                request.sourceThreadId());
        return OrderViews.FoodPublicView.from(requireFoodOrder(id));
    }

    @Transactional
    public OrderViews.StayPublicView createStay(String visitorId, OrderRequests.StayCreate request) {
        OrderRepository.RoomCatalog room = repository.findRoomCatalog(request.roomTypeId())
                .orElseThrow(OrderService::resourceNotFound);
        requirePublished(room.catalogStatus(), room.stayStatus(), room.merchantStatus());
        if (request.peopleCount() > room.maxGuests()) {
            throw new ApiRequestException(HttpStatus.CONFLICT, "CAPACITY_EXCEEDED", "入住人数超过该房型容量");
        }
        String id = UUID.randomUUID().toString();
        repository.insertStayBooking(id, visitorId, request.roomTypeId(), request.checkInDate(), request.peopleCount(),
                request.contactName(), request.contactPhone(), request.note(),
                StayBookingStatus.PENDING_CONFIRMATION.name(), request.sourceThreadId());
        return OrderViews.StayPublicView.from(requireStayBooking(id));
    }

    public List<OrderViews.ProductPublicView> myProducts(String visitorId) {
        return repository.findProductOrdersByVisitor(visitorId).stream()
                .map(OrderViews.ProductPublicView::from)
                .toList();
    }

    public List<OrderViews.FoodPublicView> myFoods(String visitorId) {
        return repository.findFoodOrdersByVisitor(visitorId).stream()
                .map(OrderViews.FoodPublicView::from)
                .toList();
    }

    public List<OrderViews.StayPublicView> myStays(String visitorId) {
        return repository.findStayBookingsByVisitor(visitorId).stream()
                .map(OrderViews.StayPublicView::from)
                .toList();
    }

    public List<OrderViews.ProductAdminView> adminProducts(String status) {
        String filter = productStatusFilter(status);
        return repository.findProductOrders(filter).stream().map(OrderViews.ProductAdminView::from).toList();
    }

    public List<OrderViews.FoodAdminView> adminFoods(String status) {
        String filter = foodStatusFilter(status);
        return repository.findFoodOrders(filter).stream().map(OrderViews.FoodAdminView::from).toList();
    }

    public List<OrderViews.StayAdminView> adminStays(String status) {
        String filter = stayStatusFilter(status);
        return repository.findStayBookings(filter).stream().map(OrderViews.StayAdminView::from).toList();
    }

    @Transactional
    public OrderViews.ProductAdminView updateProductStatus(String rawId, String rawTarget) {
        String id = OrderRequests.canonicalUuid(rawId);
        ProductOrder current = requireProductOrder(id);
        ProductOrderStatus target = productTransitionTarget(rawTarget);
        if (current.status() != ProductOrderStatus.PENDING_PICKUP || !PRODUCT_PENDING_TARGETS.contains(target)) {
            throw invalidTransition();
        }
        if (repository.updateProductStatus(id, current.status().name(), target.name()) == 0) {
            throw invalidTransitionAfterConcurrentChange(repository.findProductOrder(id).isPresent());
        }
        return OrderViews.ProductAdminView.from(requireProductOrder(id));
    }

    @Transactional
    public OrderViews.FoodAdminView updateFoodStatus(String rawId, String rawTarget) {
        String id = OrderRequests.canonicalUuid(rawId);
        FoodOrder current = requireFoodOrder(id);
        FoodOrderStatus target = foodTransitionTarget(rawTarget);
        if (current.status() != FoodOrderStatus.PENDING_VISIT || !FOOD_PENDING_TARGETS.contains(target)) {
            throw invalidTransition();
        }
        if (repository.updateFoodStatus(id, current.status().name(), target.name()) == 0) {
            throw invalidTransitionAfterConcurrentChange(repository.findFoodOrder(id).isPresent());
        }
        return OrderViews.FoodAdminView.from(requireFoodOrder(id));
    }

    @Transactional
    public OrderViews.StayAdminView updateStayStatus(String rawId, String rawTarget) {
        String id = OrderRequests.canonicalUuid(rawId);
        StayBooking current = requireStayBooking(id);
        StayBookingStatus target = stayTransitionTarget(rawTarget);
        boolean allowed = current.status() == StayBookingStatus.PENDING_CONFIRMATION
                ? STAY_PENDING_TARGETS.contains(target)
                : current.status() == StayBookingStatus.CONFIRMED && STAY_CONFIRMED_TARGETS.contains(target);
        if (!allowed) {
            throw invalidTransition();
        }
        if (repository.updateStayStatus(id, current.status().name(), target.name()) == 0) {
            throw invalidTransitionAfterConcurrentChange(repository.findStayBooking(id).isPresent());
        }
        return OrderViews.StayAdminView.from(requireStayBooking(id));
    }

    private static String productStatusFilter(String status) {
        if (status == null) {
            return null;
        }
        try {
            return ProductOrderStatus.valueOf(status).name();
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("商品订单状态筛选无效");
        }
    }

    private static String foodStatusFilter(String status) {
        if (status == null) {
            return null;
        }
        try {
            return FoodOrderStatus.valueOf(status).name();
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("餐食订单状态筛选无效");
        }
    }

    private static String stayStatusFilter(String status) {
        if (status == null) {
            return null;
        }
        try {
            return StayBookingStatus.valueOf(status).name();
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("住宿预约状态筛选无效");
        }
    }

    private static ProductOrderStatus productTransitionTarget(String status) {
        try {
            return ProductOrderStatus.valueOf(status);
        } catch (IllegalArgumentException exception) {
            throw invalidTransition();
        }
    }

    private static FoodOrderStatus foodTransitionTarget(String status) {
        try {
            return FoodOrderStatus.valueOf(status);
        } catch (IllegalArgumentException exception) {
            throw invalidTransition();
        }
    }

    private static StayBookingStatus stayTransitionTarget(String status) {
        try {
            return StayBookingStatus.valueOf(status);
        } catch (IllegalArgumentException exception) {
            throw invalidTransition();
        }
    }

    private ProductOrder requireProductOrder(String id) {
        return repository.findProductOrder(id).orElseThrow(OrderService::resourceNotFound);
    }

    private FoodOrder requireFoodOrder(String id) {
        return repository.findFoodOrder(id).orElseThrow(OrderService::resourceNotFound);
    }

    private StayBooking requireStayBooking(String id) {
        return repository.findStayBooking(id).orElseThrow(OrderService::resourceNotFound);
    }

    private static void requirePublished(String... statuses) {
        for (String status : statuses) {
            if (!PUBLISHED.equals(status)) {
                throw new ApiRequestException(HttpStatus.CONFLICT, "CATALOG_NOT_PUBLISHED", "目录目标当前未发布");
            }
        }
    }

    private static ApiRequestException resourceNotFound() {
        return new ApiRequestException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "未找到或无权访问该资源");
    }

    private static ApiRequestException invalidTransition() {
        return new ApiRequestException(HttpStatus.CONFLICT, "INVALID_STATUS_TRANSITION", "订单状态不能这样变更");
    }

    private static ApiRequestException invalidTransitionAfterConcurrentChange(boolean stillExists) {
        return stillExists ? invalidTransition() : resourceNotFound();
    }
}
