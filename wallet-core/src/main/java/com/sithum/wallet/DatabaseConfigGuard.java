package com.sithum.wallet;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.boot.autoconfigure.jdbc.JdbcConnectionDetails;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Fails startup with a clear message when {@code DB_URL}, {@code DB_USER} or {@code DB_PASSWORD}
 * is missing. {@code application.yml} deliberately has no defaults for them, but Spring Boot leaves
 * an unresolved {@code ${DB_URL}} in place as literal text, which would otherwise surface later as
 * an obscure JDBC error. The check runs before the {@code dataSource} bean is created.
 */
@Configuration(proxyBeanMethods = false)
class DatabaseConfigGuard {

    private static final Pattern UNRESOLVED = Pattern.compile("\\$\\{(\\w+)}");

    @Bean
    static BeanFactoryPostProcessor databaseConfigBeforeDataSource() {
        return beanFactory -> {
            if (beanFactory.containsBeanDefinition("dataSource")) {
                BeanDefinition dataSource = beanFactory.getBeanDefinition("dataSource");
                List<String> dependsOn = new ArrayList<>(Arrays.asList(
                        dataSource.getDependsOn() == null ? new String[0] : dataSource.getDependsOn()));
                dependsOn.add("databaseConfigCheck");
                dataSource.setDependsOn(dependsOn.toArray(String[]::new));
            }
        };
    }

    @Bean
    Object databaseConfigCheck(JdbcConnectionDetails details) {
        Set<String> missing = new LinkedHashSet<>();
        for (String value : new String[] {details.getJdbcUrl(), details.getUsername(), details.getPassword()}) {
            Matcher m = UNRESOLVED.matcher(value == null ? "" : value);
            while (m.find()) {
                missing.add(m.group(1));
            }
        }
        if (!missing.isEmpty()) {
            throw new IllegalStateException("Missing required environment variable(s): "
                    + String.join(", ", missing)
                    + ". Set DB_URL, DB_USER and DB_PASSWORD, or run locally with the 'local' Spring profile "
                    + "(-Dspring-boot.run.profiles=local).");
        }
        return new Object();
    }
}
