# Roadmap — spd-ranked

Bản fork của Shattered Pixel Dungeon (nền v4.0.2) gồm hai mảng:
1. **Ranked Duel** — chế độ thi đấu 1v1 có server riêng, rút gọn thành speedrun các boss.
2. **Learning / QoL** — giúp người chơi *hiểu → quyết định → học*, không làm nhân vật mạnh hơn
   ("make player smarter, not character stronger").

Ý tưởng gốc và nghiên cứu: [`docs/ideas/research-va-y-tuong-da-chon.md`](ideas/research-va-y-tuong-da-chon.md).
Repo cũ (chỉ tham khảo, nền v3.3.8): `C:\Workspace\20_Projects\Shattered pixel dungeon Ranked!`.

---

## A. Tính năng giữ lại từ repo cũ (viết lại trên nền mới)

| # | Tính năng | Ghi chú | Tham khảo trong repo cũ |
|---|---|---|---|
| A1 | **Ranked Duel client–server** | ranked-server riêng; matchmaking; rating theo mùa/ladder; strike; vote reroll; reconnect/RESUME; xử lý tranh chấp | `ranked-server/`, `services/`, `networking/VoteReroll.java`, `ui/ResumeIndicator.java`, `TECHNICAL_DESIGN.md` |
| A2 | **Chế độ Duel = speedrun boss** | Ranked không chơi từ đầu đến cuối mà rút gọn thành chuỗi boss | (mới — thiết kế lại) |
| A3 | **Chống gian lận** | digest trạng thái, forensics, lưu replay để xác minh | `TECHNICAL_DESIGN.md`, `docs/redteam-*.md` |
| A4 | **Seed pool có chấm điểm** | SeedScanner/SeedScorer chấm seed theo độ hiệu quả đi tầng 1→14 để loại seed quá xấu | `networking/SeedScanner.java`, `SeedScorer.java`, `seed-criteria/traversal-v1.json` |
| A5 | **Ghost race, replay, telemetry** | | `networking/GhostRace.java`, `ReplayLogger.java`, `RankedTelemetry.java` |
| A6 | **Pack & Skin** | người chơi cài texture pack; khi đấu ranked pack bị giới hạn bởi denylist; package `skins/`; tab Skins trong Settings | `skins/*`, `assets/skins/ranked_denylist.properties` |
| A7 | **Hạ tầng server 24/7** | chạy trên OPPO A53 đã root (chroot Ubuntu); đã đo ~1,1 s khởi động, ~50 MB RAM | `docs/deploy-runbook.md`, `docs/plans/plan-1-database-a53.md` |

Lưu ý: A2 làm thay đổi phạm vi của A4 (seed chấm theo tầng 1→14) và A5 (ghost/replay theo boss) — cần chốt thiết kế A2 trước.

## B. Tính năng mới đã chọn (Learning / QoL)

| # | Tính năng | Tóm tắt |
|---|---|---|
| B1 | **Death Analysis** | sau khi chết: diễn biến vài lượt cuối, sát thương, các lựa chọn thoát thân đã có mà chưa dùng |
| B2 | **Contextual Knowledge** | gặp quái lần đầu hiện tóm tắt ngắn; chỉ hiện thông tin người chơi *đã khám phá* |
| B3 | **Known Item Recognition** | potion/scroll đã biết hiện icon/chữ ngay trên sàn, trong shop, trong kho đồ |
| B4 | **Smart Inventory + Run Value** | tự phân loại kho đồ; đánh giá ★ gợi ý, không tự quyết định thay |
| B5 | **Food Forecast** | thanh đói + ước tính số lượt tới Starving |
| B6 | **Adaptive Training Mode** | practice boss, test item, xem lại combat từ log, thử nghiệm; rồi quay về run thường. Không tính vào ranked/leaderboard |

## C. Cần tìm hiểu thêm (chưa chốt)
- **Quickslot / controls**: second hotbar (12 slot), enemy HP bar, camera keybind, mouse tile indicator, auto-assign talents, zoom tốt hơn.
- **Item note marker**: item có ghi chú hiện icon; ghi chú xuất hiện ở inventory, sàn, shop.
- **Build Archetype Discovery**.
- **In-game Wiki that learns with you** (knowledge acquisition system).
- **Exploration Memory + map annotations tự sinh** — chỉ xuất hiện khi người chơi đã phát hiện.
- **Combat Probability Preview** (Hit ~72% hoặc High/Medium/Low, damage 13–21).
- **Chế độ chơi / cộng đồng**: các hạng mục speedrun kiểu Speedrun.com (loại run, phiên bản, phân loại seed); Weekly Dungeon League (cho người có thời gian đi hết 1 run); Run Ghost để xem lại; Ranked Seasons; hồ sơ cá nhân (thắng/thua, chỉ số đặc biệt); phòng chat chung; Community Challenges; backend xác minh thành tích.

---

## Nguyên tắc thiết kế
1. **Không phá core**: giữ permadeath, khan hiếm tài nguyên, identification, RNG, hunger, nâng cấp giới hạn.
   Tính năng B/C chỉ giảm *friction* và tăng *thông tin*, không buff nhân vật.
2. **Tách ranked và solo**: mọi tính năng hỗ trợ phải có cờ bật/tắt. Trong ranked, cả hai người chơi
   phải có cùng bộ tính năng (công bằng). Tính năng hiển thị thông tin không được làm thay đổi RNG hay
   kết quả mô phỏng (tránh làm lệch replay/digest).
3. **Dễ merge upstream**: code mới để ở package riêng; chỉ hook vài dòng vào file gốc của SPD, đánh dấu `// spd-ranked:`.
4. **Mỗi tính năng có test** trước khi merge vào `master`.

## Thứ tự đề xuất (để thảo luận)
1. **Nền móng**: package riêng, cờ tính năng, chế độ Solo/Ranked tách biệt, khung test.
2. **QoL nhỏ, độc lập, dễ kiểm chứng**: B3 Known Item Recognition → B5 Food Forecast → B4 Smart Inventory.
3. **Ghi log trận đấu** (dùng chung cho B1 Death Analysis, B6 Training, A5 replay/telemetry, A3 chống gian lận) → B1.
4. **Ranked core**: thiết kế A2 (boss speedrun) → A4 seed pool → A1 server + matchmaking → A3 → A5 ghost.
5. **A6 Pack & Skin** (độc lập, làm song song được).
6. **A7 triển khai** server lên A53.
7. Mục C theo kết quả nghiên cứu.
