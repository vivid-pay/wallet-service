package com.sithum.wallet.accounts.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;

@SpringBootTest
@AutoConfigureMockMvc
@Import(WalletApiIntegrationTests.PostgresConfig.class)
class WalletApiIntegrationTests {

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

    @MockitoSpyBean
    WalletProfileRepository profiles;

    private MockHttpServletRequestBuilder create(UUID owner, String body) {
        MockHttpServletRequestBuilder request = post("/api/v1/wallets")
                .contentType(MediaType.APPLICATION_JSON).content(body);
        return owner == null ? request : request.header("X-User-Id", owner.toString());
    }

    private static String body(String currency) {
        return "{\"currency\":\"" + currency + "\"}";
    }

    private long walletAccounts() {
        return jdbc.queryForObject("select count(*) from ledger_account where type = 'WALLET'", Long.class);
    }

    @Test
    void createsAWalletAndReturns201WithLocationAndBody() throws Exception {
        UUID owner = UUID.randomUUID();

        mvc.perform(create(owner, body("LKR")))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/v1/wallets/")))
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.currency").value("LKR"))
                .andExpect(jsonPath("$.balance").value(0))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(result -> {
                    String id = com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.id");
                    assertThat(result.getResponse().getHeader("Location")).endsWith("/api/v1/wallets/" + id);
                    assertThat(jdbc.queryForObject("select balance from ledger_account where id = ?::uuid",
                            Long.class, id)).isZero();
                    assertThat(jdbc.queryForObject(
                            "select owner_id from wallet_profile where ledger_account_id = ?::uuid",
                            UUID.class, id)).isEqualTo(owner);
                });
    }

    private MockHttpServletRequestBuilder fetch(UUID owner, String id) {
        MockHttpServletRequestBuilder request = get("/api/v1/wallets/" + id);
        return owner == null ? request : request.header("X-User-Id", owner.toString());
    }

    private String createWallet(UUID owner, String currency) throws Exception {
        String json = mvc.perform(create(owner, body(currency))).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(json, "$.id");
    }

    @Test
    void getReturnsTheOwnersWalletWithItsBalanceInMinorUnits() throws Exception {
        UUID owner = UUID.randomUUID();
        String id = createWallet(owner, "LKR");
        jdbc.update("update ledger_account set balance = 123456 where id = ?::uuid", id);

        mvc.perform(fetch(owner, id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.currency").value("LKR"))
                .andExpect(jsonPath("$.balance").value(123456))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(content().string(containsString("\"balance\":123456,")));
    }

    @Test
    void getOfAMissingWalletIs404ProblemJson() throws Exception {
        mvc.perform(fetch(UUID.randomUUID(), UUID.randomUUID().toString()))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.correlationId").isNotEmpty());
    }

    @Test
    void getOfSomeoneElsesWalletIsIndistinguishableFromMissing() throws Exception {
        String id = createWallet(UUID.randomUUID(), "USD");
        String missing = UUID.randomUUID().toString();

        String notYours = mvc.perform(fetch(UUID.randomUUID(), id))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andReturn().getResponse().getContentAsString();
        String doesNotExist = mvc.perform(fetch(UUID.randomUUID(), missing)).andReturn().getResponse()
                .getContentAsString();

        assertThat(com.jayway.jsonpath.JsonPath.<String>read(notYours, "$.detail"))
                .isEqualTo(com.jayway.jsonpath.JsonPath.<String>read(doesNotExist, "$.detail"));
        assertThat(com.jayway.jsonpath.JsonPath.<String>read(notYours, "$.title"))
                .isEqualTo(com.jayway.jsonpath.JsonPath.<String>read(doesNotExist, "$.title"));
        assertThat(com.jayway.jsonpath.JsonPath.<Object>read(notYours, "$.detail").toString()).doesNotContain(id);
        assertThat(notYours).doesNotContain("owner");
    }

    @Test
    void getWithAMalformedIdIs400ProblemJson() throws Exception {
        for (String bad : new String[] {"not-a-uuid", "123", "00000000-0000-0000-0000-00000000000g"}) {
            mvc.perform(fetch(UUID.randomUUID(), bad))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.status").value(400));
        }
    }

    @Test
    void getWithoutUserIdIs401() throws Exception {
        mvc.perform(fetch(null, UUID.randomUUID().toString()))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void balanceIsAnIntegerNotADecimal() throws Exception {
        mvc.perform(create(UUID.randomUUID(), body("USD")))
                .andExpect(content().string(containsString("\"balance\":0")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("\"balance\":0."))));
    }

    @Test
    void secondWalletInTheSameCurrencyIs409ButOtherCurrencyAndOtherOwnerAreFine() throws Exception {
        UUID owner = UUID.randomUUID();
        mvc.perform(create(owner, body("USD"))).andExpect(status().isCreated());
        long before = walletAccounts();

        mvc.perform(create(owner, body("USD")))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.correlationId").isNotEmpty());
        assertThat(walletAccounts()).isEqualTo(before);

        mvc.perform(create(owner, body("EUR"))).andExpect(status().isCreated());
        mvc.perform(create(UUID.randomUUID(), body("USD"))).andExpect(status().isCreated());
    }

    @Test
    void concurrentCreatesProduceOneWalletAndOne409() throws Exception {
        UUID owner = UUID.randomUUID();
        long before = walletAccounts();
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                results.add(pool.submit(() -> {
                    go.await();
                    return mvc.perform(create(owner, body("EUR"))).andReturn().getResponse().getStatus();
                }));
            }
            go.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> f : results) {
                statuses.add(f.get());
            }
            assertThat(statuses).containsExactlyInAnyOrder(201, 409);
        } finally {
            pool.shutdownNow();
        }
        assertThat(jdbc.queryForObject("select count(*) from wallet_profile where owner_id = ? and currency = 'EUR'",
                Long.class, owner)).isEqualTo(1);
        assertThat(walletAccounts()).isEqualTo(before + 1);
    }

    @Test
    void failureWhileSavingTheProfileLeavesNoLedgerAccountBehind() throws Exception {
        long before = walletAccounts();
        doThrow(new IllegalStateException("boom")).when(profiles).saveAndFlush(any());

        mvc.perform(create(UUID.randomUUID(), body("USD"))).andExpect(status().isInternalServerError());

        assertThat(walletAccounts()).isEqualTo(before);
        Mockito.reset(profiles);
    }

    @Test
    void unsupportedOrMalformedCurrencyIs400ProblemJson() throws Exception {
        UUID owner = UUID.randomUUID();
        long before = walletAccounts();
        for (String bad : new String[] {"JPY", "lkr", "LK", "LKRX", "L1R", "", " "}) {
            mvc.perform(create(owner, body(bad)))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.status").value(400));
        }
        assertThat(walletAccounts()).isEqualTo(before);
    }

    @Test
    void missingNullOrMalformedBodyIs400() throws Exception {
        UUID owner = UUID.randomUUID();
        for (String bad : new String[] {"{}", "{\"currency\":null}", "{not json", ""}) {
            mvc.perform(create(owner, bad))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        }
    }

    @Test
    void missingOrInvalidUserIdIs401ProblemJson() throws Exception {
        long before = walletAccounts();
        mvc.perform(create(null, body("USD")))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.instance").value("/api/v1/wallets"));
        mvc.perform(create(null, body("USD")).header("X-User-Id", "not-a-uuid"))
                .andExpect(status().isUnauthorized());
        assertThat(walletAccounts()).isEqualTo(before);
    }
}
