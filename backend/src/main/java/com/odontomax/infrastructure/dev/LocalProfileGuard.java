package com.odontomax.infrastructure.dev;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SOLO DESARROLLO LOCAL. Con el perfil "local" la aplicación siembra usuarios con contraseñas conocidas,
 * así que se niega a arrancar si la base de datos (o la de Flyway) no está en localhost.
 * Se ejecuta como BeanFactoryPostProcessor: antes de crear el DataSource y antes de que Flyway migre.
 */
@Component
@Profile("local")
public class LocalProfileGuard implements BeanFactoryPostProcessor, EnvironmentAware {

    private static final Set<String> HOSTS_LOCALES = Set.of("localhost", "127.0.0.1", "[::1]", "::1");
    private static final Pattern HOST_JDBC = Pattern.compile("^jdbc:postgresql://([^/:?]+|\\[[^\\]]+\\])");

    private Environment environment;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
        verificarLocal("spring.datasource.url", environment.getProperty("spring.datasource.url"));
        verificarLocal("spring.flyway.url", environment.getProperty("spring.flyway.url"));
    }

    static void verificarLocal(String propiedad, String url) {
        if (url == null || url.isBlank()) {
            return; // Sin URL propia, Flyway usa la del DataSource (ya verificada)
        }
        Matcher m = HOST_JDBC.matcher(url.trim().toLowerCase(Locale.ROOT));
        if (!m.find() || !HOSTS_LOCALES.contains(m.group(1))) {
            throw new IllegalStateException("El perfil 'local' solo puede usarse contra una base de datos en localhost, "
                    + "pero " + propiedad + " apunta a otro host. Arranque abortado para no sembrar datos de prueba "
                    + "en una base real.");
        }
    }
}
