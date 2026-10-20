package edu.austral.ingsis.snippethandler;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class SnippetHandlerApplication {

    public static void main(String[] args) {
        SpringApplication.run(SnippetHandlerApplication.class, args);
    }
}
