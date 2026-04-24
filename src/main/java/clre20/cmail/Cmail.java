package clre20.cmail;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class Cmail extends JavaPlugin implements CommandExecutor, Listener {
    private DatabaseManager db;
    private MailGUI gui;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.db = new DatabaseManager(this);
        this.gui = new MailGUI(this);

        getCommand("mail").setExecutor(this);
        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getPluginManager().registerEvents(gui, this);
    }

    public DatabaseManager getDb() { return db; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) return true;

        if (args.length == 0) {
            gui.openMainMenu(player);
        } else if (args[0].equalsIgnoreCase("send")) {
            // 檢查參數長度: /mail send [收件者] [文字]
            if (args.length < 3) {
                player.sendMessage("§c用法: /mail send [收件者名稱] [訊息文字]");
                return true;
            }

            String targetName = args[1];
            // 取得離線玩家資訊
            OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);

            // 驗證玩家是否曾加入過伺服器
            if (!target.hasPlayedBefore() && !target.isOnline()) {
                player.sendMessage("§c找不到玩家 " + targetName + " 的紀錄，無法寄信。");
                return true;
            }

            // 組合後續所有文字作為訊息內容
            StringBuilder msgBuilder = new StringBuilder();
            for (int i = 2; i < args.length; i++) {
                msgBuilder.append(args[i]).append(" ");
            }
            String message = msgBuilder.toString().trim();

            // 開啟附件 GUI，並傳入收件者的 UUID
            gui.openSendGUI(player, target.getUniqueId(), message);
        }
        return true;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        db.getMails(player.getUniqueId(), true).thenAccept(mails -> {
            if (!mails.isEmpty()) {
                String msg = getConfig().getString("messages.login-notify").replace("%n%", String.valueOf(mails.size()));
                player.sendMessage(org.bukkit.ChatColor.translateAlternateColorCodes('&', msg));
            }
        });
    }
}