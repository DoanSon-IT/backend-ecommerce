package com.sondv.phone.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.*;

@Configuration
public class WebConfig {

    @Bean
    public WebMvcConfigurer corsConfigurer(@Value("${frontend.url:http://localhost:3000}") String frontendUrl) {
        System.out.println("✅ CORS allowed origin: " + frontendUrl);

        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/**") // có thể để /api/** nếu bạn muốn giới hạn
                        .allowedOrigins(frontendUrl)
                        .allowedMethods("*")
                        .allowedHeaders("*")
                        .exposedHeaders("*")
                        .allowCredentials(true);
            }
        };
    }
}
