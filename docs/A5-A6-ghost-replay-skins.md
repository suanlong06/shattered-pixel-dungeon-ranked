# A5 Ghost, replay, telemetry và A6 Pack & Skin (đợt 1)

Test: `core/src/test/java/.../ranked/test/RaceReplaySkinTest.java` (headless). Test tất định, lint và golden digest vẫn xanh.

## A5

| Phần | Code | Ghi chú |
|---|---|---|
| Mốc theo tầng | `ranked/race/Splits` | Lần đầu tới mỗi tầng và lúc hạ boss, tính bằng `Statistics.duration` (không bị dời gốc khi đổi tầng hoặc lưu như `Actor.now()`). Nạp từ `EventLog`, tách khỏi vòng 4096 sự kiện của Solo nên run dài không mất mốc đầu. |
| Ghost | `ranked/race/Ghost` | Mốc của một run trước cùng seed. `compare` cho chênh lệch ở mốc chung gần nhất (âm là đang nhanh hơn), nên so được suốt cả run chứ không chỉ lúc cuối. `better` chọn ghost tốt hơn: hạ boss sâu hơn, rồi xuống sâu hơn, rồi nhanh hơn. |
| Replay | `ranked/replay/ReplayFile` | Tệp văn bản `spdr-replay v1`: phiên bản game, seed, class, mode, digest cuối, rồi các lệnh theo định dạng `CommandLog`. Xem lại = đưa lệnh qua `TurnRunner`; server dùng cùng tệp để mô phỏng lại (A3). Test: phát lại tệp cho cùng digest, cùng mốc, cùng telemetry. |
| Telemetry | `ranked/telemetry/Telemetry` | Tóm tắt run từ event log: số sự kiện theo loại, sát thương nhận và gây theo nguồn, đồ đã dùng, hồi máu, số lượt ở mỗi tầng. Chỉ cục bộ, không gửi đi đâu. |
| Event log | `EventLog` thêm loại `DEPTH` ở cuối enum | Ghi ở lượt đầu của hero trên một tầng. Trình đọc cũ gặp `DEPTH` sẽ báo lỗi dòng đó; định dạng dòng không đổi nên vẫn là v1. |

Còn để sau: hiển thị ghost trong trận (dòng chữ hoặc bóng mờ), cửa sổ xem replay trong game, lưu ghost tốt nhất theo seed ra tệp, gửi telemetry lên server (cần user quyết định có gửi không và gửi gì).

## A6

Xác minh trên v4.0.2: mọi texture nạp theo đường dẫn đi qua `TextureCache.getBitmap` (`Gdx.files.internal`), giống v3.3.8.

| Phần | Code | Ghi chú |
|---|---|---|
| Hook | `TextureCache.override` (SPD-classes, 1 chỗ `// spd-ranked: A6`) | Trả về ảnh thay thế hoặc null để dùng ảnh gốc. |
| Gói skin | `ranked/skins/SkinPack` | Thư mục `skins/<id>/` trong bộ nhớ local, file PNG cùng đường dẫn tương đối với asset gốc, tùy chọn `skin.properties` (`name`, `author`). Từ chối đường dẫn có `..`, `\`, `:` hoặc bắt đầu bằng `/`. |
| Quản lý | `ranked/skins/Skins` | Cài lúc khởi động (`ShatteredPixelDungeon.create`), gọi lại khi đổi mode (`Features`). Gói chọn được lưu trong `GameSettings`. |
| Denylist Ranked | `assets/skins/ranked_denylist.properties`, `SkinDenylist` | Dữ liệu, không phải code. Chặn cả file: `sprites/statue.png`, `environment/custom_tiles/weak_floor.png`. Chặn vùng: 3 khung ẩn nấp của 4 loại mimic trong `mimic.png`, 4 ô rương trong `items.png` (đã đối chiếu `MimicSprite` và `ItemSpriteSheet` v4.0.2). Vùng bị chặn được chép lại từ ảnh gốc. Không đọc được denylist thì Ranked không dùng skin nào. |
| Tab Skins | `ranked/skins/SkinsTab`, hook trong `WndSettings` | Tab thứ 7 trong cài đặt: "Original art" và danh sách gói; chọn xong nạp lại scene. Chuỗi trong `messages/ranked/ranked.properties`. |

Danh sách chặn lấy từ thiết kế repo cũ (18 vùng, quy về 10 mục ở đây). Nếu muốn chặn thêm hoặc bớt, chỉ cần sửa file properties.

Còn để sau: chặn theo khung cho đúng cả các gói có kích thước ảnh khác bản gốc, gửi mã băm skin cho server, tải gói từ trong game, bản dịch tiếng Việt cho tab.
