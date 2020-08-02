package lavalink.client.io.D4J;


import discord4j.core.event.domain.VoiceServerUpdateEvent;
import discord4j.core.event.domain.VoiceStateUpdateEvent;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.channel.VoiceChannel;
import lavalink.client.io.Link;
import org.json.JSONObject;

import javax.annotation.Nonnull;

public class D4JVoiceInterceptor {


    private final D4JLavalink lavalink;

    public D4JVoiceInterceptor(D4JLavalink lavalink) {
        this.lavalink = lavalink;
    }

    public void onVoiceServerUpdate(@Nonnull VoiceServerUpdateEvent update) {

        JSONObject content = new JSONObject(update);

        Guild guild = update.getGuild().block();

        if (guild == null)
            throw new IllegalArgumentException("Attempted to start audio connection with Guild that doesn't exist! JSON: " + content);

        // Maybe it will be null?
        lavalink.getLink(guild.getId().asString()).onVoiceServerUpdate(content, guild.getSelfMember().block().getVoiceState().block().getSessionId());
    }

    public boolean onVoiceStateUpdate(@Nonnull VoiceStateUpdateEvent update) {
        VoiceChannel channel = update.getCurrent().getChannel().block();
        D4JLink link = lavalink.getLink(update.getCurrent().getGuildId().asString());

        if (channel == null) {
            // Null channel means disconnected
            if (link.getState() != Link.State.DESTROYED) {
                link.onDisconnected();
            }
        } else {
            link.setChannel(channel.getId().asString()); // Change expected channel
        }

        return link.getState() == Link.State.CONNECTED;
    }
}
