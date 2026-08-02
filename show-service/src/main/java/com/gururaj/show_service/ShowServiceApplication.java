package com.gururaj.show_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import com.gururaj.show_service.config.ApplicationConfig.ClientTimeoutProperties;

@SpringBootApplication
@EnableConfigurationProperties(ClientTimeoutProperties.class)
public class ShowServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(ShowServiceApplication.class, args);
	}

}
