package fu.stockspace.stockspace_be.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Cấu hình OpenAPI (Swagger UI) hỗ trợ định dạng JWT Bearer Token để test API.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        final String securitySchemeName = "bearerAuth";
        return new OpenAPI()
                .info(new Info()
                        .title("StockSpace API Documentation")
                        .version("1.0")
                        .description("Tài liệu API cho hệ thống quản lý kho StockSpace. Hỗ trợ đầy đủ phân quyền Full RBAC."))
                .addSecurityItem(new SecurityRequirement().addList(securitySchemeName))
                .components(new Components()
                        .addSecuritySchemes(securitySchemeName,
                                new SecurityScheme()
                                        .name(securitySchemeName)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("Nhập chuỗi JWT access token của bạn vào đây (Không cần ghi chữ 'Bearer ' ở trước).")
                        ));
    }
}
