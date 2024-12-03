package keyp.forev.fmc.velocity.discord;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;

import org.slf4j.Logger;

import keyp.forev.fmc.common.database.Database;
import keyp.forev.fmc.common.libs.ClassManager;
import keyp.forev.fmc.velocity.Main;
import keyp.forev.fmc.velocity.cmd.sub.VelocityRequest;
import keyp.forev.fmc.velocity.cmd.sub.interfaces.Request;
import keyp.forev.fmc.velocity.libs.VClassManager;
import keyp.forev.fmc.velocity.libs.VPackageManager;
import keyp.forev.fmc.velocity.util.config.VelocityConfig;
import com.google.inject.Singleton;

@Singleton
public class Discord {
    public static Object jdaInstance = null; // JDAインスタンス // ログイン時に代入される
	public static boolean isDiscord = false;
    private final Logger logger;
    private final VelocityConfig config;
    private final Database db;
    private final Request req;
    private final Class<?> jdaBuilderClazz, gatewayIntentsClazz, subcommandDataClazz,
        optionDataClazz, optionTypeClazz, entityMessageClazz,
        entityActivityClazz, entityMessageEmbedClazz, buttonClazz,
        presenceActivityClazz, webhookClientClazz, webhookMessageClazz,
        webhookBuilderClazz, embedBuilderClazz, errorResponseExceptionClazz;
    public Discord(Logger logger, VelocityConfig config, Database db, Request req) throws ClassNotFoundException {
    	this.logger = logger;
    	this.config = config;
        this.db = db;
        this.req = req;
        this.jdaBuilderClazz = VClassManager.JDA.JDA_BUILDER.get().getClazz();
        this.gatewayIntentsClazz = VClassManager.JDA.GATEWAY_INTENTS.get().getClazz();
        this.subcommandDataClazz = VClassManager.JDA.SUB_COMMAND.get().getClazz();
        this.optionDataClazz = VClassManager.JDA.OPTION_DATA.get().getClazz();
        this.optionTypeClazz = VClassManager.JDA.OPTION_TYPE.get().getClazz();
        this.entityMessageClazz = VClassManager.JDA.ENTITYS_MESSAGE.get().getClazz();
        this.entityActivityClazz = VClassManager.JDA.ENTITYS_ACTIVITY.get().getClazz();
        this.entityMessageEmbedClazz = VClassManager.JDA.ENTITYES_MESSAGE_EMBED.get().getClazz();
        this.buttonClazz = VClassManager.JDA.BUTTON.get().getClazz();
        this.presenceActivityClazz = VClassManager.JDA.PRESENCE.get().getClazz();
        this.webhookClientClazz = VClassManager.CLUB_MINNCED.WEBHOOK_CLIENT.get().getClazz();
        this.webhookMessageClazz = VClassManager.CLUB_MINNCED.WEBHOOK_MESSAGE.get().getClazz();
        this.webhookBuilderClazz = VClassManager.CLUB_MINNCED.WEBHOOK_MESSAGE_BUILDER.get().getClazz();
        this.embedBuilderClazz = VClassManager.JDA.EMBED_BUILDER.get().getClazz();
        this.errorResponseExceptionClazz = VClassManager.JDA.ERROR_RESPONSE_EXCEPTION.get().getClazz();
    }

    public CompletableFuture<Object> loginDiscordBotAsync() {
        return CompletableFuture.supplyAsync(() -> {
            if (config.getString("Discord.Token","").isEmpty()) {
                return CompletableFuture.completedFuture(null);
            }
            try {
                Field gatewayIntent = gatewayIntentsClazz.getField("GUILD_MESSAGES");
                Field gatewayIntent2 = gatewayIntentsClazz.getField("MESSAGE_CONTENT");

                Enum<?> intent = (Enum<?>) gatewayIntent.get(null);
                Enum<?> intent2 = (Enum<?>) gatewayIntent2.get(null);

                Method createDefault = jdaBuilderClazz.getMethod("createDefault", String.class);
                Object jdaBuilder = createDefault.invoke(null, config.getString("Discord.Token"));

                // リスナーの登録は、独自アノテーションクラスを使用して動的に行う
                // Method addEventListeners = jdaBuilder.getClass().getMethod("addEventListeners", Object[].class);
                // jdaBuilder = addEventListeners.invoke(jdaBuilder, Main.getInjector().getInstance(DiscordEventListener.class));
                try {
                    DynamicEventRegister.registerListeners(
                        Main.getInjector().getInstance(DiscordEventListener.class),
                        jdaInstance,
                        ClassManager.urlClassLoaderMap.get(VPackageManager.JDA)
                    );
                } catch (Exception e) {
                    logger.error("An error occurred: " + e.getMessage());
                    for (StackTraceElement element : e.getStackTrace()) {
                        logger.error(element.toString());
                    }
                }

                Method enableIntents = jdaBuilder.getClass().getMethod("enableIntents", gatewayIntentsClazz, gatewayIntentsClazz);
                jdaBuilder = enableIntents.invoke(jdaBuilder, intent, intent2);

                Method build = jdaBuilder.getClass().getMethod("build");
                jdaInstance = build.invoke(jdaBuilder);

                Method awaitReady = jdaInstance.getClass().getMethod("awaitReady");
                awaitReady.invoke(jdaInstance);

                Method upsertCommand = jdaInstance.getClass().getMethod("upsertCommand", String.class, String.class);
                Object createFMCCommand = upsertCommand.invoke(jdaInstance, "fmc", "FMC commands");

                Field optionTypeStringField = optionTypeClazz.getField("STRING");
                Field optionTypeAttachmentField = optionTypeClazz.getField("ATTACHMENT");

                Object stringType = optionTypeStringField.get(null);
                Object attachmentType = optionTypeAttachmentField.get(null);

                Constructor<?> subcommandC = subcommandDataClazz.getConstructor(String.class, String.class);
                Object createImageSubcommand = subcommandC.newInstance("image_add_q", "画像マップをキューに追加するコマンド(urlか添付ファイルのどっちかを指定可能)");
                Object createSyncRuleBookSubcommand = subcommandC.newInstance("syncrulebook", "ルールブックの同期を行うコマンド");

                Method addOptions = createImageSubcommand.getClass().getMethod("addOptions", optionDataClazz);
                Constructor<?> optionDataC = optionDataClazz.getConstructor(optionTypeClazz, String.class, String.class);
                addOptions.invoke(createImageSubcommand,
                    optionDataC.newInstance(stringType, "url", "画像リンクの設定項目"),
                    optionDataC.newInstance(attachmentType, "image", "ファイルの添付項目"),
                    optionDataC.newInstance(stringType, "title", "画像マップのタイトル設定項目"),
                    optionDataC.newInstance(stringType, "comment", "画像マップのコメント設定項目")
                );

                Method addSubcommands = createFMCCommand.getClass().getMethod("addSubcommands", subcommandDataClazz.arrayType());
                addSubcommands.invoke(createFMCCommand, new Object[]{createImageSubcommand, createSyncRuleBookSubcommand});

                Method queue = createFMCCommand.getClass().getMethod("queue");
                queue.invoke(createFMCCommand);

                Method getPresence = jdaInstance.getClass().getMethod("getPresence");
                Object presence = getPresence.invoke(jdaInstance);

                Method setActivity = presence.getClass().getMethod("setActivity", entityActivityClazz);
                Method playing = presenceActivityClazz.getMethod("playing", String.class);
                Object activity = playing.invoke(null, config.getString("Discord.Presence.Activity", "FMCサーバー"));
                setActivity.invoke(presence, activity);

                isDiscord = true;
                logger.info("discord bot has been logged in.");
                return jdaInstance;
            } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException | InstantiationException | NoSuchFieldException e) {
                logger.error("An discord-bot-login error occurred: " + e.getMessage());
                for (StackTraceElement element : e.getStackTrace()) {
                    logger.error(element.toString());
                }
                return CompletableFuture.completedFuture(null);
            }
        });
    }

    public CompletableFuture<Void> logoutDiscordBot() {
        return CompletableFuture.runAsync(() -> {
            try {
                if (jdaInstance != null) {
                    Method shutdown = jdaInstance.getClass().getMethod("shutdown");
                    shutdown.invoke(jdaInstance);
                    isDiscord = false;
                    logger.info("Discord-Botがログアウトしました。");
                }
            } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
                logger.error("An discord-bot-logout error occurred: " + e.getMessage());
                for (StackTraceElement element : e.getStackTrace()) {
                    logger.error(element.toString());
                }
            }
        });
    }

    public CompletableFuture<String> getMessageContent() throws Exception {
        CompletableFuture<String> future = new CompletableFuture<>();
        String ruleChannelId = Long.toString(config.getLong("Discord.Rule.ChannelId", 0));
        String ruleMessageId = Long.toString(config.getLong("Discord.Rule.MessageId", 0));

        Method getTextChannelById = jdaInstance.getClass().getMethod("getTextChannelById", String.class);
        Object ruleChannel = getTextChannelById.invoke(jdaInstance, ruleChannelId);

        if (ruleChannel == null) {
            future.completeExceptionally(new IllegalArgumentException("チャンネルが見つかりませんでした。"));
            return future;
        }

        Method retrieveMessageById = ruleChannel.getClass().getMethod("retrieveMessageById", String.class);
        Object retrieveMessageByIdResult = retrieveMessageById.invoke(ruleChannel, ruleMessageId);

        Method queue = retrieveMessageByIdResult.getClass().getMethod("queue", Consumer.class, Consumer.class);
        queue.invoke(retrieveMessageByIdResult,
            (Consumer<Object>) message -> {
                try {
                    Method getContentDisplay = entityMessageClazz.getMethod("getContentDisplay");
                    String content = (String) getContentDisplay.invoke(message);
                    future.complete(content);
                } catch (Exception e) {
                    future.completeExceptionally(e);
                }
            },
            (Consumer<Throwable>) throwable -> {
                try {
                    if (errorResponseExceptionClazz.isInstance(throwable)) {
                        Method getErrorResponse = errorResponseExceptionClazz.getMethod("getErrorResponse");
                        Object errorResponse = getErrorResponse.invoke(throwable);

                        Method getMeaning = errorResponse.getClass().getMethod("getMeaning");
                        String meaning = (String) getMeaning.invoke(errorResponse);

                        logger.error("A sendWebhookMessage error occurred: " + meaning);
                    } else {
                        logger.error("A sendWebhookMessage error occurred: " + throwable.getMessage());
                    }

                    for (StackTraceElement element : throwable.getStackTrace()) {
                        logger.error(element.toString());
                    }

                    future.completeExceptionally(throwable);
                } catch (Exception e) {
                    logger.error("An error occurred: " + e.getMessage());
                    for (StackTraceElement element : e.getStackTrace()) {
                        logger.error(element.toString());
                    }
                }
            }
        );

        return future;
    }


    public void sendRequestButtonWithMessage(String buttonMessage) throws Exception {
    	if (config.getLong("Discord.AdminChannelId", 0) == 0 || !isDiscord) return;
		String channelId = Long.toString(config.getLong("Discord.AdminChannelId"));

        Method getTextChannelById = jdaInstance.getClass().getMethod("getTextChannelById", String.class);
        Object channel = getTextChannelById.invoke(jdaInstance, channelId);

        if (channel == null) return;

        Method successButton = buttonClazz.getMethod("success", String.class, String.class);
        Method dangerButton = buttonClazz.getMethod("danger", String.class, String.class);

        Object button1 = successButton.invoke(buttonClazz, "reqOK", "YES");
        Object button2 = dangerButton.invoke(buttonClazz, "reqCancel", "NO");

        Method sendMessage = channel.getClass().getMethod("sendMessage", String.class);
        Object sendMessageResult = sendMessage.invoke(channel, buttonMessage);

        Method setActionRow = sendMessageResult.getClass().getMethod("setActionRow", buttonClazz, buttonClazz);
        Object setActionRowResult = setActionRow.invoke(sendMessageResult, button1, button2);

        Method queue = setActionRowResult.getClass().getMethod("queue", Consumer.class);
        queue.invoke(setActionRowResult, (Consumer<Object>) message -> {
            try {
                CompletableFuture.delayedExecutor(3, TimeUnit.MINUTES).execute(() -> {
                    if (!VelocityRequest.PlayerReqFlags.isEmpty()) {
                        try {
                            String buttonMessage2 = (String) entityMessageClazz.getMethod("getContentRaw").invoke(message);
                            Map<String, String> reqMap = req.paternFinderMapForReq(buttonMessage2);
                            if (!reqMap.isEmpty()) {
                                try (Connection conn = db.getConnection()) {
                                    db.insertLog(conn, "INSERT INTO log (name, uuid, reqsul, reqserver, reqsulstatus) VALUES (?, ?, ?, ?, ?);", new Object[] {reqMap.get("playerName"), reqMap.get("playerUUID"), true, reqMap.get("serverName"), "nores"});
                                } catch (SQLException | ClassNotFoundException e) {
                                    logger.error("A SQLException | ClassNotFoundException error occurred: {}", e.getMessage());
                                    for (StackTraceElement element : e.getStackTrace()) {
                                        logger.error(element.toString());
                                    }
                                }
                            }
                        } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException
                                | NoSuchMethodException | SecurityException e) {
                            e.printStackTrace();
                        }

                    }
                });
            } catch (Exception e) {
                logger.error("An error occurred: " + e.getMessage());
                for (StackTraceElement element : e.getStackTrace()) {
                    logger.error(element.toString());
                }
            }
        });
    }

    public void sendWebhookMessage(String userName, String avatarUrl, String content) throws Exception {
        Constructor<?> webhookBuilderC = webhookBuilderClazz.getConstructor();
        Object webhookBuilder = webhookBuilderC.newInstance();

        Method setUsername = webhookBuilderClazz.getMethod("setUsername", String.class);
        Method setAvatarUrl = webhookBuilderClazz.getMethod("setAvatarUrl", String.class);
        Method setContent = webhookBuilderClazz.getMethod("setContent", String.class);
        Method build = webhookBuilderClazz.getMethod("build");
        setUsername.invoke(webhookBuilder, userName);
        setAvatarUrl.invoke(webhookBuilder, avatarUrl);
        setContent.invoke(webhookBuilder, content);
        build.invoke(webhookBuilder);
        
    	String webhookUrl = config.getString("Discord.Webhook_URL","");

    	if (webhookUrl.isEmpty()) return;

        Method withUrl = webhookClientClazz.getMethod("withUrl", String.class);
        Object webhookClient = withUrl.invoke(null, webhookUrl);

        Method send = webhookClient.getClass().getMethod("send", webhookMessageClazz);
        Object sendResult = send.invoke(webhookClient, webhookBuilder);

        Method thenAccept = sendResult.getClass().getMethod("thenAccept", Consumer.class);
        thenAccept.invoke(sendResult, (Consumer<Object>) _p -> {
            try {
                Method exceptionally = sendResult.getClass().getMethod("exceptionally", Function.class);
                exceptionally.invoke(sendResult, (Function<Throwable, Object>) throwable -> {
                    logger.error("A sendWebhookMessage error occurred: " + throwable.getMessage());
                    for (StackTraceElement element : throwable.getStackTrace()) {
                        logger.error(element.toString());
                    }
                    return null;
                });
            } catch (Exception e) {
                logger.error("An error occurred: " + e.getMessage());
                for (StackTraceElement element : e.getStackTrace()) {
                    logger.error(element.toString());
                }
            }
        });
    }

    public CompletableFuture<Void> editBotEmbed(String messageId, String additionalDescription, boolean isChat) throws Exception {
        CompletableFuture<Void> future = new CompletableFuture<>();
        getBotMessage(messageId, currentEmbed -> {
            if (currentEmbed == null) {
                future.completeExceptionally(new RuntimeException("No embed found to edit."));
                return;
            }
            String channelId;
            if (isChat) {
                channelId = Long.toString(config.getLong("Discord.ChatChannelId", 0));
                if (channelId == "0" || !isDiscord) {
                    future.completeExceptionally(new RuntimeException("Chat channel ID is invalid or Discord is not enabled."));
                    return;
                }
            } else {
                channelId = Long.toString(config.getLong("Discord.ChannelId", 0));
                if (channelId == "0" || !isDiscord) {
                    future.completeExceptionally(new RuntimeException("Channel ID is invalid or Discord is not enabled."));
                    return;
                }
            }

            try {
                Method getTextChannelById = jdaInstance.getClass().getMethod("getTextChannelById", String.class);
                Object channel = getTextChannelById.invoke(jdaInstance, channelId);

                if (channel == null) {
                    future.completeExceptionally(new RuntimeException("Channel not found!"));
                    return;
                }

                Object newEmbed = addDescriptionToEmbed(currentEmbed, additionalDescription);

                Method editMessageEmbedsById = channel.getClass().getMethod("editMessageEmbedsById", String.class, entityMessageEmbedClazz);
                Object messageAction = editMessageEmbedsById.invoke(channel, messageId, newEmbed);

                Method queue = messageAction.getClass().getMethod("queue", Consumer.class, Consumer.class);
                queue.invoke(messageAction,
                    (Consumer<Object>) _p -> future.complete(null),
                    (Consumer<Throwable>) error -> {
                        future.completeExceptionally((Throwable) error);
                        logger.info("Failed to edit message with ID: " + messageId);
                    }
                );
            } catch (Exception e) {
                future.completeExceptionally(e);
                logger.error("An error occurred: " + e.getMessage());
                for (StackTraceElement element : e.getStackTrace()) {
                    logger.error(element.toString());
                }
            }
        }, isChat);

        return future;
    }


    public CompletableFuture<Void> editBotEmbed(String messageId, String additionalDescription) throws Exception {
    	return editBotEmbed(messageId, additionalDescription, false);
    }


    public void getBotMessage(String messageId, Consumer<Object> embedConsumer, boolean isChat) throws Exception {
        String channelId;
    	if (isChat) {
    		if (config.getLong("Discord.ChatChannelId", 0) == 0 || !isDiscord) return;
            channelId = Long.toString(config.getLong("Discord.ChatChannelId"));
    	} else {
    		if (config.getLong("Discord.ChannelId", 0) == 0 || !isDiscord) return;
    		channelId = Long.toString(config.getLong("Discord.ChannelId"));
    	}
        Object channel = jdaInstance.getClass().getMethod("getTextChannelById", String.class).invoke(jdaInstance, channelId);

        if (channel == null) return;

        Method retrieveMessageById = channel.getClass().getMethod("retrieveMessageById", String.class);
        retrieveMessageById.invoke(channel, messageId);

        Method queue = retrieveMessageById.getReturnType().getMethod("queue", Consumer.class, Consumer.class);
        queue.invoke(retrieveMessageById.invoke(channel, messageId),
            (Consumer<Object>) message -> {
                try {
                    Method getEmbeds = entityMessageClazz.getMethod("getEmbeds");
                    Object embeds = getEmbeds.invoke(message);
                    if (embeds instanceof List<?>) {
                        // embedsがList<Object>型であることを保証しなければならない
                        boolean isList = ((List<?>) embeds).stream().allMatch(e -> e instanceof Object);
                        if (isList) {
                            @SuppressWarnings("unchecked")
                            List<Object> embedList = (List<Object>) embeds;
                            if (!embedList.isEmpty()) {
                                embedConsumer.accept(embedList.get(0));
                            } else {
                                embedConsumer.accept(null);
                            }
                        }
                    } else {
                        embedConsumer.accept(null);
                    }
                } catch (Exception e) {
                    embedConsumer.accept(null);
                    logger.error("An error occurred: " + e.getMessage());
                    for (StackTraceElement element : e.getStackTrace()) {
                        logger.error(element.toString());
                    }
                }
            },
            (Consumer<Throwable>) error -> {
                logger.error("A getBotMessage error occurred: " + error.getMessage());
                for (StackTraceElement element : error.getStackTrace()) {
                    logger.error(element.toString());
                }
                embedConsumer.accept(null);
            }
        );
    }


    public Object addDescriptionToEmbed(Object embed, String additionalDescription) throws Exception {
        Method getDescription = embed.getClass().getMethod("getDescription");
        String description = (String) getDescription.invoke(embed);

        Constructor<?> embedBuilderC = embedBuilderClazz.getConstructor(entityMessageEmbedClazz);
        Object builder = embedBuilderC.newInstance(embed);

        String newDescription = (description != null ? description : "") + additionalDescription;
        Method setDescription = embedBuilderClazz.getMethod("setDescription", CharSequence.class);
        setDescription.invoke(builder, newDescription);

        Method build = embedBuilderClazz.getMethod("build");

        return build.invoke(builder);
    }

    public void editBotEmbedReplacedAll(String messageId, Object newEmbed) throws Exception {
    	if (config.getLong("Discord.ChannelId", 0)==0 || !isDiscord) return;
        String channelId = Long.toString(config.getLong("Discord.ChannelId"));
        Method getTextChannelById = jdaInstance.getClass().getMethod("getTextChannelById", String.class);
        Object channel = getTextChannelById.invoke(jdaInstance, channelId);

        if (channel == null) return;

        Method editMessageEmbedsById = channel.getClass().getMethod("editMessageEmbedsById", String.class, entityMessageEmbedClazz);
        Object messageAction = editMessageEmbedsById.invoke(channel, messageId, newEmbed);

        Method queue = messageAction.getClass().getMethod("queue", Consumer.class, Consumer.class);
        queue.invoke(messageAction,
            (Consumer<Object>) _p -> {
                // 特に何もしない
            },
            (Consumer<Throwable>) error -> {
                logger.error("A editBotEmbedReplacedAll error occurred: " + error.getMessage());
                for (StackTraceElement element : error.getStackTrace()) {
                    logger.error(element.toString());
                }
            }
        );
    }


    public CompletableFuture<String> sendBotMessageAndgetMessageId(String content, Object embed, boolean isChat) throws Exception {
    	CompletableFuture<String> future = new CompletableFuture<>();
        String channelId;
    	if (isChat) {
            channelId = Long.toString(config.getLong("Discord.ChatChannelId", 0));
    		if (channelId == "0" || !isDiscord) {
    			future.complete(null);
                return future;
    		}
    	} else {
            channelId = Long.toString(config.getLong("Discord.ChannelId"));
    		if (channelId == "0" || !isDiscord) {
            	future.complete(null);
                return future;
            }
    	}

        Method getTextChannelById = jdaInstance.getClass().getMethod("getTextChannelById", String.class);
        Object channel = getTextChannelById.invoke(jdaInstance, channelId);

        if (channel == null) {
        	logger.error("Channel not found!");
        	future.complete(null);
            return future;
        }

    	if (embed != null) {
            Method sendMessageEmbeds = channel.getClass().getMethod("sendMessageEmbeds", entityMessageEmbedClazz);
            Object messageAction = sendMessageEmbeds.invoke(channel, embed);

            Method queue = messageAction.getClass().getMethod("queue", Consumer.class, Consumer.class);
            queue.invoke(messageAction,
                (Consumer<Object>) response -> {
                    try {
                        Method getId = entityMessageClazz.getMethod("getId");
                        String messageId = (String) getId.invoke(response);
                        future.complete(messageId);
                    } catch (Exception e) {
                        logger.error("Failed to send embedded message: " + e.getMessage());
                        future.complete(null);
                    }
                },
                (Consumer<Throwable>) failure -> {
                    logger.error("Failed to send embedded message: " + failure.getMessage());
                    future.complete(null);
                }
            );
        }

        if (content != null && !content.isEmpty()) {
            Method sendMessage = channel.getClass().getMethod("sendMessage", String.class);
            Object messageAction = sendMessage.invoke(channel, content);

            Method queue = messageAction.getClass().getMethod("queue", Consumer.class, Consumer.class);
            queue.invoke(messageAction,
                (Consumer<Object>) response -> {
                    try {
                        Method getId = entityMessageClazz.getMethod("getId");
                        String messageId = (String) getId.invoke(response);
                        future.complete(messageId);
                    } catch (Exception e) {
                        logger.error("Failed to send text message: " + e.getMessage());
                        future.complete(null);
                    }
                },
                (Consumer<Throwable>) failure -> {
                    logger.error("Failed to send text message: " + failure.getMessage());
                    future.complete(null);
                }
            );
        }
    	return future;
    }


    public CompletableFuture<String> sendBotMessageAndgetMessageId(String content) throws Exception {
    	return sendBotMessageAndgetMessageId(content, null, false);
    }


    public CompletableFuture<String> sendBotMessageAndgetMessageId(Object embed) throws Exception {
    	return sendBotMessageAndgetMessageId(null, embed, false);
    }


    public CompletableFuture<String> sendBotMessageAndgetMessageId(String content, boolean isChat) throws Exception {
    	return sendBotMessageAndgetMessageId(content, null, isChat);
    }


    public CompletableFuture<String> sendBotMessageAndgetMessageId(Object embed, boolean isChat) throws Exception {
    	return sendBotMessageAndgetMessageId(null, embed, isChat);
    }


    public Object createEmbed(String description, int color) throws Exception {
        // "net.dv8tion.jda.api.entities.MessageEmbed"の内部クラスを取得
        ClassLoader loader = entityMessageEmbedClazz.getClassLoader();
        Constructor<?> messageEmbedC = entityMessageEmbedClazz.getConstructor(
            String.class, String.class, String.class,
            Class.forName("net.dv8tion.jda.api.entities.MessageEmbed$EmbedType", true, loader),
            Class.forName("java.time.OffsetDateTime"),
            int.class,
            Class.forName("net.dv8tion.jda.api.entities.MessageEmbed$Thumbnail", true, loader),
            Class.forName("net.dv8tion.jda.api.entities.MessageEmbed$Provider", true, loader),
            Class.forName("net.dv8tion.jda.api.entities.MessageEmbed$AuthorInfo", true, loader),
            Class.forName("net.dv8tion.jda.api.entities.MessageEmbed$VideoInfo", true, loader),
            Class.forName("net.dv8tion.jda.api.entities.MessageEmbed$Footer", true, loader),
            Class.forName("net.dv8tion.jda.api.entities.MessageEmbed$ImageInfo", true, loader),
            List.class
        );
        return messageEmbedC.newInstance(
            null, // URL
            null, // Title
            description, // Description
            null, // Type
            null, // Timestamp
            color, // Color
            null, // Thumbnail
            null, // SiteProvider
            null, // Author
            null, // VideoInfo
            null, // Footer
            null, // Image
            null  // Fields
        );
    }


    public void sendBotMessage(String content, Object embed) throws Exception {
    	CompletableFuture<String> future = new CompletableFuture<>();
        if (config.getLong("Discord.ChannelId", 0)==0 || !isDiscord) {
        	future.complete(null);
            return;
        }
    	String channelId = Long.toString(config.getLong("Discord.ChannelId"));

        Method getTextChannelById = jdaInstance.getClass().getMethod("getTextChannelById", String.class);
        Object channel = getTextChannelById.invoke(jdaInstance, channelId);

        if (channel == null) {
        	future.complete(null);
            return;
        }
    	if (embed != null) {
            Method sendMessageEmbeds = channel.getClass().getMethod("sendMessageEmbeds", entityMessageEmbedClazz);
            Object messageAction = sendMessageEmbeds.invoke(channel, embed);

            Method queue = messageAction.getClass().getMethod("queue", Consumer.class, Consumer.class);
            queue.invoke(messageAction,
                (Consumer<Object>) response -> {
                    // 特に何もしない
                },
                (Consumer<Throwable>) failure -> logger.error("Failed to send embedded message: " + failure.getMessage())
            );
        }
    	if (content != null && !content.isEmpty()) {
            Method sendMessage = channel.getClass().getMethod("sendMessage", String.class);
            Object messageAction = sendMessage.invoke(channel, content);

            Method queue = messageAction.getClass().getMethod("queue", Consumer.class, Consumer.class);
            queue.invoke(messageAction,
                (Consumer<Object>) response -> {
                    // 特に何もしない
                },
                (Consumer<Throwable>) failure -> logger.error("Failed to send text message: " + failure.getMessage())
            );
    	}
    }


    public void sendBotMessage(String content) throws Exception {
    	sendBotMessage(content, null);
    }


    public void sendBotMessage(Object embed) throws Exception {
    	sendBotMessage(null, embed);
    }
}
