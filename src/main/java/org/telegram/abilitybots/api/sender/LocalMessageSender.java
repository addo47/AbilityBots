package org.telegram.abilitybots.api.sender;

import org.telegram.telegrambots.api.methods.send.SendMessage;
import org.telegram.telegrambots.api.objects.Message;
import org.telegram.telegrambots.bots.DefaultAbsSender;
import org.telegram.telegrambots.logging.BotLogger;

import java.util.Optional;

import static java.lang.String.format;
import static java.util.Optional.empty;

/**
 * Created by Addo on 2/9/2017.
 */
public class LocalMessageSender implements MessageSender {
    private static final String TAG = LocalMessageSender.class.getName();

    public LocalMessageSender(DefaultAbsSender sender) {
        // do nothing
    }

    @Override
    public Optional<Message> send(String message, long id) {
        BotLogger.info(TAG, format("Sending message [%s] to ID [%d]", message, id));
        return empty();
    }

    @Override
    public Optional<Message> sendFormatted(String message, long id) {
        BotLogger.info(TAG, format("Sending formatted message [%s] to ID [%d]", message, id));
        return empty();
    }

    @Override
    public Optional<Message> send(SendMessage message) {
        BotLogger.info(TAG, format("Sending SendMessage [%s]", message.toString()));
        return empty();
    }
}
