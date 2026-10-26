# Nhóm task — spd-ranked

Tổng hợp lại từ [`ROADMAP.md`](ROADMAP.md) và [`ideas/research-va-y-tuong-da-chon.md`](ideas/research-va-y-tuong-da-chon.md).
Chia làm 3 nhóm:

- **Nhóm 1 — Task sẽ thực hiện** (đã rõ mục tiêu, kèm việc cần xác minh trước khi code).
- **Nhóm 2 — Cần user xác nhận / làm rõ** (chọn hướng, ảnh hưởng đến thiết kế hoặc trải nghiệm).
- **Nhóm 3 — Lựa chọn kỹ thuật & tối ưu** (Claude tự quyết khi làm; ghi lại ở đây để user biết và phản đối nếu muốn).

Mỗi câu hỏi ở Nhóm 2 có **đề xuất mặc định**. Nếu user không trả lời, sẽ làm theo mặc định.

---

## Nhóm 1 — Task sẽ thực hiện

Ký hiệu: **[X]** = việc cần xác minh trước khi code; **[T]** = việc cần làm.
Mã A/B/C lấy từ ROADMAP. "Chờ Q#" = phụ thuộc câu hỏi ở Nhóm 2.

### 1.0 Nền móng (làm đầu tiên, mọi thứ khác dựa vào)
| ID | Việc | Ghi chú |
|---|---|---|
| T0.1 | [X] Package riêng cho code mới, quy ước hook `// spd-ranked:` | theo CLAUDE.md; xem `docs/T0.1-T0.2-T0.7-ranked-mode.md` |
| T0.2 | [X] Hệ thống cờ tính năng (feature flags), tách Solo / Ranked | ranked: cả hai người chơi cùng bộ cờ |
| T0.3 | [T] Khung test + CI chạy build/test | chưa có code tính năng nào |
| T0.4 | [X] Xác minh build desktop v4.0.2 chạy được sạch (JDK 17) và chạy được test headless | tiền đề của T0.3 |
| T0.5 | [X] Đọc lại thiết kế repo cũ (`TECHNICAL_DESIGN.md`, `docs/`) để liệt kê điều còn dùng / đã lỗi thời | chỉ đọc, không sửa repo cũ |
| T0.6 | [X] **Spike tất định S1 đến S6** (xem `DECISIONS.md` mục 7.4): digest tầng 1 đến 15 giữa hai JVM, nhật ký RNG, vector toán ART/JVM, headless, bot đo thời gian, thử vá | quyết định mức xác thực D5 dựa trên số đo |
| T0.7 | [X] Chế độ Ranked "sạch": mở khóa cả 6 class, tắt Bones, chuẩn hóa meta (trang hướng dẫn, intro, Catalog), chặn rút bộ bài lúc chơi dùng chung với sinh tầng | cần cho "cùng seed, cùng tầng" |
| T0.8 | [X] **Nền tất định** (D5 Nặng, xem `docs/T0.8-deterministic-core.md`; còn: lệnh cho lựa chọn trong cửa sổ, chuyển tầng đặc biệt, chạy trên ART): PRNG gameplay lưu được trạng thái, tách RNG hiệu ứng, `StrictMath`, thứ tự Actor `(time, priority, id)`, đổi HashMap/HashSet sang có thứ tự, bộ chạy headless và ghi/phát lại input theo lệnh | làm sau spike T0.6, **trước mọi QoL**; xem `DECISIONS.md` mục 7.6 |

### 1.1 Learning / QoL — nhóm B (làm trước vì độc lập, dễ kiểm chứng)
| ID | Tính năng | [X] Xác minh trước | [T] Việc làm |
|---|---|---|---|
| B3 | Known Item Recognition | nơi SPD vẽ item trên sàn/shop/kho; item chưa nhận diện có icon riêng không | hiển thị tên/icon đã biết ở sàn, shop, kho; chỉ hiện cái người chơi *đã biết* (không lộ thông tin) |
| B5 | Food Forecast | công thức đói của v4.0.2 (tốc độ, trạng thái Hungry/Weak/Starving, ảnh hưởng buff) | thanh đói + ước tính lượt tới Starving; liệt kê thức ăn đang có. Mức dự báo xem L10 |
| B4 | Smart Inventory + Run Value | cách kho đồ/bag của v4.0.2 tổ chức; xem có chồng lấn với UI gốc không | tự phân loại; ★ gợi ý, có thể bật/tắt; không tự quyết định hộ. Luật Run Value xem L11 |
| T-log | **Ghi log trận (event log)** dùng chung | xác định điểm hook: sát thương, di chuyển, dùng item, hồi máu | một định dạng log duy nhất cho B1, B6, A3, A5 (xem 3.3) |
| B1 | Death Analysis | thông tin nào *game thật sự biết* tại thời điểm chết | màn hình sau khi chết: vài lượt cuối, sát thương theo nguồn, đồ thoát thân chưa dùng; chỉ nêu dữ kiện, không phán "chơi dở" |
| B2 | Contextual Knowledge | kiểm tra hệ Journal/Catalog/Bestiary của v4.0.2 để tái sử dụng "đã khám phá" | tóm tắt quái lần đầu gặp; chỉ hiện thông tin đã khám phá |
| B6 | Adaptive Training Mode và **Boss Practice** (vào thẳng boss bằng kit, chơi đơn, chế độ riêng ngoài Ranked, xem L17) | khả năng lưu/khôi phục trạng thái một encounter mà không phá RNG | practice boss, test item, xem lại combat từ log; không tính vào ranked/leaderboard. Cách khôi phục xem L13 |

### 1.2 Ranked core — nhóm A (làm sau khi chốt A2)
| ID | Tính năng | [X] Xác minh trước | [T] Việc làm |
|---|---|---|---|
| A2 | Duel = đua song song từ tầng 1 lên boss 5/10/15, ai hạ boss trước thắng | D1 đã chọn (3 boss đầu); còn D2 (class), D4 (cách tính thắng) | mô tả luật, điều kiện thắng, hòa, hết giờ, bỏ cuộc |
| A4 | Seed pool có chấm điểm | **spike T-seed**: chạy thật để xác nhận đồ có sẵn từng tầng không đổi theo hành vi chơi (đọc code cho thấy không đổi; còn vài ngoại lệ hẹp, xem DECISIONS F14); tiêu chí chấm cũ (`traversal-v1.json`, tầng 1 đến 14) cần đổi sang "đồ tốt tầng 1 đến 5" | SeedScanner/SeedScorer viết lại: tạo seed pool có đồ giúp sống sót tầng 1 đến 5 (xem L18); pool riêng cho ranked |
| A1 | Server + matchmaking + rating mùa | quyết định L1, L2, L3 | ranked-server, hàng chờ, rating, strike, vote reroll, reconnect/RESUME, tranh chấp |
| A3 | Chống gian lận | danh sách vector tấn công trong `docs/redteam-*.md` còn đúng với client mới | digest trạng thái, forensics, lưu replay để xác minh phía server |
| A5 | Ghost race, replay, telemetry | phụ thuộc T-log và A2 (ghost theo tầng/mốc trong cả run) | ghost, replay viewer, telemetry |
| A6 | Pack & Skin | cách v4.0.2 nạp texture/asset (có thể đã đổi so với v3.3.8) | package `skins/`, tab Skins, denylist khi ranked |
| A7 | Triển khai server 24/7 lên OPPO A53 | quyết định L1; lại đo RAM/boot khi có server thật | runbook, giám sát, sao lưu DB |

### 1.3 Nghiên cứu nhóm C (xác minh xong mới quyết định có làm không)
Mỗi mục: [X] khảo sát khả thi + chi phí + rủi ro lệch ranked → báo cáo ngắn → user chọn làm hoặc bỏ.

| ID | Mục | Câu cần trả lời khi nghiên cứu |
|---|---|---|
| C1 | Quickslot / controls (hotbar 2, 12 slot, enemy HP bar, keybind camera, mouse tile indicator, auto-assign talents, zoom) | đã kiểm chứng: HP bar kẻ địch đã có (`CharHealthIndicator`, chỉ hiện khi mất máu), phím zoom đã có, `QuickSlot.SIZE = 6` là static; còn lại: hotbar 2, camera keybind, tile indicator, auto-assign talents chưa xác minh. Cái nào cần sửa file gốc (khó merge upstream)? |
| C2 | Item note marker | đã kiểm chứng: `Notes.CustomRecord` có kiểu gắn với item. Có thể chỉ thiếu icon đánh dấu và hiển thị ở sàn/shop |
| C3 | Build Archetype Discovery | luật nhận diện build là gì; có cần dữ liệu balance? |
| C4 | In-game Wiki học cùng người chơi | `Catalog.isSeen` và `Bestiary.isSeen` đã có; kiểm tra tái sử dụng được đến đâu |
| C5 | Exploration Memory + annotation tự sinh | điều kiện "đã phát hiện" để không lộ thông tin; có cần sửa render map |
| C6 | Combat Probability Preview | công thức hit/damage trong v4.0.2; xem L14 |
| C7 | Chế độ ngoài 1v1 | xem L15; khảo sát hạng mục Speedrun.com thật sự có gì (**chưa kiểm chứng**, ghi trong research) |
| C8 | Cộng đồng: Weekly Dungeon League, Run Ghost, Ranked Seasons, hồ sơ cá nhân, chat chung, Community Challenges, xác minh thành tích | phụ thuộc A1/A3/A5; làm sau khi ranked core chạy |

---

## Nhóm 2 — Cần user xác nhận / làm rõ

Đã tách và phân tích quan hệ trong [`DECISIONS.md`](DECISIONS.md):

- **Hỏi ngay (D1 đến D7):** luật Duel (số boss, kit, class/build, điều kiện thắng), mức xác thực kết quả, thứ tự ưu tiên, cách theo upstream. Mỗi câu có 3 lựa chọn và một đề xuất.
- **Hỏi khi đang code (L1 đến L19):** hạ tầng, tài khoản, rating, chi tiết từng tính năng QoL, phát hành. Mỗi câu có thời điểm hỏi và mặc định.

Các câu Q1 đến Q15 ở bản trước đã được gộp vào hai danh sách trên (Q1 đến Q4 thành D1 đến D4; Q13 thành D6; Q9 thành L1; Q10 thành L2, L3; Q5 thành L9; Q6 thành L10; Q7 thành L11; Q8 thành L13; Q11 thành L14; Q12 thành L15; Q14 thành L16). Q15 đã có đáp án: giữ nhánh để duyệt.

---

## Nhóm 3 — Lựa chọn kỹ thuật & tối ưu (tự quyết khi làm)

Những mục này không cần hỏi trước; nếu bạn không đồng ý, báo lại.

### 3.1 Cấu trúc và merge upstream
- Code mới nằm trong package riêng; chỉ hook ít dòng vào file gốc, đánh dấu `// spd-ranked:`.
- Mỗi tính năng một nhánh `feature/<tên>`; merge `upstream/master` định kỳ vào `master` rồi vào các nhánh.
- Mọi tính năng có cờ bật/tắt; cờ ranked được **server quyết định**, client không tự bật.

### 3.2 Không làm lệch mô phỏng
- Sự thật từ code (xem `DECISIONS.md` F7, F8, F15, F16): RNG chiến đấu không seed, hiệu ứng dùng chung `Random`, `Actor` chọn lượt bằng `HashSet`. D5 đã chọn Nặng nên các điểm này phải được sửa trong T0.8 (thiết kế ở `DECISIONS.md` mục 7.6).
- Tính năng chỉ-hiển-thị **không bao giờ** gọi RNG của game và không đổi trạng thái; nếu cần ngẫu nhiên thì dùng nguồn riêng.
- Test tự động kiểm tra: cùng seed + cùng input, bật/tắt tính năng cho cùng digest trạng thái.
- Tính toán như dự báo đói, xác suất đánh trúng dùng đúng công thức game (đọc từ class gốc), không sao chép số cứng.

### 3.3 Dữ liệu dùng chung
- **Một event log duy nhất** (versioned, thêm-only, nén được) cho Death Analysis, Training, replay, telemetry và chống gian lận — tránh bốn hệ ghi riêng.
- Log lưu theo vòng giới hạn kích thước ở solo; ranked gửi digest + log cho server.
- Bảng dữ liệu cấu hình (ví dụ Run Value, tóm tắt quái) tách khỏi code để chỉnh mà không build lại.

### 3.4 Server
- Viết lại gọn, ưu tiên nhẹ (mục tiêu giữ ~50 MB RAM, boot ~1 s như đã đo ở repo cũ); chọn thư viện sau khi benchmark nhỏ.
- Server là nguồn sự thật: kiểm tra kết quả bằng digest + replay, không tin client.
- Lưu trữ nhúng (SQLite hoặc tương đương) với sao lưu định kỳ; sẽ chốt sau khi biết L1.
- Giao thức có số phiên bản và hỗ trợ RESUME ngay từ bản đầu để không phải đập đi làm lại.

### 3.5 Chất lượng
- Repo chưa có khung test (không JUnit, không thư mục `test`). Việc đầu tiên là spike dựng test cho logic thuần và thử khởi tạo `Dungeon` headless; mức test tích hợp tùy kết quả spike.
- Mỗi tính năng: test đơn vị + test hồi quy digest; merge `master` chỉ khi pass.
- Đo hiệu năng cho thứ chạy mỗi lượt (log, Food Forecast, Probability Preview) để không làm chậm game trên máy yếu.
- Khả năng truy cập: không dùng màu làm tín hiệu duy nhất cho ★/trạng thái.
- Ngôn ngữ: thêm chuỗi vào hệ i18n của SPD thay vì hard-code; bản tiếng Việt kèm theo.

---

## Điều chưa kiểm chứng (cần lưu ý)
- Nghiên cứu gốc dùng nguồn Reddit/GitHub issue, có thiên lệch chọn mẫu (research tự nêu).
- Tài liệu research nhắc "SPD 3.3.8 phát hành 3/2026"; repo hiện là v4.0.2 — chưa đối chiếu changelog xem tính năng nào trong danh sách đã có sẵn.
- Số liệu hiệu năng A53 (~1,1 s, ~50 MB) lấy từ ROADMAP, chưa đo lại trên server mới.
