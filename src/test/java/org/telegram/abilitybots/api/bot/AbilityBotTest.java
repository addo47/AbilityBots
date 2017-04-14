package org.telegram.abilitybots.api.bot;

import com.google.common.io.Files;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Matchers;
import org.telegram.abilitybots.api.db.DBContext;
import org.telegram.abilitybots.api.objects.*;
import org.telegram.abilitybots.api.sender.MessageSender;
import org.telegram.abilitybots.api.util.Pair;
import org.telegram.abilitybots.api.util.Trio;
import org.telegram.telegrambots.api.objects.*;
import org.telegram.telegrambots.exceptions.TelegramApiException;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Set;

import static com.google.common.collect.Sets.newHashSet;
import static java.util.Collections.emptySet;
import static org.apache.commons.lang3.ArrayUtils.addAll;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.mockito.internal.verification.VerificationModeFactory.times;
import static org.telegram.abilitybots.api.bot.AbilityBot.*;
import static org.telegram.abilitybots.api.bot.DefaultBot.getDefaultBuilder;
import static org.telegram.abilitybots.api.db.MapDBContext.offlineInstance;
import static org.telegram.abilitybots.api.objects.EndUser.endUser;
import static org.telegram.abilitybots.api.objects.Flag.DOCUMENT;
import static org.telegram.abilitybots.api.objects.Flag.MESSAGE;
import static org.telegram.abilitybots.api.objects.Locality.ALL;
import static org.telegram.abilitybots.api.objects.Locality.GROUP;
import static org.telegram.abilitybots.api.objects.Privacy.ADMIN;
import static org.telegram.abilitybots.api.objects.Privacy.PUBLIC;

public class AbilityBotTest {
  public static final String[] EMPTY_ARRAY = {};
  public static final long GROUP_ID = 10L;
  public static final String TEST = "test";
  public static final String[] TEXT = {TEST};
  public static final EndUser MUSER = endUser(1, "first", "first", "last");
  public static final EndUser CREATOR = endUser(1337, "creatorFirst", "creatorLast", "creatorUsername");

  private DefaultBot defaultBot;
  private DBContext db;
  private MessageSender sender;

  @Before
  public void setUp() {
    db = offlineInstance("db");
    defaultBot = new DefaultBot(EMPTY, EMPTY, db);
    sender = mock(MessageSender.class);
    defaultBot.setSender(sender);
  }

  @Test
  public void canProcessRepliesIfSatisfyRequirements() {
    Update update = mock(Update.class);
    when(update.hasMessage()).thenReturn(true);
    Message message = mock(Message.class);
    when(message.getChatId()).thenReturn(GROUP_ID);
    when(update.getMessage()).thenReturn(message);

    // False means the update was not pushed down the stream since it has been consumed by the reply
    assertFalse(defaultBot.filterReply(update));
    verify(sender, times(1)).send("reply", GROUP_ID);
  }

  @Test
  public void canBackupDB() throws TelegramApiException {
    MessageContext context = defaultContext();

    defaultBot.backupDB().consumer().accept(context);

    verify(sender, times(1)).sendDocument(any());
  }

  @Test
  public void canRecoverDB() throws TelegramApiException, IOException {
    Update update = mockBackupUpdate();
    Object backup = getDbBackup();
    java.io.File backupFile = createBackupFile(backup);

    when(sender.downloadFile(Matchers.any(File.class))).thenReturn(backupFile);
    defaultBot.recoverDB().replies().get(0).actOn(update);

    verify(sender, times(1)).send(RECOVER_SUCCESS, GROUP_ID);
    assertEquals("Bot recovered but the DB is still not in sync", db.getSet(TEST), newHashSet(TEST));
    assertTrue("Could not delete backup file", backupFile.delete());
  }

  @Test
  public void canFilterOutReplies() {
    Update update = mock(Update.class);
    when(update.hasMessage()).thenReturn(false);

    assertTrue(defaultBot.filterReply(update));
  }

  @Test
  public void canPromote() {
    db.<EndUser>getSet(USERS).add(MUSER);
    db.<Integer>getSet(ADMINS).add(MUSER.id());

    MessageContext context = defaultContext();

    defaultBot.demoteAdmin().consumer().accept(context);

    Set<Integer> actual = db.getSet(ADMINS);
    Set<Integer> expected = emptySet();
    assertEquals("Could not sudont super-admin", expected, actual);
  }

  @Test
  public void canDemote() {
    db.<EndUser>getSet(USERS).add(MUSER);

    MessageContext context = defaultContext();

    defaultBot.promoteAdmin().consumer().accept(context);

    Set<Integer> actual = db.getSet(ADMINS);
    Set<Integer> expected = newHashSet(MUSER.id());
    assertEquals("Could not sudo user", expected, actual);
  }

  @Test
  public void canBanUser() {
    db.<EndUser>getSet(USERS).add(MUSER);
    MessageContext context = defaultContext();

    defaultBot.banUser().consumer().accept(context);

    Set<Integer> actual = db.getSet(BLACKLIST);
    Set<Integer> expected = newHashSet(MUSER.id());
    assertEquals("The ban was not emplaced", expected, actual);
  }

  @Test
  public void canUnbanUser() {
    db.<EndUser>getSet(USERS).add(MUSER);
    db.<Integer>getSet(BLACKLIST).add(MUSER.id());

    MessageContext context = defaultContext();

    defaultBot.unbanUser().consumer().accept(context);

    Set<Integer> actual = db.getSet(BLACKLIST);
    Set<Integer> expected = newHashSet();
    assertEquals("The ban was not lifted", expected, actual);
  }

  @NotNull
  private MessageContext defaultContext() {
    MessageContext context = mock(MessageContext.class);
    when(context.user()).thenReturn(CREATOR);
    when(context.firstArg()).thenReturn(MUSER.username());
    return context;
  }

  @Test
  public void cannotBanCreator() {
    db.<EndUser>getSet(USERS).add(MUSER);
    db.<EndUser>getSet(USERS).add(CREATOR);
    MessageContext context = mock(MessageContext.class);
    when(context.user()).thenReturn(MUSER);
    when(context.firstArg()).thenReturn(CREATOR.username());

    defaultBot.banUser().consumer().accept(context);

    Set<Integer> actual = db.getSet(BLACKLIST);
    Set<Integer> expected = newHashSet(MUSER.id());
    assertEquals("Impostor was not added to the blacklist", expected, actual);
  }

  @Test
  public void creatorCanClaimBot() {
    MessageContext context = mock(MessageContext.class);
    when(context.user()).thenReturn(CREATOR);

    defaultBot.claimCreator().consumer().accept(context);

    Set<Integer> actual = db.getSet(ADMINS);
    Set<Integer> expected = newHashSet(CREATOR.id());
    assertEquals("Creator was not properly added to the super admins set", expected, actual);
  }

  @Test
  public void userGetsBannedIfClaimsBot() {
    db.<EndUser>getSet(USERS).add(MUSER);
    MessageContext context = mock(MessageContext.class);
    when(context.user()).thenReturn(MUSER);

    defaultBot.claimCreator().consumer().accept(context);

    Set<Integer> actual = db.getSet(BLACKLIST);
    Set<Integer> expected = newHashSet(MUSER.id());
    assertEquals("Could not find user on the blacklist", expected, actual);

    actual = db.getSet(ADMINS);
    expected = emptySet();
    assertEquals("Admins set is not empty", expected, actual);
  }

  @Test
  public void bannedCreatorPassesBlacklistCheck() {
    db.<Integer>getSet(BLACKLIST).add(CREATOR.id());
    Update update = mock(Update.class);
    Message message = mock(Message.class);
    User user = mock(User.class);

    mockUser(update, message, user);

    boolean notBanned = defaultBot.checkBlacklist(update);
    assertTrue("Creator is banned", notBanned);
  }

  @Test
  public void canAddUser() {
    Update update = mock(Update.class);
    Message message = mock(Message.class);
    User user = mock(User.class);

    mockAlternateUser(update, message, user, MUSER);

    defaultBot.addUser(update);

    Set<EndUser> actual = db.getSet(USERS);
    Set<EndUser> expected = newHashSet(MUSER);
    assertEquals("User was not added", expected, actual);
  }

  @Test
  public void canEditUser() {
    db.<EndUser>getSet(USERS).add(MUSER);
    Update update = mock(Update.class);
    Message message = mock(Message.class);
    User user = mock(User.class);

    String newUsername = MUSER.username() + "-test";
    String newFirstName = MUSER.firstName() + "-test";
    String newLastName = MUSER.lastName() + "-test";
    int sameId = MUSER.id();
    EndUser changedUser = endUser(sameId, newFirstName, newLastName, newUsername);

    mockAlternateUser(update, message, user, changedUser);

    defaultBot.addUser(update);

    Set<EndUser> actual = db.getSet(USERS);
    Set<EndUser> expected = newHashSet(changedUser);
    assertEquals("User was not properly edited", expected, actual);
  }

  @Test
  public void canValidateAbility() {
    Trio<Update, Ability, String[]> invalidPair = Trio.of(null, null, null);
    Ability validAbility = getDefaultBuilder().build();
    Trio<Update, Ability, String[]> validPair = Trio.of(null, validAbility, null);

    assertEquals("Bot can't validate ability properly", false, defaultBot.validateAbility(invalidPair));
    assertEquals("Bot can't validate ability properly", true, defaultBot.validateAbility(validPair));
  }

  @Test
  public void canCheckInput() {
    Update update = mock(Update.class);
    Message message = mock(Message.class);
    Ability abilityWithOneInput = getDefaultBuilder()
        .build();
    Ability abilityWithZeroInput = getDefaultBuilder()
        .input(0)
        .build();

    Trio<Update, Ability, String[]> trioOneArg = Trio.of(update, abilityWithOneInput, TEXT);
    Trio<Update, Ability, String[]> trioZeroArg = Trio.of(update, abilityWithZeroInput, TEXT);

    assertEquals("Unexpected result when applying token filter", true, defaultBot.checkInput(trioOneArg));

    trioOneArg = Trio.of(update, abilityWithOneInput, addAll(TEXT, TEXT));
    assertEquals("Unexpected result when applying token filter", false, defaultBot.checkInput(trioOneArg));

    assertEquals("Unexpected result  when applying token filter", true, defaultBot.checkInput(trioZeroArg));

    trioZeroArg = Trio.of(update, abilityWithZeroInput, EMPTY_ARRAY);
    assertEquals("Unexpected result when applying token filter", true, defaultBot.checkInput(trioZeroArg));
  }

  @Test
  public void canCheckPrivacy() {
    Update update = mock(Update.class);
    Message message = mock(Message.class);
    org.telegram.telegrambots.api.objects.User user = mock(User.class);
    Ability publicAbility = getDefaultBuilder().privacy(PUBLIC).build();
    Ability adminAbility = getDefaultBuilder().privacy(ADMIN).build();
    Ability creatorAbility = getDefaultBuilder().privacy(Privacy.CREATOR).build();

    Trio<Update, Ability, String[]> publicTrio = Trio.of(update, publicAbility, TEXT);
    Trio<Update, Ability, String[]> adminTrio = Trio.of(update, adminAbility, TEXT);
    Trio<Update, Ability, String[]> creatorTrio = Trio.of(update, creatorAbility, TEXT);

    mockUser(update, message, user);

    assertEquals("Unexpected result when checking for privacy", true, defaultBot.checkPrivacy(publicTrio));
    assertEquals("Unexpected result when checking for privacy", false, defaultBot.checkPrivacy(adminTrio));
    assertEquals("Unexpected result when checking for privacy", false, defaultBot.checkPrivacy(creatorTrio));
  }

  @Test
  public void canBlockAdminsFromCreatorAbilities() {
    Update update = mock(Update.class);
    Message message = mock(Message.class);
    org.telegram.telegrambots.api.objects.User user = mock(User.class);
    Ability creatorAbility = getDefaultBuilder().privacy(Privacy.CREATOR).build();

    Trio<Update, Ability, String[]> creatorTrio = Trio.of(update, creatorAbility, TEXT);

    db.getSet(ADMINS).add(MUSER.id());
    mockUser(update, message, user);

    assertEquals("Unexpected result when checking for privacy", false, defaultBot.checkPrivacy(creatorTrio));
  }

  @Test
  public void canCheckLocality() {
    Update update = mock(Update.class);
    Message message = mock(Message.class);
    User user = mock(User.class);
    Ability allAbility = getDefaultBuilder().locality(ALL).build();
    Ability userAbility = getDefaultBuilder().locality(Locality.USER).build();
    Ability groupAbility = getDefaultBuilder().locality(GROUP).build();

    Trio<Update, Ability, String[]> publicTrio = Trio.of(update, allAbility, TEXT);
    Trio<Update, Ability, String[]> userTrio = Trio.of(update, userAbility, TEXT);
    Trio<Update, Ability, String[]> groupTrio = Trio.of(update, groupAbility, TEXT);

    mockUser(update, message, user);
    when(message.isUserMessage()).thenReturn(true);

    assertEquals("Unexpected result when checking for locality", true, defaultBot.checkLocality(publicTrio));
    assertEquals("Unexpected result when checking for locality", true, defaultBot.checkLocality(userTrio));
    assertEquals("Unexpected result when checking for locality", false, defaultBot.checkLocality(groupTrio));
  }

  @Test
  public void canRetrieveContext() {
    Update update = mock(Update.class);
    Message message = mock(Message.class);
    User user = mock(User.class);
    Ability ability = getDefaultBuilder().build();
    Trio<Update, Ability, String[]> trio = Trio.of(update, ability, TEXT);

    when(message.getChatId()).thenReturn(GROUP_ID);
    mockUser(update, message, user);

    Pair<MessageContext, Ability> actualPair = defaultBot.getContext(trio);
    Pair<MessageContext, Ability> expectedPair = Pair.of(new MessageContext(update, MUSER, GROUP_ID, TEXT), ability);

    assertEquals("Unexpected result when fetching for context", expectedPair, actualPair);
  }

  @Test
  public void canCheckGlobalFlags() {
    Update update = mock(Update.class);
    Message message = mock(Message.class);

    when(update.hasMessage()).thenReturn(true);
    when(update.getMessage()).thenReturn(message);
    assertEquals("Unexpected result when checking for locality", true, defaultBot.checkGlobalFlags(update));
  }

  @Test(expected = ArithmeticException.class)
  public void canConsumeUpdate() {
    Ability ability = getDefaultBuilder()
        .action((context) -> {
          int x = 1 / 0;
        }).build();
    MessageContext context = mock(MessageContext.class);

    Pair<MessageContext, Ability> pair = Pair.of(context, ability);

    defaultBot.consumeUpdate(pair);
  }

  @Test
  public void canFetchAbility() {
    Update update = mock(Update.class);
    Message message = mock(Message.class);

    String text = "/test";
    when(update.hasMessage()).thenReturn(true);
    when(update.getMessage()).thenReturn(message);
    when(update.getMessage().hasText()).thenReturn(true);
    when(message.getText()).thenReturn(text);

    Trio<Update, Ability, String[]> trio = defaultBot.getAbility(update);

    Ability expected = defaultBot.testAbility();
    Ability actual = trio.b();

    assertEquals("Wrong ability was fetched", expected, actual);
  }

  @Test
  public void canFetchDefaultAbility() {
    Update update = mock(Update.class);
    Message message = mock(Message.class);

    String text = "test tags";
    when(update.getMessage()).thenReturn(message);
    when(message.getText()).thenReturn(text);

    Trio<Update, Ability, String[]> trio = defaultBot.getAbility(update);

    Ability expected = defaultBot.defaultAbility();
    Ability actual = trio.b();

    assertEquals("Wrong ability was fetched", expected, actual);
  }

  @Test
  public void canCheckAbilityFlags() {
    Update update = mock(Update.class);
    Message message = mock(Message.class);

    when(update.hasMessage()).thenReturn(true);
    when(update.getMessage()).thenReturn(message);
    when(message.hasDocument()).thenReturn(false);
    when(message.hasText()).thenReturn(true);

    Ability documentAbility = getDefaultBuilder().flag(DOCUMENT, MESSAGE).build();
    Ability textAbility = getDefaultBuilder().flag(Flag.TEXT, MESSAGE).build();

    Trio<Update, Ability, String[]> docTrio = Trio.of(update, documentAbility, TEXT);
    Trio<Update, Ability, String[]> textTrio = Trio.of(update, textAbility, TEXT);

    assertEquals("Unexpected result when checking for message flags", false, defaultBot.checkMessageFlags(docTrio));
    assertEquals("Unexpected result when checking for message flags", true, defaultBot.checkMessageFlags(textTrio));
  }

  @Test
  public void canReportCommands() {
    Update update = mock(Update.class);
    Message message = mock(Message.class);

    when(update.hasMessage()).thenReturn(true);
    when(update.getMessage()).thenReturn(message);
    when(message.hasText()).thenReturn(true);
    MessageContext context = mock(MessageContext.class);
    when(context.chatId()).thenReturn(GROUP_ID);

    defaultBot.reportCommands().consumer().accept(context);

    verify(sender, times(1)).send("default - dis iz default command", GROUP_ID);
  }

  @After
  public void tearDown() throws IOException {
    db.clear();
    db.close();
  }

  private void mockUser(Update update, Message message, User user) {
    when(update.hasMessage()).thenReturn(true);
    when(update.getMessage()).thenReturn(message);
    when(message.getFrom()).thenReturn(user);
    when(user.getFirstName()).thenReturn(MUSER.firstName());
    when(user.getLastName()).thenReturn(MUSER.lastName());
    when(user.getId()).thenReturn(MUSER.id());
    when(user.getUserName()).thenReturn(MUSER.username());
  }

  private void mockAlternateUser(Update update, Message message, User user, EndUser changedUser) {
    when(user.getId()).thenReturn(changedUser.id());
    when(user.getFirstName()).thenReturn(changedUser.firstName());
    when(user.getLastName()).thenReturn(changedUser.lastName());
    when(user.getUserName()).thenReturn(changedUser.username());
    when(message.getFrom()).thenReturn(user);
    when(update.hasMessage()).thenReturn(true);
    when(update.getMessage()).thenReturn(message);
  }

  private Update mockBackupUpdate() {
    Update update = mock(Update.class);
    Message message = mock(Message.class);
    Message botMessage = mock(Message.class);
    Document document = mock(Document.class);

    when(update.getMessage()).thenReturn(message);
    when(message.getDocument()).thenReturn(document);
    when(botMessage.getText()).thenReturn(RECOVERY_MESSAGE);
    when(message.isReply()).thenReturn(true);
    when(message.hasDocument()).thenReturn(true);
    when(message.getReplyToMessage()).thenReturn(botMessage);
    when(message.getChatId()).thenReturn(GROUP_ID);
    return update;
  }

  private Object getDbBackup() {
    db.getSet(TEST).add(TEST);
    Object backup = db.backup();
    db.clear();
    return backup;
  }

  private java.io.File createBackupFile(Object backup) throws IOException {
    java.io.File backupFile = new java.io.File(TEST);
    BufferedWriter writer = Files.newWriter(backupFile, Charset.defaultCharset());
    writer.write(backup.toString());
    writer.flush();
    writer.close();
    return backupFile;
  }
}
