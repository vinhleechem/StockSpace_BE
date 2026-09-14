package fu.stockspace.stockspace_be.common.exception;

import fu.stockspace.stockspace_be.wms.dataexchange.catalog.CatalogImportController;
import fu.stockspace.stockspace_be.wms.dataexchange.catalog.CatalogImportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.mock.web.MockMultipartFile;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GlobalExceptionHandlerMultipartTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new CatalogImportController(org.mockito.Mockito.mock(CatalogImportService.class)))
                .setControllerAdvice(handler)
                .build();
    }

    @Test
    void returnsBadRequestWhenFilePartIsMissing() {
        var response = handler.handleInvalidMultipart(new MissingServletRequestPartException("file"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(ErrorCode.WMS_IMPORT_FILE_INVALID.name(), response.getBody().getCode());
    }

    @Test
    void returnsBadRequestWhenMultipartRequestCannotBeParsed() {
        var response = handler.handleInvalidMultipart(new MultipartException("invalid multipart request"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(ErrorCode.WMS_IMPORT_FILE_INVALID.name(), response.getBody().getCode());
    }

    @Test
    void returnsBadRequestWhenMediaTypeIsUnsupported() {
        var response = handler.handleUnsupportedMediaType(new HttpMediaTypeNotSupportedException("text/plain"));

        assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, response.getStatusCode());
        assertEquals("Unsupported media type", response.getBody().getMessage());
    }

    @Test
    void mapsWrongMultipartFieldToBadRequestAtTheHttpBoundary() throws Exception {
        MockMultipartFile wrongPart = new MockMultipartFile(
                "workbook", "catalog.xlsx", "application/octet-stream", new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/api/tenant/wms-data/catalog/imports/validate").file(wrongPart))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.WMS_IMPORT_FILE_INVALID.name()));
    }
}
