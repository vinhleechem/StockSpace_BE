package fu.stockspace.stockspace_be.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import fu.stockspace.stockspace_be.wallet.repository.TransactionRepository;
import fu.stockspace.stockspace_be.wallet.service.PayOsService;
import fu.stockspace.stockspace_be.wallet.service.WalletService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import vn.payos.model.webhooks.WebhookData;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PayOsCallbackControllerTest {

    private MockMvc mockMvc;

    @Mock
    private PayOsService payOsService;
    @Mock
    private WalletService walletService;
    @Mock
    private TransactionRepository transactionRepository;

    @InjectMocks
    private PayOsCallbackController payOsCallbackController;

    private ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(payOsCallbackController).build();
    }

    @Test
    void testWebhookPingFromPayOs() throws Exception {
        String json = "{\"code\":\"00\",\"desc\":\"success\",\"data\":{\"orderCode\":123,\"description\":\"Ma giao dich thu nghiem\"}}";

        mockMvc.perform(post("/api/auth/payos-webhook")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isOk());
    }

    @Test
    void testRealWebhookFromPayOs() throws Exception {
        WebhookData mockData = org.mockito.Mockito.mock(WebhookData.class);
        when(mockData.getOrderCode()).thenReturn(987654321L);
        when(payOsService.verifyWebhook(any())).thenReturn(mockData);
        when(transactionRepository.findByPaymentCode("987654321")).thenReturn(Optional.of(org.mockito.Mockito.mock(fu.stockspace.stockspace_be.wallet.entity.Transaction.class)));

        String json = "{\"code\":\"00\",\"desc\":\"success\",\"data\":{\"orderCode\":987654321,\"description\":\"Thanh toan nap vi\"}}";

        mockMvc.perform(post("/api/auth/payos-webhook")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isOk());

        org.mockito.Mockito.verify(walletService).processPayOsWebhook(mockData);
    }
}
