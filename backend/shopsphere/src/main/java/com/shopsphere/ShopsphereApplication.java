package com.shopsphere;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ShopsphereApplication {

	public static void main(String[] args) {
		SpringApplication.run(ShopsphereApplication.class, args);
	}

}
