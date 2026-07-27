package clre20.cmail;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import java.util.concurrent.CompletableFuture;

import java.util.*;

@NullMarked
public class MailGUI implements Listener {
    private final Cmail plugin;

    private record PendingMail(UUID receiverUUID, String message, boolean isBroadcast, List<ItemStack> attachments) {}
    private final Map<UUID, PendingMail> pendingMails = new HashMap<>();
    private final Map<UUID, Integer> adminPages = new HashMap<>();
    private final Map<UUID, Boolean> adminSafetyLock = new HashMap<>();
    private final Map<UUID, Integer> chatEditMail = new HashMap<>();
    private final Set<UUID> isRefreshing = new HashSet<>();
    private final Map<UUID, Integer> inboxPages = new HashMap<>();
    private final Map<UUID, String> activePackEditors = new HashMap<>();

    public MailGUI(Cmail plugin) { this.plugin = plugin; }

    public Map<UUID, String> getActivePackEditors() {
        return activePackEditors;
    }

    private Component getFormattedMessage(String path) {
        String msg = plugin.getConfig().getString(path, "");
        String prefix = plugin.getConfig().getString("prefix", "");
        msg = msg.replace("%prefix%", prefix);
        return color(msg);
    }

    private String getSenderDisplayName(java.util.UUID senderUUID) {
        if (senderUUID.equals(new java.util.UUID(0L, 0L))) {
            String rawName = plugin.getConfig().getString("system-sender-name", "&4[伺服器]");
            return LegacyComponentSerializer.legacySection().serialize(
                    LegacyComponentSerializer.legacyAmpersand().deserialize(rawName)
            );
        }
        String name = Bukkit.getOfflinePlayer(senderUUID).getName();
        return name != null ? name : "未知玩家 (" + senderUUID.toString().substring(0, 8) + ")";
    }

    private String getReceiverDisplayName(java.util.UUID receiverUUID) {
        if (receiverUUID.equals(new java.util.UUID(0L, 0L))) {
            return "§a[全服玩家]";
        }
        String name = Bukkit.getOfflinePlayer(receiverUUID).getName();
        return name != null ? name : "未知玩家 (" + receiverUUID.toString().substring(0, 8) + ")";
    }

    private void playSound(Player player, String configPath, String defaultSound) {
        if ("sounds.success".equals(configPath)) return;
        playSoundWithPitch(player, configPath, defaultSound, 1.0f);
    }

    private void playSoundWithPitch(Player player, String configPath, String defaultSound, float pitch) {
        if (plugin.getConfig().getBoolean("sounds.enabled", true)) {
            String soundStr = plugin.getConfig().getString(configPath, defaultSound);
            if (soundStr == null || soundStr.isEmpty()) return;
            
            String cleanStr = soundStr.toLowerCase(java.util.Locale.ROOT).trim();
            if (cleanStr.startsWith("minecraft:")) {
                cleanStr = cleanStr.substring(10);
            }
            
            java.util.List<String> candidates = new java.util.ArrayList<>();
            candidates.add(cleanStr);
            candidates.add(cleanStr.replace("_", "."));
            
            if (cleanStr.contains("experience_orb")) {
                String special = cleanStr.replace("experience_orb", "EXP_ORB_PLACEHOLDER")
                                         .replace("_", ".")
                                         .replace("EXP_ORB_PLACEHOLDER", "experience_orb");
                candidates.add(special);
            }
            if (cleanStr.contains("note_block")) {
                String special = cleanStr.replace("note_block", "NOTE_BLOCK_PLACEHOLDER")
                                         .replace("_", ".")
                                         .replace("NOTE_BLOCK_PLACEHOLDER", "note_block");
                candidates.add(special);
            }
            
            org.bukkit.Sound sound = null;
            for (String cand : candidates) {
                try {
                    org.bukkit.NamespacedKey key = org.bukkit.NamespacedKey.minecraft(cand);
                    sound = org.bukkit.Registry.SOUNDS.get(key);
                    if (sound != null) break;
                } catch (IllegalArgumentException ignored) {}
            }
            
            if (sound != null) {
                player.playSound(player.getLocation(), sound, 1f, pitch);
            } else {
                try {
                    player.playSound(player.getLocation(), cleanStr, 1f, pitch);
                } catch (Exception ignored) {}
            }
        }
    }

    private void playSuccessSound(Player player) {
        // Muted by user request
    }

    private void playTripleDing(Player player) {
        if (plugin.getConfig().getBoolean("sounds.enabled", true)) {
            playSoundWithPitch(player, "sounds.success", "BLOCK_NOTE_BLOCK_PLING", 1.0f);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                playSoundWithPitch(player, "sounds.success", "BLOCK_NOTE_BLOCK_PLING", 1.2f);
            }, 2L);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                playSoundWithPitch(player, "sounds.success", "BLOCK_NOTE_BLOCK_PLING", 1.4f);
            }, 4L);
        }
    }

    public void openMainMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, color(plugin.getConfig().getString("gui.main.title", "&9郵件管理")));
        
        Material inboxMat = Material.matchMaterial(plugin.getConfig().getString("gui.main.inbox-button.material", "CHEST"));
        if (inboxMat == null) inboxMat = Material.CHEST;
        inv.setItem(11, createItem(inboxMat, plugin.getConfig().getString("gui.main.inbox-button.name", "&a查看收件箱")));
        
        Material sendMat = Material.matchMaterial(plugin.getConfig().getString("gui.main.send-button.material", "PAPER"));
        if (sendMat == null) sendMat = Material.PAPER;
        List<String> sendLore = plugin.getConfig().getStringList("gui.main.send-button.lore");
        inv.setItem(15, createItem(sendMat, plugin.getConfig().getString("gui.main.send-button.name", "&b寄送新郵件"), sendLore.toArray(new String[0])));
        
        player.openInventory(inv);
        playSound(player, "sounds.open-gui", "UI_TOAST_IN");
    }

    public void openInbox(Player player) {
        openInbox(player, inboxPages.getOrDefault(player.getUniqueId(), 0));
    }

    public void openInbox(Player player, int page) {
        inboxPages.put(player.getUniqueId(), page);
        plugin.getDb().getMails(player.getUniqueId(), false).thenAccept(mails -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                Inventory inv = Bukkit.createInventory(null, 54, color(plugin.getConfig().getString("gui.inbox.title", "&9收件箱")));
                
                String unreadMatStr = plugin.getConfig().getString("gui.inbox.mail-item.unread-material", "BOOK");
                String readMatStr = plugin.getConfig().getString("gui.inbox.mail-item.read-material", "PAPER");
                Material unreadMat = Material.matchMaterial(unreadMatStr);
                if (unreadMat == null) unreadMat = Material.BOOK;
                Material readMat = Material.matchMaterial(readMatStr);
                if (readMat == null) readMat = Material.PAPER;
                
                String mailNameTemplate = plugin.getConfig().getString("gui.inbox.mail-item.name", "&f來自: &e%sender%");
                List<String> mailLoreTemplates = plugin.getConfig().getStringList("gui.inbox.mail-item.lore");
                
                int start = page * 45;
                int end = Math.min(start + 45, mails.size());
                
                for (int i = start; i < end; i++) {
                    MailData mail = mails.get(i);
                    String senderName = getSenderDisplayName(mail.sender());
                    
                    Material mailMat = mail.isRead() ? readMat : unreadMat;
                    String mailName = mailNameTemplate.replace("%sender%", senderName);
                    List<String> mailLore = new ArrayList<>();
                    for (String line : mailLoreTemplates) {
                        mailLore.add(line.replace("%message%", mail.message())
                                         .replace("%id%", String.valueOf(mail.id()))
                                         .replace("%status%", mail.isRead() ? "&8已讀" : "&a未讀"));
                    }
                    mailLore.add("");
                    mailLore.add("&e點擊打開並查看詳細郵件");
                    inv.addItem(createItem(mailMat, mailName, mailLore.toArray(new String[0])));
                }
                
                // Footer Row (45-53)
                ItemStack bg = createItem(Material.BLACK_STAINED_GLASS_PANE, " ");
                for (int i = 45; i < 54; i++) {
                    inv.setItem(i, bg);
                }
                
                if (page > 0) {
                    inv.setItem(45, createItem(Material.ARROW, "&e上一頁"));
                }
                
                // 一鍵領取按鈕
                inv.setItem(47, createItem(Material.CHEST, "&a一鍵領取所有附件", "&7點擊領取所有未讀信件中的附件"));
                
                inv.setItem(49, createItem(Material.PAPER, "&b第 " + (page + 1) + " 頁"));
                
                if (end < mails.size()) {
                    inv.setItem(53, createItem(Material.ARROW, "&e下一頁"));
                }
                
                player.openInventory(inv);
                playSound(player, "sounds.open-gui", "UI_TOAST_IN");
            });
        });
    }

    public void openSendGUI(Player player, UUID receiverUUID, String message, boolean isBroadcast) {
        pendingMails.put(player.getUniqueId(), new PendingMail(receiverUUID, message, isBroadcast, new ArrayList<>()));
        Inventory inv = Bukkit.createInventory(null, 36, color(plugin.getConfig().getString("gui.send.title", "&9放入附件 (按確認送出)")));

        Material bgMat = Material.matchMaterial(plugin.getConfig().getString("gui.send.background.material", "BLACK_STAINED_GLASS_PANE"));
        if (bgMat == null) bgMat = Material.BLACK_STAINED_GLASS_PANE;
        ItemStack blackGlass = createItem(bgMat, " ");
        
        Material confirmMat = Material.matchMaterial(plugin.getConfig().getString("gui.send.confirm-button.material", "GREEN_STAINED_GLASS_PANE"));
        if (confirmMat == null) confirmMat = Material.GREEN_STAINED_GLASS_PANE;
        ItemStack confirmBtn = createItem(confirmMat, plugin.getConfig().getString("gui.send.confirm-button.name", "&a[確認送出]"));
        
        Material cancelMat = Material.matchMaterial(plugin.getConfig().getString("gui.send.cancel-button.material", "RED_STAINED_GLASS_PANE"));
        if (cancelMat == null) cancelMat = Material.RED_STAINED_GLASS_PANE;
        ItemStack cancelBtn = createItem(cancelMat, plugin.getConfig().getString("gui.send.cancel-button.name", "&c[取消寄送]"));

        for (int i = 27; i < 36; i++) {
            if (i == 29) {
                inv.setItem(i, confirmBtn);
            } else if (i == 33) {
                inv.setItem(i, cancelBtn);
            } else {
                inv.setItem(i, blackGlass);
            }
        }
        player.openInventory(inv);
        playSound(player, "sounds.open-gui", "UI_TOAST_IN");
    }

    public void openMailRead(Player player, int id) {
        plugin.getDb().getMails(player.getUniqueId(), false).thenAccept(mails -> {
            mails.stream().filter(m -> m.id() == id).findFirst().ifPresent(mail -> {
                // Mark as read immediately when opened
                plugin.getDb().markAsRead(id);
                
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Inventory inv = Bukkit.createInventory(null, 54, color("&9讀取郵件 #" + id));
                    
                    // Header Row (0-8)
                    ItemStack bg = createItem(Material.BLACK_STAINED_GLASS_PANE, " ");
                    for (int i = 0; i < 9; i++) {
                        inv.setItem(i, bg);
                    }
                    
                    String senderName = getSenderDisplayName(mail.sender());
                    
                    ItemStack info = createItem(Material.BOOK, "&f信件詳細資訊",
                            "&7發送者: &e" + senderName,
                            "&7文字內容: &f" + mail.message(),
                            "&7狀態: &8已讀");
                    inv.setItem(0, info);
                    
                    ItemStack returnBtn = createItem(Material.RECOVERY_COMPASS, "&c退回郵件", "&7點擊將此信件與剩餘附件退回給原寄件者");
                    inv.setItem(2, returnBtn);
                    
                    ItemStack deleteBtn = createItem(Material.BARRIER, "&4刪除郵件", "&7點擊將永久刪除此信件與剩餘附件");
                    inv.setItem(4, deleteBtn);
                    
                    ItemStack claimBtn = createItem(Material.CHEST, "&a領取所有附件", "&7點擊自動領取該信件中所有附件");
                    inv.setItem(6, claimBtn);
                    
                    inv.setItem(8, createItem(Material.ARROW, "&7返回收件箱"));
                    
                    // Attachment slots (9-35)
                    List<ItemStack> attachments = mail.attachments();
                    for (int i = 0; i < Math.min(attachments.size(), 27); i++) {
                        inv.setItem(9 + i, attachments.get(i));
                    }
                    
                    // Footer Row (36-53)
                    for (int i = 36; i < 54; i++) {
                        inv.setItem(i, bg);
                    }
                    
                    isRefreshing.add(player.getUniqueId());
                    player.openInventory(inv);
                    playSound(player, "sounds.open-gui", "UI_TOAST_IN");
                });
            });
        });
    }

    public void openDeleteConfirmGUI(Player player, int id) {
        Inventory inv = Bukkit.createInventory(null, 27, color("&4確認刪除郵件 #" + id));
        ItemStack bg = createItem(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, bg);
        }
        
        ItemStack confirmBtn = createItem(Material.RED_STAINED_GLASS_PANE, "&4[確認刪除]", 
                "&7警告：此郵件中仍有附件未領取！", 
                "&7點擊後該郵件與附件將永久刪除，無法復原！");
        ItemStack cancelBtn = createItem(Material.GREEN_STAINED_GLASS_PANE, "&a[取消返回]", 
                "&7點擊取消刪除，返回郵件內容頁面。");
                
        inv.setItem(11, confirmBtn);
        inv.setItem(15, cancelBtn);
        
    isRefreshing.add(player.getUniqueId());
        player.openInventory(inv);
        playSound(player, "sounds.error", "BLOCK_NOTE_BLOCK_BASS");
    }

        public void openAdminDeleteConfirmGUI(Player player, int id) {
        Inventory inv = Bukkit.createInventory(null, 27, color("&4管理員確認刪除郵件 #" + id));
        ItemStack bg = createItem(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, bg);
        }
        
        ItemStack confirmBtn = createItem(Material.RED_STAINED_GLASS_PANE, "&4[確認刪除]", 
                "&7此操作將會永久刪除此信件（包含其原始附件）！", 
                "&7刪除後無法復原！");
        ItemStack cancelBtn = createItem(Material.GREEN_STAINED_GLASS_PANE, "&a[取消返回]", 
                "&7點擊取消刪除，返回管理員預覽頁面。");
                
        inv.setItem(11, confirmBtn);
        inv.setItem(15, cancelBtn);
        
        isRefreshing.add(player.getUniqueId());
        player.openInventory(inv);
        playSound(player, "sounds.error", "BLOCK_NOTE_BLOCK_BASS");
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        if (event.getView().title() == null) return;
        String title = LegacyComponentSerializer.legacySection().serialize(event.getView().title());

        if (title.contains("打包禮包: ")) {
            int slot = event.getRawSlot();
            if (slot >= 27 && slot <= 34) {
                event.setCancelled(true);
            } else if (slot == 35) {
                event.setCancelled(true);
                player.closeInventory();
            }
            return;
        }

        if (title.contains("確認支付手續費？")) {
            event.setCancelled(true);
            int slot = event.getRawSlot();
            if (slot == 11) {
                PendingMail pending = pendingMails.remove(player.getUniqueId());
                if (pending == null) {
                    player.closeInventory();
                    return;
                }
                long nonAirCount = pending.attachments().stream().filter(item -> item != null && item.getType() != Material.AIR).count();
                double cost = plugin.getConfig().getDouble("economy.cost-send-mail", 10.0)
                        + (nonAirCount * plugin.getConfig().getDouble("economy.cost-per-attachment", 5.0));
                
                if (VaultHook.getBalance(player) < cost) {
                    player.sendMessage(plugin.getFormattedMessage("messages.insufficient-funds").replace("%cost%", String.valueOf(cost)));
                    playSound(player, "sounds.error", "BLOCK_NOTE_BLOCK_BASS");
                    returnPendingMailAttachments(player, pending.attachments());
                    player.closeInventory();
                    return;
                }
                
                VaultHook.withdraw(player, cost);
                player.sendMessage(plugin.getFormattedMessage("messages.mail-cost-deducted").replace("%cost%", String.valueOf(cost)));
                sendPendingMail(player, pending);
            } else if (slot == 15) {
                PendingMail pending = pendingMails.remove(player.getUniqueId());
                if (pending != null) {
                    returnPendingMailAttachments(player, pending.attachments());
                }
                player.sendMessage(color("&c[Cmail] 郵件發送已取消，附件已退回背包。"));
                playSound(player, "sounds.error", "BLOCK_NOTE_BLOCK_BASS");
                player.closeInventory();
            }
            return;
        }

        if (title.contains("確認刪除郵件 #") && !title.contains("管理員")) {
            event.setCancelled(true);
            int id = Integer.parseInt(title.substring(title.lastIndexOf("#") + 1));
            int slot = event.getRawSlot();
            if (slot == 11) {
                plugin.getDb().deleteMail(id).thenRun(() -> {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        player.sendMessage(color("&a[Cmail] 郵件已成功刪除！"));
                        openInbox(player, 0);
                    });
                });
            } else if (slot == 15) {
                openMailRead(player, id);
            }
            return;
        }

        if (title.contains("管理員確認刪除郵件 #")) {
            event.setCancelled(true);
            int id = Integer.parseInt(title.substring(title.lastIndexOf("#") + 1));
            int slot = event.getRawSlot();
            if (slot == 11) {
                plugin.getDb().deleteMail(id).thenRun(() -> {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        player.sendMessage(color("&a[Cmail] 郵件已成功刪除！"));
                        openAdminMainMenu(player, adminPages.getOrDefault(player.getUniqueId(), 0));
                    });
                });
            } else if (slot == 15) {
                openMailPreview(player, id);
            }
            return;
        }

        if (title.contains("收件箱")) {
            event.setCancelled(true);
            int slot = event.getRawSlot();
            if (slot >= 0 && slot < 45) {
                ItemStack clickedItem = event.getCurrentItem();
                if (clickedItem == null || clickedItem.getItemMeta() == null) return;

                List<Component> lore = clickedItem.getItemMeta().lore();
                if (lore == null || lore.isEmpty()) return;

                // Parse ID from the lore's ID field
                for (Component comp : lore) {
                    String line = LegacyComponentSerializer.legacySection().serialize(comp);
                    if (line.contains("ID:")) {
                        try {
                            int id = Integer.parseInt(line.substring(line.lastIndexOf(" ") + 1));
                            openMailRead(player, id);
                            return;
                        } catch (Exception ignored) {}
                    }
                }
            } else if (slot == 45) {
                int page = inboxPages.getOrDefault(player.getUniqueId(), 0);
                if (page > 0) openInbox(player, page - 1);
            } else if (slot == 53) {
                int page = inboxPages.getOrDefault(player.getUniqueId(), 0);
                openInbox(player, page + 1);
            } else if (slot == 47) {
                // 一鍵領取所有附件
                plugin.getDb().getMails(player.getUniqueId(), false).thenAccept(mails -> {
                    List<ItemStack> allItems = new ArrayList<>();
                    for (MailData mail : mails) {
                        for (ItemStack item : mail.attachments()) {
                            if (item != null && item.getType() != Material.AIR) {
                                allItems.add(item);
                            }
                        }
                    }
                    if (allItems.isEmpty()) {
                        player.sendMessage(color("&c目前沒有任何郵件附件可以領取！"));
                        playSound(player, "sounds.error", "BLOCK_NOTE_BLOCK_BASS");
                        return;
                    }
                    // 檢查空間
                    int emptySlots = 0;
                    for (ItemStack stack : player.getInventory().getStorageContents()) {
                        if (stack == null || stack.getType() == Material.AIR) emptySlots++;
                    }
                    if (emptySlots < allItems.size()) {
                        player.sendMessage(color("&c你的背包空間不足！一鍵領取需要 " + allItems.size() + " 格空間。"));
                        playSound(player, "sounds.error", "BLOCK_NOTE_BLOCK_BASS");
                        return;
                    }
                    // 給予物品
                    for (ItemStack item : allItems) {
                        player.getInventory().addItem(item);
                    }
                    // 標記已讀並清空附件
                    List<CompletableFuture<Void>> futures = new ArrayList<>();
                    for (MailData mail : mails) {
                        boolean hasAttachments = mail.attachments().stream().anyMatch(item -> item != null && item.getType() != Material.AIR);
                        if (hasAttachments) {
                            futures.add(plugin.getDb().updateMail(mail.id(), mail.message(), new ArrayList<>())
                                    .thenRun(() -> plugin.getDb().markAsRead(mail.id())));
                        }
                    }
                    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).thenRun(() -> {
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            player.sendMessage(getFormattedMessage("messages.attachments-claimed"));
                            playSuccessSound(player);
                            openInbox(player, 0);
                        });
                    });
                });
            }
        } else if (title.contains("讀取郵件 #")) {
            int id = Integer.parseInt(title.substring(title.lastIndexOf("#") + 1));
            int slot = event.getRawSlot();
            if (slot >= 0 && slot < 54) {
                if (slot >= 9 && slot <= 35) {
                    event.setCancelled(true);
                    ItemStack clickedItem = event.getCurrentItem();
                    if (clickedItem != null && clickedItem.getType() != Material.AIR) {
                        int emptySlots = 0;
                        for (ItemStack stack : player.getInventory().getStorageContents()) {
                            if (stack == null || stack.getType() == Material.AIR) emptySlots++;
                        }
                        if (emptySlots < 1) {
                            player.sendMessage(color("&c你的背包空間不足！"));
                            playSound(player, "sounds.error", "BLOCK_NOTE_BLOCK_BASS");
                            return;
                        }
                        player.getInventory().addItem(clickedItem);
                        event.getInventory().setItem(slot, null);
                        
                        List<ItemStack> newAttachments = new ArrayList<>();
                        for (int i = 9; i <= 35; i++) {
                            ItemStack item = event.getInventory().getItem(i);
                            if (item != null && item.getType() != Material.AIR) {
                                newAttachments.add(item);
                            } else {
                                newAttachments.add(null);
                            }
                        }
                        plugin.getDb().getMails(player.getUniqueId(), false).thenAccept(mails -> {
                            mails.stream().filter(m -> m.id() == id).findFirst().ifPresent(m -> {
                                plugin.getDb().updateMail(id, m.message(), newAttachments).thenRun(() -> {
                                    Bukkit.getScheduler().runTask(plugin, () -> {
                                        player.sendMessage(getFormattedMessage("messages.attachments-claimed"));
                                        playSound(player, "sounds.success", "BLOCK_NOTE_BLOCK_PLING");
                                        openMailRead(player, id);
                                    });
                                });
                            });
                        });
                    }
                } else {
                    event.setCancelled(true);
                    if (slot == 8) {
                        openInbox(player, inboxPages.getOrDefault(player.getUniqueId(), 0));
                    } else if (slot == 6) {
                        List<ItemStack> toClaim = new ArrayList<>();
                        for (int i = 9; i <= 35; i++) {
                            ItemStack item = event.getInventory().getItem(i);
                            if (item != null && item.getType() != Material.AIR) {
                                toClaim.add(item);
                            }
                        }
                        if (toClaim.isEmpty()) {
                            player.sendMessage(color("&c這封信件沒有附件可以領取！"));
                            playSound(player, "sounds.error", "BLOCK_NOTE_BLOCK_BASS");
                            return;
                        }
                        int emptySlots = 0;
                        for (ItemStack stack : player.getInventory().getStorageContents()) {
                            if (stack == null || stack.getType() == Material.AIR) emptySlots++;
                        }
                        if (emptySlots < toClaim.size()) {
                            player.sendMessage(color("&c你的背包空間不足！需要 " + toClaim.size() + " 格。"));
                            playSound(player, "sounds.error", "BLOCK_NOTE_BLOCK_BASS");
                            return;
                        }
                        for (ItemStack item : toClaim) {
                            player.getInventory().addItem(item);
                        }
                        plugin.getDb().getMails(player.getUniqueId(), false).thenAccept(mails -> {
                            mails.stream().filter(m -> m.id() == id).findFirst().ifPresent(m -> {
                                plugin.getDb().updateMail(id, m.message(), new ArrayList<>()).thenRun(() -> {
                                    plugin.getDb().markAsRead(id);
                                    Bukkit.getScheduler().runTask(plugin, () -> {
                                        player.sendMessage(getFormattedMessage("messages.attachments-claimed"));
                                        playSuccessSound(player);
                                        openMailRead(player, id);
                                    });
                                });
                            });
                        });
                    } else if (slot == 4) {
                        boolean hasAttachments = false;
                        for (int i = 9; i <= 35; i++) {
                            ItemStack item = event.getInventory().getItem(i);
                            if (item != null && item.getType() != Material.AIR) {
                                hasAttachments = true;
                                break;
                            }
                        }
                        if (hasAttachments) {
                            openDeleteConfirmGUI(player, id);
                        } else {
                            plugin.getDb().deleteMail(id).thenRun(() -> {
                                Bukkit.getScheduler().runTask(plugin, () -> {
                                    player.sendMessage(color("&a[Cmail] 郵件已成功刪除！"));
                                    playSound(player, "sounds.success", "BLOCK_NOTE_BLOCK_PLING");
                                    openInbox(player, 0);
                                });
                            });
                        }
                    } else if (slot == 2) {
                        plugin.getDb().getMails(player.getUniqueId(), false).thenAccept(mails -> {
                            mails.stream().filter(m -> m.id() == id).findFirst().ifPresent(mail -> {
                                long origCount = mail.originalAttachments().stream().filter(item -> item != null && item.getType() != Material.AIR).count();
                                long currCount = mail.attachments().stream().filter(item -> item != null && item.getType() != Material.AIR).count();
                                if (currCount < origCount) {
                                    player.sendMessage(color("&c[Cmail] 您已領取部分或全部附件，無法退回此郵件！"));
                                    playSound(player, "sounds.error", "BLOCK_NOTE_BLOCK_BASS");
                                    return;
                                }
                                plugin.getDb().returnMail(id).thenRun(() -> {
                                    Bukkit.getScheduler().runTask(plugin, () -> {
                                         player.sendMessage(color("&a[Cmail] 郵件已成功退回給原寄件者！"));
                                         playSuccessSound(player);
                                         openInbox(player, 0);
                                     });
                                 });
                            });
                        });
                    }
                }
            } else {
                if (event.isShiftClick()) {
                    event.setCancelled(true);
                }
            }
        } else if (title.contains("放入附件")) {
            int slot = event.getRawSlot();
            if (slot >= 27 && slot <= 35) {
                event.setCancelled(true);
                if (slot == 29) {
                    PendingMail pending = pendingMails.remove(player.getUniqueId());
                    if (pending == null) return;

                    List<ItemStack> items = new ArrayList<>();
                    for (int i = 0; i < 27; i++) {
                        ItemStack item = event.getInventory().getItem(i);
                        if (item != null && item.getType() != Material.AIR) {
                            items.add(item);
                        } else {
                            items.add(null);
                        }
                    }

                    boolean economyEnabled = plugin.getConfig().getBoolean("economy.enabled", false) && VaultHook.isHooked();
                    if (economyEnabled) {
                        long nonAirCount = items.stream().filter(item -> item != null && item.getType() != Material.AIR).count();
                        double cost = plugin.getConfig().getDouble("economy.cost-send-mail", 10.0)
                                + (nonAirCount * plugin.getConfig().getDouble("economy.cost-per-attachment", 5.0));
                        if (VaultHook.getBalance(player) < cost) {
                            player.sendMessage(plugin.getFormattedMessage("messages.insufficient-funds").replace("%cost%", String.valueOf(cost)));
                            playSound(player, "sounds.error", "BLOCK_NOTE_BLOCK_BASS");
                            
                            // Return items to inventory
                            for (ItemStack item : items) {
                                if (item != null && item.getType() != Material.AIR) {
                                    Map<Integer, ItemStack> leftOver = player.getInventory().addItem(item);
                                    for (ItemStack drop : leftOver.values()) {
                                        player.getWorld().dropItemNaturally(player.getLocation(), drop);
                                    }
                                }
                            }
                            // Clear GUI slots
                            for (int i = 0; i < 27; i++) {
                                event.getInventory().setItem(i, null);
                            }
                            player.closeInventory();
                            return;
                        }
                        
                        pendingMails.put(player.getUniqueId(), new PendingMail(pending.receiverUUID(), pending.message(), pending.isBroadcast(), items));
                        for (int i = 0; i < 27; i++) {
                            event.getInventory().setItem(i, null);
                        }
                        openSendConfirmGUI(player, cost);
                        return;
                    }

                    for (int i = 0; i < 27; i++) {
                        event.getInventory().setItem(i, null);
                    }
                    sendPendingMail(player, new PendingMail(pending.receiverUUID(), pending.message(), pending.isBroadcast(), items));
                } else if (slot == 33) {
                    pendingMails.remove(player.getUniqueId());
                    player.closeInventory();
                }
            }
        } else if (title.contains("郵件管理")) {
            event.setCancelled(true);
            if (event.getRawSlot() == 11) openInbox(player);
        } else if (title.contains("管理員 - 所有郵件")) {
            event.setCancelled(true);
            int slot = event.getRawSlot();
            if (slot >= 0 && slot < 45) {
                ItemStack clickedItem = event.getCurrentItem();
                if (clickedItem != null && clickedItem.getType() == Material.WRITTEN_BOOK) {
                    ItemMeta meta = clickedItem.getItemMeta();
                    if (meta != null && meta.displayName() != null) {
                        String name = LegacyComponentSerializer.legacySection().serialize(meta.displayName());
                        try {
                            int id = Integer.parseInt(name.substring(name.lastIndexOf(" ") + 1));
                            openMailPreview(player, id);
                        } catch (Exception ignored) {}
                    }
                }
            } else if (slot == 45) {
                int page = adminPages.getOrDefault(player.getUniqueId(), 0);
                if (page > 0) openAdminMainMenu(player, page - 1);
            } else if (slot == 53) {
                int page = adminPages.getOrDefault(player.getUniqueId(), 0);
                openAdminMainMenu(player, page + 1);
            }
        } else if (title.contains("管理員 - 預覽郵件 #")) {
            int id = Integer.parseInt(title.substring(title.lastIndexOf("#") + 1));
            int slot = event.getRawSlot();
            boolean isUnlocked = adminSafetyLock.getOrDefault(player.getUniqueId(), false);

            if (slot >= 0 && slot < 54) {
                if (slot >= 9 && slot <= 35) {
                    if (!isUnlocked) {
                        event.setCancelled(true);
                    }
                } else {
                    event.setCancelled(true);
                    if (slot == 8) {
                        adminSafetyLock.put(player.getUniqueId(), !isUnlocked);
                        playSound(player, "sounds.success", "BLOCK_NOTE_BLOCK_PLING");
                        openMailPreview(player, id);
                    } else if (slot == 2) {
                        if (!isUnlocked) {
                            player.sendMessage(color("&c[安全警告] 安全鎖未解開，無法修改！"));
                            playSound(player, "sounds.error", "BLOCK_NOTE_BLOCK_BASS");
                            return;
                        }
                        chatEditMail.put(player.getUniqueId(), id);
                        player.closeInventory();
                        player.sendMessage(color("&e[Cmail] 請在聊天欄輸入新的信件內容（輸入 &ccancel &e以取消修改）："));
                    } else if (slot == 4) {
                        if (!isUnlocked) {
                            player.sendMessage(color("&c[安全警告] 安全鎖未解開，無法修改！"));
                            playSound(player, "sounds.error", "BLOCK_NOTE_BLOCK_BASS");
                            return;
                        }
                        List<ItemStack> newAttachments = new ArrayList<>();
                        for (int i = 9; i <= 35; i++) {
                            ItemStack item = event.getInventory().getItem(i);
                            if (item != null && item.getType() != Material.AIR) {
                                newAttachments.add(item);
                            }
                        }
                        plugin.getDb().getAllMails().thenAccept(mails -> {
                            mails.stream().filter(m -> m.id() == id).findFirst().ifPresent(m -> {
                                plugin.getDb().updateMail(id, m.message(), newAttachments).thenRun(() -> {
                                     Bukkit.getScheduler().runTask(plugin, () -> {
                                          player.sendMessage(color("&a[Cmail] 郵件附件已手動儲存！"));
                                          playSound(player, "sounds.success", "BLOCK_NOTE_BLOCK_PLING");
                                          openMailPreview(player, id);
                                      });
                                 });
                            });
                        });
                    } else if (slot == 5) {
                        openAdminDeleteConfirmGUI(player, id);
                    } else if (slot == 6) {
                        openAdminOriginalAttachments(player, id);
                    } else if (slot == 49) {
                        openAdminMainMenu(player, adminPages.getOrDefault(player.getUniqueId(), 0));
                    }
                }
            } else {
                if (!isUnlocked && event.isShiftClick()) {
                    event.setCancelled(true);
                }
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        Player player = (Player) event.getPlayer();
        if (isRefreshing.remove(player.getUniqueId())) {
            return;
        }

        String packName = activePackEditors.remove(player.getUniqueId());
        if (packName != null) {
            List<ItemStack> items = new ArrayList<>();
            for (int i = 0; i < 27; i++) {
                ItemStack item = event.getInventory().getItem(i);
                if (item != null && item.getType() != Material.AIR) {
                    items.add(item);
                } else {
                    items.add(null);
                }
            }
            
            java.io.File packsFolder = new java.io.File(plugin.getDataFolder(), "packs");
            if (!packsFolder.exists()) {
                packsFolder.mkdirs();
            }
            java.io.File packFile = new java.io.File(packsFolder, packName + ".yml");
            org.bukkit.configuration.file.YamlConfiguration packConfig = new org.bukkit.configuration.file.YamlConfiguration();
            packConfig.set("items", items);
            try {
                packConfig.save(packFile);
                player.sendMessage(color("&a[Cmail] 禮包範本 &e" + packName + " &a打包並存檔成功！"));
            } catch (Exception e) {
                player.sendMessage(color("&c[Cmail] 儲存禮包範本時發生錯誤: " + e.getMessage()));
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "Failed to save pack " + packName, e);
                for (ItemStack item : items) {
                    if (item != null) {
                        Map<Integer, ItemStack> leftOver = player.getInventory().addItem(item);
                        for (ItemStack drop : leftOver.values()) {
                            player.getWorld().dropItemNaturally(player.getLocation(), drop);
                        }
                    }
                }
            }
            return;
        }

        if (event.getView().title() != null) {
            String title = LegacyComponentSerializer.legacySection().serialize(event.getView().title());
            if (title.contains("放入附件")) {
                for (int i = 0; i < 27; i++) {
                    ItemStack item = event.getInventory().getItem(i);
                    if (item != null && item.getType() != Material.AIR) {
                        Map<Integer, ItemStack> leftOver = player.getInventory().addItem(item);
                        for (ItemStack drop : leftOver.values()) {
                            player.getWorld().dropItemNaturally(player.getLocation(), drop);
                        }
                    }
                }
            } else if (title.contains("預覽郵件 #")) {
                try {
                    int mailId = Integer.parseInt(title.substring(title.lastIndexOf("#") + 1));
                    Boolean isUnlocked = adminSafetyLock.remove(player.getUniqueId());
                    if (isUnlocked != null && isUnlocked) {
                        List<ItemStack> newAttachments = new ArrayList<>();
                        for (int i = 9; i <= 35; i++) {
                            ItemStack item = event.getInventory().getItem(i);
                            if (item != null && item.getType() != Material.AIR) {
                                newAttachments.add(item);
                            } else {
                                newAttachments.add(null);
                            }
                        }
                        plugin.getDb().getAllMails().thenAccept(mails -> {
                            mails.stream().filter(m -> m.id() == mailId).findFirst().ifPresent(m -> {
                                plugin.getDb().updateMail(mailId, m.message(), newAttachments);
                            });
                        });
                        player.sendMessage(color("&a[Cmail] 已自動保存管理員附件修改。"));
                    }
                } catch (Exception ignored) {}
            } else if (title.contains("讀取郵件 #")) {
                boolean hasLeftOver = false;
                for (int i = 9; i <= 35; i++) {
                    ItemStack item = event.getInventory().getItem(i);
                    if (item != null && item.getType() != Material.AIR) {
                        hasLeftOver = true;
                        break;
                    }
                }
                if (hasLeftOver) {
                    player.sendMessage(color("&e[Cmail] &c提醒：該郵件中還有附件未領取！"));
                    playSound(player, "sounds.error", "BLOCK_NOTE_BLOCK_BASS");
                }
            } else if (title.contains("確認支付手續費？")) {
                PendingMail pending = pendingMails.remove(player.getUniqueId());
                if (pending != null) {
                    returnPendingMailAttachments(player, pending.attachments());
                    player.sendMessage(color("&c[Cmail] 郵件發送已取消，附件已退回背包。"));
                    playSound(player, "sounds.error", "BLOCK_NOTE_BLOCK_BASS");
                }
            }
        }
        pendingMails.remove(player.getUniqueId());
    }

    @EventHandler
    public void onDrag(org.bukkit.event.inventory.InventoryDragEvent event) {
        if (event.getView().title() == null) return;
        String title = LegacyComponentSerializer.legacySection().serialize(event.getView().title());

        if (title.contains("收件箱") || title.contains("郵件管理") || title.contains("管理員 - 所有郵件") || title.contains("讀取郵件 #") || title.contains("確認刪除郵件 #") || title.contains("原始附件預覽 #") || title.contains("確認支付手續費？")) {
            event.setCancelled(true);
        } else if (title.contains("放入附件") || title.contains("管理員 - 預覽郵件 #")) {
            int minAllowed = title.contains("放入附件") ? 0 : 9;
            int maxAllowed = title.contains("放入附件") ? 26 : 35;
            
            if (title.contains("管理員 - 預覽郵件 #")) {
                Player player = (Player) event.getWhoClicked();
                boolean isUnlocked = adminSafetyLock.getOrDefault(player.getUniqueId(), false);
                if (!isUnlocked) {
                    event.setCancelled(true);
                    return;
                }
            }
            
            for (int rawSlot : event.getRawSlots()) {
                if (rawSlot < 54) {
                    if (rawSlot < minAllowed || rawSlot > maxAllowed) {
                        event.setCancelled(true);
                        return;
                    }
                }
            }
        }
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        Integer mailId = chatEditMail.remove(player.getUniqueId());
        if (mailId != null) {
            event.setCancelled(true);
            String message = event.getMessage().trim();

            if (message.equalsIgnoreCase("cancel")) {
                player.sendMessage(color("&c[Cmail] 已取消修改信件內容。"));
                Bukkit.getScheduler().runTask(plugin, () -> openMailPreview(player, mailId));
                return;
            }

            plugin.getDb().getAllMails().thenAccept(mails -> {
                mails.stream().filter(m -> m.id() == mailId).findFirst().ifPresent(m -> {
                    plugin.getDb().updateMail(mailId, message, m.attachments()).thenRun(() -> {
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            player.sendMessage(color("&a[Cmail] 信件內容已修改成功！"));
                            openMailPreview(player, mailId);
                        });
                    });
                });
            });
        }
    }

    public void openAdminMainMenu(Player player, int page) {
        adminPages.put(player.getUniqueId(), page);
        plugin.getDb().getAllMails().thenAccept(mails -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                Inventory inv = Bukkit.createInventory(null, 54, color("&9管理員 - 所有郵件"));
                
                int start = page * 45;
                int end = Math.min(start + 45, mails.size());
                
                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
                
                for (int i = start; i < end; i++) {
                    MailData mail = mails.get(i);
                    String senderName = getSenderDisplayName(mail.sender());
                    String receiverName = getReceiverDisplayName(mail.receiver());
                    
                    ItemStack mailItem = createItem(Material.WRITTEN_BOOK, "&f郵件 ID: " + mail.id(),
                            "&7發送者: &e" + senderName,
                            "&7接收者: &e" + receiverName,
                            "&7內容: &f" + mail.message(),
                            "&7附件數量: &b" + mail.attachments().stream().filter(item -> item != null && item.getType() != Material.AIR).count(),
                            "&7狀態: " + (mail.isRead() ? "&8已讀" : "&a未讀"),
                            "&7時間: &7" + sdf.format(new Date(mail.timestamp())),
                            "",
                            "&e點擊進入預覽與編輯");
                    inv.addItem(mailItem);
                }
                
                ItemStack bg = createItem(Material.BLACK_STAINED_GLASS_PANE, " ");
                for (int i = 45; i < 54; i++) {
                    inv.setItem(i, bg);
                }
                
                if (page > 0) {
                    inv.setItem(45, createItem(Material.ARROW, "&e上一頁"));
                }
                inv.setItem(49, createItem(Material.PAPER, "&b第 " + (page + 1) + " 頁"));
                                if (end < mails.size()) {
                    inv.setItem(53, createItem(Material.ARROW, "&e下一頁"));
                }
                
                player.openInventory(inv);
                playSound(player, "sounds.open-gui", "UI_TOAST_IN");
            });
        });
    }

    public void openMailPreview(Player player, int id) {
        plugin.getDb().getAllMails().thenAccept(mails -> {
            mails.stream().filter(m -> m.id() == id).findFirst().ifPresent(mail -> {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Inventory inv = Bukkit.createInventory(null, 54, color("&9管理員 - 預覽郵件 #" + id));
                    
                    ItemStack bg = createItem(Material.BLACK_STAINED_GLASS_PANE, " ");
                    for (int i = 0; i < 9; i++) {
                        inv.setItem(i, bg);
                    }
                    
                    String senderName = getSenderDisplayName(mail.sender());
                    String receiverName = getReceiverDisplayName(mail.receiver());
                    
                    ItemStack info = createItem(Material.BOOK, "&f信件詳細資訊",
                            "&7發送者: &e" + senderName,
                            "&7接收者: &e" + receiverName,
                            "&7狀態: " + (mail.isRead() ? "&8已讀" : "&a未讀"));
                    inv.setItem(0, info);
                    
                    boolean isUnlocked = adminSafetyLock.getOrDefault(player.getUniqueId(), false);
                    
                    ItemStack editMsg = createItem(Material.WRITABLE_BOOK, "&b修改信件內容",
                            "&7目前內容: &f" + mail.message(),
                            "",
                            "&7點擊後在聊天欄輸入新內容",
                            "&e編輯狀態: " + (isUnlocked ? "&a可編輯" : "&c安全鎖鎖定中"));
                    inv.setItem(2, editMsg);
                    
                    ItemStack saveBtn = createItem(Material.EMERALD, "&a儲存附件修改",
                            "&7手動保存附件的變更",
                            "",
                            "&e編輯狀態: " + (isUnlocked ? "&a可編輯" : "&c安全鎖鎖定中"));
                    inv.setItem(4, saveBtn);

                    ItemStack deleteBtn = createItem(Material.BARRIER, "&4刪除此郵件", 
                            "&7將此郵件從伺服器永久刪除", 
                            "&7此操作將會彈出確認視窗。");
                    inv.setItem(5, deleteBtn);
                    
                    ItemStack origBtn = createItem(Material.BOOK, "&d[查看剛寄出的原始附件]", "&7點擊以唯讀方式查看郵件剛寄出時的原始附件排版。");
                    inv.setItem(6, origBtn);
                    
                    ItemStack lockBtn = isUnlocked ?
                            createItem(Material.LIME_DYE, "&a安全鎖: 已解鎖", "&7點擊以鎖定修改權限") :
                            createItem(Material.RED_DYE, "&c安全鎖: 已鎖定", "&7點擊解鎖後始可編輯文字或拖曳附件");
                    inv.setItem(8, lockBtn);
                    
                    List<ItemStack> attachments = mail.attachments();
                    for (int i = 0; i < Math.min(attachments.size(), 27); i++) {
                        inv.setItem(9 + i, attachments.get(i));
                    }
                    
                    for (int i = 36; i < 54; i++) {
                        inv.setItem(i, bg);
                    }
                    inv.setItem(49, createItem(Material.ARROW, "&7返回所有郵件列表"));
                    
                    isRefreshing.add(player.getUniqueId());
                    player.openInventory(inv);
                    playSound(player, "sounds.open-gui", "UI_TOAST_IN");
                });
            });
        });
    }

    public void openSendConfirmGUI(Player player, double cost) {
        Inventory inv = Bukkit.createInventory(null, 27, color("&4確認支付手續費？"));
        ItemStack bg = createItem(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, bg);
        }
        
        ItemStack confirmBtn = createItem(Material.GREEN_STAINED_GLASS_PANE, "&a[確認支付並寄送]", 
                "&7此封郵件發送所需的手續費為:", 
                "&e$ " + cost + " &7元",
                "",
                "&7點擊後將會扣除手續費並寄出郵件！");
        ItemStack cancelBtn = createItem(Material.RED_STAINED_GLASS_PANE, "&c[取消寄送]", 
                "&7點擊取消寄送郵件，將全額退回您的附件道具。");
                
        inv.setItem(11, confirmBtn);
        inv.setItem(15, cancelBtn);
        
        isRefreshing.add(player.getUniqueId());
        player.openInventory(inv);
        playSound(player, "sounds.open-gui", "UI_TOAST_IN");
    }

    private void returnPendingMailAttachments(Player player, List<ItemStack> attachments) {
        for (ItemStack item : attachments) {
            if (item != null && item.getType() != Material.AIR) {
                Map<Integer, ItemStack> leftOver = player.getInventory().addItem(item);
                for (ItemStack drop : leftOver.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), drop);
                }
            }
        }
    }

    private void sendPendingMail(Player player, PendingMail pending) {
        if (pending.isBroadcast()) {
            plugin.getDb().saveBroadcastMail(player.getUniqueId(), pending.message(), pending.attachments()).thenRun(() -> {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    player.sendMessage(plugin.getFormattedMessage("messages.broadcast-sent"));
                    playSuccessSound(player);
                    player.closeInventory();
                });
            });
        } else {
            plugin.getDb().saveMail(player.getUniqueId(), pending.receiverUUID(), pending.message(), pending.attachments()).thenRun(() -> {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    player.sendMessage(plugin.getFormattedMessage("messages.mail-sent"));
                    playSuccessSound(player);
                    player.closeInventory();
                });
            });
        }
    }

    public void openAdminOriginalAttachments(Player player, int id) {
        plugin.getDb().getAllMails().thenAccept(mails -> {
            mails.stream().filter(m -> m.id() == id).findFirst().ifPresent(mail -> {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Inventory inv = Bukkit.createInventory(null, 54, color("&d原始附件預覽 #" + id));
                    
                    ItemStack bg = createItem(Material.BLACK_STAINED_GLASS_PANE, " ");
                    for (int i = 0; i < 9; i++) {
                        inv.setItem(i, bg);
                    }
                    
                    inv.setItem(4, createItem(Material.BOOK, "&b[剛寄出的原始附件]", "&7此頁面顯示郵件剛寄出時的原始附件排版，", "&7內容為唯讀狀態，無法修改。"));
                    inv.setItem(8, createItem(Material.ARROW, "&7返回郵件預覽頁面"));
                    
                    List<ItemStack> orig = mail.originalAttachments();
                    for (int i = 0; i < Math.min(orig.size(), 27); i++) {
                        inv.setItem(9 + i, orig.get(i));
                    }
                    
                    for (int i = 36; i < 54; i++) {
                        inv.setItem(i, bg);
                    }
                    
                    isRefreshing.add(player.getUniqueId());
                    player.openInventory(inv);
                    playSound(player, "sounds.open-gui", "UI_TOAST_IN");
                });
            });
        });
    }

    private ItemStack createItem(Material m, String name, String... lore) {
        ItemStack s = new ItemStack(m);
        ItemMeta meta = s.getItemMeta();
        if (meta != null) {
            meta.displayName(color(name));
            if (lore.length > 0) {
                List<Component> l = new ArrayList<>();
                for (String line : lore) l.add(color(line));
                meta.lore(l);
            }
            s.setItemMeta(meta);
        }
        return s;
    }

    private Component color(String s) { return LegacyComponentSerializer.legacyAmpersand().deserialize(s); }
}




