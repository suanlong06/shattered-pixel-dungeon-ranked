# Quyết định cần chốt — spd-ranked

Tài liệu này thay cho phần "Nhóm 2" cũ của [`TASKS.md`](TASKS.md). Mục đích: biết quyết định nào phải chốt **trước khi bắt tay làm**, quyết định nào có thể hỏi **khi đang code**, và chúng ảnh hưởng nhau thế nào.

Trạng thái: **7 câu "Hỏi ngay" đang chờ user chọn** (mục 4). Sau khi chọn xong sẽ chạy Agent kiểm tra khả thi lâu dài; kết quả ghi ở mục 7.

---

## 1. Đã kiểm chứng trong code SPD v4.0.2

Những điều dưới đây đọc trực tiếp từ source, không phải suy đoán. Chúng là cơ sở cho các câu hỏi.

| # | Phát hiện | Nguồn | Hệ quả |
|---|---|---|---|
| F1 | Boss ở tầng 5, 10, 15, 20, 25 (Goo, Tengu, DM-300, Dwarf King, Yog); tầng 26 là `LastLevel` | `Dungeon.newLevel`, `Dungeon.bossLevel` | Chuỗi boss cố định theo tầng |
| F2 | Seed của một tầng chỉ phụ thuộc seed gốc + số tầng, không phụ thuộc tầng trước | `Dungeon.seedForDepth` | Sinh thẳng tầng boss được về mặt seed. Cách "thả" người chơi vào tầng boss (InterlevelScene) **chưa kiểm chứng** → việc T-start |
| F3 | Tầng boss là đấu trường dựng sẵn; seed chỉ đổi vị trí, trang trí, hazard | `levels/*BossLevel.java` | Seed pool chấm tầng 1→14 (A4) mất tác dụng nếu bỏ qua tầng thường |
| F4 | Có trạng thái tích lũy qua các tầng: `LimitedDrops` (bình sức mạnh, scroll nâng cấp...), bộ bài `Generator`, quest | `Dungeon.LimitedDrops` | Bắt đầu từ tầng boss thì trạng thái này không có, kit phải cấp đủ |
| F5 | Subclass mở bằng `TengusMask` (Tengu), armor ability bằng `KingsCrown` (Dwarf King) | `items/TengusMask.java`, `items/KingsCrown.java` | Số boss mỗi trận quyết định build cần những phần nào |
| F6 | 6 class (Warrior, Mage, Rogue, Huntress, Duelist, Cleric), mỗi class 2 subclass | `HeroClass` | Số kit phải cân bằng |
| F7 | RNG là một stack; gameplay mặc định dùng generator gốc **không seed**; hiệu ứng/sprite (38 file) gọi cùng `Random.Float()` | `SPD-classes/.../Random.java`, `effects/`, `sprites/` | Chiến đấu không tái tạo được chỉ từ seed. Tính năng chỉ hiển thị không được gọi `Random` |
| F8 | Actor chọn lượt từ `HashSet`; hòa thời gian và `actPriority` thì phụ thuộc thứ tự duyệt set. `Actor.java` không override `hashCode` | `actors/Actor.java:152`, `:250-270` | Mô phỏng lại tất định cần sửa nhiều chỗ (phương án C của D5) |
| F9 | Không có JUnit, không có thư mục `test` | `build.gradle`, `core/src` | Khung test dựng từ đầu; libGDX headless dùng được đến đâu **chưa biết** (T0.4) |
| F10 | Đã có sẵn: HP bar kẻ địch (chỉ hiện khi mất máu), ghi chú gắn item (`Notes.CustomRecord`), `Catalog/Bestiary.isSeen`, daily seed, `QuickSlot.SIZE = 6` (static), phím zoom | `ui/CharHealthIndicator`, `journal/Notes`, `Dungeon.daily` | Nhóm C nhỏ hơn dự kiến |
| F11 | Hunger: HUNGRY 300, STARVING 450, +1/lượt; đổi theo `Shadows` (x1,5), `SaltCube`; tạm dừng khi `WellFed`, tầng bị khóa | `actors/buffs/Hunger.java` | Food Forecast tính chính xác được từ buff, không cần ước lượng |
| F12 | Bản debug Android có `applicationIdSuffix ".indev"` | `android/build.gradle` | Cài song song với SPD gốc để thử, chưa cần đổi tên ngay |
| F13 | Thông báo cập nhật và news feed trỏ về upstream | `services/updates`, `services/news`, `docs/recommended-changes.md` | Phải tắt/đổi trước khi phát hành |

## 2. Giả định đang dùng (báo lại nếu sai)

- **Duel là đua song song**: mỗi người chơi bản riêng của cùng một chuỗi, thấy ghost/tiến độ đối thủ, hai nhân vật không tương tác trực tiếp. (Suy ra từ ghost race, reconnect/RESUME, vote reroll trong ROADMAP.)
- Ranked giai đoạn đầu chỉ có 1v1.
- Server do user tự vận hành (OPPO A53), chưa dùng dịch vụ cloud trả phí.
- Repo cũ nằm trên máy user nên phiên cloud này **không đọc được**. Mọi thông tin về thiết kế cũ lấy từ `ROADMAP.md`, chưa đối chiếu với `TECHNICAL_DESIGN.md`.

## 3. Bản đồ phụ thuộc

```mermaid
flowchart LR
  D1["D1 Số boss mỗi trận"] --> D2["D2 Kit và mang theo"]
  D1 --> D3["D3 Class và build"]
  D2 --> D3
  D1 --> A4["A4 Seed pool"]
  D2 --> A4
  D1 --> B6["B6 Training"]
  D2 --> B6
  D4["D4 Điều kiện thắng"] --> A3["A3 Chống gian lận"]
  D4 --> A5["A5 Ghost và replay"]
  D5["D5 Mức xác thực"] --> LOG["T-log: định dạng log"]
  D5 --> A3
  D5 --> A5
  D5 --> B6
  D4 -.-> D5
  LOG --> B1["B1 Death Analysis"]
  LOG --> B6
  D6["D6 Thứ tự làm"] --> PLAN["Lịch làm"]
  D7["D7 Cách theo upstream"] --> VER["Phiên bản luật và cờ tính năng"]
```

Đọc bản đồ theo ba nhóm:

**Nhóm I, luật Duel (D1 đến D4).** D1 là gốc. Số boss quyết định có cần mang đồ giữa các boss không (D2), có cần armor ability không (D3, F5), và seed pool còn ý nghĩa không (A4, F3). D2 và D1 cùng quyết định Training Mode có thể dùng lại "bắt đầu ở tầng boss với kit X" (cùng một cơ chế dùng cho Duel và luyện boss). D4 quyết định server phải đo và kiểm tra cái gì.

**Nhóm II, tin cậy và dữ liệu (D5).** Quyết định này đổi **định dạng event log**, mà log là nền của Death Analysis (B1), Training (B6), replay (A5) và chống gian lận (A3). Vì B1 là tính năng làm sớm, phải chốt D5 trước khi cố định định dạng log, dù Ranked làm sau. D5 cũng chi phối phạm vi sửa code gốc (F7, F8), nên ảnh hưởng độ khó merge upstream (D7).

**Nhóm III, quy trình (D6, D7).** D6 quyết định thứ tự, nhưng chỉ có nghĩa khi D1 đến D5 đã rõ (nếu làm Ranked trước mà luật chưa chốt thì làm lại). D7 quyết định luật có ổn định trong một mùa không.

Hệ quả khi chọn theo đề xuất (D1=A, D2=A): Duel là chuỗi 3 trận boss độc lập, mỗi trận bắt đầu bằng kit cố định. A4 thu nhỏ thành "lọc seed cho đấu trường boss". Số kit cần dựng: 6 class x 3 boss = 18. B6 dùng lại chính cơ chế bắt đầu của Duel.

## 4. Hỏi ngay (7 câu)

Tiêu chí "hỏi ngay": câu trả lời làm thay đổi nền móng (bước 1 đến 3 của thứ tự làm), hoặc làm đổi hình dạng của từ 3 task trở lên, hoặc rất tốn kém để đổi sau.

| ID | Câu hỏi | Chặn / ảnh hưởng | Đề xuất | Kết quả |
|---|---|---|---|---|
| D1 | Số boss mỗi trận Duel | A2, A4, A5, B6, D2, D3 | A: 3 boss đầu (Goo, Tengu, DM-300) | chờ |
| D2 | Trang bị và mang theo giữa các boss | A2, A4, B6, khối lượng cân bằng | A: kit cố định, reset mỗi boss | chờ |
| D3 | Class và build trong Duel | số kit, màn hình chọn, giao thức | A: chọn class, build đặt sẵn | chờ |
| D4 | Điều kiện thắng | A1, A3, A5, ghost | A: ai hạ boss cuối trước (giờ thực) | chờ |
| D5 | Mức xác thực kết quả | T-log, A3, A5, B1, B6, sửa code gốc | B: log có chữ ký + server kiểm quy tắc | chờ |
| D6 | Thứ tự ưu tiên | toàn bộ lịch | A: nền móng, QoL nhỏ, rồi Ranked | chờ |
| D7 | Cách theo upstream | quy trình merge, ổn định mùa | A: theo bản phát hành (tag) | chờ |

Chi tiết ba lựa chọn của từng câu nằm trong các thẻ quyết định ở thread; bảng này ghi kết quả khi user chọn.

## 5. Hỏi khi đang code

Các câu này chỉ ảnh hưởng một task, hoặc cần thấy tính năng chạy rồi mới chọn được. Mỗi câu có mặc định để không dừng việc.

| ID | Câu hỏi | Hỏi khi | Mặc định nếu không trả lời | Task |
|---|---|---|---|---|
| L1 | Hosting: chỉ OPPO A53, hay thêm VPS dự phòng | trước khi dựng khung server | server nhẹ, chạy được cả hai, chưa mua gì | A1, A7 |
| L2 | Tài khoản: ID thiết bị hay email/Google; telemetry bắt buộc hay chọn tham gia | thiết kế A1 | khóa thiết bị + tên hiển thị; telemetry chỉ trong Ranked và nói rõ | A1, A5 |
| L3 | Android và desktop chung một bảng xếp hạng hay tách | thiết kế rating (liên quan D4) | tách theo nền tảng nhập liệu cho đến khi có số liệu | A1 |
| L4 | Rating: thuật toán, độ dài mùa, trận xếp hạng đầu | thiết kế rating | rating kiểu Elo/Glicko đơn giản, mùa 8 tuần | A1 |
| L5 | Tính năng Learning nào bật trong Ranked | trước khi bật Ranked đầu tiên | whitelist do server gửi; bản đầu chỉ tính năng không lộ thông tin thêm | T0.2, B* |
| L6 | Chính sách ngắt kết nối, strike, vote reroll, tranh chấp | thiết kế A1 (đối chiếu repo cũ) | giữ luật cũ nếu đọc được, nếu không dùng bộ luật tối giản | A1 |
| L7 | Tên game, `appPackageName`, icon, thông báo cập nhật, news, nút ủng hộ | trước bản phát hành đầu | dùng `.indev` để thử, chưa phát hành | F12, F13 |
| L8 | Ngôn ngữ cho chuỗi mới: chỉ EN, hay EN + VI | tính năng UI đầu tiên (B3) | EN + VI, ngôn ngữ khác rơi về EN | B* |
| L9 | Known Item Recognition: chỉ item đã định danh hay cả đã thử dùng; ký hiệu cho item chưa biết | B3 | chỉ đã định danh, có ký hiệu rõ cho item chưa biết | B3 |
| L10 | Food Forecast: chỉ số lượt hay thêm dự báo theo tầng | B5 | chỉ số lượt + thức ăn đang có | B5 |
| L11 | Run Value: luật cố định hay thích ứng theo build | B4 | luật cố định, bảng dữ liệu chỉnh được | B4 |
| L12 | Nội dung và tông Death Analysis: liệt kê lựa chọn bỏ lỡ theo luật nào | B1 | chỉ nêu dữ kiện, không phán đoán | B1 |
| L13 | Training: khôi phục từ đầu encounter hay từ log; có tính vào hồ sơ không | B6 (phụ thuộc D5) | đầu encounter, không tính vào hồ sơ | B6 |
| L14 | Combat Probability: dạng % hay Cao/Vừa/Thấp; có cho Ranked không | C6 | % , chỉ Solo | C6 |
| L15 | Mục C nào đáng làm, và chế độ ngoài 1v1 (2v2, Weekly League, Daily) | sau khi có báo cáo khảo sát C | chưa làm gì cho đến khi 1v1 ổn định | C* |
| L16 | Có hỗ trợ iOS không | trước khi dựng CI phát hành | không | CI |

## 6. Tự quyết (kỹ thuật và tối ưu)

Danh sách đầy đủ ở [`TASKS.md`](TASKS.md) mục "Nhóm 3". Bổ sung từ phát hiện ở mục 1:

- **Luật rule-set là dữ liệu**: số boss, kit, có mang theo hay không, whitelist tính năng nằm trong một file cấu hình có số phiên bản, không hard-code. Nhờ vậy chọn sai ở D1/D2 vẫn đổi được bằng dữ liệu thay vì viết lại.
- **Tính năng chỉ hiển thị không gọi `Random`** (F7), có test kiểm tra trạng thái trước/sau khi bật tính năng.
- **Log luôn ghi đủ để nâng cấp xác thực sau** (kết quả mỗi lần tung RNG, thao tác, mốc thời gian), kể cả khi D5 chọn mức nhẹ hơn.
- **Spike khung test trước tiên** (F9): thử dựng test logic thuần (công thức Food Forecast, bảng Run Value, mã hóa log) và một test headless khởi tạo `Dungeon`; kết quả quyết định mức test tích hợp khả thi.
- **Spike "thả vào tầng boss"** (F2): kiểm tra đường vào tầng boss với hero dựng sẵn, và các trạng thái ở F4.
- Dùng `.indev` khi thử trên điện thoại (F12).

## 7. Kết quả kiểm tra khả thi lâu dài

Chưa chạy. Sẽ chạy Agent sau khi user chọn xong 7 câu, kiểm tra: mâu thuẫn giữa các lựa chọn, đối chiếu với các phát hiện ở mục 1, rủi ro khi mở rộng (5 boss, thêm chế độ, nhiều mùa), và những lựa chọn nên đổi.
