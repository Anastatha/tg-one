package org.example.telegram_bot_one.bot;

import org.example.telegram_bot_one.bot.enums.BotCommand;
import org.example.telegram_bot_one.bot.model.MessageTask;
import org.example.telegram_bot_one.service.impl.MessageQueueService;
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
            textCommands.getOrDefault(messageText, id -> sendMessage(id, "Я вас не понимаю"))
                    .accept(chatId);
        } else if (update.hasCallbackQuery()) {
            var data = update.getCallbackQuery().getData();
            callbackCommands.getOrDefault(data, cq -> sendMessage(cq.getFrom().getId(), "Неизвестная команда"))
                    .accept(update.getCallbackQuery());

        }
    }

    private void sendMessage(Long chatId, String text) {
        messageQueue.addMessage(MessageTask.builder()
                .chatId(chatId)
                .text(text)
                .build());
    }

    private void sendReplyKeyboard(Long chatId) {
        List<KeyboardRow> rows = List.of(new KeyboardRow(BotCommand.HELLO.getValue(), BotCommand.IMAGE.getValue()));
        ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup(rows);
        markup.setResizeKeyboard(true);

        messageQueue.addMessage(MessageTask.builder()
                .chatId(chatId)
                .text("Выберите действие:")
                .replyKeyboardMarkup(markup)
                .build());
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

        messageQueue.addMessage(MessageTask.builder()
                .chatId(chatId)
                .text("Добро пожаловать! Выберите действие:")
                .replyMarkup(markup)
                .build());
    }

    private void sendImage(Long chatId) {
        messageQueue.addMessage(MessageTask.builder()
                .chatId(chatId)
                .text("Ваша случайная картинка:")
                .photoUrl("https://picsum.photos/200")
                .build());
    }

    private void sendRandom(Long chatId) {
        messageQueue.addMessage(MessageTask.builder()
                .chatId(chatId)
                .text("Ваше рандомное число: " + ThreadLocalRandom.current().nextInt())
                .build());
    }

    private void sendMyName(Long chatId, User user) {
        String text;
        if (user == null) {
            text = "Привет!";
        } else {
            text = "Привет!\nВас зовут: %s\nВаш ник: @%s".formatted(user.getFirstName(), user.getUserName());
        }

        messageQueue.addMessage(MessageTask.builder()
                .chatId(chatId)
                .text(text)
                .build());
    }
}
