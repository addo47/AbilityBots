package org.telegram.abilitybots.api.bot;

import org.apache.commons.io.IOUtils;
import org.telegram.abilitybots.api.db.DBContext;
import org.telegram.abilitybots.api.objects.*;
import org.telegram.abilitybots.api.sender.MessageSender;
import org.telegram.abilitybots.api.sender.DefaultMessageSender;
import org.telegram.abilitybots.api.util.Pair;
import org.telegram.abilitybots.api.util.Trio;
import org.telegram.telegrambots.api.methods.GetFile;
import org.telegram.telegrambots.api.methods.send.SendDocument;
import org.telegram.telegrambots.api.methods.send.SendMessage;
import org.telegram.telegrambots.api.objects.Message;
import org.telegram.telegrambots.api.objects.Update;
import org.telegram.telegrambots.api.objects.User;
import org.telegram.telegrambots.api.objects.replykeyboard.ForceReplyKeyboard;
import org.telegram.telegrambots.bots.DefaultBotOptions;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.exceptions.TelegramApiException;
import org.telegram.telegrambots.logging.BotLogger;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static java.lang.String.format;
import static java.time.ZonedDateTime.now;
import static java.util.Arrays.stream;
import static java.util.Objects.nonNull;
import static java.util.Optional.ofNullable;
import static java.util.function.Function.identity;
import static java.util.regex.Pattern.CASE_INSENSITIVE;
import static java.util.regex.Pattern.compile;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static jersey.repackaged.com.google.common.base.Throwables.propagate;
import static org.telegram.abilitybots.api.db.MapDBContext.onlineInstance;
import static org.telegram.abilitybots.api.objects.Ability.builder;
import static org.telegram.abilitybots.api.objects.EndUser.fromUser;
import static org.telegram.abilitybots.api.objects.Flag.*;
import static org.telegram.abilitybots.api.objects.Locality.*;
import static org.telegram.abilitybots.api.objects.Privacy.*;

/**
 * @author Abbas Abou Daya
 * @version 1.0
 * @brief Parent bot abstract class that helps find and register Abilities found in the calling subclass
 * @date 18th of February, 2016
 */
public abstract class AbilityBot extends TelegramLongPollingBot {
  private static final String TAG = AbilityBot.class.getSimpleName();

  // DB objects
  public static final String ADMINS = "ADMINS";
  public static final String USERS = "USERS";
  public static final String BLACKLIST = "BLACKLIST";

  // Factory commands
  public static final String DEFAULT = "default";
  public static final String CLAIM = "claim";
  public static final String BAN = "ban";
  public static final String PROMOTE = "promote";
  public static final String DEMOTE = "demote";
  public static final String UNBAN = "unban";
  public static final String BACKUP = "backup";
  public static final String RECOVER = "recover";
  public static final String COMMANDS = "commands";
  public static final String RECOVERY_MESSAGE = "I am ready to receive the backup file. Please reply to this message with the backup file attached.";
  public static final String RECOVER_SUCCESS = "I have successfully recovered.";

  // DB and sender
  protected final DBContext db;
  protected MessageSender sender;

  // Bot token and username
  private final String botToken;
  private final String botUsername;

  // Command registry
  private Map<String, Ability> abilities;
  // Reply registry
  private List<Reply> replies;

  protected AbilityBot(String botToken, String botUsername, DBContext db, DefaultBotOptions botOptions) {
    super(botOptions);

    this.botToken = botToken;
    this.botUsername = botUsername;
    this.db = db;
    this.sender = new DefaultMessageSender(this);

    registerAbilities();
  }

  protected AbilityBot(String botToken, String botUsername, DBContext db) {
    this(botToken, botUsername, db, new DefaultBotOptions());
  }

  protected AbilityBot(String botToken, String botUsername, DefaultBotOptions botOptions) {
    this(botToken, botUsername, onlineInstance(botUsername), botOptions);
  }

  protected AbilityBot(String botToken, String botUsername) {
    this(botToken, botUsername, onlineInstance(botUsername));
  }

  public static Predicate<Update> isReplyTo(String msg) {
    return update -> update.getMessage().getReplyToMessage().getText().equals(msg);
  }

  public abstract int creatorId();

  @Override
  public void onUpdateReceived(Update update) {
    BotLogger.info(format("New update [%s] received at %s", update.getUpdateId(), now()), TAG);
    BotLogger.info(update.toString(), TAG);
    long millisStarted = System.currentTimeMillis();

    Stream.of(update)
        .filter(this::checkGlobalFlags)
        .filter(this::checkBlacklist)
        .map(this::addUser)
        .filter(this::filterReply)
        .map(this::getAbility)
        .filter(this::validateAbility)
        .filter(this::checkMessageFlags)
        .filter(this::checkPrivacy)
        .filter(this::checkLocality)
        .filter(this::checkInput)
        .map(this::getContext)
        .map(this::consumeUpdate)
        .forEach(this::postConsumption);

    long processingTime = System.currentTimeMillis() - millisStarted;
    BotLogger.info(format("Processing of update [%s] ended at %s%n---> Processing time: [%d ms] <---%n", update.getUpdateId(), now(), processingTime), TAG);
  }

  @Override
  public String getBotToken() {
    return botToken;
  }

  @Override
  public String getBotUsername() {
    return botUsername;
  }

  public Ability reportCommands() {
    return builder()
        .name(COMMANDS)
        .locality(ALL)
        .privacy(PUBLIC)
        .input(0)
        .action(ctx -> {
          String commands = abilities.entrySet().stream()
              .filter(entry -> nonNull(entry.getValue().info()))
              .map(entry -> {
                String name = entry.getValue().name();
                String info = entry.getValue().info();
                return format("%s - %s", name, info);
              })
              .sorted()
              .reduce((a, b) -> format("%s%n%s", a, b)).orElse("No public commands found.");
          sender.send(commands, ctx.chatId());
        })
        .build();
  }

  public Ability recoverDB() {
    return builder()
        .name(RECOVER)
        .locality(USER)
        .privacy(CREATOR)
        .input(0)
        .action(ctx -> {
          SendMessage message = new SendMessage();
          message.setChatId(ctx.chatId());
          message.setText(RECOVERY_MESSAGE);
          message.setReplyMarkup(new ForceReplyKeyboard());

          sender.sendMessage(message);
        })
        .reply(update -> {
          Long chatId = update.getMessage().getChatId();
          String fileId = update.getMessage().getDocument().getFileId();

          try (FileReader reader = new FileReader(downloadFileWithId(fileId))) {
            String backupData = IOUtils.toString(reader);
            if (db.recover(backupData)) {
              sender.send(RECOVER_SUCCESS, chatId);
            } else {
              sender.send("Oops, something went wrong during recovery.", chatId);
            }
          } catch (Exception e) {
            BotLogger.error("Could not recover DB from backup", TAG, e);
            sender.send("I have failed to recover.", chatId);
          }
        }, MESSAGE, DOCUMENT, REPLY, isReplyTo(RECOVERY_MESSAGE))
        .build();
  }

  private File downloadFileWithId(String fileId) throws TelegramApiException {
    return sender.downloadFile(sender.getFile(new GetFile().setFileId(fileId)));
  }

  public Ability backupDB() {
    return builder()
        .name(BACKUP)
        .locality(USER)
        .privacy(CREATOR)
        .input(0)
        .action(ctx -> {
          File backup = new File("backup.json");

          try {
            PrintStream printStream = new PrintStream(backup);
            printStream.print(db.backup());
            sender.sendDocument(new SendDocument()
                .setNewDocument(backup)
                .setChatId(ctx.chatId())
            );
          } catch (FileNotFoundException e) {
            BotLogger.error("Error while fetching backup", TAG, e);
          } catch (TelegramApiException e) {
            BotLogger.error("Error while sending document/backup file", TAG, e);
          }
        })
        .build();
  }

  public Ability banUser() {
    return builder()
        .name(BAN)
        .locality(ALL)
        .privacy(ADMIN)
        .input(1)
        .action(ctx -> {
          String username = stripTag(ctx.firstArg());
          Optional<Integer> endUser = getUser(username).map(EndUser::id);

          endUser.ifPresent(user -> {
            String actualuser = username;
            // Protection from abuse
            if (user == creatorId()) {
              user = ctx.user().id();
              actualuser = getUser(user).get().firstName();
            }

            Set<Integer> blacklist = db.getSet(BLACKLIST);

            if (blacklist.contains(user))
              sender.sendFormatted(format("%s is already *banned*.", actualuser), ctx.chatId());
            else {
              blacklist.add(user);
              sender.sendFormatted(format("%s is now *banned*.", actualuser), ctx.chatId());
            }
          });
        })
        .post(commit())
        .build();
  }

  public Ability unbanUser() {
    return builder()
        .name(UNBAN)
        .locality(ALL)
        .privacy(ADMIN)
        .input(1)
        .action(ctx -> {
          String username = stripTag(ctx.firstArg());

          Optional<Integer> endUser = getUser(username).map(EndUser::id);
          endUser.ifPresent(user -> {
            Set<Integer> blacklist = db.getSet(BLACKLIST);

            if (!blacklist.contains(user))
              sender.sendFormatted(format("@%s is *not* on the *blacklist*.", username), ctx.chatId());
            else {
              blacklist.remove(user);
              sender.sendFormatted(format("@%s, your ban has been *lifted*.", username), ctx.chatId());
            }
          });
        })
        .post(commit())
        .build();
  }

  public Ability promoteAdmin() {
    return builder()
        .name(PROMOTE)
        .locality(ALL)
        .privacy(ADMIN)
        .input(1)
        .action(ctx -> {
          String username = stripTag(ctx.firstArg());
          Optional<Integer> endUserId = getUser(username).map(EndUser::id);

          endUserId.ifPresent(id -> {
            Set<Integer> superAdmins = db.getSet(ADMINS);
            if (superAdmins.contains(id))
              sender.sendFormatted(format("@%s is already a *super admin*.", username), ctx.chatId());
            else {
              superAdmins.add(id);
              sender.sendFormatted(format("@%s is now a *super admin*.", username), ctx.chatId());
            }
          });
        })
        .post(commit())
        .build();
  }

  public Ability demoteAdmin() {
    return builder()
        .name(DEMOTE)
        .locality(ALL)
        .privacy(ADMIN)
        .input(1)
        .action(ctx -> {
          String username = stripTag(ctx.firstArg());

          Optional<Integer> endUserId = getUser(username).map(EndUser::id);

          endUserId.ifPresent(id -> {
            Set<Integer> superAdmins = db.getSet(ADMINS);
            if (!superAdmins.contains(id)) {
              sender.sendFormatted(format("@%s is *not* a *super admin*.", username), ctx.chatId());
            } else {
              superAdmins.remove(id);
              sender.sendFormatted(format("@%s has been *demoted*.", username), ctx.chatId());
            }
          });
        })
        .post(commit())
        .build();
  }

  public Ability claimCreator() {
    return builder()
        .name(CLAIM)
        .locality(ALL)
        .privacy(PUBLIC)
        .input(0)
        .action(ctx -> {
          if (ctx.user().id() == creatorId()) {
            Set<Integer> superadmins = db.getSet(ADMINS);
            setMaster(superadmins, creatorId(), ctx.chatId());
          } else {
            // This is not a joke
            abilities.get(BAN).consumer().accept(new MessageContext(ctx.update(), ctx.user(), ctx.chatId(), ctx.user().username()));
          }
        })
        .post(commit())
        .build();
  }

  private void registerAbilities() {
    try {
      abilities = stream(this.getClass().getMethods())
          .filter(method -> method.getReturnType().equals(Ability.class))
          .map(this::invokeMethod)
          .collect(toMap(Ability::name, identity()));

      replies = abilities.values().stream()
          .flatMap(ability -> ability.replies().stream())
          .collect(toList());

    } catch (IllegalStateException e) {
      BotLogger.error(TAG, "Duplicate names found while registering abilities. Make sure that the abilities declared don't clash with the reserved ones.", e);
      throw propagate(e);
    }

  }

  private Ability invokeMethod(Method method) {
    try {
      return (Ability) method.invoke(this);
    } catch (IllegalAccessException | InvocationTargetException e) {
      BotLogger.error("Could not add ability", TAG, e);
      throw propagate(e);
    }
  }

  private String stripTag(String name) {
    String username = name.toLowerCase();
    username = username.startsWith("@") ? username.substring(1, username.length()) : username;
    return username;
  }

  private void setMaster(Set<Integer> admins, int id, long chatId) {
    if (admins.contains(id))
      sender.send("You're already my master.", chatId);
    else {
      admins.add(id);
      sender.send("You're now my master.", chatId);
    }
  }

  private Consumer<MessageContext> commit() {
    return ctx -> db.commit();
  }

  private Optional<EndUser> getUser(String username) {
    return db.<EndUser>getSet(USERS).stream().filter(user -> user.username().equalsIgnoreCase(username)).findFirst();
  }

  private Optional<EndUser> getUser(int id) {
    return db.<EndUser>getSet(USERS).stream().filter(user -> user.id() == id).findFirst();
  }

  private void postConsumption(Pair<MessageContext, Ability> pair) {
    ofNullable(pair.b().postConsumer())
        .ifPresent(consumer -> consumer.accept(pair.a()));
  }

  Pair<MessageContext, Ability> consumeUpdate(Pair<MessageContext, Ability> pair) {
    pair.b().consumer().accept(pair.a());
    return pair;
  }

  Pair<MessageContext, Ability> getContext(Trio<Update, Ability, String[]> trio) {
    Update update = trio.a();
    EndUser user = fromUser(getUser(update));

    return Pair.of(new MessageContext(update, user, getChatId(update), trio.c()), trio.b());
  }

  boolean checkBlacklist(Update update) {
    Integer id = getUser(update).getId();

    return id == creatorId() || !db.<Integer>getSet(BLACKLIST).contains(id);
  }

  User getUser(Update update) {
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

  Long getChatId(Update update) {
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

  boolean isUserMessage(Update update) {
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

  boolean checkInput(Trio<Update, Ability, String[]> trio) {
    String[] tokens = trio.c();
    int abilityTokens = trio.b().tokens();

    return abilityTokens == 0 || (tokens.length > 0 && tokens.length == abilityTokens);
  }

  boolean checkLocality(Trio<Update, Ability, String[]> trio) {
    Update update = trio.a();
    Locality locality = isUserMessage(update) ? USER : GROUP;
    Locality abilityLocality = trio.b().locality();
    return abilityLocality == ALL || locality == abilityLocality;
  }

  boolean checkPrivacy(Trio<Update, Ability, String[]> trio) {
    Update update = trio.a();
    EndUser user = fromUser(getUser(update));
    Privacy privacy;
    int id = user.id();

    privacy = isCreator(id) ? CREATOR : isAdmin(id) ? ADMIN : PUBLIC;

    return privacy.compareTo(trio.b().privacy()) >= 0;
  }

  private boolean isCreator(int id) {
    return id == creatorId();
  }

  private boolean isAdmin(Integer id) {
    return db.<Integer>getSet(ADMINS).contains(id);
  }

  boolean validateAbility(Trio<Update, Ability, String[]> trio) {
    return trio.b() != null;
  }

  Trio<Update, Ability, String[]> getAbility(Update update) {
    // Handle updates without messages
    // Passing through this function means that the global flags have passed
    Message msg = update.getMessage();
    if (!update.hasMessage() || !msg.hasText())
      return Trio.of(update, abilities.get(DEFAULT), new String[]{});

    // Priority goes to text before captions
    String[] tokens = msg.getText().split(" ");

    if (tokens[0].startsWith("/")) {
      String abilityToken = stripBotUsername(tokens[0].substring(1));
      Ability ability = abilities.get(abilityToken);
      tokens = Arrays.copyOfRange(tokens, 1, tokens.length);
      return Trio.of(update, ability, tokens);
    } else {
      Ability ability = abilities.get(DEFAULT);
      return Trio.of(update, ability, tokens);
    }
  }

  private String stripBotUsername(String token) {
    return compile(format("@%s", botUsername), CASE_INSENSITIVE)
        .matcher(token)
        .replaceAll("");
  }

  Update addUser(Update update) {
    EndUser endUser = fromUser(getUser(update));
    Set<EndUser> set = db.getSet(USERS);

    Optional<EndUser> optUser = set.stream().filter(user -> user.id() == endUser.id()).findAny();
    if (!optUser.isPresent()) {
      set.add(endUser);
      db.commit();
      return update;
    } else if (!optUser.get().equals(endUser)) {
      set.remove(optUser.get());
      set.add(endUser);
      db.commit();
    }

    return update;
  }

  boolean filterReply(Update update) {
    return replies.stream()
        .filter(reply -> reply.isOkFor(update))
        .map(reply -> {
          reply.actOn(update);
          return false;
        }).reduce(true, Boolean::logicalAnd);
  }

  boolean checkMessageFlags(Trio<Update, Ability, String[]> trio) {
    Ability ability = trio.b();
    Update update = trio.a();

    return ability.flags().stream()
        .map(flag -> flag.test(update))
        .reduce(true, Boolean::logicalAnd);
  }

  protected boolean checkGlobalFlags(Update update) {
    return MESSAGE.test(update);
  }
}