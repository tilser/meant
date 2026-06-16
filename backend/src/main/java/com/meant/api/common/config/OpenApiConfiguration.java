package com.meant.api.common.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Meant API",
                version = "0.1.0",
                description = "Backend API for Meant merchant search and cart testing endpoints.",
                contact = @Contact(name = "Meant"),
                license = @License(name = "Proprietary")
        ),
        servers = {
                @Server(url = "/", description = "Current host")
        }
)
public class OpenApiConfiguration {
}
