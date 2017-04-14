package org.telegram.abilitybots.api.util;

import org.telegram.abilitybots.api.db.DBContext;
import org.telegram.abilitybots.api.objects.MessageContext;
import org.telegram.telegrambots.api.objects.Update;
import org.telegram.telegrambots.api.objects.User;

import java.util.function.Consumer;

import static org.telegram.abilitybots.api.objects.Flag.*;

/**
 * Helper and utility methods
 */
public final class AbilityUtils {
  private AbilityUtils() {

  }

  /**
   * @param name
   * @return the name with the preceding "@" stripped off
   */
  public static String stripTag(String name) {
    String username = name.toLowerCase();
    username = username.startsWith("@") ? username.substring(1, username.length()) : username;
    return username;
  }

  /**
   * Commits to DB.
   */
  public static Consumer<MessageContext> commitTo(DBContext db) {
    return ctx -> db.commit();
  }

  /**
   * Fetches the user who caused the update.
   *
   * @param update a Telegram {@link Update}
   * @return the originating user
   * @throws IllegalStateException if the user could not be found
   */
  public static User getUser(Update update) {
    if (MESSAGE.test(update)) {
      return update.getMessage().getFrom();
    } else if (CALLBACK_QUERY.test(update)) {
      return update.getCallbackQuery().getFrom();
    } else if (INLINE_QUERY.test(update)) {
      return update.getInlineQuery().getFrom();
    } else if (CHANNEL_POST.test(update)) {
      return update.getChannelPost().getFrom();
    } else if (EDITED_CHANNEL_POST.test(update)) {
      return update.getEditedChannelPost().getFrom();
    } else if (EDITED_MESSAGE.test(update)) {
      return update.getEditedMessage().getFrom();
    } else if (CHOSEN_INLINE_QUERY.test(update)) {
      return update.getChosenInlineQuery().getFrom();
    } else {
      throw new IllegalStateException("Could not retrieve originating user from update");
    }
  }

  /**
   * Fetches the direct chat ID of the specified update.
   *
   * @param update a Telegram {@link Update}
   * @return the originating chat ID
   * @throws IllegalStateException if the chat ID could not be found
   */
  public static Long getChatId(Update update) {
    if (MESSAGE.test(update)) {
      return update.getMessage().getChatId();
    } else if (CALLBACK_QUERY.test(update)) {
      return update.getCallbackQuery().getMessage().getChatId();
    } else if (INLINE_QUERY.test(update)) {
      return (long) update.getInlineQuery().getFrom().getId();
    } else if (CHANNEL_POST.test(update)) {
      return update.getChannelPost().getChatId();
    } else if (EDITED_CHANNEL_POST.test(update)) {
      return update.getEditedChannelPost().getChatId();
    } else if (EDITED_MESSAGE.test(update)) {
      return update.getEditedMessage().getChatId();
    } else if (CHOSEN_INLINE_QUERY.test(update)) {
      return (long) update.getChosenInlineQuery().getFrom().getId();
    } else {
      throw new IllegalStateException("Could not retrieve originating chat ID from update");
    }
  }

  /**
   * @param update a Telegram {@link Update}
   * @return <tt>true</tt> if the update contains contains a private user message
   */
  public static boolean isUserMessage(Update update) {
    if (MESSAGE.test(update)) {
      return update.getMessage().isUserMessage();
    } else if (CALLBACK_QUERY.test(update)) {
      return update.getCallbackQuery().getMessage().isUserMessage();
    } else if (CHANNEL_POST.test(update)) {
      return update.getChannelPost().isUserMessage();
    } else if (EDITED_CHANNEL_POST.test(update)) {
      return update.getEditedChannelPost().isUserMessage();
    } else if (EDITED_MESSAGE.test(update)) {
      return update.getEditedMessage().isUserMessage();
    } else if (CHOSEN_INLINE_QUERY.test(update) || INLINE_QUERY.test(update)) {
      return true;
    } else {
      throw new IllegalStateException("Could not retrieve update context origin (user/group)");
    }
  }

  /**
   * @return the username prefixed with the "@" tag.
   */
  public static String addTag(String username) {
    return "@" + username;
  }
}
