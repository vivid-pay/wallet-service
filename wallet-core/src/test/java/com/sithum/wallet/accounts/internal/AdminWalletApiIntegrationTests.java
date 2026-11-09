package com.sithum.wallet.accounts.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.sithum.wallet.accounts.WalletNotFoundException;
import com.sithum.wallet.accounts.Wallets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;

@SpringBootTest
@AutoConfigureMockMvc
@Import(AdminWalletApiIntegrationTests.PostgresConfig.class)
class AdminWalletApiIntegrationTests {

    @TestConfiguration(proxyBeanMethods = false)
    static class PostgresConfig {
        @Bean
        @ServiceConnection
        PostgreSQLContainer<?> postgres() {
            return new PostgreSQLContainer<>("postgres:16-alpine");
        }
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    Wallets wallets;

    private String createWallet(UUID owner) throws Exception {
        String json = mvc.perform(post("/api/v1/wallets").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currency\":\"USD\"}").header("X-User-Id", owner.toString()))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.id");
    }

    private MockHttpServletRequestBuilder setStatus(UUID actor, String walletId, String body) {
        MockHttpServletRequestBuilder request = put("/api/v1/admin/wallets/" + walletId + "/status")
                .contentType(MediaType.APPLICATION_JSON).content(body);
        return actor == null ? request : request.header("X-User-Id", actor.toString());
    }

    private static String body(String status, String reason) {
        return "{\"status\":\"" + status + "\",\"reason\":\"" + reason + "\"}";
    }

    private List<Map<String, Object>> history(String walletId) {
        return jdbc.queryForList("select * from wallet_status_history where wallet_id = ?::uuid order by changed_at",
                walletId);
    }

    private String currentStatus(String walletId) {
        return jdbc.queryForObject("select status from wallet_profile where ledger_account_id = ?::uuid",
                String.class, walletId);
    }

    @Test
    void freezeAndUnfreezeChangeTheStatusReturnTheWalletAndRecordWhoWhenAndWhy() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID operator = UUID.randomUUID();
        String id = createWallet(owner);
        jdbc.update("update ledger_account set balance = 750 where id = ?::uuid", id);

        mvc.perform(setStatus(operator, id, body("FROZEN", "suspected fraud")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.balance").value(750))
                .andExpect(jsonPath("$.status").value("FROZEN"));
        assertThat(currentStatus(id)).isEqualTo("FROZEN");
        assertThat(wallets.isActive(UUID.fromString(id))).isFalse();

        mvc.perform(setStatus(operator, id, body("ACTIVE", "cleared after review")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        assertThat(currentStatus(id)).isEqualTo("ACTIVE");
        assertThat(wallets.isActive(UUID.fromString(id))).isTrue();

        List<Map<String, Object>> rows = history(id);
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0)).containsEntry("from_status", "ACTIVE").containsEntry("to_status", "FROZEN")
                .containsEntry("actor_id", operator).containsEntry("reason", "suspected fraud");
        assertThat(rows.get(0).get("changed_at")).isNotNull();
        assertThat(rows.get(1)).containsEntry("from_status", "FROZEN").containsEntry("to_status", "ACTIVE")
                .containsEntry("actor_id", operator).containsEntry("reason", "cleared after review");
    }

    @Test
    void repeatingTheCurrentStatusIs200AndChangesAndRecordsNothing() throws Exception {
        UUID operator = UUID.randomUUID();
        String id = createWallet(UUID.randomUUID());

        mvc.perform(setStatus(operator, id, body("ACTIVE", "already active")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIVE"));
        assertThat(history(id)).isEmpty();

        mvc.perform(setStatus(operator, id, body("FROZEN", "first freeze"))).andExpect(status().isOk());
        mvc.perform(setStatus(operator, id, body("FROZEN", "second freeze")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("FROZEN"));

        assertThat(currentStatus(id)).isEqualTo("FROZEN");
        assertThat(history(id)).hasSize(1).first().satisfies(row ->
                assertThat(row).containsEntry("reason", "first freeze"));
    }

    @Test
    void aMissingOrBlankReasonIs400AndRecordsNothing() throws Exception {
        String id = createWallet(UUID.randomUUID());
        for (String bad : new String[] {"{\"status\":\"FROZEN\"}", "{\"status\":\"FROZEN\",\"reason\":null}",
                body("FROZEN", ""), body("FROZEN", "   ")}) {
            mvc.perform(setStatus(UUID.randomUUID(), id, bad))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        }
        assertThat(currentStatus(id)).isEqualTo("ACTIVE");
        assertThat(history(id)).isEmpty();
    }

    @Test
    void anInvalidOrMissingStatusIs400() throws Exception {
        String id = createWallet(UUID.randomUUID());
        for (String bad : new String[] {body("SUSPENDED", "why"), body("frozen", "why"), body("", "why"),
                "{\"reason\":\"why\"}", "{\"status\":null,\"reason\":\"why\"}", "{not json"}) {
            mvc.perform(setStatus(UUID.randomUUID(), id, bad))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        }
        assertThat(history(id)).isEmpty();
    }

    @Test
    void aMissingWalletIs404() throws Exception {
        mvc.perform(setStatus(UUID.randomUUID(), UUID.randomUUID().toString(), body("FROZEN", "why")))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404));
        assertThatThrownBy(() -> wallets.isActive(UUID.randomUUID())).isInstanceOf(WalletNotFoundException.class);
    }

    @Test
    void aMalformedWalletIdIs400() throws Exception {
        mvc.perform(setStatus(UUID.randomUUID(), "not-a-uuid", body("FROZEN", "why")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void withoutAValidUserIdTheAnswerIs401AndNothingChanges() throws Exception {
        String id = createWallet(UUID.randomUUID());

        mvc.perform(setStatus(null, id, body("FROZEN", "why")))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401));
        mvc.perform(setStatus(null, id, body("FROZEN", "why")).header("X-User-Id", "not-a-uuid"))
                .andExpect(status().isUnauthorized());

        assertThat(currentStatus(id)).isEqualTo("ACTIVE");
        assertThat(history(id)).isEmpty();
    }

    @Test
    void theHistoryTableRejectsUpdatesAndDeletes() throws Exception {
        String id = createWallet(UUID.randomUUID());
        mvc.perform(setStatus(UUID.randomUUID(), id, body("FROZEN", "why"))).andExpect(status().isOk());

        assertThatThrownBy(() -> jdbc.update("update wallet_status_history set reason = 'edited' "
                + "where wallet_id = ?::uuid", id)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("delete from wallet_status_history where wallet_id = ?::uuid", id))
                .isInstanceOf(DataAccessException.class);
        assertThat(history(id)).hasSize(1).first().satisfies(row -> assertThat(row).containsEntry("reason", "why"));
    }
}
