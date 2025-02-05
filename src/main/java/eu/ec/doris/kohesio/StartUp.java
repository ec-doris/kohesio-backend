package eu.ec.doris.kohesio;

import eu.ec.doris.kohesio.controller.FacetController;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class StartUp {

    @Value("${server.port}")
    String port;

    @Value("#{new Boolean('${kohesio.startup.initialize}')}")
    Boolean initialize;

    @Autowired
    FacetController facetController;

    @EventListener(ApplicationReadyEvent.class)
    public void initialize() throws Exception {
        if (initialize)
            facetController.initialize("en");
        System.out.println("Welcome to the Kohesio-backend!");
        System.out.println("Open swagger documentation at http://localhost:" + port + "/api/swagger-ui.html");
    }

}