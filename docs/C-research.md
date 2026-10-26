# Nghiên cứu nhóm C (C1 đến C7)

Đọc code v4.0.2 (master `35f037d5b` trở về sau), **không sửa code game**. Mỗi mục: khả thi, chi phí (S = vài giờ, M = 1 đến 3 ngày, L = trên 1 tuần), rủi ro lệch tất định của Ranked, đề xuất. Đường dẫn tương đối từ `core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/`.

## Tóm tắt để chọn

| ID | Khả thi | Chi phí | Rủi ro lệch Ranked | Đề xuất |
|---|---|---|---|---|
| C1 hotbar 2 / 12 slot | Có, nhưng đụng nhiều file gốc | M đến L | Thấp nếu không đổi save; trung bình nếu đổi `QuickSlot` | Làm sau, chỉ Solo trước |
| C1 camera keybind | Có | S | Không | Làm |
| C1 mouse tile indicator | Có | S đến M | Không | Làm |
| C1 auto-assign talents | Đã có sẵn (nút "ngẫu nhiên") nhưng **dùng `Random` của game** | S | **Cao** nếu dùng trong Ranked | Tắt nút này ở Ranked; bản riêng dùng RNG UI |
| C2 item note marker | Có, dữ liệu đã có | S đến M | Không | Làm |
| C3 build archetype | Có nếu dùng luật tay | M | Không | Hoãn, cần chốt luật |
| C4 wiki học cùng | Có, tái sử dụng Catalog/Bestiary | M | Không (nhưng Ranked đã chuẩn hóa Catalog ở T0.7) | Làm cho Solo |
| C5 exploration memory | Có, dùng `visited`/`mapped` | M | Không | Làm sau B3 |
| C6 combat preview | Có, nhưng phải tách công thức | M | **Trung bình**: `Char.hit` có tác dụng phụ | Chỉ Solo (đúng L14) |
| C7 chế độ ngoài 1v1 | Phụ thuộc server | L | Thấp (cùng nền tất định) | Hoãn tới khi 1v1 ổn định (đúng L15) |

## C1 — Quickslot / controls

- **Đã có:** `QuickSlot.SIZE = 6` (`QuickSlot.java:40`, `public static int`, không `final`). Toolbar chỉ hiện 4 đến 6 ô tùy bề rộng (`ui/Toolbar.java:494`). Phím `QUICKSLOT_1..6`, `ZOOM_IN/OUT`, `CYCLE` có trong `SPDAction.java`. HP bar kẻ địch đã có (`CharHealthIndicator`, `TargetHealthIndicator`).
- **Hotbar 2 / 12 slot:** tăng `SIZE` kéo theo `QuickSlotButton` (mảng `instance`), `Toolbar.btnQuick` và layout, `HeroClass:146`, `WndRanking:380`, lưu/đọc `QuickSlot` (bundle), `EscapeCrystal` lưu placeholder, thêm 6 `SPDAction` và chuỗi i18n. Ít nhất 6 file gốc, layout Toolbar là phần khó merge upstream nhất. Cách nhẹ hơn: hotbar 2 là UI riêng trong package `spdranked`, chỉ hook 1 dòng vào `Toolbar`/`GameScene`, giữ `QuickSlot` 6 ô và thêm một mảng thứ hai lưu riêng (không vào save gốc).
- **Rủi ro:** quickslot là input, không đổi mô phỏng. Nếu save ranked chứa thêm trường thì digest trạng thái phải loại trường UI này.
- **Camera keybind:** chưa có hành động di chuyển/neo camera. Thêm `SPDAction` mới và xử lý trong `GameScene`/`CellSelector` (đã có xử lý zoom chuột ở `scenes/CellSelector.java:60-75`). Chỉ là camera, không rủi ro.
- **Mouse tile indicator:** chưa có hover ô bản đồ; hover hiện chỉ cho `Button` (`ui/Button.java:89`). Cần lắng nghe `PointerEvent` không bấm trong `CellSelector` và vẽ khung ô. Chỉ hiển thị.
- **Auto-assign talents:** đã có nút "random" (`ui/TalentsPane.java:183-215`), nhưng chọn bằng `Random.element(buttons)`, tức **RNG gameplay**. Trong Ranked (T0.8 tách RNG) nút này sẽ làm lệch chuỗi RNG giữa hai người chơi. Cần chặn ở Ranked hoặc sửa thành RNG riêng, và ghi lựa chọn talent dưới dạng lệnh.

## C2 — Item note marker

- `journal/Notes.java:375-415`: `CustomType` có `ITEM_TYPE` (theo class) và `SPECIFIC_ITEM`; đã lưu vào bundle. Thiếu: icon đánh dấu trên item ở sàn (`sprites/ItemSprite`), shop và ô kho (`ui/ItemSlot`).
- Cách làm: một hàm tra cứu `Notes` theo class, vẽ thêm một icon nhỏ; hook 1 dòng ở `ItemSlot` và heap sprite. Ghi chú chỉ dữ liệu người chơi tự nhập, không lộ thông tin.
- Rủi ro: không. Ghi chú nằm trong save nên digest cần bỏ qua `Notes` custom (hoặc chỉ cho Solo).

## C3 — Build Archetype Discovery

- Không có khái niệm "build" trong code. Dữ liệu có thể dùng: talent đã chọn (`Hero.pointsInTalent`), subclass, armor ability, vũ khí/khiên/wand/ring/artifact đang trang bị.
- Luật phải viết tay (ví dụ "wand build" = 2 wand đã nâng trở lên). Không cần dữ liệu balance cho bản đầu; muốn gợi ý "build mạnh" thì cần telemetry từ A5.
- Rủi ro: chỉ đọc trạng thái, không. **Cần user:** danh sách archetype và mục đích (chỉ đặt tên build, hay gợi ý hướng đi).

## C4 — In-game Wiki học cùng người chơi

- `Catalog.isSeen/setSeen` (`journal/Catalog.java:299`), `Bestiary.isSeen` và `encounterCount` (`journal/Bestiary.java:296-322`), UI journal có sẵn (`windows/WndJournal`). Mô tả item/quái lấy từ i18n sẵn có.
- Phần thiếu là nội dung "wiki" (mẹo, tương tác) ngoài mô tả gốc: phải tự viết, đó là chi phí chính. Khóa trang theo `isSeen` để không lộ.
- Rủi ro: không lệch mô phỏng. Lưu ý T0.7 chuẩn hóa Catalog ở Ranked, nên trong Ranked wiki sẽ theo Catalog đã chuẩn hóa, không theo tiến độ thật của người chơi.

## C5 — Exploration Memory + annotation tự sinh

- `levels/Level.java:154-156`: `visited`, `mapped`, `discoverable` đã lưu theo tầng; heap/trap đã thấy có cờ riêng (`Heap.seen`, `Trap.visible`). Điều kiện "đã phát hiện" = `visited || mapped`, cộng cờ đã thấy của đối tượng.
- Annotation tự sinh (ví dụ "shop", "khóa", "bẫy đã thấy") vẽ thành lớp phủ riêng trên `GameScene`/minimap; không cần sửa renderer tilemap gốc nếu dùng lớp phủ.
- Rủi ro: không, miễn chỉ đọc. Cẩn thận không đọc `level.map` ở ô chưa phát hiện.

## C6 — Combat Probability Preview

- Công thức ở `actors/Char.java:619-684`: `acuRoll = Random.Float(acuStat)`, `defRoll = Random.Float(defStat)`, nhân hệ số Bless 1.25, Hex 0.8, Daze 0.5, Champion, Ascension, talent BLESS, `FerretTuft`; trúng khi `acuRoll >= defRoll`; vô hạn ở 1.000.000; tàng hình và Focus là trường hợp đặc biệt. Xác suất tính đóng được: với a, d là giá trị đã nhân hệ số, P(trúng) = 1 - d/(2a) nếu a ≥ d, ngược lại a/(2d).
- **Không gọi được `Char.hit` để thử**: nó gọi `Random` và có tác dụng phụ (`hero.interrupt()` khi `damageInterrupt`, ghi `hitMissIcon`). Phải chép logic hệ số vào một hàm thuần, hoặc refactor `hit` thành "tính hệ số" + "tung xúc xắc" (sửa file gốc). `attackSkill/defenseSkill` của một số class có thể dựa vào trạng thái; cần rà từng override trước khi gọi.
- Sát thương: `damageRoll()` cũng dùng `Random`, nên preview chỉ nên hiện khoảng min đến max, không gọi hàm.
- Rủi ro: cao nếu lỡ gọi hàm có RNG; test digest bật/tắt (mục 3.2) bắt được. Thông tin này cũng là lợi thế, nên theo L14: chỉ Solo.

## C7 — Chế độ ngoài 1v1

- 2v2, Weekly League, Daily seed đều dùng lại cùng nền: seed pool (A4), nền tất định (T0.8), server (A1). Daily/Weekly là dễ nhất (chung seed, xếp hạng theo thời gian/tầng, không cần ghép trận thời gian thực). 2v2 cần luật đội mới và server phức tạp hơn.
- Speedrun.com: **chưa kiểm chứng** hạng mục thật trên trang (không truy cập web trong lần nghiên cứu này). Cần mở trang game SPD trên speedrun.com để xem các category trước khi chọn luật tương thích.
- Rủi ro lệch: thấp, vì không có tương tác giữa người chơi trong trận. Đề xuất giữ mặc định L15: chưa làm cho đến khi 1v1 ổn định; nếu làm, Daily seed trước.
