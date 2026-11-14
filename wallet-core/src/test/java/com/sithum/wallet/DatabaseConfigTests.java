package com.sithum.wallet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.WebApplicationType;
import org.springframework.core.io.ClassPathResource;

/** Database settings come from the environment; local defaults live only in the `local` profile. */
class DatabaseConfigTests {

    private static final List<String> DB_KEYS =
            List.of("spring.datasource.url", "spring.datasource.username", "spring.datasource.password");

    private static Map<Object, Object> load(String resource) {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource(resource));
        return yaml.getObject();
    }

    @Test
    void baseConfigHasNoDefaultForDbUrlUserOrPassword() {
        Map<Object, Object> base = load("application.yml");
        assertThat(base.get("spring.datasource.url")).isEqualTo("${DB_URL}");
        assertThat(base.get("spring.datasource.username")).isEqualTo("${DB_USER}");
        assertThat(base.get("spring.datasource.password")).isEqualTo("${DB_PASSWORD}");
    }

    @Test
    void serviceFailsAtStartupNamingTheMissingVariable() {
        assertThatThrownBy(() -> new SpringApplicationBuilder(WalletApplication.class)
                        .web(WebApplicationType.NONE)
                        .properties("spring.profiles.active=", "spring.main.banner-mode=off")
                        .run())
                .hasStackTraceContaining("Missing required environment variable(s): DB_URL, DB_USER, DB_PASSWORD");
    }

    @Test
    void localProfileHoldsTheLocalDatabaseSettings() throws IOException {
        Map<Object, Object> local = load("application-local.yml");
        assertThat(local.keySet()).containsAll(DB_KEYS);
        assertThat((String) local.get("spring.datasource.url")).contains("localhost:5432/wallet-service");
    }
}
