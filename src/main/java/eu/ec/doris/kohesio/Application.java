package eu.ec.doris.kohesio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import springfox.documentation.swagger2.annotations.EnableSwagger2;

@EnableWebMvc
@EnableSwagger2
@SpringBootApplication
public class Application {
  private static final Logger logger = LoggerFactory.getLogger(eu.ec.doris.kohesio.Application.class);

  @Value("${server.port}")
  String port;

  public static void main(String[] args) {
    System.setProperty("https.protocols", "TLSv1,TLSv1.1,TLSv1.2");
    SpringApplication.run(eu.ec.doris.kohesio.Application.class, args);
  }

}
