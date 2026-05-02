package com.dlqrevive.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TemplateController.class)
class TemplateControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("GET /dlq/templates returns empty list when no templates exist")
    void getTemplates_empty() throws Exception {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), anyString())).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/dlq/templates"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    @DisplayName("POST /dlq/templates successfully saves first template")
    void saveTemplate_success() throws Exception {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), anyString())).thenReturn(0);
        when(jdbcTemplate.update(anyString(), any(), any(), any(), any(), any())).thenReturn(1);

        mockMvc.perform(post("/dlq/templates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"My Template\", \"expression\": \"$uppercase(status)\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("My Template"))
                .andExpect(jsonPath("$.expression").value("$uppercase(status)"));
    }

    @Test
    @DisplayName("POST /dlq/templates returns 402 if limit is reached")
    void saveTemplate_limitReached() throws Exception {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), anyString())).thenReturn(1);

        mockMvc.perform(post("/dlq/templates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"My Template\", \"expression\": \"$uppercase(status)\"}"))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.error").exists());
    }
}
