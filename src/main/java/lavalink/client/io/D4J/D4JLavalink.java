package lavalink.client.io.D4J;

import discord4j.common.util.Snowflake;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.EventDispatcher;
import discord4j.core.event.domain.Event;
import discord4j.core.event.domain.VoiceServerUpdateEvent;
import discord4j.core.event.domain.VoiceStateUpdateEvent;
import discord4j.core.event.domain.channel.VoiceChannelDeleteEvent;
import discord4j.core.event.domain.guild.GuildDeleteEvent;
import discord4j.core.event.domain.lifecycle.ReconnectEvent;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.channel.GuildChannel;
import discord4j.core.object.entity.channel.VoiceChannel;
import edu.umd.cs.findbugs.annotations.Nullable;
import lavalink.client.io.Lavalink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.EventListener;
import java.util.Optional;
import java.util.function.Function;

public class D4JLavalink extends Lavalink<D4JLink> implements EventListener {

    private static final Logger log = LoggerFactory.getLogger(D4JLavalink.class);
    private GatewayDiscordClient d4jProvider;
    private boolean autoReconnect = true;
    private final D4JVoiceInterceptor voiceInterceptor;

    public D4JLavalink(@Nullable String userId, int numShards, GatewayDiscordClient d4jProvider) {
        super(userId, numShards);
        this.d4jProvider = d4jProvider;
        voiceInterceptor = new D4JVoiceInterceptor(this);
        // Subscribing to events
        subscribeToEvents();
    }

    private void subscribeToEvents() {

        // Not sure what this exactly does lol.
        GatewayDiscordClient client = this.getD4J();


        EventDispatcher dispatcher = client.getEventDispatcher();

        // All events we will need
        dispatcher.on(Event.class).subscribe(this::onEvent);
        dispatcher.on(VoiceStateUpdateEvent.class).subscribe(voiceInterceptor::onVoiceStateUpdate);
        dispatcher.on(VoiceServerUpdateEvent.class).subscribe(voiceInterceptor::onVoiceServerUpdate);

    }

    @SuppressWarnings("unused")
    public boolean getAutoReconnect() {
        return autoReconnect;
    }

    @SuppressWarnings("unused")
    public void setAutoReconnect(boolean autoReconnect) {
        this.autoReconnect = autoReconnect;
    }

    @Override
    protected D4JLink buildNewLink(String guildId) {
        return new D4JLink(this, guildId);
    }

    public GatewayDiscordClient getD4J() {
        return this.d4jProvider;
    }

    public void onEvent(Event event) {

        if (event instanceof ReconnectEvent) {
            if (autoReconnect) {
                getLinksMap().forEach((guildId, link) -> {
                    if (link.getLastChannel() != null) {
                        event.getClient().getGuildById(Snowflake.of(guildId)).subscribe(guild -> {
                            if (guild != null) {

                                GuildChannel channel = guild.getChannelById(Snowflake.of(guildId)).block();

                                // Check if channel is valid voice channel.
                                if (channel instanceof VoiceChannel) {
                                    try {
                                        link.connect((VoiceChannel) channel, false);
                                    } catch (Exception e) {
                                        log.warn("Filed to connect to voice channel", e);
                                    }
                                }

                            }
                        });
                    }
                });
            }
        } else if (event instanceof GuildDeleteEvent) {

            Optional<Guild> guild = ((GuildDeleteEvent) event).getGuild();

            if (guild.isPresent()) {
                D4JLink link = getLinksMap().get(guild.get().getId().asString());
                if (link == null) return;;
                link.removeConnection();
            }
        } else if (event instanceof VoiceChannelDeleteEvent) {
            Guild guild = ((VoiceChannelDeleteEvent) event).getChannel().getGuild().block();
            if (guild == null) return;

            D4JLink link = getLinksMap().get(guild.getId().asString());
            if (link == null) return;
            link.removeConnection();
        }

    }
}
