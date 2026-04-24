package clre20.cmail;

import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;
import org.yaml.snakeyaml.external.biz.base64Coder.Base64Coder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class DatabaseManager {
    private Connection connection;
    private final Cmail plugin; // 這裡改為 Cmail

    public DatabaseManager(Cmail plugin) { // 這裡改為 Cmail
        this.plugin = plugin;
        init();
    }

    private void init() {
        try {
            if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
            connection = DriverManager.getConnection("jdbc:sqlite:" + plugin.getDataFolder() + "/mails.db");
            Statement s = connection.createStatement();
            s.execute("CREATE TABLE IF NOT EXISTS mails (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "sender TEXT, receiver TEXT, message TEXT, items TEXT, " +
                    "timestamp LONG, is_read INTEGER DEFAULT 0)");
        } catch (SQLException e) { e.printStackTrace(); }
    }

    public CompletableFuture<Void> saveMail(UUID sender, UUID receiver, String msg, List<ItemStack> items) {
        return CompletableFuture.runAsync(() -> {
            String sql = "INSERT INTO mails (sender, receiver, message, items, timestamp) VALUES (?,?,?,?,?)";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, sender.toString());
                ps.setString(2, receiver.toString());
                ps.setString(3, msg);
                ps.setString(4, serializeItems(items));
                ps.setLong(5, System.currentTimeMillis());
                ps.executeUpdate();
            } catch (Exception e) { e.printStackTrace(); }
        });
    }

    public CompletableFuture<List<MailData>> getMails(UUID receiver, boolean onlyUnread) {
        return CompletableFuture.supplyAsync(() -> {
            List<MailData> list = new ArrayList<>();
            String sql = onlyUnread ? "SELECT * FROM mails WHERE receiver = ? AND is_read = 0"
                    : "SELECT * FROM mails WHERE receiver = ?";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, receiver.toString());
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    list.add(new MailData(rs.getInt("id"), UUID.fromString(rs.getString("sender")),
                            receiver, rs.getString("message"), deserializeItems(rs.getString("items")),
                            rs.getLong("timestamp"), rs.getInt("is_read") == 1));
                }
            } catch (Exception e) { e.printStackTrace(); }
            return list;
        });
    }

    public void markAsRead(int id) {
        CompletableFuture.runAsync(() -> {
            try (PreparedStatement ps = connection.prepareStatement("UPDATE mails SET is_read = 1 WHERE id = ?")) {
                ps.setInt(1, id);
                ps.executeUpdate();
            } catch (SQLException e) { e.printStackTrace(); }
        });
    }

    private String serializeItems(List<ItemStack> items) throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream);
        dataOutput.writeInt(items.size());
        for (ItemStack item : items) dataOutput.writeObject(item);
        dataOutput.close();
        return Base64Coder.encodeLines(outputStream.toByteArray());
    }

    private List<ItemStack> deserializeItems(String data) throws Exception {
        ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64Coder.decodeLines(data));
        BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream);
        int size = dataInput.readInt();
        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < size; i++) items.add((ItemStack) dataInput.readObject());
        dataInput.close();
        return items;
    }
}