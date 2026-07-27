package clre20.cmail;

import org.bukkit.Material;
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
            try (Statement s = connection.createStatement()) {
                s.execute("CREATE TABLE IF NOT EXISTS mails (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "sender TEXT, receiver TEXT, message TEXT, items TEXT, " +
                        "original_items TEXT, timestamp LONG, is_read INTEGER DEFAULT 0)");
                
                try {
                    s.execute("ALTER TABLE mails ADD COLUMN original_items TEXT");
                    org.bukkit.Bukkit.getConsoleSender().sendMessage("§e[Cmail] §a[SQLite] 成功升級資料庫：新增 original_items 欄位。");
                } catch (SQLException ignored) {}
                
                org.bukkit.Bukkit.getConsoleSender().sendMessage("§e[Cmail] §a[SQLite] 成功連接至數據庫 mails.db 並完成初始化。");
            }
        } catch (SQLException e) { 
            org.bukkit.Bukkit.getConsoleSender().sendMessage("§e[Cmail] §c[SQLite] 數據庫初始化失敗！錯誤資訊:");
            e.printStackTrace(); 
        }
    }

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                org.bukkit.Bukkit.getConsoleSender().sendMessage("§e[Cmail] §e[SQLite] 已成功關閉數據庫連接。");
            }
        } catch (SQLException e) { 
            org.bukkit.Bukkit.getConsoleSender().sendMessage("§e[Cmail] §c[SQLite] 關閉數據庫連接時出錯:");
            e.printStackTrace(); 
        }
    }

    public CompletableFuture<Void> saveMail(UUID sender, UUID receiver, String msg, List<ItemStack> items) {
        return CompletableFuture.runAsync(() -> {
            synchronized (this) {
                String sql = "INSERT INTO mails (sender, receiver, message, items, original_items, timestamp) VALUES (?,?,?,?,?,?)";
                try (PreparedStatement ps = connection.prepareStatement(sql)) {
                    ps.setString(1, sender.toString());
                    ps.setString(2, receiver.toString());
                    ps.setString(3, msg);
                    String serialized = serializeItems(items);
                    ps.setString(4, serialized);
                    ps.setString(5, serialized);
                    ps.setLong(6, System.currentTimeMillis());
                    ps.executeUpdate();
                    org.bukkit.Bukkit.getConsoleSender().sendMessage("§e[Cmail] §e[Mail] 郵件寄送成功並存入數據庫 (發送者: " + sender + " -> 接收者: " + receiver + ")");
                } catch (Exception e) { 
                    org.bukkit.Bukkit.getConsoleSender().sendMessage("§e[Cmail] §c[Mail] 儲存郵件至數據庫時出錯:");
                    e.printStackTrace(); 
                }
            }
        });
    }

    public CompletableFuture<List<MailData>> getMails(UUID receiver, boolean onlyUnread) {
        return CompletableFuture.supplyAsync(() -> {
            synchronized (this) {
                List<MailData> list = new ArrayList<>();
                String sql = onlyUnread ? "SELECT * FROM mails WHERE receiver = ? AND is_read = 0 ORDER BY id DESC"
                        : "SELECT * FROM mails WHERE receiver = ? ORDER BY id DESC";
                try (PreparedStatement ps = connection.prepareStatement(sql)) {
                    ps.setString(1, receiver.toString());
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            String origItemsStr = rs.getString("original_items");
                            List<ItemStack> origItems = (origItemsStr != null) ? deserializeItems(origItemsStr) : deserializeItems(rs.getString("items"));
                            list.add(new MailData(rs.getInt("id"), UUID.fromString(rs.getString("sender")),
                                    receiver, rs.getString("message"), deserializeItems(rs.getString("items")),
                                    origItems, rs.getLong("timestamp"), rs.getInt("is_read") == 1));
                        }
                    }
                } catch (Exception e) { 
                    org.bukkit.Bukkit.getConsoleSender().sendMessage("§e[Cmail] §c[Mail] 從數據庫讀取玩家郵件時出錯 (" + receiver + "):");
                    e.printStackTrace(); 
                }
                return list;
            }
        });
    }

    public void markAsRead(int id) {
        CompletableFuture.runAsync(() -> {
            synchronized (this) {
                try (PreparedStatement ps = connection.prepareStatement("UPDATE mails SET is_read = 1 WHERE id = ?")) {
                    ps.setInt(1, id);
                    ps.executeUpdate();
                    org.bukkit.Bukkit.getConsoleSender().sendMessage("§e[Cmail] §a[Mail] 郵件標記為已讀成功 (ID: " + id + ")");
                } catch (SQLException e) { 
                    org.bukkit.Bukkit.getConsoleSender().sendMessage("§e[Cmail] §c[Mail] 標記郵件為已讀時出錯 (ID: " + id + "):");
                    e.printStackTrace(); 
                }
            }
        });
    }

    public CompletableFuture<List<MailData>> getAllMails() {
        return CompletableFuture.supplyAsync(() -> {
            synchronized (this) {
                List<MailData> list = new ArrayList<>();
                String sql = "SELECT * FROM mails ORDER BY id DESC";
                try (PreparedStatement ps = connection.prepareStatement(sql);
                     ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String origItemsStr = rs.getString("original_items");
                        List<ItemStack> origItems = (origItemsStr != null) ? deserializeItems(origItemsStr) : deserializeItems(rs.getString("items"));
                        list.add(new MailData(rs.getInt("id"), UUID.fromString(rs.getString("sender")),
                                UUID.fromString(rs.getString("receiver")), rs.getString("message"),
                                deserializeItems(rs.getString("items")), origItems, rs.getLong("timestamp"),
                                rs.getInt("is_read") == 1));
                    }
                } catch (Exception e) { 
                    org.bukkit.Bukkit.getConsoleSender().sendMessage("§e[Cmail] §c[Mail] 讀取所有郵件時出錯:");
                    e.printStackTrace(); 
                }
                return list;
            }
        });
    }

    public CompletableFuture<Void> updateMail(int id, String newMsg, List<ItemStack> newItems) {
        return CompletableFuture.runAsync(() -> {
            synchronized (this) {
                String sql = "UPDATE mails SET message = ?, items = ? WHERE id = ?";
                try (PreparedStatement ps = connection.prepareStatement(sql)) {
                    ps.setString(1, newMsg);
                    ps.setString(2, serializeItems(newItems));
                    ps.setInt(3, id);
                    ps.executeUpdate();
                    org.bukkit.Bukkit.getConsoleSender().sendMessage("§e[Cmail] §a[Mail] 成功更新郵件內容 (ID: " + id + ")");
                } catch (Exception e) { 
                    org.bukkit.Bukkit.getConsoleSender().sendMessage("§e[Cmail] §c[Mail] 更新郵件時出錯 (ID: " + id + "):");
                    e.printStackTrace(); 
                }
            }
        });
    }

    public CompletableFuture<Void> deleteMail(int id) {
        return CompletableFuture.runAsync(() -> {
            synchronized (this) {
                String sql = "DELETE FROM mails WHERE id = ?";
                try (PreparedStatement ps = connection.prepareStatement(sql)) {
                    ps.setInt(1, id);
                    ps.executeUpdate();
                    org.bukkit.Bukkit.getConsoleSender().sendMessage("§e[Cmail] §a[Mail] 成功刪除郵件 (ID: " + id + ")");
                } catch (SQLException e) {
                    org.bukkit.Bukkit.getConsoleSender().sendMessage("§e[Cmail] §c[Mail] 刪除郵件時出錯 (ID: " + id + "):");
                    e.printStackTrace();
                }
            }
        });
    }

    public CompletableFuture<Void> returnMail(int id) {
        return CompletableFuture.runAsync(() -> {
            synchronized (this) {
                String selectSql = "SELECT sender, receiver, message FROM mails WHERE id = ?";
                try (PreparedStatement ps = connection.prepareStatement(selectSql)) {
                    ps.setInt(1, id);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            String originalSender = rs.getString("sender");
                            String originalReceiver = rs.getString("receiver");
                            String originalMsg = rs.getString("message");
                            
                            String newMsg = originalMsg.startsWith("[退回] ") ? originalMsg : "[退回] " + originalMsg;
                            
                            String updateSql = "UPDATE mails SET sender = ?, receiver = ?, message = ?, is_read = 0, timestamp = ? WHERE id = ?";
                            try (PreparedStatement ups = connection.prepareStatement(updateSql)) {
                                ups.setString(1, originalReceiver);
                                ups.setString(2, originalSender);
                                ups.setString(3, newMsg);
                                ups.setLong(4, System.currentTimeMillis());
                                ups.setInt(5, id);
                                ups.executeUpdate();
                                org.bukkit.Bukkit.getConsoleSender().sendMessage("§e[Cmail] §a[Mail] 郵件退信成功 (ID: " + id + ")");
                            }
                        }
                    }
                } catch (SQLException e) {
                    org.bukkit.Bukkit.getConsoleSender().sendMessage("§e[Cmail] §c[Mail] 退回郵件時出錯 (ID: " + id + "):");
                    e.printStackTrace();
                }
            }
        });
    }

    public CompletableFuture<Void> saveBroadcastMail(UUID sender, String msg, List<ItemStack> items) {
        return CompletableFuture.runAsync(() -> {
            synchronized (this) {
                String sql = "INSERT INTO mails (sender, receiver, message, items, original_items, timestamp) VALUES (?,?,?,?,?,?)";
                try {
                    connection.setAutoCommit(false);
                    try (PreparedStatement ps = connection.prepareStatement(sql)) {
                        String serialized = serializeItems(items);
                        long time = System.currentTimeMillis();
                        for (org.bukkit.OfflinePlayer target : org.bukkit.Bukkit.getOfflinePlayers()) {
                            ps.setString(1, sender.toString());
                            ps.setString(2, target.getUniqueId().toString());
                            ps.setString(3, msg);
                            ps.setString(4, serialized);
                            ps.setString(5, serialized);
                            ps.setLong(6, time);
                            ps.addBatch();
                        }
                        ps.executeBatch();
                        connection.commit();
                        org.bukkit.Bukkit.getConsoleSender().sendMessage("§e[Cmail] §a[Mail] 全服郵件發送成功！");
                    } catch (Exception e) {
                        connection.rollback();
                        throw e;
                    } finally {
                        connection.setAutoCommit(true);
                    }
                } catch (Exception e) {
                    org.bukkit.Bukkit.getConsoleSender().sendMessage("§e[Cmail] §c[Mail] 發送全服郵件時出錯:");
                    e.printStackTrace();
                }
            }
        });
    }

    public CompletableFuture<Integer> cleanupExpiredMails(int days, boolean deleteUnread) {
        return CompletableFuture.supplyAsync(() -> {
            synchronized (this) {
                long cutoffTime = System.currentTimeMillis() - (days * 24L * 3600L * 1000L);
                List<Integer> idsToDelete = new ArrayList<>();
                String selectSql = "SELECT id, items, is_read FROM mails WHERE timestamp < ?";
                try (PreparedStatement ps = connection.prepareStatement(selectSql)) {
                    ps.setLong(1, cutoffTime);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            int id = rs.getInt("id");
                            int isRead = rs.getInt("is_read");
                            String itemsStr = rs.getString("items");
                            
                            boolean shouldDelete = false;
                            if (deleteUnread) {
                                shouldDelete = true;
                            } else {
                                if (isRead == 1) {
                                    try {
                                        List<ItemStack> attachments = deserializeItems(itemsStr);
                                        boolean hasAttachments = attachments.stream().anyMatch(item -> item != null && item.getType() != Material.AIR);
                                        if (!hasAttachments) {
                                            shouldDelete = true;
                                        }
                                    } catch (Exception e) {
                                        shouldDelete = true;
                                    }
                                }
                            }
                            
                            if (shouldDelete) {
                                idsToDelete.add(id);
                            }
                        }
                    }
                } catch (SQLException e) {
                    org.bukkit.Bukkit.getConsoleSender().sendMessage("§e[Cmail] §c[Cleanup] 讀取過期郵件時出錯:");
                    e.printStackTrace();
                }
                
                if (idsToDelete.isEmpty()) {
                    return 0;
                }
                
                int deletedCount = 0;
                String deleteSql = "DELETE FROM mails WHERE id = ?";
                try {
                    connection.setAutoCommit(false);
                    try (PreparedStatement ps = connection.prepareStatement(deleteSql)) {
                        for (int id : idsToDelete) {
                            ps.setInt(1, id);
                            ps.addBatch();
                        }
                        int[] results = ps.executeBatch();
                        for (int r : results) {
                            if (r >= 0 || r == Statement.SUCCESS_NO_INFO) deletedCount++;
                        }
                        connection.commit();
                    } catch (Exception e) {
                        connection.rollback();
                        throw e;
                    } finally {
                        connection.setAutoCommit(true);
                    }
                } catch (SQLException e) {
                    org.bukkit.Bukkit.getConsoleSender().sendMessage("§e[Cmail] §c[Cleanup] 刪除過期郵件時出錯:");
                    e.printStackTrace();
                }
                
                if (deletedCount > 0) {
                    org.bukkit.Bukkit.getConsoleSender().sendMessage("§e[Cmail] §a[Cleanup] 成功清理了 " + deletedCount + " 封過期郵件。");
                }
                return deletedCount;
            }
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