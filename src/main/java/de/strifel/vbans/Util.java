package de.strifel.vbans;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import de.strifel.vbans.database.Ban;
import me.lucko.luckperms.api.LocalizedNode;
import me.lucko.luckperms.api.LuckPermsApi;
import me.lucko.luckperms.api.Tristate;
import me.lucko.luckperms.api.User;
import net.kyori.text.TextComponent;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;


public class Util {

    public static List<String> getAllPlayernames(ProxyServer server) {
        List<String> players = new ArrayList<>();
        for (Player player : server.getAllPlayers()) players.add(player.getUsername());
        return players;
    }

    static String BAN_TEMPLATE = "";
    public final static SimpleDateFormat UNBAN_DATE_FORMAT = new SimpleDateFormat("dd.MM.yyyy HH:mm");

    public static TextComponent formatBannedMessage(String bannedBy, String reason, long expires) {
        String bannedUntil = "forever";
        if (expires != -1) {
            bannedUntil = UNBAN_DATE_FORMAT.format(expires * 1000);
        }
        return TextComponent.of(BAN_TEMPLATE.replace("$bannedUntil", bannedUntil).replace("$reason", reason).replace("$bannedBy", bannedBy));
    }

    public static TextComponent formatBannedMessage(Ban ban, VBans vbans) {
        return formatBannedMessage(ban.getBannedByUsername(vbans), ban.getReason(), ban.getUntil());
    }

    public static boolean isInt(String str) {
        try {
            Integer.parseInt(str);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public static void broadcastMessage(String message, String permission, ProxyServer server){
        for (Player player : server.getAllPlayers()) {
            if (permission == null || player.hasPermission(permission)) {
                player.sendMessage(TextComponent.of(message));
            }
        }
        server.getConsoleCommandSource().sendMessage(TextComponent.of(message));
    }

    public static boolean hasOfflineProtectBanPermission(String uuid, VBans vBans) {
        if (vBans.luckPermsApi != null) {
            try {
                User user = ((LuckPermsApi) vBans.luckPermsApi).getUserManager().loadUser(UUID.fromString(uuid)).get();
                if (user != null) {
                    Set<LocalizedNode> nodes = user.getAllNodesFiltered(((LuckPermsApi) vBans.luckPermsApi).getContextManager().getStaticContexts());
                    boolean allowed = false;
                    for (LocalizedNode localizedNode : nodes) {
                        if (localizedNode.getPermission().equals("*")) {
                            if (localizedNode.getTristate() == Tristate.TRUE) {
                                allowed = true;
                            } else if (localizedNode.getTristate() == Tristate.FALSE) {
                                return false;
                            }
                        }
                        if (localizedNode.getPermission().equalsIgnoreCase("VBans.*")) {
                            if (localizedNode.getTristate() == Tristate.TRUE) {
                                allowed = true;
                            } else if (localizedNode.getTristate() == Tristate.FALSE) {
                                return false;
                            }
                        }
                        if (localizedNode.getPermission().equalsIgnoreCase("VBans.prevent")) {
                            if (localizedNode.getTristate() == Tristate.TRUE) {
                                allowed = true;
                            } else if (localizedNode.getTristate() == Tristate.FALSE) {
                                return false;
                            }
                        }
                    }
                    return allowed;
                }
            } catch (InterruptedException | ExecutionException e) {
                return false;
            }
        }
        return false;
    }
}
