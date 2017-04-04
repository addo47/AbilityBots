package org.telegram.abilitybots.api.bot;

import org.apache.commons.io.IOUtils;
import org.telegram.abilitybots.api.db.DBContext;
import org.telegram.abilitybots.api.objects.*;
import org.telegram.abilitybots.api.sender.MessageSender;
import org.telegram.abilitybots.api.sender.MessageSenderImpl;
import org.telegram.abilitybots.api.util.Pair;
import org.telegram.abilitybots.api.util.Trio;
import org.telegram.telegrambots.api.methods.GetFile;
import org.telegram.telegrambots.api.methods.send.SendDocument;
import org.telegram.telegrambots.api.objects.Message;
import org.telegram.telegrambots.api.objects.Update;
import org.telegram.telegrambots.api.objects.User;
import org.telegram.telegrambots.bots.DefaultAbsSender;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.exceptions.TelegramApiException;
import org.telegram.telegrambots.logging.BotLogger;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static java.lang.String.format;
import static java.time.ZonedDateTime.now;
import static java.util.Arrays.stream;
import static java.util.Objects.nonNull;
import static java.util.Optional.ofNullable;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;
import static jersey.repackaged.com.google.common.base.Throwables.propagate;
import static org.telegram.abilitybots.api.db.MapDBContext.onlineInstance;
import static org.telegram.abilitybots.api.objects.Ability.builder;
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
    public static final String SUPER_ADMINS = "SUPER_ADMINS";
    public static final String ADMINS = "ADMINS";
    public static final String USERS = "USERS";
    public static final String BLACKLIST = "BLACKLIST";

    // Factory commands
    public static final String DEFAULT = "default";
    public static final String CLAIM = "claim";
    public static final String BAN = "ban";
    public static final String SUDO = "sudo";
    public static final String SUDONT = "sudont";
    public static final String PROMOTE = "promote";
    public static final String DEMOTE = "demote";
    public static final String UNBAN = "unban";

    protected final DBContext db;
    protected MessageSender sender;

    private final String botToken;
    private final String botUsername;

    // Command registry
    private Map<String, Ability> abilities;

    public AbilityBot(String botToken, String botUsername, DBContext db, Class<? extends MessageSender> clazz) {
        this.botToken = botToken;
        this.botUsername = botUsername;
        this.db = db;

        try {
            this.sender = clazz.getConstructor(DefaultAbsSender.class).newInstance(this);
        } catch (Exception e) {
            BotLogger.error(TAG, "Couldn't construct MessageSender. If this is a custom implementation, make sure to have a constructor that requires a DefaultAbsSender instance.", e);
            throw propagate(e);
        }

        registerAbilities(this);
    }

    public AbilityBot(String token, String username) {
        this(token, username, onlineInstance(), MessageSenderImpl.class);
    }

    public abstract int creatorId();

    @Override
    public void onUpdateReceived(Update update) {
        BotLogger.info(format("New update [%s] received at %s", update.getUpdateId(), now()), TAG);
        BotLogger.info(update.toString(), TAG);

        Stream.of(update)
                .filter(this::checkGlobalFlags)
                .filter(this::checkBlacklist)
                .map(this::addUser)
                .map(this::getAbility)
                .filter(this::validateAbility)
                .filter(this::checkMessageFlags)
                .filter(this::checkPrivacy)
                .filter(this::checkLocality)
                .filter(this::checkInput)
                .map(this::getContext)
                .map(this::consumeUpdate)
                .forEach(this::postConsumption);

        BotLogger.info(format("Processing of update [%s] ended at %s", update.getUpdateId(), now()), TAG);
    }

    @Override
    public String getBotToken() {
        return botToken;
    }

    @Override
    public String getBotUsername() {
        return botUsername;
    }

    public Ability commands() {
        return builder()
                .name("commands")
                .locality(ALL)
                .privacy(PUBLIC)
                .flag(TEXT)
                .input(0)
                .consumer(ctx -> {
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

    public Ability recover() {
        return builder()
                .name("recover")
                .locality(USER)
                .privacy(CREATOR)
                .flag(DOCUMENT, CAPTION)
                .input(0)
                .consumer(ctx -> {
                    String fileId = ctx.update().getMessage().getDocument().getFileId();
                    try {
                        File backup = downloadFile(getFile(new GetFile().setFileId(fileId)));
                        String backupData = IOUtils.toString(new FileReader(backup));
                        if (db.recover(backupData)) {
                            sender.send("I have successfully recovered.", ctx.chatId());
                        } else {
                            sender.send("Oops, something went wrong during recovery.", ctx.chatId());
                        }

                    } catch (Exception e) {
                        BotLogger.error("Could not recover DB from backup", TAG, e);
                        sender.send("I have failed to recover.", ctx.chatId());
                    }
                })
                .build();
    }

    public Ability backup() {
        return builder()
                .name("backup")
                .locality(USER)
                .privacy(CREATOR)
                .input(0)
                .consumer(ctx -> {
                    File backup = new File("backup.json");

                    try {
                        PrintStream printStream = new PrintStream(backup);
                        printStream.print(db.backup());
                        sendDocument(new SendDocument()
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
                .privacy(SUPERADMIN)
                .input(1)
                .consumer(ctx -> {
                    String username = stripTag(ctx.firstArg());
                    Optional<Integer> endUser = getUser(username).map(EndUser::id);

                    endUser.ifPresent(user -> {
                        String actualuser = username;
                        // Protection from abuse
                        if (user == creatorId()) {
                            user = ctx.user().id();
                            actualuser = getUser(user).get().name();
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
                .privacy(SUPERADMIN)
                .input(1)
                .consumer(ctx -> {
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
                .locality(GROUP)
                .privacy(SUPERADMIN)
                .input(1)
                .consumer(ctx -> {
                    String username = stripTag(ctx.firstArg());

                    Optional<Integer> endUser = getUser(username).map(EndUser::id);
                    endUser.ifPresent(user -> {
                        Set<Integer> admins = db.getGroupSet(ADMINS, ctx.chatId());
                        boolean isAdmin = admins.contains(user) || isSuperAdmin(user);

                        if (isAdmin)
                            sender.sendFormatted(format("@%s is already an *admin*.", username), ctx.chatId());
                        else {
                            admins.add(user);
                            sender.sendFormatted(format("@%s is now an *admin*.", username), ctx.chatId());
                        }
                    });
                })
                .post(commit())
                .build();
    }

    public Ability demoteAdmin() {
        return builder()
                .name(DEMOTE)
                .locality(GROUP)
                .privacy(SUPERADMIN)
                .input(1)
                .consumer(ctx -> {
                    String username = stripTag(ctx.firstArg());

                    Optional<Integer> endUserId = getUser(username).map(EndUser::id);
                    endUserId.ifPresent(id -> {
                        Set<Integer> admins = db.getGroupSet(ADMINS, ctx.chatId());
                        if (!admins.contains(id)) {
                            sender.sendFormatted(format("@%s is *not* an *admin*.", username), ctx.chatId());
                        } else {
                            admins.remove(id);
                            sender.sendFormatted(format("@%s has been *demoted*.", username), ctx.chatId());
                        }
                    });
                })
                .post(commit())
                .build();
    }

    public Ability promoteSuper() {
        return builder()
                .name(SUDO)
                .locality(ALL)
                .privacy(SUPERADMIN)
                .input(1)
                .consumer(ctx -> {
                    String username = stripTag(ctx.firstArg());
                    Optional<Integer> endUserId = getUser(username).map(EndUser::id);

                    endUserId.ifPresent(id -> {
                        Set<Integer> superAdmins = db.getSet(SUPER_ADMINS);
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

    public Ability demoteSuper() {
        return builder()
                .name(SUDONT)
                .locality(ALL)
                .privacy(SUPERADMIN)
                .input(1)
                .consumer(ctx -> {
                    String username = stripTag(ctx.firstArg());

                    Optional<Integer> endUserId = getUser(username).map(EndUser::id);

                    endUserId.ifPresent(id -> {
                        Set<Integer> superAdmins = db.getSet(SUPER_ADMINS);
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
                .consumer(ctx -> {
                    if (ctx.user().id() == creatorId()) {
                        Set<Integer> superadmins = db.getSet(SUPER_ADMINS);
                        setMaster(superadmins, creatorId(), ctx.chatId());
                    } else {
                        // This is not a joke
                        abilities.get(BAN).consumer().accept(new MessageContext(ctx.update(), ctx.user(), ctx.chatId(), ctx.user().username()));
                    }
                })
                .post(commit())
                .build();
    }

    protected <T extends AbilityBot> void registerAbilities(T bot) {
        try {
            abilities = stream(bot.getClass().getMethods())
                    .filter(method -> method.getReturnType().equals(Ability.class))
                    .map(method -> {
                        try {
                            return (Ability) method.invoke(bot);
                        } catch (IllegalAccessException | InvocationTargetException e) {
                            BotLogger.error("Could not add ability", TAG, e);
                            throw propagate(e);
                        }
                    })
                    .collect(toMap(Ability::name, identity()));
        } catch (IllegalStateException e) {
            BotLogger.error(TAG, "Duplicates found while registering abilities. Make sure that the abilities declared don't clash with the reserved ones.", e);
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
        return db.<EndUser>getSet(USERS).stream().filter(user -> user.username().equals(username)).findFirst();
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
        EndUser user = new EndUser(getUser(update));

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
            throw new IllegalStateException("Could not retrieve originating user ID from update");
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
        EndUser user = new EndUser(getUser(update));
        boolean isUserMsg = isUserMessage(update);
        Long groupId = getChatId(update);
        Privacy privacy;
        int id = user.id();
        // Sonar
        if (isCreator(id)) {
            privacy = CREATOR;
        } else {
            privacy = isSuperAdmin(id) ? SUPERADMIN : !isUserMsg && isAdmin(id, groupId) ? ADMIN : PUBLIC;
        }

        return privacy.compareTo(trio.b().privacy()) >= 0;
    }

    private boolean isCreator(int id) {
        return id == creatorId();
    }

    private boolean isSuperAdmin(Integer id) {
        return db.<Integer>getSet(SUPER_ADMINS).contains(id);
    }

    private boolean isAdmin(Integer id, long groupId) {
        if (db.hasDataStructure(ADMINS, groupId))
            return db.<Integer>getGroupSet(ADMINS, groupId).contains(id);
        else
            return false;
    }

    boolean validateAbility(Trio<Update, Ability, String[]> trio) {
        return trio.b() != null;
    }

    Trio<Update, Ability, String[]> getAbility(Update update) {
        // Handle updates without messages
        // Passing through this function means that the global flags have passed
        Message msg = update.getMessage();
        if (!update.hasMessage() || !(msg.hasText() || nonNull(msg.getCaption())))
            return Trio.of(update, abilities.get(DEFAULT), new String[]{});

        // Priority goes to text before captions
        String[] tokens = msg.hasText() ?
                msg.getText().split(" ") :
                msg.getCaption().split(" ");

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
        return token.replace("@".concat(botUsername), "");
    }

    Update addUser(Update update) {
        EndUser endUser = new EndUser(getUser(update));
        Set<EndUser> set = db.getSet(USERS);

        Optional<EndUser> optUser = set.stream().filter(user -> user.id() == endUser.id()).findAny();
        if (optUser.isPresent()) {
            EndUser prevUser = optUser.get();
            if (!prevUser.equals(endUser)) {
                set.remove(prevUser);
                set.add(endUser);
                db.commit();
            }
        } else {
            set.add(endUser);
            db.commit();
        }

        return update;
    }

    boolean checkMessageFlags(Trio<Update, Ability, String[]> trio) {
        Ability ability = trio.b();
        Update update = trio.a();

        return ofNullable(ability.flags())
                .map(flags -> stream(flags)
                        .reduce(true, (a, b) -> a && b.test(update), (a, b) -> a && b))
                .orElse(true);
    }

    protected boolean checkGlobalFlags(Update update) {
        return MESSAGE.test(update);
    }
}
