package com.guru.seat_inventory_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients
public class SeatInventoryServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(SeatInventoryServiceApplication.class, args);
	}

}
