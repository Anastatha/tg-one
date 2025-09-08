package org.example.telegram_bot_one.bot;

import jakarta.annotation.PostConstruct;
import org.example.telegram_bot_one.bot.enums.BotCommand;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.example.telegram_bot_one.service.MessageQueueService;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

@Component
public class UpdateConsumer implements LongPollingSingleThreadUpdateConsumer {

    private final MessageQueueService messageQueue;

    public UpdateConsumer(MessageQueueService messageQueue) {
        this.messageQueue = messageQueue;
    }

//
//    @PostConstruct
//    public void init() {
//        testRateLimits();
//    }
//    public void testRateLimits() {
//        for (int i = 0; i < 10; i++) {
//            messageQueue.addTextMessage(257612150L, "Message " + i);
//            messageQueue.addTextMessage(257612150L, "Message " + i);
//        }
//    }

    private final Map<String, Consumer<Long>> textCommands = Map.of(
            BotCommand.START.getValue(), this::sendMainMenu,
            BotCommand.KEYBOARD.getValue(), this::sendReplyKeyboard,
            BotCommand.HELLO.getValue(), chatId -> sendMyName(chatId, null),
            BotCommand.IMAGE.getValue(), this::sendImage
    );

    private final Map<String, Consumer<CallbackQuery>> callbackCommands = Map.of(
            "my_name", cq -> sendMyName(cq.getFrom().getId(), cq.getFrom()),
            "random", cq -> sendRandom(cq.getFrom().getId()),
            "long_process", cq -> sendImage(cq.getFrom().getId())
    );

    @Override
    public void consume(Update update) {
        if (update.hasMessage()) {
            var messageText = update.getMessage().getText();
            var chatId = update.getMessage().getChatId();
            textCommands.getOrDefault(messageText, id -> messageQueue.addTextMessage(id, "Я вас не понимаю"))
                    .accept(chatId);
        } else if (update.hasCallbackQuery()) {
            var data = update.getCallbackQuery().getData();
            callbackCommands.getOrDefault(data, cq -> messageQueue.addTextMessage(cq.getFrom().getId(), "Неизвестная команда"))
                    .accept(update.getCallbackQuery());
        }
    }

    // Методы отправки сообщений
    private void sendReplyKeyboard(Long chatId) {
        List<KeyboardRow> rows = List.of(new KeyboardRow(BotCommand.HELLO.getValue(), BotCommand.IMAGE.getValue()));
        ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup(rows);
        markup.setResizeKeyboard(true);
        messageQueue.addTextMessage(chatId, "Выберите действие:", markup);
    }

    private void sendMainMenu(Long chatId) {
        var button1 = InlineKeyboardButton.builder().text("Как меня зовут?").callbackData("my_name").build();
        var button2 = InlineKeyboardButton.builder().text("Случайное число").callbackData("random").build();
        var button3 = InlineKeyboardButton.builder().text("Долгий процесс").callbackData("long_process").build();
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup(List.of(
                new InlineKeyboardRow(button1),
                new InlineKeyboardRow(button2),
                new InlineKeyboardRow(button3)
        ));
        messageQueue.addTextMessage(chatId, "Добро пожаловать! Выберите действие:", markup);
    }

    private void sendImage(Long chatId) {
        messageQueue.addPhotoMessage(chatId, "https://picsum.photos/200", "Ваша случайная картинка:");
    }

    private void sendRandom(Long chatId) {
        messageQueue.addTextMessage(chatId, "Ваше рандомное число: " + ThreadLocalRandom.current().nextInt());
    }

    private void sendMyName(Long chatId, User user) {
        if (user == null) {
            messageQueue.addTextMessage(chatId, "Привет!");
        } else {
            messageQueue.addTextMessage(chatId,
                    "Привет!\nВас зовут: %s\nВаш ник: @%s".formatted(user.getFirstName(), user.getUserName()));
        }
    }
}
