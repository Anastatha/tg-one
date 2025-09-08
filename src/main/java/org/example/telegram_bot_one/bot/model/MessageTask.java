package org.example.telegram_bot_one.bot.model;

import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.example.telegram_bot_one.bot.enums.MessageType;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

public class MessageTask {
    public static final int MAX_ATTEMPTS = 5;

    public Long chatId;
    public String text;
    public String photoUrl;
    public MessageType type;
    public AtomicInteger attempts = new AtomicInteger(0);
    public Instant nextRetryTime = Instant.EPOCH; // когда можно отправлять задачу
    public InlineKeyboardMarkup replyMarkup;
    public ReplyKeyboardMarkup replyKeyboardMarkup;

    public MessageTask() {

    }

    public MessageTask(Long chatId, String text, String photoUrl, MessageType type) {
        this.chatId = chatId;
        this.text = text;
        this.photoUrl = photoUrl;
        this.type = type;
    }

    public MessageTask(Long chatId, String text, InlineKeyboardMarkup replyMarkup) {
        this.chatId = chatId;
        this.text = text;
        this.type = MessageType.TEXT;
        ;
        this.replyMarkup = replyMarkup;
    }

    public MessageTask(Long chatId, String text, ReplyKeyboardMarkup replyKeyboardMarkup) {
        this.chatId = chatId;
        this.text = text;
        this.type = MessageType.TEXT;
        this.replyKeyboardMarkup = replyKeyboardMarkup;
    }
}
