# Cmail

Cmail 是一款專為 Paper (Minecraft 1.21.x+) 伺服器設計的虛擬郵件與禮包系統。本插件採用 SQLite 本地資料庫儲存郵件數據，並支援 Vault 經濟系統。具備完整的 NBT 道具保存能力，可供管理員打包禮包並進行批次指令分發，適用於伺服器道具發放與自動化商城整合。

## 功能特性

* **安全防護機制**：
  * 當玩家在「放入附件」或「編輯禮包」時，若因異常斷線、伺服器重載或非正常關閉選單，系統會自動將所有道具退回玩家背包。若背包空間不足，則會自然掉落於玩家所在的世界座標，防止道具遺失或複製漏洞。
  * 唯讀選單全面禁用 Shift-Click 與滑鼠拖曳限制，防止附件道具異常移出。
* **NBT 禮包範本系統**：
  * 管理員可在遊戲內打開 36 格介面，將含有自訂 NBT 數據（如自訂金幣、特殊標籤道具、Lore 武器）擺放完成並進行打包。
  * 支援通過主機或控制台指令（支援以逗號分隔多位玩家名稱）將打包好的禮包範本直接分發給多位玩家。
* **控制台指令整合**：
  * 提供 `consolesend` 與 `sendpack` 指令，可直接由控制台、RCON 或外部腳本調用以發送郵件與物品。
* **管理員後台審查**：
  * 管理員可調閱全伺服器所有歷史信件。內建安全鎖機制，解鎖後始可修改信件內文、調整信件附件，並支援在確認後永久刪除郵件。
* **Vault 經濟整合**：
  * 可啟用發信手續費與附件手續費，並在玩家寄信時顯示扣款確認界面。
* **發信人自訂**：
  * 系統發送的信件（如禮包、後台信件）之發信人名稱可於 `config.yml` 中自由配置，支援色彩代碼。
* **自動清理機制**：
  * 定時自動清理過期郵件，可設定保留天數及是否清理未讀/含附件郵件。
* **DiscordLink 整合 (`link_dc_player`)**：
  * 收件者填入 `link_dc_player`，即可一次寄信給所有已綁定 Discord 的玩家。
  * 名單在發送當下直接從 [DiscordLink](https://github.com/clre20/Discord-link-Minecraft-Plugin) 的 SQLite 資料庫以唯讀方式讀取，不需修改 DiscordLink，也不需開啟其 HTTP API。
  * 資料庫位置會自動依照 DiscordLink 的 `config.yml` 中 `database.sqlite-file-name` 判斷 (預設 `plugins/DiscordLink/database.db`)。
  * 支援 `send`、`sendpack`、`consolesend` 三個指令，皆需 `cmail.admin` 權限。

## 指令與權限

本插件主指令為 `/cmail`。

| 指令 | 說明 | 執行端 | 權限節點 | 預設 |
| :--- | :--- | :--- | :--- | :--- |
| `/cmail` 或 `/cmail mail` | 開啟個人收件箱 GUI | 僅限玩家 | `cmail.use` | 所有人 |
| `/cmail send <玩家 或 all 或 link_dc_player> <訊息>` | 撰寫郵件並開啟附件放置 GUI（`all` 發給全服、`link_dc_player` 發給所有已綁定 Discord 的玩家） | 僅限玩家 | `all` / `link_dc_player` 需 `cmail.admin` / 普通為 `cmail.use` | 所有人 |
| `/cmail help` | 顯示指令說明清單 | 遊戲內 / 控制台 | `cmail.use` | 所有人 |
| `/cmail admin` | 開啟管理員審查選單 | 僅限玩家 | `cmail.admin` | OP |
| `/cmail reload` | 重新載入設定檔 | 遊戲內 / 控制台 | `cmail.admin` | OP |
| `/cmail savepack <禮包名稱>` | 在遊戲內打包道具為禮包範本 | 僅限玩家 | `cmail.admin` | OP |
| `/cmail sendpack <玩家1,玩家2,... 或 all 或 link_dc_player> <禮包名稱> <訊息>` | 發送禮包範本給指定玩家、全服或所有已綁定 Discord 的玩家 | 遊戲內 / 控制台 | `cmail.admin` | OP |
| `/cmail consolesend <玩家1,玩家2,... 或 link_dc_player> <訊息> [材質:格位:數量]...` | 透過純指令發送特定排列的道具給多位玩家或所有已綁定 Discord 的玩家 | 遊戲內 / 控制台 | 控制台無限制 / `cmail.admin` | OP |

> 指令別名：`/mail` 等同 `/cmail`。
>
> 收件者關鍵字：
> * `all`：伺服器上所有曾加入過的玩家。
> * `link_dc_player`：所有已透過 DiscordLink 綁定 Discord 的玩家（需安裝 DiscordLink，名單於發送當下讀取）。

## 使用範例

### 1. 打包並發送自訂 NBT 道具
1. 管理員將自訂道具放入快捷欄，在遊戲內執行：
   ```bash
   /cmail savepack cash_v1
   ```
2. 將道具放入前 27 格，點擊右下角儲存並關閉。
3. 於控制台或遊戲內發送給指定玩家：
   ```bash
   cmail sendpack playerA,playerB cash_v1 感謝贊助，這是您的禮包。
   ```
   或是發送給全服所有玩家（需 cmail.admin 權限、OP 或控制台執行）：
   ```bash
   cmail sendpack all cash_v1 感謝大家的支持，這是全服補償禮包！
   ```

### 2. 控制台直接發送基本道具
控制台發送 64 個鑽石在第 4 格，16 個金錠在第 22 格給特定玩家：
```bash
cmail consolesend smart 這是您的活動獎勵 DIAMOND:4:64 GOLD_INGOT:22:16
```

### 3. 寄給所有已綁定 Discord 的玩家
遊戲內撰寫並放入附件（需 `cmail.admin`）：
```bash
/cmail send link_dc_player 感謝綁定 Discord，這是您的綁定獎勵！
```
控制台發送禮包範本：
```bash
cmail sendpack link_dc_player cash_v1 感謝綁定 Discord，這是您的綁定獎勵！
```
控制台直接發送道具：
```bash
cmail consolesend link_dc_player 綁定獎勵 DIAMOND:0:5
```
* 若找不到 DiscordLink 資料庫，會提示 `link-dc-unavailable` 訊息並在控制台顯示詳細原因，郵件不會寄出。
* 若目前沒有任何玩家綁定，會提示 `link-dc-empty` 訊息。
* 寄出後，在線上的收件者會立即收到新郵件提示。

## 設定檔新增訊息

以下訊息可於 `config.yml` 的 `messages` 區塊自訂：

> **設定檔自動補齊**：插件啟動或執行 `/cmail reload` 時，若伺服器上的 `config.yml` 缺少新版欄位，會自動補上預設值與註解，原有的設定值不會被覆蓋。`help-menu` 會保留自訂內容，只會將舊版錯誤的 `/cmail sendall` 修正為 `/cmail send all`，並補上 `link_dc_player` 說明。

| 欄位 | 說明 |
| :--- | :--- |
| `broadcast-sent` | 全服郵件寄出成功 |
| `link-dc-sent` | 寄給已綁定 Discord 玩家成功，`%n%` 為收件人數 |
| `link-dc-empty` | 目前沒有任何玩家綁定 Discord |
| `link-dc-unavailable` | 無法讀取 DiscordLink 綁定資料 |


# Cmail

Cmail is a virtual mail and gift package plugin designed specifically for Paper (Minecraft 1.21.x+) servers. It utilizes a local SQLite database for persistent mail storage and seamlessly integrates with the Vault economy system. Featuring full NBT data preservation, it allows administrators to bundle items into gift packages and distribute them via batch console commands—ideal for reward distribution and automated webstore integrations.

## Features

* **Anti-Exploit & Safety Mechanisms**:
  * If a player disconnects unexpectedly, the server reloads, or an inventory GUI closes improperly during "Attachment Placement" or "Package Editing," all items are automatically returned to the player's inventory. If the inventory is full, items drop naturally at the player's location, preventing item loss and duplication glitches.
  * Read-only GUIs strictly disable Shift-Clicking and drag actions to prevent unintended removal of attached items.
* **NBT Package Template System**:
  * Administrators can open a 36-slot GUI in-game, arrange items containing custom NBT data (such as custom currencies, items with custom tags, or lore weapons), and save them as reusable templates.
  * Supports distributing saved package templates directly to multiple players using comma-separated player names via in-game or console commands.
* **Console Command Integration**:
  * Offers `consolesend` and `sendpack` commands that can be invoked via the server console, RCON, or external scripts to distribute mail and items automatically.
* **Admin Inspection & Audit GUI**:
  * Administrators can review the entire server's mail history. A built-in security lock mechanism requires unlocking before modifying mail contents, adjusting attachments, or permanently deleting emails.
* **Vault Economy Support**:
  * Optionally configure mail sending fees and attachment fees, complete with a fee confirmation interface when players compose mail.
* **Custom Sender Profiles**:
  * The sender display name for automated mail (such as reward packages or administrative mail) can be customized in `config.yml` with color code support.
* **Automated Mail Cleanup**:
  * Periodically purges expired mail according to configured retention periods, with options to preserve or clear unread mail and mail with unclaimed attachments.
* **DiscordLink Integration (`link_dc_player`)**:
  * Specify `link_dc_player` as the recipient to broadcast mail to all players with linked Discord accounts simultaneously.
  * The recipient list is read in real time from [DiscordLink](https://github.com/clre20/Discord-link-Minecraft-Plugin)'s SQLite database in read-only mode, requiring no modifications to DiscordLink or enabling its HTTP API.
  * Automatically detects the database location based on the `database.sqlite-file-name` setting in DiscordLink's `config.yml` (default: `plugins/DiscordLink/database.db`).
  * Supported by `send`, `sendpack`, and `consolesend` commands (all require the `cmail.admin` permission).

## Commands & Permissions

The primary command for this plugin is `/cmail`.

| Command | Description | Executor | Permission Node | Default |
| :--- | :--- | :--- | :--- | :--- |
| `/cmail` or `/cmail mail` | Open personal mailbox GUI | Player only | `cmail.use` | Everyone |
| `/cmail send <player\|all\|link_dc_player> <message>` | Compose mail and open attachment placement GUI (`all` for all players, `link_dc_player` for all Discord-linked players) | Player only | `all` / `link_dc_player` require `cmail.admin` / Standard: `cmail.use` | Everyone |
| `/cmail help` | Display command help list | In-game / Console | `cmail.use` | Everyone |
| `/cmail admin` | Open admin inspection GUI | Player only | `cmail.admin` | OP |
| `/cmail reload` | Reload configuration files | In-game / Console | `cmail.admin` | OP |
| `/cmail savepack <package_name>` | Package inventory items into a gift template in-game | Player only | `cmail.admin` | OP |
| `/cmail sendpack <player1,player2...\|all\|link_dc_player> <package_name> <message>` | Send a package template to specified players, the entire server, or all Discord-linked players | In-game / Console | `cmail.admin` | OP |
| `/cmail consolesend <player1,player2...\|link_dc_player> <message> [MATERIAL:SLOT:AMOUNT]...` | Send specific items in designated slots to players via command | In-game / Console | Console unrestricted / `cmail.admin` | OP |

> Command Alias: `/mail` is an alias for `/cmail`.
>
> Recipient Keywords:
> * `all`: All players who have ever joined the server.
> * `link_dc_player`: All players currently linked via DiscordLink (requires DiscordLink installed; list is queried dynamically upon execution).

## Usage Examples

### 1. Packaging and Sending Custom NBT Items
1. Place custom items into your hotbar, then run in-game:
   ```bash
   /cmail savepack cash_v1
   ```
2. Place the items into the first 27 slots of the GUI, then click the bottom-right corner to save and exit.
3. Send to specific players via console or in-game:
   ```bash
   cmail sendpack playerA,playerB cash_v1 Thank you for your support! Here is your reward package.
   ```
   Or send to all players across the server (requires `cmail.admin`, OP, or console):
   ```bash
   cmail sendpack all cash_v1 Thank you for your support, here is server-wide compensation!
   ```

### 2. Direct Item Distribution via Console
Send 64 diamonds to slot 4 and 16 gold ingots to slot 22 for a specific player from console:
```bash
cmail consolesend smart Event Reward DIAMOND:4:64 GOLD_INGOT:22:16
```

### 3. Sending to All Discord-Linked Players
Compose in-game and attach items (requires `cmail.admin`):
```bash
/cmail send link_dc_player Thank you for linking your Discord! Here is your linking reward.
```
Send a package template via console:
```bash
cmail sendpack link_dc_player cash_v1 Thank you for linking your Discord! Here is your linking reward.
```
Send raw items via console:
```bash
cmail consolesend link_dc_player Linking_Reward DIAMOND:0:5
```
* If the DiscordLink database cannot be located, a `link-dc-unavailable` message will trigger and log details to the console; the mail will not be sent.
* If no players are currently linked, a `link-dc-empty` notification will appear.
* Once delivered, any online recipients will receive an instant notification for new mail.

## Configurable Message Keys

The following messages can be configured under the `messages` section of `config.yml`:

> **Automatic config update**: On startup or `/cmail reload`, any keys missing from the server's `config.yml` are added automatically with their default values and comments. Existing values are never overwritten. `help-menu` keeps its custom content; only the outdated `/cmail sendall` line is corrected to `/cmail send all`, and the `link_dc_player` line is added.

| Key | Description |
| :--- | :--- |
| `broadcast-sent` | Successfully broadcast mail to the entire server |
| `link-dc-sent` | Successfully sent mail to Discord-linked players; `%n%` represents the recipient count |
| `link-dc-empty` | No players are currently linked with Discord |
| `link-dc-unavailable` | Failed to load DiscordLink database records |