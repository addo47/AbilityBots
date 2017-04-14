[![Telegram](http://trellobot.doomdns.org/telegrambadge.svg)](https://telegram.me/AbilityBots)

[![Build Status](https://travis-ci.org/addo37/AbilityBots.svg?branch=master)](https://travis-ci.org/addo37/AbilityBots)
[![Jitpack](https://jitpack.io/v/addo37/AbilityBots.svg)](https://jitpack.io/#addo37/AbilityBots)

Motivation
----------
Ever since I have started programming bots for Telegram, I have been using the Telegram Bot Java API. It is a basic and nicely done API that is a 1-to-1 translation of the HTTP API exposed by Telegram.

Dealing with a basic API has its advantages and disadvantages. Obviously, there's nothing hidden. If it is there on Telegram, it is here in the Java API.
When you want to implement a feature in your bot, you start asking these questions:

* The **WHO**?
    * Who is going to use this feature? Should they be allowed to use all the features?
* The **WHAT**?
    * Under what conditions should I allow this feature?
    * Should the message have a photo? A document? Oh, maybe a callback query?
* The **HOW**?
    * If my bot crashes, how can I resume my operation?
    * Should I utilize a DB?
    * How can I separate logic execution of different features?
    * How can I unit-test my feature outside Telegram?

Every time you write a command or a feature, you will need to answer these questions and insure that your feature logic works.

Ability Bot Abstraction
-----------------------
After implementing my fifth bot using that API, I had it with the amount of **overhead** that was needed for every added feature. Methods were getting overly-complex and readability became sub-par.
That is where the notion of an another layer of abstraction (AbilityBot) started to take shape.

The AbilityBot abstraction defines a new object, named **Ability**. An ability, is conditions, flags, action, post-action, replies all combined.
As an example, here's a code-snippet of an ability that create a ***/hello*** command:

```
public Ability sayHelloWorld() {
    return Ability
              .builder()
              .name("hello")
              .info("says hello world!")
              .input(0)
              .locality(USER)
              .privacy(ADMIN)
              .action(ctx -> sender.send("Hello world!", ctx.chatId()))
              .post(ctx -> sender.send("Bye world!", ctx.chatId()))
              .build();
}
```
Here's a breakdown of what has been written above:
* *.name()* - the name of the ability (essentially, this is the command)
* *.info()* - provides information for the command
    * More on this later, but it basically centralizes command information in-code.
* *.input()* - the number of input arguments needed, 0 is for don't-care
* *.locality()* - this answers where you want the ability to be available
    * In GROUP, USER private chats or ALL (both)
* *.privacy()* - this answers who you want to access your ability
    * CREATOR, ADMIN, or everyone as PUBLIC
* *.action()* - the feature logic resides here (a lambda function that takes a *MessageContext*)
    * *MessageContext* provides fast accessors for the **chatId**, **user** and the underlying **update**. It also conforms to the specifications of the basic API.
* *.post()* - the logic executed **after** your main action finishes execution

For a more complete example of the API usages, please check the ExampleBots repository.

It would be a total nightmare to write something similar with the basic API, here's a snippet of how this would look like with the plain basic API.

```
   @Override
   public void onUpdateReceived(Update update) {
       // Global checks...
       // Switch, if, logic to route to hello world method
       // Execute method
   }

   public void sayHelloWorld(Update update) {
       if (!update.hasMessage() || !update.getMessage().isUserMessage() || !update.getMessage().hasText() || update.getMessage.getText().isEmpty())
           return;
       User maybeAdmin = update.getMessage().getFrom();
       /* Query DB for if the user is an admin, can be SQL, Reddis, Ignite, etc...
          If user is not an admin, then return here.
       */

       SendMessage snd = new SendMessage();
       snd.setChatId(update.getMessage().getChatId());
       snd.setText("Hello world!");

       try {
           sendMessage(snd);
       } catch (TelegramApiException e) {
           BotLogger.error("Could not send message", TAG, e);
       }
   }
```

I will leave the choice to you to decide between the more **readable**, **writable** and **testable** snippet.

Objective
---------
The AbilityBot abstraction intends to provide the following:
* New feature is a new "Ability", a new method; no fuss, zero overhead, no cross-code with other features
* Forcing an argument length on a command is as easy as changing a single integer
* Privacy of an ability - providing access levels to abilities! User | Admin | Creator
* An embedded database provided on-the-spot in every declared Ability
* Proxy sender interface to enhance testability; accurate results pre-release

Alongside these exciting core features of the AbilityBot, the following were introduced:
* The bot automatically maintain an up-to-date set of all the users who contacted the bot
    * up-to-date: if a user changes his or her username/first-name/last-name, the bot updates it in the embedded-DB
* Backup and recover the DB
    * Default implementation relies on JSON/Jackson
* Ban and unban users from accessing your bots
    * If someone is spamming your bot, you are able to ban them
    * It will execute the shortest path to discard the update the next time they try to spam
* Promote and demote users as bot admins
    * That would allow admins to execute admin abilities

What's next?
------------
This readme was associated with the initial 1.0 release of the AbilityBot abstraction. I'm looking forward to:
* Provide a trigger to record metrics per ability
* Implement AsyncAbilities
* Maintain integration with the latest updates on the basic API
* Enrich the bot with features requested by the community

Examples
--------
[Example Bots](https://github.com/addo37/ExampleBots)

Support
-------
For issues and features, please use GitHub's [issues tab](https://github.com/addo37/AbilityBots/issues).

For quick feedback, chatting or just having fun, please come and join us in our Telegram super-group.

[![Telegram](http://trellobot.doomdns.org/telegrambadge.svg)](https://telegram.me/AbilityBots)

Credits
-------
This project would not have been made possible if it weren't for the [Telegram Bot Java API](https://github.com/rubenlagus/TelegramBots) made by [Ruben](https://github.com/rubenlagus).
I strongly urge you to checkout that project and implement a bot to have a sense of how the basic API feels like.
Ruben has made a great job in supplying a clear and straight-forward API that conforms to Telegram's HTTP API.
You can join the chat that is specific to that API through [![Telegram](http://trellobot.doomdns.org/telegrambadge.svg)](https://telegram.me/JavaBotsApi)
