package clre20.cmail;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jspecify.annotations.NullMarked;

/**
 * Cmail 所有 GUI 共用的 InventoryHolder，供其他外掛（如 Item-lock）辨識 Cmail 介面。
 */
@NullMarked
public final class CmailHolder implements InventoryHolder {
    private Inventory inventory;

    private CmailHolder() {}

    public static Inventory createInventory(int size, Component title) {
        CmailHolder holder = new CmailHolder();
        holder.inventory = Bukkit.createInventory(holder, size, title);
        return holder.inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
