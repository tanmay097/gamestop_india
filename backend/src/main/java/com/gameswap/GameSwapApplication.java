package com.gameswap;

import com.gameswap.common.config.GameSwapProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(GameSwapProperties.class)
public class GameSwapApplication {

    public static void main(String[] args) {
        SpringApplication.run(GameSwapApplication.class, args);
    }
}
