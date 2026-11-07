package de.campusshop.cw1.service;

import de.campusshop.cw1.dto.OrderResponse;
import de.campusshop.cw1.dto.PartnerBuyer;
import de.campusshop.cw1.dto.PartnerItem;
import de.campusshop.cw1.dto.PartnerOrderRequest;
import de.campusshop.cw1.exception.DuplicateOrderException;
import de.campusshop.cw1.exception.OrderNotFoundException;
import de.campusshop.cw1.mapper.OrderMapper;
import de.campusshop.cw1.model.Order;
import de.campusshop.cw1.model.OrderStatus;
import de.campusshop.cw1.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link OrderService} with a <b>mocked</b> {@link OrderRepository} (Week 6).
 *
 * <p>TODO (student): use Mockito to stub the repository and verify the service behaviour. The mock
 * and a fixed {@link Clock} are wired for you below.
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository repository;

    private OrderService service;

    private final Clock clock = Clock.fixed(Instant.parse("2026-05-01T12:00:00Z"), ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        service = new OrderService(repository, new OrderMapper(), clock);
    }

    private static final String UUID_REGEX =
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";

    private PartnerOrderRequest request(String externalId) {
        return new PartnerOrderRequest(
                externalId,
                new PartnerBuyer("11111111-2222-3444-5666-777777777777", "Olivia Bird", "omb7060@gmail.com"),
                List.of(new PartnerItem("BOOK-1", "Clean Code", 2, new BigDecimal("32.50"))),
                "partner-a");
    }

    @Test
    void importOrderPersistsAndReturnsResponse() {
        // TODO: stub repository.findAll()/save(...), call service.importOrder(...), assert the response
        OrderResponse response = service.importOrder(request("22222222-2222-4222-8222-222222222201"));

        assertThat(response.orderId()).matches(UUID_REGEX); // check response's orderId matches UUID format
        assertThat(response.status()).isEqualTo(OrderStatus.IMPORTED);

        assertThat(response.totalAmount()).isEqualByComparingTo("65.00");
        assertThat(response.createdAt()).isEqualTo(Instant.parse("2026-05-01T12:00:00Z"));

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class); // captures order passed to save()
        verify(repository).save(captor.capture()); // check save() was called and cspture what was passed to it
        assertThat(captor.getValue().externalOrderId()).isEqualTo("22222222-2222-4222-8222-222222222201");
    }

    @Test
    void importOrderRejectsDuplicateExternalId() {
        Order existing = new Order("11111111-1111-4111-8111-111111111111", "22222222-2222-4222-8222-222222222201", "33333333-3333-4333-8333-333333333333", OrderStatus.IMPORTED,
                new BigDecimal("65.00"), Instant.now(), List.of());
        when(repository.findAll()).thenReturn(List.of(existing));

        assertThatThrownBy(() -> // check an exception was thrown
                service.importOrder(request("22222222-2222-4222-8222-222222222201")))
                .isInstanceOf(DuplicateOrderException.class); // check it's the right exception

        verify(repository, never()).save(any()); // ensure repository.save() was not called as to not persist existing order
    }

    @Test
    void findByIdThrowsWhenMissing() {
        when(repository.findById("00000000-0000-4000-8000-000000000999")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById("00000000-0000-4000-8000-000000000999"))
                .isInstanceOf(OrderNotFoundException.class); // ensure OrderNotFoundException was thrown
    }
}
