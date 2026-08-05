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
import org.bukkit.command.TabCompleter;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.HashMap;
import java.util.Map;
import java.util.Locale;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

@NullMarked
public class Cmail extends JavaPlugin implements CommandExecutor, TabCompleter, Listener {
    private DatabaseManager db;
    private MailGUI gui;
    private final Map<UUID, Long> cooldowns = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.db = new DatabaseManager(this);
        this.gui = new MailGUI(this);

        org.bukkit.command.PluginCommand cmd = getCommand("cmail");
        if (cmd != null) {
            cmd.setExecutor(this);
            cmd.setTabCompleter(this);
        }
        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getPluginManager().registerEvents(gui, this);

        // Vault Setup
        if (getServer().getPluginManager().getPlugin("Vault") != null) {
            if (VaultHook.setup()) {
                Bukkit.getConsoleSender().sendMessage("§e[Cmail] §a[Vault] 成功連接至經濟系統。");
            } else {
                Bukkit.getConsoleSender().sendMessage("§e[Cmail] §e[Vault] 連接經濟系統失敗。");
            }
        } else {
            Bukkit.getConsoleSender().sendMessage("§e[Cmail] §e[Vault] 未檢測到 Vault 插件，經濟系統功能將停用。");
        }

        // Cleanup task
        if (getConfig().getBoolean("expiration.enabled", true)) {
            int days = getConfig().getInt("expiration.days", 30);
            boolean deleteUnread = getConfig().getBoolean("expiration.delete-unread", false);
            Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> db.cleanupExpiredMails(days, deleteUnread), 100L, 1728000L);
        }

        // Colored console logs
        Bukkit.getConsoleSender().sendMessage("§e[Cmail] §b==========================================");
        Bukkit.getConsoleSender().sendMessage("§e[Cmail] §a      Cmail 郵件系統已載入啟用 §e(v" + getPluginMeta().getVersion() + ")");
        Bukkit.getConsoleSender().sendMessage("§e[Cmail] §a      作者: Clre20 | 支援版本: 1.21.11+");
        Bukkit.getConsoleSender().sendMessage("§e[Cmail] §b==========================================");
    }

    @Override
    public void onDisable() {
        if (db != null) {
            db.close();
        }
        Bukkit.getConsoleSender().sendMessage("§e[Cmail] §c==========================================");
        Bukkit.getConsoleSender().sendMessage("§e[Cmail] §c      Cmail 郵件系統已卸載關閉");
        Bukkit.getConsoleSender().sendMessage("§e[Cmail] §c==========================================");
    }

    public DatabaseManager getDb() { return db; }

    public String getFormattedMessage(String path) {
        String msg = getConfig().getString(path, "");
        String prefix = getConfig().getString("prefix", "");
        msg = msg.replace("%prefix%", prefix);
        return LegacyComponentSerializer.legacySection().serialize(
                LegacyComponentSerializer.legacyAmpersand().deserialize(msg)
        );
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length > 0) {
            if (args[0].equalsIgnoreCase("help")) {
                if (sender instanceof Player player && !player.hasPermission("cmail.use")) {
                    player.sendMessage(getFormattedMessage("messages.no-permission"));
                    return true;
                }
                for (String line : getConfig().getStringList("messages.help-menu")) {
                    sender.sendMessage(LegacyComponentSerializer.legacySection().serialize(
                            LegacyComponentSerializer.legacyAmpersand().deserialize(line)
                    ));
                }
                return true;
            } else if (args[0].equalsIgnoreCase("reload")) {
                if (sender instanceof Player player && !player.hasPermission("cmail.admin")) {
                    player.sendMessage(getFormattedMessage("messages.no-permission"));
                    return true;
                }
                reloadConfig();
                sender.sendMessage(getFormattedMessage("messages.config-reloaded"));
                Bukkit.getConsoleSender().sendMessage("§e[Cmail] §a[Config] 設定檔已成功重新載入！由 " + sender.getName() + " 執行。");
                return true;
            } else if (args[0].equalsIgnoreCase("consolesend")) {
                if (sender instanceof Player player && !player.hasPermission("cmail.admin")) {
                    player.sendMessage(getFormattedMessage("messages.no-permission"));
                    return true;
                }
                
                if (args.length < 3) {
                    sender.sendMessage("§e[Cmail] §c用法: /cmail consolesend <玩家1,玩家2,...> <訊息內容> [物品材質:格位:數量] ...");
                    return true;
                }
                
                String targetsArg = args[1];
                String[] targetNames = targetsArg.split(",");
                
                // Parse items from the end of arguments list
                List<ItemStack> attachments = new ArrayList<>();
                for (int i = 0; i < 27; i++) {
                    attachments.add(null);
                }
                
                int itemArgsCount = 0;
                for (int i = args.length - 1; i >= 2; i--) {
                    String arg = args[i];
                    String[] parts = arg.split(":");
                    if (parts.length == 3) {
                        Material mat = Material.matchMaterial(parts[0].toUpperCase(Locale.ROOT));
                        if (mat != null) {
                            try {
                                int slot = Integer.parseInt(parts[1]);
                                int amount = Integer.parseInt(parts[2]);
                                if (slot >= 0 && slot < 27 && amount > 0) {
                                    ItemStack item = new ItemStack(mat, Math.min(amount, mat.getMaxStackSize()));
                                    attachments.set(slot, item);
                                    itemArgsCount++;
                                    continue;
                                }
                            } catch (NumberFormatException ignored) {}
                        }
                    }
                    break;
                }
                
                StringBuilder msgBuilder = new StringBuilder();
                int messageEndIndex = args.length - itemArgsCount;
                for (int i = 2; i < messageEndIndex; i++) {
                    msgBuilder.append(args[i]).append(" ");
                }
                String message = msgBuilder.toString().trim();
                
                if (message.isEmpty()) {
                    sender.sendMessage("§e[Cmail] §c郵件訊息內容不能為空。");
                    return true;
                }
                
                UUID consoleUUID = new UUID(0L, 0L);
                
                for (String name : targetNames) {
                    String targetName = name.trim();
                    if (targetName.isEmpty()) continue;
                    
                    OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
                    UUID receiverUUID = target.getUniqueId();
                    
                    db.saveMail(consoleUUID, receiverUUID, message, attachments).thenRun(() -> {
                        Player onlineTarget = Bukkit.getPlayer(receiverUUID);
                        if (onlineTarget != null && onlineTarget.isOnline()) {
                            String notify = getFormattedMessage("messages.login-notify").replace("%n%", "1");
                            onlineTarget.sendMessage(notify);
                        }
                    });
                }
                
                sender.sendMessage("§e[Cmail] §a已成功發送指令郵件給指定玩家。");
                return true;
            } else if (args[0].equalsIgnoreCase("savepack")) {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("§e[Cmail] §c此指令只能由玩家在遊戲內執行。");
                    return true;
                }
                if (!player.hasPermission("cmail.admin")) {
                    player.sendMessage(getFormattedMessage("messages.no-permission"));
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage("§e[Cmail] §c用法: /cmail savepack <禮包名稱>");
                    return true;
                }
                String packName = args[1].trim();
                if (packName.isEmpty()) {
                    player.sendMessage("§e[Cmail] §c禮包名稱不能為空。");
                    return true;
                }
                
                gui.getActivePackEditors().put(player.getUniqueId(), packName);
                
                org.bukkit.inventory.Inventory inv = Bukkit.createInventory(null, 36, LegacyComponentSerializer.legacyAmpersand().deserialize("&9打包禮包: " + packName));
                
                ItemStack bg = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
                org.bukkit.inventory.meta.ItemMeta bgMeta = bg.getItemMeta();
                if (bgMeta != null) {
                    bgMeta.displayName(LegacyComponentSerializer.legacyAmpersand().deserialize(" "));
                    bg.setItemMeta(bgMeta);
                }
                for (int i = 27; i < 35; i++) {
                    inv.setItem(i, bg);
                }
                
                ItemStack saveBtn = new ItemStack(Material.GREEN_STAINED_GLASS_PANE);
                org.bukkit.inventory.meta.ItemMeta btnMeta = saveBtn.getItemMeta();
                if (btnMeta != null) {
                    btnMeta.displayName(LegacyComponentSerializer.legacyAmpersand().deserialize("&a[儲存並關閉]"));
                    saveBtn.setItemMeta(btnMeta);
                }
                inv.setItem(35, saveBtn);
                
                java.io.File packFile = new java.io.File(getDataFolder(), "packs/" + packName + ".yml");
                if (packFile.exists()) {
                    org.bukkit.configuration.file.YamlConfiguration packConfig = 
                        org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(packFile);
                    List<?> items = packConfig.getList("items");
                    if (items != null) {
                        for (int i = 0; i < Math.min(items.size(), 27); i++) {
                            if (items.get(i) instanceof ItemStack stack) {
                                inv.setItem(i, stack);
                            }
                        }
                    }
                }
                
                player.openInventory(inv);
                player.sendMessage("§e[Cmail] §a請放入您要打包的道具，關閉視窗後將自動存檔。");
                return true;
            } else if (args[0].equalsIgnoreCase("sendpack")) {
                if (sender instanceof Player player && !player.hasPermission("cmail.admin")) {
                    player.sendMessage(getFormattedMessage("messages.no-permission"));
                    return true;
                }
                if (args.length < 4) {
                    sender.sendMessage("§e[Cmail] §c用法: /cmail sendpack <玩家1,玩家2,... 或 all> <禮包名稱> <訊息內容>");
                    return true;
                }
                
                String targetsArg = args[1];
                boolean isAll = targetsArg.equalsIgnoreCase("all");
                if (isAll && !sender.hasPermission("cmail.admin")) {
                    sender.sendMessage(getFormattedMessage("messages.no-permission"));
                    return true;
                }
                
                String packName = args[2].trim();
                
                StringBuilder msgBuilder = new StringBuilder();
                for (int i = 3; i < args.length; i++) {
                    msgBuilder.append(args[i]).append(" ");
                }
                String message = msgBuilder.toString().trim();
                
                if (message.isEmpty()) {
                    sender.sendMessage("§e[Cmail] §c郵件訊息內容不能為空。");
                    return true;
                }
                
                java.io.File packFile = new java.io.File(getDataFolder(), "packs/" + packName + ".yml");
                if (!packFile.exists()) {
                    sender.sendMessage("§e[Cmail] §c找不到名為 &e" + packName + " §c的禮包。請先在遊戲內執行 /cmail savepack " + packName + " 進行設定。");
                    return true;
                }
                
                org.bukkit.configuration.file.YamlConfiguration packConfig = 
                    org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(packFile);
                List<?> itemsRaw = packConfig.getList("items");
                if (itemsRaw == null) {
                    sender.sendMessage("§e[Cmail] §c禮包 &e" + packName + " §c的內容格式不正確或為空。");
                    return true;
                }
                
                List<ItemStack> attachments = new ArrayList<>();
                for (int i = 0; i < 27; i++) {
                    attachments.add(null);
                }
                
                for (int i = 0; i < Math.min(itemsRaw.size(), 27); i++) {
                    if (itemsRaw.get(i) instanceof ItemStack stack) {
                        attachments.set(i, stack);
                    }
                }
                
                UUID consoleUUID = new UUID(0L, 0L);
                
                if (isAll) {
                    db.saveBroadcastMail(consoleUUID, message, attachments).thenRun(() -> {
                        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                            String notify = getFormattedMessage("messages.login-notify").replace("%n%", "1");
                            onlinePlayer.sendMessage(notify);
                        }
                    });
                    sender.sendMessage("§e[Cmail] §a已成功發送禮包郵件給全服所有玩家。");
                    return true;
                }
                
                String[] targetNames = targetsArg.split(",");
                for (String name : targetNames) {
                    String targetName = name.trim();
                    if (targetName.isEmpty()) continue;
                    
                    OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
                    UUID receiverUUID = target.getUniqueId();
                    
                    db.saveMail(consoleUUID, receiverUUID, message, attachments).thenRun(() -> {
                        Player onlineTarget = Bukkit.getPlayer(receiverUUID);
                        if (onlineTarget != null && onlineTarget.isOnline()) {
                            String notify = getFormattedMessage("messages.login-notify").replace("%n%", "1");
                            onlineTarget.sendMessage(notify);
                        }
                    });
                }
                
                sender.sendMessage("§e[Cmail] §a已成功發送禮包郵件給指定玩家。");
                return true;
            }
        }

        // Other commands require sender to be a Player
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§e[Cmail] §c此指令只能由玩家在遊戲內執行。");
            return true;
        }

        if (args.length == 0) {
            if (!player.hasPermission("cmail.use")) {
                player.sendMessage(getFormattedMessage("messages.no-permission"));
                return true;
            }
            gui.openMainMenu(player);
        } else if (args[0].equalsIgnoreCase("mail")) {
            if (!player.hasPermission("cmail.use")) {
                player.sendMessage(getFormattedMessage("messages.no-permission"));
                return true;
            }
            gui.openMainMenu(player);
        } else if (args[0].equalsIgnoreCase("admin")) {
            if (!player.hasPermission("cmail.admin")) {
                player.sendMessage(getFormattedMessage("messages.no-permission"));
                return true;
            }
            gui.openAdminMainMenu(player, 0);
        } else if (args[0].equalsIgnoreCase("send")) {
            if (!player.hasPermission("cmail.use")) {
                player.sendMessage(getFormattedMessage("messages.no-permission"));
                return true;
            }

            // 檢查參數長度: /cmail send [收件者] [文字]
            if (args.length < 3) {
                player.sendMessage(getFormattedMessage("messages.usage"));
                return true;
            }

            String targetName = args[1];
            boolean isAll = targetName.equalsIgnoreCase("all");

            if (isAll) {
                if (!player.hasPermission("cmail.admin")) {
                    player.sendMessage(getFormattedMessage("messages.no-permission"));
                    return true;
                }
            } else {
                // Cooldown check
                if (getConfig().getBoolean("cooldown.enabled", true) && !player.hasPermission("cmail.admin")) {
                    long now = System.currentTimeMillis();
                    long cooldownEnd = cooldowns.getOrDefault(player.getUniqueId(), 0L);
                    if (now < cooldownEnd) {
                        long remaining = (cooldownEnd - now) / 1000L;
                        player.sendMessage(getFormattedMessage("messages.cooldown").replace("%time%", String.valueOf(remaining)));
                        return true;
                    }
                }
            }

            // 組合後續所有文字作為訊息內容
            StringBuilder msgBuilder = new StringBuilder();
            for (int i = 2; i < args.length; i++) {
                msgBuilder.append(args[i]).append(" ");
            }
            String message = msgBuilder.toString().trim();

            if (isAll) {
                // 開啟附件 GUI，傳入 Nil UUID，並設定 isBroadcast = true
                gui.openSendGUI(player, new UUID(0L, 0L), message, true);
            } else {
                // 取得離線玩家資訊
                OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);

                // 驗證玩家是否曾加入過伺服器
                if (!target.hasPlayedBefore() && !target.isOnline()) {
                    player.sendMessage(getFormattedMessage("messages.player-not-found").replace("%player%", targetName));
                    return true;
                }

                // 開啟附件 GUI，並傳入收件者的 UUID
                gui.openSendGUI(player, target.getUniqueId(), message, false);

                // Set Cooldown
                if (getConfig().getBoolean("cooldown.enabled", true) && !player.hasPermission("cmail.admin")) {
                    int seconds = getConfig().getInt("cooldown.seconds", 10);
                    cooldowns.put(player.getUniqueId(), System.currentTimeMillis() + (seconds * 1000L));
                }
            }
        } else {
            if (player.hasPermission("cmail.use")) {
                player.sendMessage(getFormattedMessage("messages.usage"));
            } else {
                player.sendMessage(getFormattedMessage("messages.no-permission"));
            }
        }
        return true;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        db.getMails(player.getUniqueId(), true).thenAccept(mails -> {
            if (!mails.isEmpty()) {
                String msg = getFormattedMessage("messages.login-notify").replace("%n%", String.valueOf(mails.size()));
                player.sendMessage(msg);
            }
        });
    }

    @Override
    public @Nullable List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> suggestions = new ArrayList<>();
        if (args.length == 1) {
            List<String> subCommands = new ArrayList<>();
            if (sender.hasPermission("cmail.use")) {
                subCommands.add("mail");
                subCommands.add("send");
                subCommands.add("help");
            }
            if (sender.hasPermission("cmail.admin")) {
                subCommands.add("reload");
                subCommands.add("admin");
                subCommands.add("consolesend");
                subCommands.add("savepack");
                subCommands.add("sendpack");
            }
            String currentArg = args[0].toLowerCase();
            for (String sub : subCommands) {
                if (sub.startsWith(currentArg)) {
                    suggestions.add(sub);
                }
            }
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("send") || args[0].equalsIgnoreCase("consolesend") || args[0].equalsIgnoreCase("sendpack"))) {
            if (sender.hasPermission("cmail.use") || sender.hasPermission("cmail.admin")) {
                String currentArg = args[1].toLowerCase();
                if (args[0].equalsIgnoreCase("sendpack") && (sender.hasPermission("cmail.admin") || sender.isOp())) {
                    if ("all".startsWith(currentArg)) {
                        suggestions.add("all");
                    }
                }
                if (args[0].equalsIgnoreCase("send") && (sender.hasPermission("cmail.admin") || sender.isOp())) {
                    if ("all".startsWith(currentArg)) {
                        suggestions.add("all");
                    }
                }
                for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                    if (onlinePlayer.getName().toLowerCase().startsWith(currentArg)) {
                        suggestions.add(onlinePlayer.getName());
                    }
                }
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("sendpack")) {
            if (sender.hasPermission("cmail.admin")) {
                java.io.File packsFolder = new java.io.File(getDataFolder(), "packs");
                if (packsFolder.exists() && packsFolder.isDirectory()) {
                    java.io.File[] files = packsFolder.listFiles();
                    if (files != null) {
                        String currentArg = args[2].toLowerCase();
                        for (java.io.File file : files) {
                            if (file.isFile() && file.getName().endsWith(".yml")) {
                                String packName = file.getName().substring(0, file.getName().length() - 4);
                                if (packName.toLowerCase().startsWith(currentArg)) {
                                    suggestions.add(packName);
                                }
                            }
                        }
                    }
                }
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("savepack")) {
            if (sender.hasPermission("cmail.admin")) {
                java.io.File packsFolder = new java.io.File(getDataFolder(), "packs");
                if (packsFolder.exists() && packsFolder.isDirectory()) {
                    java.io.File[] files = packsFolder.listFiles();
                    if (files != null) {
                        String currentArg = args[1].toLowerCase();
                        for (java.io.File file : files) {
                            if (file.isFile() && file.getName().endsWith(".yml")) {
                                String packName = file.getName().substring(0, file.getName().length() - 4);
                                if (packName.toLowerCase().startsWith(currentArg)) {
                                    suggestions.add(packName);
                                }
                            }
                        }
                    }
                }
            }
        }
        return suggestions;
    }
}