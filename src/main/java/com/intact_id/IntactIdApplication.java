package com.intact_id;

import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableAsync
@EnableScheduling
@EnableFeignClients
public class IntactIdApplication {

	public static void main(String[] args) {
		SpringApplication.run(IntactIdApplication.class, args);
	}

}
