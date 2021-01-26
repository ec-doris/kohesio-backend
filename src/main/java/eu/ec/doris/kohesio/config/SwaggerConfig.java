package eu.ec.doris.kohesio.config;

import com.google.common.base.Predicates;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Collections;

import springfox.documentation.builders.PathSelectors;
import springfox.documentation.builders.RequestHandlerSelectors;
import springfox.documentation.service.ApiInfo;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spring.web.plugins.Docket;
import springfox.documentation.swagger2.annotations.EnableSwagger2;

@Configuration
@EnableSwagger2
public class SwaggerConfig {

  @Value("${kohesio.publicUrl}")
  private String publicUrl;

  @Bean
  public Docket api() {
    return new Docket(DocumentationType.SWAGGER_2)
        .host(publicUrl)
        .select()
        .apis(RequestHandlerSelectors.any())
            .paths(Predicates.not(PathSelectors.regex("/error.*")))
            .paths(PathSelectors.any())
            .build()
        .apiInfo(apiInfo());
  }

  private ApiInfo apiInfo() {
    return new ApiInfo(
        "Kohesio: Api Documentation",
        "APIs provided by Kohesio. Note: most APIs are secured",
        "1.0",
        "",
        null,
        null,
        null,
        Collections.emptyList());
  }
}
