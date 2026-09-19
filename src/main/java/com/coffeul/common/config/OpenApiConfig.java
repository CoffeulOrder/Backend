package com.coffeul.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI coffeulOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Coffeul API")
                .description("학교 카페 원격 주문 서비스 백엔드 API")
                .version("v1"));
    }
}
