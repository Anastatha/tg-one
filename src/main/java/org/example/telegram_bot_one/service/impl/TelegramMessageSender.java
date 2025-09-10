package org.example.telegram_bot_one.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.example.telegram_bot_one.bot.model.MessageTask;
import org.example.telegram_bot_one.service.IMessageSender;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.exceptions.TelegramApiRequestException;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class TelegramMessageSender implements IMessageSender {

    private final TelegramClient telegramClient;
    private final FloodBlockService floodBlockService;

    public TelegramMessageSender(TelegramClient telegramClient,
                                 FloodBlockService floodBlockService) {
        this.telegramClient = telegramClient;
        this.floodBlockService = floodBlockService;
    }

    @Override
    public boolean sendTask(MessageTask task) {
        return switch (task.type) {
            case TEXT -> sendText(task);
            case PHOTO -> sendPhoto(task);
        };
    }

    private boolean sendText(MessageTask task) {
        SendMessage msg = SendMessage.builder()
                .chatId(task.chatId.toString())
                .text(task.text)
                .build();

        if (task.replyMarkup != null) msg.setReplyMarkup(task.replyMarkup);
        if (task.replyKeyboardMarkup != null) msg.setReplyMarkup(task.replyKeyboardMarkup);

        try {
            telegramClient.execute(msg);
            log.info("Sent text to chatId={}", task.chatId);
            return true;
        } catch (TelegramApiRequestException e) {
            handleRateLimit(e, task);
            return false;
        } catch (Exception e) {
            log.error("SendText failed for chatId={}", task.chatId, e);
            return false;
        }
    }

    private boolean sendPhoto(MessageTask task) {
        try (InputStream in = new URL(task.photoUrl).openStream()) {
            SendPhoto photo = SendPhoto.builder()
                    .chatId(task.chatId.toString())
                    .caption(task.text)
                    .photo(new InputFile(in, "image.jpg"))
                    .build();

            if (task.replyMarkup instanceof InlineKeyboardMarkup ik) {
                photo.setReplyMarkup(ik);
            }

            telegramClient.execute(photo);
            log.info("Sent photo to chatId={}", task.chatId);
            return true;
        } catch (TelegramApiRequestException e) {
            handleRateLimit(e, task);
            return false;
        } catch (Exception e) {
            log.error("SendPhoto failed for chatId={}", task.chatId, e);
            return false;
        }
    }

    private void handleRateLimit(TelegramApiRequestException e, MessageTask task) {
        String response = e.getApiResponse();

        if (response != null && response.contains("429")) {
            int retrySeconds = 0;
            try {
                Matcher m = Pattern.compile("(retry after|FLOOD_WAIT_)\\s*(\\d+)").matcher(response);
                if (m.find()) {
                    retrySeconds = Integer.parseInt(m.group(2));
                }
            } catch (Exception ignored) {
            }

            log.warn("Telegram global FLOOD_WAIT={}s, pausing bot", retrySeconds);

            // Сообщаем сервису, что бот в блоке
            floodBlockService.blockGlobally(retrySeconds);
        } else {
            log.error("Telegram API error for chatId={}: {}", task.chatId, e.getMessage(), e);
        }
    }

}
