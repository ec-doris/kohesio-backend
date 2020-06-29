package eu.ec.doris.kohesio.controller.config;

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
        .paths(Predicates.not(PathSelectors.regex("/api/expansion.*")))
        .paths(Predicates.not(PathSelectors.regex("/api/connection.*")))
        .paths(Predicates.not(PathSelectors.regex("/api/qa/annotation.*")))
        .paths(Predicates.not(PathSelectors.regex("/api/chat/annotation.*")))
        .paths(Predicates.not(PathSelectors.regex("/api/link")))
        .paths(Predicates.not(PathSelectors.regex("/api/user/admin.*")))
        .paths(Predicates.not(PathSelectors.regex("/api/dataset/admin.*")))
        .build()
        .apiInfo(apiInfo());
  }

  private ApiInfo apiInfo() {
    return new ApiInfo(
        "Kohesio: Api Documentation",
        "APIs provided by QAnswer. Note: most APIs are secured",
        "1.0",
        "",
        null,
        null,
        null,
        Collections.emptyList());
  }
}
