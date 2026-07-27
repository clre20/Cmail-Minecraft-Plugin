package clre20.cmail;

import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import net.milkbowl.vault.economy.Economy;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public class VaultHook {
    private static @Nullable Economy econ = null;

    public static boolean setup() {
        if (org.bukkit.Bukkit.getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = org.bukkit.Bukkit.getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        econ = rsp.getProvider();
        return econ != null;
    }

    public static boolean isHooked() {
        return econ != null;
    }

    public static double getBalance(Player player) {
        if (econ == null) return 0.0;
        return econ.getBalance(player);
    }

    public static void withdraw(Player player, double amount) {
        if (econ != null) {
            econ.withdrawPlayer(player, amount);
        }
    }
}
