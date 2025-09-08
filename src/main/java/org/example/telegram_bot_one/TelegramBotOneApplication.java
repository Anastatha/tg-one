package org.example.telegram_bot_one;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class TelegramBotOneApplication {

    public static void main(String[] args) {
        SpringApplication.run(TelegramBotOneApplication.class, args);
    }

}
