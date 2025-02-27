package com.insy2s.mskeycloak;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

@Slf4j
@EnableDiscoveryClient
@SpringBootApplication
@RequiredArgsConstructor
@EnableFeignClients
public class KeyCloakAuthServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(KeyCloakAuthServiceApplication.class, args);
    }



}
