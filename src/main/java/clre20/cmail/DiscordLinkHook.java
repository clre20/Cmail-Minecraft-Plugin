package clre20.cmail;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * 以唯讀方式讀取 DiscordLink 插件的 SQLite 資料庫 (bindings 資料表)，
 * 取得所有已綁定 Discord 的 Minecraft 帳號 UUID。不需要修改 DiscordLink 本身。
 */
public final class DiscordLinkHook {
    public static final String TARGET_KEYWORD = "link_dc_player";
    /** 已綁定 Discord 且目前在線上的玩家 */
    public static final String ONLINE_TARGET_KEYWORD = "online_link_dc_player";

    private DiscordLinkHook() {}

    /** 是否為 link_dc_player 或 online_link_dc_player */
    public static boolean isKeyword(String arg) {
        return TARGET_KEYWORD.equalsIgnoreCase(arg) || isOnlineKeyword(arg);
    }

    public static boolean isOnlineKeyword(String arg) {
        return ONLINE_TARGET_KEYWORD.equalsIgnoreCase(arg);
    }

    /**
     * 取得所有已綁定 Discord 的玩家 UUID。
     * 必須在主執行緒呼叫 (會讀取 DiscordLink 的設定)，資料庫查詢在非同步執行緒進行。
     * 若找不到 DiscordLink 資料庫或讀取失敗，Future 會以例外結束。
     */
    public static CompletableFuture<List<UUID>> getLinkedPlayers(Cmail plugin) {
        File dbFile = resolveDatabaseFile(plugin);
        return CompletableFuture.supplyAsync(() -> {
            if (!dbFile.isFile()) {
                throw new IllegalStateException("找不到 DiscordLink 資料庫: " + dbFile.getAbsolutePath());
            }
            Properties props = new Properties();
            props.setProperty("open_mode", "1"); // SQLITE_OPEN_READONLY
            props.setProperty("busy_timeout", "3000");

            List<UUID> list = new ArrayList<>();
            try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath(), props);
                 PreparedStatement ps = conn.prepareStatement("SELECT DISTINCT uuid FROM bindings");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    try {
                        list.add(UUID.fromString(rs.getString("uuid")));
                    } catch (IllegalArgumentException | NullPointerException ignored) {}
                }
            } catch (Exception e) {
                throw new IllegalStateException("讀取 DiscordLink 資料庫失敗: " + e.getMessage(), e);
            }
            return list;
        });
    }

    private static File resolveDatabaseFile(Cmail plugin) {
        Plugin discordLink = Bukkit.getPluginManager().getPlugin("DiscordLink");
        File folder;
        FileConfiguration config;
        if (discordLink != null) {
            folder = discordLink.getDataFolder();
            config = discordLink.getConfig();
        } else {
            folder = new File(plugin.getDataFolder().getParentFile(), "DiscordLink");
            config = YamlConfiguration.loadConfiguration(new File(folder, "config.yml"));
        }
        String dbName = config.getString("database.sqlite-file-name", config.getString("sqlite-file-name", "database.db"));
        return new File(folder, dbName);
    }
}
