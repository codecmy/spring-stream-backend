package com.example.spring_stream_backend.config;

import com.example.spring_stream_backend.filter.HankoSessionFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class SecurityConfig {
    @Bean
    public FilterRegistrationBean<HankoSessionFilter> hankoSessionFilterRegistration(HankoSessionFilter filter) {
        FilterRegistrationBean<HankoSessionFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(filter);
        registration.setUrlPatterns(List.of("/secured/*", "/api/v1/videos", "/api/v1/videos/*"));
        registration.setOrder(1);
        return registration;
    }
}
