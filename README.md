# 狹縫之間圖書館（Situation Puzzle）

一款以「時空縫隙」為舞台的網頁敘事解謎遊戲：玩家掉進時空縫隙中的一座神祕圖書館，必須陪館長玩完四則故事猜謎，才能從縫隙回到原來的世界——否則將永遠留在這裡。

每則故事都以**倒敘**呈現：館長先說出故事的「結果」，玩家透過四次提問拼湊「原因」。而這些故事，全都改編自**真實發生過的事件**。

🎮 線上試玩：<https://situationpuzzle.mypc.tw/>

## 特色

- 🕯️ **倒敘式真實事件改編**：四則灰暗故事（孟喬森母女、盜屍之愛、完美女兒的謊言、座間九人殺害事件），通關後可逐則解鎖「聆聽故事起源」，查看對應的真實案件介紹與延伸連結
- 🤖 **雙 AI 角色**（[OpenRouter](https://openrouter.ai/)）：館長 NPC 依故事脈絡與真相卡演繹回覆（非複誦腳本）；迴紋針精靈是玩家助理，五段提示等級（OFF～SPOILER），具備畫面導覽能力
- 🎵 **背景音樂**（[PeriTune](https://peritune.booth.pm/) 素材）：大廳與各則故事專屬配樂、無縫循環、右下角一鍵靜音；開場進度條預載全部曲目，遊玩中切曲零卡頓；結局動畫內建 Forest Sage 配樂（結尾漸淡）
- 🎬 **通關動畫**：完成四則故事後，中央浮現「通往世界」按鈕——按下後播放開門動畫，回到現實世界
- ✨ **演出細節**：NPC 百葉窗式登場、打字機字幕（40ms/字，可調）、說話動畫（動態 WebP 眨眼／張嘴循環）
- 📜 **無狀態後端**：遊戲進度以簽章加密 Cookie 攜帶，伺服器不存 session

## 技術棧

| 層 | 技術 |
|----|------|
| 後端 | Java 21 · Spring Boot 3.4（Web / JPA / Validation） |
| 資料庫 | MySQL |
| 前端 | JavaScript（原生 ES Modules）· CSS |
| AI | OpenRouter REST API（Java HttpClient 自行串接，無 SDK） |

## 快速開始

需求：JDK 21+、Maven 3.9+、MySQL。

```bash
# 1. 建立資料庫（一次即可）
mysql -uroot -p -e "CREATE DATABASE IF NOT EXISTS situation_puzzle CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

# 2. 環境變數（建議寫在 .env，已 gitignore）
export MYSQL_USER=root
export MYSQL_PASSWORD=你的密碼
export OPENROUTER_API_KEY=sk-or-...        # 選填：無 Key 時走 DB 內建腳本，遊戲仍可玩
export GAME_COOKIE_SECRET=一段固定亂數     # 建議：未設則重啟後玩家進度失效

# 3. 啟動
mvn spring-boot:run
# 或打包執行
mvn -DskipTests package && java -jar target/situation-puzzle-0.1.0-SNAPSHOT.jar
```

瀏覽器打開 <http://localhost:8080/>。啟動時自動執行 `schema.sql` + `data.sql`（種子資料會刷新）。

開發用 H2 記憶體庫：`mvn spring-boot:run -Dspring-boot.run.profiles=h2`

## 環境變數

| 變數 | 必填 | 說明 |
|------|------|------|
| `MYSQL_USER` / `MYSQL_PASSWORD` | ✅ | 資料庫帳密 |
| `OPENROUTER_API_KEY` | ➖ | AI 回覆；未設時使用 DB 腳本回覆 |
| `GAME_COOKIE_SECRET` | ➖ | 進度 Cookie 簽章密鑰（正式環境建議固定值） |
| `LLM_ENABLED` | ➖ | 預設 `true`，設 `false` 全走腳本 |

## 遊戲規則

- 每則故事 **4 次提問**，從八個選項中選擇；正確提問每題 **+20 分**
- 單則 **≥60 分** 館長會揭曉該則謎底（真相）
- 四則皆完成後可通往世界；**所有故事都揭謎＝真結局**
- 進度以 Cookie 保存（2 小時），關閉分頁前可隨時續玩

## 主要 API

- `POST /api/game/start`、`GET /api/game/state`、`POST /api/game/finish`、`/ending/done`
- `POST /api/stories/select`、`current/answer(/stream)`、`current/continue`、`current/finalize`、`current/back-to-menu`
- `GET /api/stories/{order}/real-case`（該則玩過後解鎖）
- `POST /api/ai/helper/chat(/stream)`、`hint(/stream)`、`settings`

## 專案結構

```
src/main/java/com/situationpuzzle/
  web/            REST API（狀態重建 Filter、SSE、ControllerAdvice）
  service/game/   遊戲狀態機（階段、計分、揭謎、結局）
  service/ai/     LLM 客戶端、館長對話、精靈提示
  service/state/  無狀態進度編解碼（Cookie 核心）
  domain/         JPA Entity
src/main/resources/
  static/         前端（原生 JS/CSS、立繪、背景、結局影片）
  schema.sql / data.sql / application.yml
```

## 開發狀態

- [x] 故事一～四定稿（含真實案件檔案）
- [x] 結局動畫、逐則真實事件解鎖、雙 AI、無狀態進度
- [x] 背景音樂系統（大廳／各則故事配樂、靜音切換、開場預載）

## 製作與感謝

- **劇本**：林青乖（Brad.Lin）
- **人物繪製**：林青乖（Brad.Lin）
- **UI 設計**：傅遠佳（Cody.Fu）
- **程式開發**：傅遠佳（Cody.Fu）
- **背景素材**：[ネギ屋（細長いネギ）](https://booth.pm/zh-tw/items/3280495)、[Stream Atelier - AI素材工房 -](https://booth.pm/zh-tw/items/8494276)
- **音樂**：[PeriTune](https://peritune.booth.pm/) — [Forgotten Past](https://booth.pm/zh-tw/items/4911242)、[Silent Witness](https://booth.pm/zh-tw/items/5740420)、[Wacky Witnes](https://booth.pm/zh-tw/items/6532506)、[Spooky Night](https://peritune.booth.pm/items/2715397)、[Coppelia_Room](https://peritune.booth.pm/items/4553746)、[Forest Sage](https://booth.pm/zh-tw/items/4913874)
- **AI 串接**：[OpenRouter](https://openrouter.ai/)
- **AI 輔助開發**：Claude-Code（GLM-5.1）
- **AI 動畫製作**：Grok

遊戲內標題頁「製作名單」亦有完整清單。
