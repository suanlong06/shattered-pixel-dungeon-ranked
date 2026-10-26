# B3, B5, B4, T-log (nhóm B đầu tiên)

Code mới nằm ở `ranked/qol` và `ranked/log`; file gốc SPD chỉ có hook một dòng `// spd-ranked`.
Cả ba tính năng QoL kiểm tra `Features.enabled(...)` (Solo bật mặc định, Ranked tắt cho đến khi chốt).
Test: `QolTest` (headless). Test tất định và golden digest vẫn xanh.

| Task | Xác minh trên v4.0.2 | Đã làm | Hook |
|---|---|---|---|
| B3 Known Item Recognition | Kho và shop (`ItemSlot`) đã có icon loại đã biết, điều kiện `isIdentified()` hoặc ring `isKnown()`. Thiếu ở đồ nằm trên sàn. | `KnownItems.icon` dùng đúng điều kiện của `ItemSlot`; `KnownItemMarkers` vẽ icon ở góc đống đồ đã thấy (chỉ `HEAP` và `FOR_SALE`, không lộ đồ trong rương, mộ, xương). | `GameScene` (1) |
| B5 Food Forecast | `Hunger.act`: +1/lượt, chia 1.5 khi `Shadows`, nhân `SaltCube`; dừng khi `WellFed` hoặc tầng bị khóa (boss). Hungry 300, Starving 450. | `FoodForecast`: số lượt tới Hungry/Starving, thức ăn đang mang và số lượt nó mua được (L10 mặc định). Hiện một dòng "Hunger" trong cửa sổ nhân vật. Chỉ đọc. | `WndHero` (1) |
| B4 Smart Inventory + Run Value | v4.0.2 đã tự xếp đồ vào túi theo loại, nên không thêm bộ xếp thứ hai. | `RunValue`: bảng luật cố định (L11 mặc định) chỉnh được ở `RunValue.TABLE`; chỉ chấm thứ người chơi đã biết. Dấu `*` vàng góc dưới trái ô đồ là gợi ý, không tự làm gì. | `ItemSlot` (2) |
| T-log | Điểm hook: `Char.damage` (sau khiên), `Char.die`, `Char.move`, `Item.execute`, đầu `Hero.act` (hồi máu từ mọi nguồn tính theo HP tăng giữa hai lượt). | `EventLog` v1: `v1|seq|time|TYPE|actor|subject|value`, chỉ thêm cột ở cuối. Solo: vòng 4096 sự kiện; Ranked: không giới hạn. Reset khi bắt đầu hoặc nạp run (`Features`). Không lưu vào save, không vào digest. | `Char` (3), `Item` (1), `Hero` (1) |

Còn để sau: thanh đói đồ họa (B5 hiện là dòng chữ), lưu log Ranked gửi server (A3/A5), đưa `RunValue.TABLE` ra file dữ liệu ngoài code (3.3), dịch chuỗi "Hunger" vào `Messages`.
