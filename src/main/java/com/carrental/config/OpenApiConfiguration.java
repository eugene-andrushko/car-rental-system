package com.carrental.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class OpenApiConfiguration {

    @Bean
    OpenAPI carRentalOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Car Rental API")
                .description("Reserve a car of a given type for a number of days, and cancel the reservation")
                .version("1.0"));
    }
}
