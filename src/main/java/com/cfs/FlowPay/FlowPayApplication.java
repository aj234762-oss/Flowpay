package com.cfs.FlowPay;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
/*
WebHook - It is an event-driven HTTP callback, Instead of our application polling an external API
repeatedly to check for data changes , the external service pushes and HTTP POST request with a JSON
payload directly to your Spring boot backend the moment an event occurs.
 */
@SpringBootApplication
@EnableScheduling
public class FlowPayApplication {

	public static void main(String[] args) {
		SpringApplication.run(FlowPayApplication.class, args);
	}

}
