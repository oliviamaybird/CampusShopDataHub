package de.campusshop.cw1.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.campusshop.cw1.dto.OrderResponse;
import de.campusshop.cw1.dto.PartnerBuyer;
import de.campusshop.cw1.dto.PartnerItem;
import de.campusshop.cw1.dto.PartnerOrderRequest;
import de.campusshop.cw1.model.OrderStatus;
import de.campusshop.cw1.service.OrderService;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer tests for {@link OrderController} using {@link MockMvc} and a mocked service
 * (Week 6).
 *
 * <p>TODO (student): assert that a valid {@code POST /partner-orders} returns
 * {@code 201} and an invalid one returns {@code 400}.
 */
@WebMvcTest(OrderController.class)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockitoBean
    private OrderService service;

    private OrderResponse sampleResponse() {
        return new OrderResponse("order-1", "partner-1", "cust-alice", OrderStatus.IMPORTED,
                new BigDecimal("47.48"), Instant.parse("2026-05-01T12:00:00Z"), List.of());
    }

    private String validBody() throws Exception {
        return objectMapper.writeValueAsString // converts Java object to JSON string
                (new PartnerOrderRequest("partner-1",
                        new PartnerBuyer("11111111-2222-3444-5666-777777777777", "Olivia Bird", "omb7060@gmail.com"), // sample buyer details
                List.of(new PartnerItem("BOOK-1", "Book", 2, new BigDecimal("19.99"))), "demo"));
    }

    @Test
    void validPostReturns201() throws Exception{
        // TODO: stub service.importOrder(...), perform POST with a valid body, expect 201
        when(service.importOrder(any())).thenReturn(sampleResponse()); // when importOrder() is called, return sampleReponse
        mockMvc.perform(post("/partner-orders") // send simulated POST request
                        .contentType(MediaType.APPLICATION_JSON).content(validBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderId").value("order-1"));
    }

    @Test
    void invalidPostReturns400() throws Exception{
        // TODO: perform POST with an invalid body (e.g. empty items), expect 400
        mockMvc.perform(post("/partner-orders")
                        .contentType(MediaType.APPLICATION_JSON).content("{ not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }
}
