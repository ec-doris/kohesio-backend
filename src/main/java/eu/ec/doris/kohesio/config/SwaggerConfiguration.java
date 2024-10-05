package eu.ec.doris.kohesio.config;

import org.springframework.boot.context.event.ApplicationPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.stereotype.Component;

import java.util.Properties;

@Component
public class SwaggerConfiguration implements ApplicationListener<ApplicationPreparedEvent> {

    @Override
    public void onApplicationEvent(final ApplicationPreparedEvent event) {
        ConfigurableEnvironment environment = event.getApplicationContext().getEnvironment();
        Properties props = new Properties();
        props.put("springdoc.swagger-ui.path", "/api/swagger-ui.html");
        props.put("springdoc.api-docs.path", "/api/v3/api-docs");


        environment.getPropertySources()
                .addFirst(new PropertiesPropertySource("programmatically", props));
    }

}
