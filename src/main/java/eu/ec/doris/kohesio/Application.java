package eu.ec.doris.kohesio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class Application {
  private static final Logger logger = LoggerFactory.getLogger(eu.ec.doris.kohesio.Application.class);

  @Value("${server.port}")
  String port;

  public static void main(String[] args) {
    SpringApplication.run(eu.ec.doris.kohesio.Application.class, args);
  }

}
