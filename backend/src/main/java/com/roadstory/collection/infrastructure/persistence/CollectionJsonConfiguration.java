package com.roadstory.collection.infrastructure.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@Profile("!local")
class CollectionJsonConfiguration {
    @Bean
    ObjectMapper collectionObjectMapper() {
        return new ObjectMapper();
    }
}
