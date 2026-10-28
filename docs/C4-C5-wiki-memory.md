# C4, C5

Code mới ở `ranked/qol` (`Wiki`, `ExplorationMemory`); file gốc SPD chỉ có hook một dòng `// spd-ranked`.
Cờ `Feature.WIKI`, `Feature.EXPLORATION_MEMORY`: Solo bật mặc định, Ranked bật cho cả hai người (chỉ hiển thị).
Test: `WikiMemoryTest` (headless). Toàn bộ test core (tất định, lint, golden digest) vẫn xanh.

| Task | Đã làm | Hook |
|---|---|---|
| C4 Wiki | Dùng lại Catalog/Bestiary của nhật ký. Trang chỉ mở khi `isSeen` (đã gặp theo hồ sơ); trang mở có thêm dòng **Mẹo** sau mô tả gốc, lấy từ bundle ranked (`ranked.qol.wiki.tip.<class>`, Anh + Việt, **bản nháp chờ duyệt**). Mẹo gắn đúng class, không kế thừa (Fetid Rat không nhận mẹo của Rat). Ranked: trong trận, nhật ký dùng danh mục chuẩn hóa = mở hết mọi trang, giống nhau cho hai người, không theo hồ sơ. Không code gameplay nào đọc phần này. | `WndJournal` (4) |
| C5 Bản đồ ghi nhớ | Mỗi tầng chính: cửa dẫn tới vùng chưa khám phá, cửa khóa/pha lê (đã có chìa chưa), đồ và rương đã thấy nhưng bỏ lại. Hiện thành một dòng dưới tầng trong mục ghi chú nhật ký, và dấu `?` vàng trên cửa chưa khám phá. Chỉ đọc ô đã khám phá (`visited || mapped`) và heap đã thấy (`Heap.seen`); cửa bí mật vẫn là tường cho tới khi tìm ra (đã thử trên desktop). Lưu cùng run (ngoài bundle tầng nên digest tầng không đổi). | `WndJournal` (1), `GameScene` (1) |

Mẹo hiện có: quái tầng 1 đến 15 và 3 boss (Goo, Tengu, DM-300), 12 thuốc, 12 cuộn giấy. Mỗi mẹo đã đối chiếu với code v4.0.2. Test kiểm mỗi khóa mẹo trỏ tới một mục có trong nhật ký và hai ngôn ngữ có cùng danh sách.

Sửa kèm: mở Settings trong game bị crash (`SkinsTab.buttons` null vì `createChildren()` chạy trước khởi tạo field).
