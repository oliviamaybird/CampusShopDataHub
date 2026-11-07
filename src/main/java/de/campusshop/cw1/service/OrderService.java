package de.campusshop.cw1.service;

import de.campusshop.cw1.dto.OrderResponse;
import de.campusshop.cw1.dto.PartnerOrderRequest;
import de.campusshop.cw1.exception.DuplicateOrderException;
import de.campusshop.cw1.exception.OrderNotFoundException;
import de.campusshop.cw1.mapper.OrderMapper;
import de.campusshop.cw1.model.Order;
import de.campusshop.cw1.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

/**
 * Coordinates order import, lookup and deletion. Business rules (id generation,
 * duplicate detection, status assignment) live here, not in the controller.
 *
 * <p>TODO (student): implement the methods below. The dependencies are already wired
 * for you via constructor injection.
 */
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository repository;
    private final OrderMapper mapper;
    private final Clock clock;

    public OrderService(OrderRepository repository, OrderMapper mapper, Clock clock) {
        this.repository = repository;
        this.mapper = mapper;
        this.clock = clock;
    }

    /**
     * Import a partner order:
     * <ol>
     *   <li>orders are <b>always unique</b> — if {@code repository.existsByExternalOrderId(...)} is
     *       true, reject with a {@code DuplicateOrderException} (maps to {@code 409}) before saving;</li>
     *   <li>generate an {@code orderId} with {@code UUID.randomUUID()};</li>
     *   <li>map the request to an {@code Order} (use {@code mapper} and {@code clock.instant()});</li>
     *   <li>persist it via {@code repository};</li>
     *   <li>return the {@code OrderResponse}.</li>
     * </ol>
     */
    public OrderResponse importOrder(PartnerOrderRequest request) {
        if (externalOrderIdExists(request.partnerOrderId())) {
            log.warn("DuplicateOrderException", request.partnerOrderId()); // order is not unique so reject
            throw new DuplicateOrderException(request.partnerOrderId());
        }
        String orderId = UUID.randomUUID().toString(); // generating new orderID
        Order order = mapper.toDomain(request, orderId, clock.instant());
        repository.save(order); //map to an order then save to repository
        log.info("Imported order orderId={} externalOrderId={} customerId={} total={}",
                order.orderId(), order.externalOrderId(), order.customerId(), order.totalAmount());
        return mapper.toResponse(order);
    }

    private boolean externalOrderIdExists(String externalOrderId) {
        return repository.findAll().stream().anyMatch(o -> externalOrderId.equals(o.externalOrderId()));
        // goes through a stream of a list of all orders to see if it exists
    }

    /** Return all stored orders as response DTOs. */
    public List<OrderResponse> findAll() {
        return repository.findAll().stream().map(mapper::toResponse).toList();
        // applies toResponse to every order in list - mapping internal order to its API response DTO
    }

    /** Return one order by id, or throw {@code OrderNotFoundException} (maps to {@code 404}). */
    public OrderResponse findById(String orderId) {
        return repository.findById(orderId).map(mapper::toResponse).orElseThrow(() -> new OrderNotFoundException(orderId));
        // if order exists, map to its response DTO and return it
    }

    /** Return all orders for a customer (an empty list is valid). */
    public List<OrderResponse> findByCustomerId(String customerId) {
        return repository.findByCustomerId(customerId).stream().map(mapper::toResponse).toList();
        // return orders with this customerId as a list
    }

    /** Delete an order by id, or throw {@code OrderNotFoundException} if it does not exist. */
    public void deleteById(String orderId) {
        if (!repository.deleteById(orderId)) {
            throw new OrderNotFoundException(orderId);
            // if orderId is not found, throw exception
        }
        log.info("Deleted order orderId={}", orderId);
        // if found, log deletion
    }
}
