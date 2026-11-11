package com.coffeul.common.error;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringJUnitWebConfig(GlobalExceptionHandlerTest.MvcConfig.class)
class GlobalExceptionHandlerTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void missingRouteReturnsCommonNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/unregistered-route"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("C004"));
    }

    @Test
    void missingStaticResourceReturnsCommonNotFound() throws Exception {
        mockMvc.perform(get("/assets/missing.css"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("C004"));
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableWebMvc
    @Import(GlobalExceptionHandler.class)
    static class MvcConfig implements WebMvcConfigurer {

        @Override
        public void addResourceHandlers(ResourceHandlerRegistry registry) {
            registry.addResourceHandler("/assets/**").addResourceLocations("classpath:/static/");
        }
    }
}
