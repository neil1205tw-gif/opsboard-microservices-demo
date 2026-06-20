package com.opsboard.ops.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    @Bean
    public RestClient incidentServiceRestClient(
            @Value("${incident-service.base-url}") String incidentServiceBaseUrl) {
        return RestClient.builder()
                .baseUrl(incidentServiceBaseUrl)
                .build();
    }
}
