package lavalink.client.io.D4J;

import discord4j.common.util.Snowflake;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.Member;
import discord4j.core.object.entity.channel.GuildChannel;
import discord4j.core.object.entity.channel.VoiceChannel;
import discord4j.rest.util.Permission;
import discord4j.rest.util.PermissionSet;
import discord4j.voice.AudioProvider;
import discord4j.voice.VoiceGatewayOptions;
import lavalink.client.LavalinkUtil;
import lavalink.client.io.GuildUnavailableException;
import lavalink.client.io.Link;
import lavalink.client.io.jda.JdaLink;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.rmi.server.ExportException;
import java.text.MessageFormat;

public class D4JLink extends Link {

    private static final Logger log = LoggerFactory.getLogger(JdaLink.class);
    private D4JLavalink lavalink;

    protected D4JLink(D4JLavalink lavalink, String guildId) {
        super(lavalink, guildId);
    }


    public void connect(VoiceChannel channel, boolean checkChannel) throws Exception {

        Guild guild = channel.getGuild().block();

        if (guild == null) throw new IllegalArgumentException("Channel provided is not valid Guild voice channel");

        if (!guild.getId().equals(Snowflake.of(getGuildId())))
            throw new IllegalArgumentException("The provided VoiceChannel is not a part of the Guild that this AudioManager handle " +
                "Please provide a VoiceChannel from the proper Guild");

        if (guild.isUnavailable()) throw new GuildUnavailableException("Cannot open an Audio Connection with an unavailable guild. " +
                "Please wait until this Guild is available to open a connection.");

        Member member = guild.getSelfMember().block();

        if (member == null) throw new Exception("I was unable to fetch self from guild. ");

        PermissionSet permissionSet =  channel.getEffectivePermissions(member.getId()).block();

        if (!permissionSet.contains(Permission.CONNECT) || !permissionSet.contains(Permission.SPEAK)) {
            throw new ExportException(MessageFormat.format("Insufficient permission for {0} Channel!", channel.getId().asString()));
        }


        setState(State.CONNECTING);
        queueAudioConnect(channel.getId().asLong());
    }


    @Override
    protected void removeConnection() {
        // pretty sure D4J also handles this!
    }

    @Override
    protected void queueAudioDisconnect() {
        GatewayDiscordClient clinet = this.lavalink.getD4J(LavalinkUtil.getShardFromSnowflake(getGuildId(), this.lavalink.getNumShards()));

        if (getChannel() == null) {
            log.warn("Found no voice channel to disconnect from");
            return;
        }

        GuildChannel guildChannel = clinet.getGuildChannels(Snowflake.of(getChannel())).blockFirst();

        if (guildChannel instanceof VoiceChannel) {
            ((VoiceChannel) guildChannel).sendDisconnectVoiceState().block();
        }
    }

    @Override
    protected void queueAudioConnect(long channelId) {
        GatewayDiscordClient clinet = this.lavalink.getD4J(LavalinkUtil.getShardFromSnowflake(getGuildId(), this.lavalink.getNumShards()));

        GuildChannel guildChannel = clinet.getGuildChannels(Snowflake.of(channelId)).blockFirst();

        if (guildChannel instanceof VoiceChannel) {
            ((VoiceChannel) guildChannel).join(spec -> {
                spec.setProvider(AudioProvider.NO_OP);
            }).block();
        }
    }
}
