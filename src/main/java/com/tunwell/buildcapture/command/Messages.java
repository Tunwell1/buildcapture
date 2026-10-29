package com.tunwell.buildcapture.command;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.tunwell.buildcapture.BuildCapture;
import com.tunwell.buildcapture.network.ModNetwork;
import net.minecraft.command.CommandSource;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TranslationTextComponent;

import javax.annotation.Nullable;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.IllegalFormatException;
import java.util.Locale;
import java.util.Map;

/**
 * Builds chat components that also read correctly on a client without the mod.
 *
 * <p>A translation key only means something where the language file exists; a
 * vanilla or mod-less client has no {@code buildcapture} lang file and would
 * print {@code buildcapture.capture.list}. Players whose connection carries the
 * mod channel get a normal translatable component (localized on their side);
 * everyone else gets the English text resolved on the server from the mod's own
 * {@code en_us.json}.</p>
 */
public final class Messages {
    private static Map<String, String> english;

    private Messages() {
    }

    public static ITextComponent forSource(CommandSource source, String key, Object... args) {
        return forEntity(source.getEntity(), key, args);
    }

    public static ITextComponent forPlayer(ServerPlayerEntity player, String key, Object... args) {
        return forEntity(player, key, args);
    }

    private static ITextComponent forEntity(@Nullable Entity entity, String key, Object... args) {
        if (entity instanceof ServerPlayerEntity) {
            ServerPlayerEntity player = (ServerPlayerEntity) entity;
            if (ModNetwork.CHANNEL.isRemotePresent(player.connection.connection)) {
                return new TranslationTextComponent(key, args);
            }
        }
        return new StringTextComponent(plain(key, args));
    }

    /** Resolves a key and its arguments against the mod's English text. */
    private static String plain(String key, Object... args) {
        String template = english().getOrDefault(key, key);
        Object[] resolved = new Object[args.length];
        for (int index = 0; index < args.length; index++) {
            Object argument = args[index];
            resolved[index] = argument instanceof ITextComponent
                    ? plainComponent((ITextComponent) argument)
                    : argument;
        }
        try {
            return String.format(Locale.ROOT, template, resolved);
        } catch (IllegalFormatException exception) {
            return template;
        }
    }

    private static String plainComponent(ITextComponent component) {
        if (component instanceof TranslationTextComponent) {
            TranslationTextComponent translation = (TranslationTextComponent) component;
            return plain(translation.getKey(), translation.getArgs());
        }
        return component.getString();
    }

    private static synchronized Map<String, String> english() {
        if (english != null) {
            return english;
        }
        Map<String, String> loaded = new HashMap<>();
        try (InputStream input = Messages.class.getResourceAsStream(
                "/assets/buildcapture/lang/en_us.json")) {
            if (input != null) {
                JsonObject json = new JsonParser()
                        .parse(new InputStreamReader(input, StandardCharsets.UTF_8))
                        .getAsJsonObject();
                for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
                    loaded.put(entry.getKey(), entry.getValue().getAsString());
                }
            }
        } catch (Exception exception) {
            BuildCapture.LOGGER.warn("Failed to load the English fallback messages", exception);
        }
        english = loaded;
        return english;
    }
}
