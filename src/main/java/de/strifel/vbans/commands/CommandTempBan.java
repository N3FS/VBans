package de.strifel.vbans.commands;

import com.velocitypowered.api.command.Command;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.ConsoleCommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import de.strifel.vbans.Util;
import de.strifel.vbans.VBans;
import de.strifel.vbans.database.Ban;
import de.strifel.vbans.database.DatabaseConnection;
import net.kyori.text.TextComponent;
import net.kyori.text.format.TextColor;
import org.checkerframework.checker.nullness.qual.NonNull;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class CommandTempBan implements Command {
    private final ProxyServer server;
    private final DatabaseConnection database;
    private final VBans vBans;
    private final String DEFAULT_REASON;
    private final String BANNED_BROADCAST;

    public CommandTempBan(VBans vBans) {
        this.server = vBans.getServer();
        database = vBans.getDatabaseConnection();
        this.vBans = vBans;
        this.DEFAULT_REASON = vBans.getMessages().getString("StandardBanMessage");
        this.BANNED_BROADCAST = vBans.getMessages().getString("BannedBroadcast");
    }

    public void execute(CommandSource commandSource, @NonNull String[] strings) {
        if (strings.length > 1) {
            long duration = getBanDuration(strings[1]);
            if (duration == 0) {
                commandSource.sendMessage(TextComponent.of("Invalid duration! Us d, m, h or s as suffix for time!").color(TextColor.RED));
                return;
            }
            long end = (System.currentTimeMillis() / 1000) + duration;
            String reason = DEFAULT_REASON;
            if (strings.length > 2 && commandSource.hasPermission("VBans.temp.reason")) {
                reason = String.join(" ", Arrays.copyOfRange(strings, 2, strings.length));
            }
            Optional<Player> oPlayer = server.getPlayer(strings[0]);
            if (oPlayer.isPresent()) {
                Player player = oPlayer.get();
                if (!player.hasPermission("VBans.prevent") || commandSource instanceof ConsoleCommandSource) {

                    player.disconnect(Util.formatBannedMessage(commandSource instanceof ConsoleCommandSource ? "Console" : ((Player) commandSource).getUsername(), reason, end));
                    try {
                        database.addBan(player.getUniqueId().toString(), end, commandSource instanceof ConsoleCommandSource ? "Console" : ((Player) commandSource).getUniqueId().toString(), reason);
                    } catch (SQLException e) {
                        e.printStackTrace();
                        commandSource.sendMessage(TextComponent.of("Your ban can not be registered.").color(TextColor.RED));
                        return;
                    }
                    commandSource.sendMessage(TextComponent.of("You banned " + strings[0] + " for " + duration + " seconds!").color(TextColor.YELLOW));
                    Util.broadcastMessage(BANNED_BROADCAST
                                    .replace("$bannedBy", commandSource instanceof ConsoleCommandSource ? "Console" : ((Player) commandSource).getUsername())
                                    .replace("$player", strings[0])
                                    .replace("$bannedUntil", Util.UNBAN_DATE_FORMAT.format(end * 1000))
                                    .replace("$reason", reason)
                            , "VBans.bannedBroadcast", server);
                } else {
                    commandSource.sendMessage(TextComponent.of("You are not allowed to Ban this player!").color(TextColor.RED));
                }
            } else {
                try {
                    String uuid = database.getUUID(strings[0]);
                    if (uuid != null) {
                        if (!Util.hasOfflineProtectBanPermission(uuid, vBans) || commandSource instanceof ConsoleCommandSource) {
                            Ban currentBan = database.getBan(uuid);
                            if (currentBan == null) {
                                database.addBan(uuid, end, commandSource instanceof ConsoleCommandSource ? "Console" : ((Player) commandSource).getUniqueId().toString(), reason);
                                commandSource.sendMessage(TextComponent.of("You banned " + strings[0] + " for " + duration + " seconds!").color(TextColor.YELLOW));
                                Util.broadcastMessage(BANNED_BROADCAST
                                                .replace("$bannedBy", commandSource instanceof ConsoleCommandSource ? "Console" : ((Player) commandSource).getUsername())
                                                .replace("$player", strings[0])
                                                .replace("$bannedUntil", Util.UNBAN_DATE_FORMAT.format(end * 1000))
                                                .replace("$reason", reason)
                                        , "VBans.bannedBroadcast", server);
                            } else {
                                commandSource.sendMessage(TextComponent.of(strings[0] + " is already banned until " + (currentBan.getUntil() == -1 ? "the end of his life." : Util.UNBAN_DATE_FORMAT.format(currentBan.getUntil() * 1000))).color(TextColor.RED));
                            }
                        } else {
                            commandSource.sendMessage(TextComponent.of("You are not allowed to ban this player!").color(TextColor.RED));
                        }
                    } else {
                        commandSource.sendMessage(TextComponent.of("Player not found!").color(TextColor.RED));
                    }
                } catch (SQLException e) {
                    commandSource.sendMessage(TextComponent.of("An database issue occurred!").color(TextColor.RED));
                }
            }
        } else {
            commandSource.sendMessage(TextComponent.of("Usage: /tempban <player> <time> [reason]").color(TextColor.RED));
        }
    }

    public List<String> suggest(CommandSource source, @NonNull String[] currentArgs) {
        if (currentArgs.length == 1) {
            return Util.getAllPlayernames(server);
        }
        if (currentArgs.length == 2) {
            return Arrays.asList("30d", "12h", "30m", "5s");
        }
        return new ArrayList<String>();
    }

    public boolean hasPermission(CommandSource source, @NonNull String[] args) {
        return source.hasPermission("VBans.temp");
    }

    public static long getBanDuration(String durationString) {
        if (Util.isInt(durationString)) {
            return 60 * 60 * 24 * Integer.parseInt(durationString);
        } else if (durationString.endsWith("d")) {
            durationString = durationString.replace("d", "");
            if (!Util.isInt(durationString)) return 0;
            return 60 * 60 * 24 * Integer.parseInt(durationString);
        } else if (durationString.endsWith("h")) {
            durationString = durationString.replace("h", "");
            if (!Util.isInt(durationString)) return 0;
            return 60 * 60 * Integer.parseInt(durationString);
        } else if (durationString.endsWith("m")) {
            durationString = durationString.replace("m", "");
            if (!Util.isInt(durationString)) return 0;
            return 60 * Integer.parseInt(durationString);
        } else if (durationString.endsWith("s")) {
            durationString = durationString.replace("s", "");
            if (!Util.isInt(durationString)) return 0;
            return Integer.parseInt(durationString);
        } else {
            return 0;
        }
    }

}
