package com.guru.seat_inventory_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import com.guru.seat_inventory_service.config.SeatLockProperties;

@SpringBootApplication
@EnableFeignClients
@EnableConfigurationProperties(SeatLockProperties.class)
public class SeatInventoryServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(SeatInventoryServiceApplication.class, args);
	}

}
