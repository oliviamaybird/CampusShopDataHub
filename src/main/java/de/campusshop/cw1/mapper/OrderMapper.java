package de.campusshop.cw1.mapper;

import de.campusshop.cw1.dto.OrderItemResponse;
import de.campusshop.cw1.dto.OrderResponse;
import de.campusshop.cw1.dto.PartnerItem;
import de.campusshop.cw1.dto.PartnerOrderRequest;
import de.campusshop.cw1.model.Order;
import de.campusshop.cw1.model.OrderItem;
import de.campusshop.cw1.model.OrderStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Manual mapper that keeps transformation logic out of the controller and service.
 * (Week 2 shows both manual and MapStruct mappers; a manual mapper is clearer here
 * because the total is computed.)
 *
 * <p>TODO (student): implement the two mapping methods.
 */
@Component
public class OrderMapper {

    /**
     * Build a new internal {@link Order} from a validated partner request:
     * copy {@code partnerOrderId} → {@code externalOrderId} and {@code buyer.id} → {@code customerId},
     * map each item, compute {@code totalAmount} as the sum of {@code quantity * unitPrice},
     * set status {@code IMPORTED} and use the supplied {@code orderId}/{@code createdAt}.
     */
    public Order toDomain(PartnerOrderRequest request, String orderId, Instant createdAt) {
        List<OrderItem> items = request.items().stream()
                .map(this::toDomainItem)
                .toList();
        // map data from partnerItem to new internal orderItem using toDomainItem function
        BigDecimal total = items.stream()
                .map(OrderItem::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add); // reduce() combines all elements of stream into single summary result
        // total = summation of (price * quantity) of all items in order
        return new Order(
                orderId,
                request.partnerOrderId(),
                request.buyer().id(),
                OrderStatus.IMPORTED,
                total,
                createdAt,
                items);
    }

    //function that, for each partnerItem, retrieves the corresponding values and passes them into the new object
    private OrderItem toDomainItem(PartnerItem item) {
        return new OrderItem(item.sku(), item.name(), item.quantity(), item.unitPrice());
    }

    /** Map an internal {@link Order} to its API response DTO. */
    public OrderResponse toResponse(Order order) {
        List<OrderItemResponse> items = order.items().stream()
                .map(i -> new OrderItemResponse(i.sku(), i.name(), i.quantity(), i.unitPrice()))
                .toList();
        return new OrderResponse(
                order.orderId(),
                order.externalOrderId(),
                order.customerId(),
                order.status(),
                order.totalAmount(),
                order.createdAt(),
                items);
    }
}
