package com.microservicios.gestionempleados;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;

@SpringBootApplication
@EnableRetry
public class GestionempleadosApplication {

	public static void main(String[] args) {
		SpringApplication.run(GestionempleadosApplication.class, args);
	}

}
