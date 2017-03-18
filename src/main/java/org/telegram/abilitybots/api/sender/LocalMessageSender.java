package org.telegram.abilitybots.api.sender;

import org.telegram.telegrambots.api.methods.send.SendMessage;
import org.telegram.telegrambots.api.objects.Message;
import org.telegram.telegrambots.bots.DefaultAbsSender;
import org.telegram.telegrambots.logging.BotLogger;

import java.util.List;
import java.util.Optional;

import static com.google.common.collect.Lists.newArrayList;
import static java.lang.String.format;
import static java.util.Optional.empty;

/**
 * Created by Addo on 2/9/2017.
 */
public class LocalMessageSender implements MessageSender {
    private static final String TAG = LocalMessageSender.class.getName();
    private final List<String> log;

    public LocalMessageSender(DefaultAbsSender sender) {
        log = newArrayList();
    }

    @Override
    public Optional<Message> send(String message, long id) {
        log.add(message);
        BotLogger.info(TAG, format("Sending message [%s] to ID [%d]", message, id));
        return empty();
    }

    @Override
    public Optional<Message> sendFormatted(String message, long id) {
        log.add(message);
        BotLogger.info(TAG, format("Sending formatted message [%s] to ID [%d]", message, id));
        return empty();
    }

    @Override
    public Optional<Message> send(SendMessage message) {
        BotLogger.info(TAG, format("Sending SendMessage [%s]", message.toString()));
        return empty();
    }

    public List<String> log() {
        return log;
    }
}
