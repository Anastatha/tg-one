package org.example.telegram_bot_one.bot;

import org.springframework.stereotype.Component;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.objects.Update;

@Component
public class UpdateConsumer implements LongPollingSingleThreadUpdateConsumer {

    private final BotCommandRegistry commandRegistry;

    public UpdateConsumer(BotCommandRegistry commandRegistry) {
        this.commandRegistry = commandRegistry;
    }

    @Override
    public void consume(Update update) {
        if (update.hasMessage()) {
            var messageText = update.getMessage().getText();
            var chatId = update.getMessage().getChatId();
            commandRegistry.getTextCommands
                    .getOrDefault(messageText, id -> commandRegistry.sendUnknownCommand(id))
                    .accept(chatId);
        } else if (update.hasCallbackQuery()) {
            var data = update.getCallbackQuery().getData();
            commandRegistry.getCallbackCommands
                    .getOrDefault(data, cq -> commandRegistry.sendUnknownCommand(cq.getFrom().getId()))
                    .accept(update.getCallbackQuery());
        }
    }
}
