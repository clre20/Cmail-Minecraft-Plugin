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
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class MailGUI implements Listener {
    private final Cmail plugin;

    private record PendingMail(UUID receiverUUID, String message) {}
    private final Map<UUID, PendingMail> pendingMails = new HashMap<>();

    public MailGUI(Cmail plugin) { this.plugin = plugin; }

    public void openMainMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, color(plugin.getConfig().getString("messages.gui-title-main")));
        inv.setItem(11, createItem(Material.CHEST, "&a查看收件箱"));
        inv.setItem(15, createItem(Material.PAPER, "&b寄送新郵件", "&7用法: /mail send [玩家] [文字]"));
        player.openInventory(inv);
    }

    public void openInbox(Player player) {
        plugin.getDb().getMails(player.getUniqueId(), true).thenAccept(mails -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                Inventory inv = Bukkit.createInventory(null, 54, color(plugin.getConfig().getString("messages.gui-title-inbox")));
                for (MailData mail : mails) {
                    inv.addItem(createItem(Material.BOOK, "&f來自: " + Bukkit.getOfflinePlayer(mail.sender()).getName(),
                            "&7內容: " + mail.message(), "&8ID: " + mail.id()));
                }
                player.openInventory(inv);
            });
        });
    }

    public void openSendGUI(Player player, UUID receiverUUID, String message) {
        pendingMails.put(player.getUniqueId(), new PendingMail(receiverUUID, message));
        Inventory inv = Bukkit.createInventory(null, 36, color(plugin.getConfig().getString("messages.gui-title-send")));

        ItemStack blackGlass = createItem(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 27; i < 36; i++) {
            if (i == 29) {
                inv.setItem(i, createItem(Material.GREEN_STAINED_GLASS_PANE, plugin.getConfig().getString("messages.confirm")));
            } else if (i == 33) {
                inv.setItem(i, createItem(Material.RED_STAINED_GLASS_PANE, plugin.getConfig().getString("messages.cancel")));
            } else {
                inv.setItem(i, blackGlass);
            }
        }
        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        if (event.getView().title() == null) return;
        String title = LegacyComponentSerializer.legacySection().serialize(event.getView().title());

        if (title.contains("收件箱")) {
            event.setCancelled(true);
            ItemStack clickedItem = event.getCurrentItem();
            if (clickedItem == null || clickedItem.getItemMeta() == null) return;

            List<Component> lore = clickedItem.getItemMeta().lore();
            if (lore == null || lore.isEmpty()) return;

            String lastLine = LegacyComponentSerializer.legacySection().serialize(lore.get(lore.size() - 1));
            try {
                int id = Integer.parseInt(lastLine.substring(lastLine.lastIndexOf(" ") + 1));
                plugin.getDb().getMails(player.getUniqueId(), false).thenAccept(mails -> {
                    mails.stream().filter(m -> m.id() == id).findFirst().ifPresent(m -> {
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            // --- 檢查背包空間 ---
                            long requiredSlots = m.attachments().stream()
                                    .filter(i -> i != null && i.getType() != Material.AIR).count();

                            int emptySlots = 0;
                            for (ItemStack stack : player.getInventory().getStorageContents()) {
                                if (stack == null || stack.getType() == Material.AIR) emptySlots++;
                            }

                            if (emptySlots < requiredSlots) {
                                // 修改物品顯示來發送警告
                                ItemMeta meta = clickedItem.getItemMeta();
                                List<Component> newLore = new ArrayList<>(lore);
                                // 加入明顯的警告紅字
                                newLore.add(Component.text(" "));
                                newLore.add(Component.text("⚠ 背包空間不足 (" + requiredSlots + " 格)", NamedTextColor.RED));
                                meta.lore(newLore);
                                clickedItem.setItemMeta(meta);

                                // 播放一個錯誤音效 (選用)
                                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                                return;
                            }
                            // -----------------

                            for (ItemStack item : m.attachments()) {
                                if (item != null) player.getInventory().addItem(item);
                            }
                            plugin.getDb().markAsRead(id);
                            player.sendMessage(color("&a已領取附件。"));
                            openInbox(player);
                        });
                    });
                });
            } catch (Exception ignored) {}

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
                        if (item != null && item.getType() != Material.AIR) items.add(item);
                    }

                    plugin.getDb().saveMail(player.getUniqueId(), pending.receiverUUID(), pending.message(), items);
                    player.sendMessage(color(plugin.getConfig().getString("messages.mail-sent")));
                    player.closeInventory();
                } else if (slot == 33) {
                    pendingMails.remove(player.getUniqueId());
                    player.closeInventory();
                }
            }
        } else if (title.contains("郵件管理")) {
            event.setCancelled(true);
            if (event.getRawSlot() == 11) openInbox(player);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        pendingMails.remove(event.getPlayer().getUniqueId());
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