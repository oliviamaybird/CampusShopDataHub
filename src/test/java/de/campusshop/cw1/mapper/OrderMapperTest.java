package de.campusshop.cw1.mapper;

import de.campusshop.cw1.dto.OrderResponse;
import de.campusshop.cw1.dto.PartnerBuyer;
import de.campusshop.cw1.dto.PartnerItem;
import de.campusshop.cw1.dto.PartnerOrderRequest;
import de.campusshop.cw1.model.Order;
import de.campusshop.cw1.model.OrderStatus;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Unit tests for {@link OrderMapper} (Week 6).
 *
 * <p>TODO (student): test that a partner request maps to an {@code Order} with the external/customer
 * ids copied across, status {@code IMPORTED}, and {@code totalAmount} = Σ {@code quantity*unitPrice};
 * and that {@code toResponse} echoes the items. Remove the {@code @Disabled} annotations once written.
 */
class OrderMapperTest {

    private final OrderMapper mapper = new OrderMapper();

    private final Instant createdAt = Instant.parse("2026-10-24T16:52:00Z");

    private PartnerOrderRequest sampleRequest() {
        return new PartnerOrderRequest(
                "22222222-2222-4222-8222-222222222222",
                new PartnerBuyer("11111111-2222-3444-5666-777777777777", "Olivia Bird", "omb7060@gmail.com"),
                List.of(
                        new PartnerItem("BOOK-1", "Clean Code", 2, new BigDecimal("32.50")),
                        new PartnerItem("USB-CABLE", "USB-C Cable", 1, new BigDecimal("8.99"))),
                "partner-a");
    }

    @Test
    void toDomainComputesTotalAndCopiesIds() {
        // TODO: arrange a PartnerOrderRequest, act with mapper.toDomain(...), assert with AssertJ
        Order order = mapper.toDomain(sampleRequest(), "11111111-1111-4111-8111-111111111111", createdAt);

        // checking that each attribute value of partnerOrderRequest maps to order
        assertThat(order.orderId()).isEqualTo("11111111-1111-4111-8111-111111111111");
        assertThat(order.externalOrderId()).isEqualTo("22222222-2222-4222-8222-222222222222");
        assertThat(order.customerId()).isEqualTo("11111111-2222-3444-5666-777777777777");
        assertThat(order.status()).isEqualTo(OrderStatus.IMPORTED);
        assertThat(order.createdAt()).isEqualTo(createdAt);
        assertThat(order.totalAmount()).isEqualByComparingTo("73.99"); // 2 books + 1 USB cable = £73.99
        assertThat(order.items()).hasSize(2);
    }

    @Test
    void toResponseEchoesItems() {
        //checkoing response accurately represents complete order
        Order order = mapper.toDomain(sampleRequest(), "11111111-1111-4111-8111-111111111117", createdAt);

        OrderResponse response = mapper.toResponse(order);

        assertThat(response.orderId()).isEqualTo("11111111-1111-4111-8111-111111111117");
        assertThat(response.customerId()).isEqualTo("11111111-2222-3444-5666-777777777777");
        assertThat(response.status()).isEqualTo(OrderStatus.IMPORTED);
        assertThat(response.totalAmount()).isEqualByComparingTo("73.99");
        assertThat(response.items())
                .extracting("sku")
                .containsExactly("BOOK-1", "USB-CABLE");
    }
}
