package clre20.cmail;

import org.bukkit.inventory.ItemStack;
import java.util.List;
import java.util.UUID;

public record MailData(int id, UUID sender, UUID receiver, String message, List<ItemStack> attachments, long timestamp, boolean isRead) {}