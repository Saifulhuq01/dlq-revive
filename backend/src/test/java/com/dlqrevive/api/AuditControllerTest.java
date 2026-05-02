package com.dlqrevive.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuditController.class)
class AuditControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("GET /dlq/audit returns empty list when no audit entries exist")
    void getAuditTrail_empty() throws Exception {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenReturn(Collections.emptyList());

        mockMvc.perform(get("/dlq/audit"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    @DisplayName("GET /dlq/audit with topic filter passes filter to query")
    void getAuditTrail_withTopicFilter() throws Exception {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenReturn(Collections.emptyList());

        mockMvc.perform(get("/dlq/audit").param("topic", "payments.dlq"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));

        verify(jdbcTemplate).query(contains("topic = ?"), any(RowMapper.class), any(Object[].class));
    }

    @Test
    @DisplayName("GET /dlq/audit with action filter passes filter to query")
    void getAuditTrail_withActionFilter() throws Exception {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenReturn(Collections.emptyList());

        mockMvc.perform(get("/dlq/audit").param("action", "REDRIVE"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));

        verify(jdbcTemplate).query(contains("action = ?"), any(RowMapper.class), any(Object[].class));
    }
}
