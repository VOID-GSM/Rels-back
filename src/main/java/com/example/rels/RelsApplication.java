package com.example.rels;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class RelsApplication {

	public static void main(String[] args) {
		SpringApplication.run(RelsApplication.class, args);
	}

}
