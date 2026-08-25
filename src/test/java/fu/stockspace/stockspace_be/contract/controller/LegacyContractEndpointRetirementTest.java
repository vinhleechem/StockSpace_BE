package fu.stockspace.stockspace_be.contract.controller;

import fu.stockspace.stockspace_be.contract.service.ContractService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LegacyContractEndpointRetirementTest {

    private MockMvc mockMvc;
    private UUID contractId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ContractController(mock(ContractService.class)))
                .build();
        contractId = UUID.randomUUID();
    }

    @Test
    void legacyHandoverAndRentalStateEndpointsAreAbsent() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.patch(
                        "/api/contracts/{id}/confirm-handover", contractId))
                .andExpect(status().isNotFound());
        mockMvc.perform(MockMvcRequestBuilders.post(
                        "/api/contracts/{id}/submit-online", contractId))
                .andExpect(status().isNotFound());
        mockMvc.perform(MockMvcRequestBuilders.post(
                        "/api/contracts/{id}/tenant-confirm", contractId))
                .andExpect(status().isNotFound());
        mockMvc.perform(MockMvcRequestBuilders.post(
                        "/api/contracts/{id}/tenant-report-failed", contractId))
                .andExpect(status().isNotFound());
        mockMvc.perform(MockMvcRequestBuilders.post(
                        "/api/contracts/{id}/owner-cancel", contractId))
                .andExpect(status().isNotFound());
        mockMvc.perform(MockMvcRequestBuilders.post(
                        "/api/contracts/{id}/tenant-respond-cancel", contractId))
                .andExpect(status().isNotFound());
    }

    @Test
    void disputeEndpointsAreNotMappedByTheContractController() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/api/disputes/mine"))
                .andExpect(status().isNotFound());
        mockMvc.perform(MockMvcRequestBuilders.get("/api/admin/disputes"))
                .andExpect(status().isNotFound());
    }
}
