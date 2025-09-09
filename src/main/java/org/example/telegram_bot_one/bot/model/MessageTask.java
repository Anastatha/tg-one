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

    // Счётчик попыток отправки
    public AtomicInteger attempts = new AtomicInteger(0);
    // Время, когда задача может быть отправлена повторно 
    public Instant nextRetryTime = Instant.EPOCH;

    public InlineKeyboardMarkup replyMarkup;
    public ReplyKeyboardMarkup replyKeyboardMarkup;

    private MessageTask() {}

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final MessageTask task = new MessageTask();

        public Builder chatId(Long chatId) { task.chatId = chatId; return this; }
        public Builder text(String text) { task.text = text; return this; }
        public Builder photoUrl(String url) { task.photoUrl = url; task.type = MessageType.PHOTO; return this; }
        public Builder type(MessageType type) { task.type = type; return this; }
        public Builder replyMarkup(InlineKeyboardMarkup markup) { task.replyMarkup = markup; return this; }
        public Builder replyKeyboardMarkup(ReplyKeyboardMarkup markup) { task.replyKeyboardMarkup = markup; return this; }
        public MessageTask build() {
            if (task.type == null) task.type = task.photoUrl != null ? MessageType.PHOTO : MessageType.TEXT;
            return task;
        }
    }
}
