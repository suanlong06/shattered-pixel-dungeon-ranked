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
| T0.1 | [T] Package riêng cho code mới, quy ước hook `// spd-ranked:` | theo CLAUDE.md |
| T0.2 | [T] Hệ thống cờ tính năng (feature flags), tách Solo / Ranked | ranked: cả hai người chơi cùng bộ cờ |
| T0.3 | [T] Khung test + CI chạy build/test | chưa có code tính năng nào |
| T0.4 | [X] Xác minh build desktop v4.0.2 chạy được sạch (JDK 17) và chạy được test headless | tiền đề của T0.3 |
| T0.5 | [X] Đọc lại thiết kế repo cũ (`TECHNICAL_DESIGN.md`, `docs/`) để liệt kê điều còn dùng / đã lỗi thời | chỉ đọc, không sửa repo cũ |

### 1.1 Learning / QoL — nhóm B (làm trước vì độc lập, dễ kiểm chứng)
| ID | Tính năng | [X] Xác minh trước | [T] Việc làm |
|---|---|---|---|
| B3 | Known Item Recognition | nơi SPD vẽ item trên sàn/shop/kho; item chưa nhận diện có icon riêng không | hiển thị tên/icon đã biết ở sàn, shop, kho; chỉ hiện cái người chơi *đã biết* (không lộ thông tin) |
| B5 | Food Forecast | công thức đói của v4.0.2 (tốc độ, trạng thái Hungry/Weak/Starving, ảnh hưởng buff) | thanh đói + ước tính lượt tới Starving; liệt kê thức ăn đang có. Chờ Q6 về mức "dự báo an toàn theo tầng" |
| B4 | Smart Inventory + Run Value | cách kho đồ/bag của v4.0.2 tổ chức; xem có chồng lấn với UI gốc không | tự phân loại; ★ gợi ý, có thể bật/tắt; không tự quyết định hộ. Chờ Q7 |
| T-log | **Ghi log trận (event log)** dùng chung | xác định điểm hook: sát thương, di chuyển, dùng item, hồi máu | một định dạng log duy nhất cho B1, B6, A3, A5 (xem 3.3) |
| B1 | Death Analysis | thông tin nào *game thật sự biết* tại thời điểm chết | màn hình sau khi chết: vài lượt cuối, sát thương theo nguồn, đồ thoát thân chưa dùng; chỉ nêu dữ kiện, không phán "chơi dở" |
| B2 | Contextual Knowledge | kiểm tra hệ Journal/Catalog/Bestiary của v4.0.2 để tái sử dụng "đã khám phá" | tóm tắt quái lần đầu gặp; chỉ hiện thông tin đã khám phá |
| B6 | Adaptive Training Mode | khả năng lưu/khôi phục trạng thái một encounter mà không phá RNG | practice boss, test item, xem lại combat từ log; không tính vào ranked/leaderboard. Chờ Q8 |

### 1.2 Ranked core — nhóm A (làm sau khi chốt A2)
| ID | Tính năng | [X] Xác minh trước | [T] Việc làm |
|---|---|---|---|
| A2 | Duel = speedrun boss | **chốt thiết kế (Q1–Q4)** | mô tả luật, chuỗi boss, đồ/loadout, điều kiện thắng, hòa, hết giờ |
| A4 | Seed pool có chấm điểm | tiêu chí chấm của repo cũ (`traversal-v1.json`) còn hợp lý với A2 không (cũ chấm tầng 1→14) | SeedScanner/SeedScorer viết lại theo thiết kế A2; pool riêng cho ranked |
| A1 | Server + matchmaking + rating mùa | quyết định Q9, Q10 | ranked-server, hàng chờ, rating, strike, vote reroll, reconnect/RESUME, tranh chấp |
| A3 | Chống gian lận | danh sách vector tấn công trong `docs/redteam-*.md` còn đúng với client mới | digest trạng thái, forensics, lưu replay để xác minh phía server |
| A5 | Ghost race, replay, telemetry | phụ thuộc T-log và A2 (ghost theo boss hay theo cả run) | ghost, replay viewer, telemetry |
| A6 | Pack & Skin | cách v4.0.2 nạp texture/asset (có thể đã đổi so với v3.3.8) | package `skins/`, tab Skins, denylist khi ranked |
| A7 | Triển khai server 24/7 lên OPPO A53 | quyết định Q9; lại đo RAM/boot khi có server thật | runbook, giám sát, sao lưu DB |

### 1.3 Nghiên cứu nhóm C (xác minh xong mới quyết định có làm không)
Mỗi mục: [X] khảo sát khả thi + chi phí + rủi ro lệch ranked → báo cáo ngắn → user chọn làm hoặc bỏ.

| ID | Mục | Câu cần trả lời khi nghiên cứu |
|---|---|---|
| C1 | Quickslot / controls (hotbar 2, 12 slot, enemy HP bar, keybind camera, mouse tile indicator, auto-assign talents, zoom) | cái nào SPD v4.0.2 đã có? cái nào cần sửa file gốc (khó merge upstream)? |
| C2 | Item note marker | v4.0.2 đã có note cho item chưa (Journal v2.5 có)? chỉ thiếu icon + hiển thị ở sàn/shop? |
| C3 | Build Archetype Discovery | luật nhận diện build là gì; có cần dữ liệu balance? |
| C4 | In-game Wiki học cùng người chơi | tái sử dụng Catalog/Bestiary đến đâu |
| C5 | Exploration Memory + annotation tự sinh | điều kiện "đã phát hiện" để không lộ thông tin; có cần sửa render map |
| C6 | Combat Probability Preview | công thức hit/damage trong v4.0.2; chờ Q11 |
| C7 | Chế độ ngoài 1v1 | xem Q12; khảo sát hạng mục Speedrun.com thật sự có gì (**chưa kiểm chứng**, ghi trong research) |
| C8 | Cộng đồng: Weekly Dungeon League, Run Ghost, Ranked Seasons, hồ sơ cá nhân, chat chung, Community Challenges, xác minh thành tích | phụ thuộc A1/A3/A5; làm sau khi ranked core chạy |

---

## Nhóm 2 — Cần user xác nhận / làm rõ

Sắp theo mức chặn: **Chặn** = không làm được phần đó nếu chưa trả lời.

### Thiết kế Duel (chặn A2, A4, A5)
- **Q1 (chặn). Match gồm những boss nào?** Các lựa chọn: (a) 5 boss chính (Goo, Tengu, DM-300, Dwarf King, Yog), (b) chỉ vài boss đầu, (c) bốc ngẫu nhiên mỗi trận từ một nhóm.
  *Đề xuất:* (a) cố định thứ tự, để seed pool và so sánh ghost đơn giản; (c) để dành cho mùa sau.
- **Q2 (chặn). Giữa các boss: người chơi tự nhặt đồ hay bắt đầu với loadout cố định?**
  (a) loadout cố định sẵn theo boss, (b) tự khám phá từng tầng (không còn là "rút gọn"), (c) kết hợp: tầng ngắn có vài rương/shop.
  *Đề xuất:* (a) hoặc (c) với đồ phát giống nhau cho hai bên — công bằng và dễ kiểm chứng. Cần user chọn.
- **Q3 (chặn). Chọn class/talent/subclass thế nào?** Tự chọn, ép cùng class, hay cấm pick trùng?
  *Đề xuất:* tự chọn class, nhưng loadout theo class do hệ thống cấp.
- **Q4 (chặn). Điều kiện thắng:** ai hạ boss cuối trước, hay tổng thời gian/lượt, hay tính điểm? Đo bằng **lượt trong game** hay **giây thực** (ảnh hưởng chống gian lận và độ trễ mạng)?
  *Đề xuất:* tổng số lượt trong game làm chính, giây thực làm tie-break; có giới hạn thời gian mỗi trận.

### Hạ tầng và kế hoạch (chặn A1, A7)
- **Q9 (chặn). Server chỉ chạy trên OPPO A53 hay có phương án dự phòng (VPS rẻ)?** Máy A53 mất điện/mạng thì sao? Cần địa chỉ công khai (tunnel/DDNS?) bạn đã có chưa?
  *Đề xuất:* thiết kế server nhẹ, chạy được cả trên A53 lẫn VPS; chưa mua gì.
- **Q10. Phạm vi nền tảng người chơi ranked:** chỉ Android, thêm desktop, hay cả hai? Có cần đăng nhập/tài khoản (email, Google) hay chỉ ID thiết bị?
  *Đề xuất:* tài khoản tối giản (tên + khóa thiết bị), chưa email; desktop + Android.

### Hành vi tính năng QoL
- **Q5. Phạm vi "đã biết" của Known Item Recognition:** chỉ item đã *định danh* hay cả item đã *thử dùng*? Item chưa biết: giữ nguyên hay thêm ký hiệu (ví dụ rune)?
  *Đề xuất:* chỉ item đã định danh; thêm ký hiệu rõ cho item chưa biết.
- **Q6. Food Forecast:** chỉ hiện số lượt tới Starving, hay thêm "an toàn đến tầng X"? Cái sau là ước tính, có thể sai.
  *Đề xuất:* chỉ số lượt + thức ăn đang có; không dự báo theo tầng.
- **Q7. Run Value (★):** gợi ý theo luật cố định (ví dụ healing luôn ★★★★★) hay thích ứng theo build hiện tại? Cái sau khó test hơn.
  *Đề xuất:* luật cố định, cấu hình được bằng bảng dữ liệu.
- **Q8. Training Mode:** khi bấm Practice sau khi chết thì dựng lại tình huống từ log (chính xác nhưng khó) hay từ snapshot đầu tầng (đơn giản)? Có cho tính vào hồ sơ không?
  *Đề xuất:* snapshot đầu encounter, không tính vào hồ sơ/ranked.
- **Q11. Combat Probability Preview trong ranked:** cho phép (hai bên cùng thấy) hay chỉ solo? Hiện số hay High/Medium/Low?
  *Đề xuất:* chỉ solo ban đầu; hiện số %; ranked bàn sau.
- **Q12. Chế độ chơi mới ngoài 1v1:** ưu tiên hướng nào — 2v2, thời gian thực kiểu race không đối đầu trực tiếp, Weekly League, Daily seed?
  *Đề xuất:* chưa chọn; làm xong 1v1 rồi mới khảo sát.

### Phạm vi / thứ tự
- **Q13. Ưu tiên:** làm QoL trước (có sản phẩm dùng được sớm) rồi Ranked, hay ngược lại? ROADMAP đang đề xuất QoL trước.
  *Đề xuất:* giữ như ROADMAP.
- **Q14. Có xây dựng cho iOS không?** (v4.0.2 có hướng dẫn iOS).
  *Đề xuất:* không, ít nhất ở giai đoạn này.
- **Q15. Nhánh làm việc:** tài liệu này đang nằm trên nhánh, chưa ở `master`. Bạn muốn merge vào `master` ngay (tài liệu thôi, không có code) hay giữ nhánh để duyệt?

---

## Nhóm 3 — Lựa chọn kỹ thuật & tối ưu (tự quyết khi làm)

Những mục này không cần hỏi trước; nếu bạn không đồng ý, báo lại.

### 3.1 Cấu trúc và merge upstream
- Code mới nằm trong package riêng; chỉ hook ít dòng vào file gốc, đánh dấu `// spd-ranked:`.
- Mỗi tính năng một nhánh `feature/<tên>`; merge `upstream/master` định kỳ vào `master` rồi vào các nhánh.
- Mọi tính năng có cờ bật/tắt; cờ ranked được **server quyết định**, client không tự bật.

### 3.2 Không làm lệch mô phỏng
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
- Lưu trữ nhúng (SQLite hoặc tương đương) với sao lưu định kỳ; sẽ chốt sau khi biết Q9.
- Giao thức có số phiên bản và hỗ trợ RESUME ngay từ bản đầu để không phải đập đi làm lại.

### 3.5 Chất lượng
- Mỗi tính năng: test đơn vị + test hồi quy digest; merge `master` chỉ khi pass.
- Đo hiệu năng cho thứ chạy mỗi lượt (log, Food Forecast, Probability Preview) để không làm chậm game trên máy yếu.
- Khả năng truy cập: không dùng màu làm tín hiệu duy nhất cho ★/trạng thái.
- Ngôn ngữ: thêm chuỗi vào hệ i18n của SPD thay vì hard-code; bản tiếng Việt kèm theo.

---

## Điều chưa kiểm chứng (cần lưu ý)
- Nghiên cứu gốc dùng nguồn Reddit/GitHub issue, có thiên lệch chọn mẫu (research tự nêu).
- Tài liệu research nhắc "SPD 3.3.8 phát hành 3/2026"; repo hiện là v4.0.2 — chưa đối chiếu changelog xem tính năng nào trong danh sách đã có sẵn.
- Số liệu hiệu năng A53 (~1,1 s, ~50 MB) lấy từ ROADMAP, chưa đo lại trên server mới.
