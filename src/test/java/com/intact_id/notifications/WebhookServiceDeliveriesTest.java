package com.intact_id.notifications;

import com.intact_id.notifications.webhook.WebhookDelivery;
import com.intact_id.notifications.webhook.WebhookDelivery.DeliveryStatus;
import com.intact_id.notifications.webhook.WebhookDeliveryRepository;
import com.intact_id.util.exception.ApplicationExceptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebhookServiceDeliveriesTest {

    private static final String COMPANY_ID = "company-1";

    @Mock
    private WebhookDeliveryRepository webhookDeliveryRepository;

    @InjectMocks
    private WebhookService webhookService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void listDeliveries_returnsNewestFirstWithoutPayload() {
        WebhookDelivery delivery = delivery("d-1", DeliveryStatus.FAILED);
        when(webhookDeliveryRepository.findByCompanyId(eq(COMPANY_ID), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(delivery)));

        Page<WebhookDeliveryView> page = webhookService.listDeliveries(COMPANY_ID, null, PageRequest.of(2, 10));

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(webhookDeliveryRepository).findByCompanyId(eq(COMPANY_ID), pageable.capture());
        assertEquals(2, pageable.getValue().getPageNumber());
        assertEquals(10, pageable.getValue().getPageSize());
        assertEquals(Sort.Direction.DESC, pageable.getValue().getSort().getOrderFor("createdAt").getDirection());

        WebhookDeliveryView view = page.getContent().get(0);
        assertEquals("d-1", view.id());
        assertEquals("FAILED", view.status());
        assertEquals(502, view.httpStatusCode());
        assertEquals("verification.completed", view.eventType());
    }

    @Test
    void listDeliveries_filtersByStatusCaseInsensitively() {
        when(webhookDeliveryRepository.findByCompanyIdAndStatus(eq(COMPANY_ID), eq(DeliveryStatus.RETRYING), any(Pageable.class)))
                .thenReturn(Page.empty());

        webhookService.listDeliveries(COMPANY_ID, "retrying", PageRequest.of(0, 20));

        verify(webhookDeliveryRepository).findByCompanyIdAndStatus(eq(COMPANY_ID), eq(DeliveryStatus.RETRYING), any(Pageable.class));
        verify(webhookDeliveryRepository, never()).findByCompanyId(any(), any());
    }

    @Test
    void listDeliveries_rejectsUnknownStatus() {
        assertThrows(ApplicationExceptions.InvalidRequestException.class,
                () -> webhookService.listDeliveries(COMPANY_ID, "LOST", PageRequest.of(0, 20)));
    }

    @Test
    void summarizeDeliveries_countsEachStatusSinceTheGivenTime() {
        LocalDateTime since = LocalDateTime.of(2026, 11, 12, 10, 0);
        when(webhookDeliveryRepository.countByCompanyIdAndStatusAndCreatedAtAfter(COMPANY_ID, DeliveryStatus.DELIVERED, since)).thenReturn(40L);
        when(webhookDeliveryRepository.countByCompanyIdAndStatusAndCreatedAtAfter(COMPANY_ID, DeliveryStatus.FAILED, since)).thenReturn(3L);
        when(webhookDeliveryRepository.countByCompanyIdAndStatusAndCreatedAtAfter(COMPANY_ID, DeliveryStatus.RETRYING, since)).thenReturn(2L);
        when(webhookDeliveryRepository.countByCompanyIdAndStatusAndCreatedAtAfter(COMPANY_ID, DeliveryStatus.PENDING, since)).thenReturn(1L);

        WebhookDeliverySummary summary = webhookService.summarizeDeliveries(COMPANY_ID, since);

        assertEquals(new WebhookDeliverySummary(since, 46, 40, 3, 2, 1), summary);
    }

    private static WebhookDelivery delivery(String id, DeliveryStatus status) {
        WebhookDelivery delivery = new WebhookDelivery();
        delivery.setId(id);
        delivery.setCompanyId(COMPANY_ID);
        delivery.setEventType("verification.completed");
        delivery.setWebhookUrl("https://example.com/hooks");
        delivery.setPayload("{\"applicant\":\"secret\"}");
        delivery.setResponseBody("Bad Gateway");
        delivery.setHttpStatusCode(502);
        delivery.setStatus(status);
        delivery.setCreatedAt(LocalDateTime.of(2026, 11, 12, 14, 0));
        return delivery;
    }
}
