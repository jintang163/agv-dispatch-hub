package com.agv.dispatch.api.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("AGV调度中心 API")
                        .description("仓储AGV多机调度系统接口文档")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("AGV Dispatch Team")
                                .email("support@agv-dispatch.com")));
    }
}
