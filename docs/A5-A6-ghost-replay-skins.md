# A5 Ghost, replay, telemetry và A6 Pack & Skin (đợt 1)

Test: `core/src/test/java/.../ranked/test/RaceReplaySkinTest.java` (headless). Test tất định, lint và golden digest vẫn xanh.

## A5

| Phần | Code | Ghi chú |
|---|---|---|
| Mốc theo tầng | `ranked/race/Splits` | Lần đầu tới mỗi tầng và lúc hạ boss, tính bằng `Statistics.duration` (không bị dời gốc khi đổi tầng hoặc lưu như `Actor.now()`). Nạp từ `EventLog`, tách khỏi vòng 4096 sự kiện của Solo nên run dài không mất mốc đầu. |
| Ghost | `ranked/race/Ghost` | Mốc của một run trước cùng seed. `compare` cho chênh lệch ở mốc chung gần nhất (âm là đang nhanh hơn), nên so được suốt cả run chứ không chỉ lúc cuối. `better` chọn ghost tốt hơn: hạ boss sâu hơn, rồi xuống sâu hơn, rồi nhanh hơn. |
| Replay | `ranked/replay/ReplayFile` | Tệp văn bản `spdr-replay v1`: phiên bản game, seed, class, mode, digest cuối, rồi các lệnh theo định dạng `CommandLog`. Xem lại = đưa lệnh qua `TurnRunner`; server dùng cùng tệp để mô phỏng lại (A3). Test: phát lại tệp cho cùng digest, cùng mốc, cùng telemetry. |
| Telemetry | `ranked/telemetry/Telemetry`, server `Telemetry` + bảng `telemetry` (schema 4) | Tóm tắt run từ event log: số sự kiện theo loại, sát thương nhận và gây theo nguồn, đồ đã dùng, hồi máu, số lượt ở mỗi tầng, cộng phiên bản game, class, cách kết thúc, tầng, lượt. Trong Ranked, client gửi một frame `TELEMETRY` (giao thức 3) ngay trước báo cáo DIED hoặc CLEARED. Gọn: dòng `key=value`, mỗi bảng theo nguồn giữ 40 mục lớn nhất, tối đa 15 KB (server nhận tối đa 16 KB). Server kiểm tra định dạng rồi lưu theo (trận, bên), không dùng để xét kết quả. Dùng để tinh chỉnh tính năng và làm dữ liệu train; muốn thêm trường thì chỉ thêm dòng mới. |
| Event log | `EventLog` thêm loại `DEPTH` ở cuối enum | Ghi ở lượt đầu của hero trên một tầng. Trình đọc cũ gặp `DEPTH` sẽ báo lỗi dòng đó; định dạng dòng không đổi nên vẫn là v1. |

Đợt 2: **ghost trong trận** (Solo, cờ `GHOST_RACE`): run tốt nhất mỗi seed lưu ở `ghosts/<seed>.txt` (`ranked/race/Ghosts`), cập nhật ở mỗi mốc mới nếu tốt hơn. Run đang chơi so với ghost đã có lúc bắt đầu và hiện một dòng "Ghost" trong cửa sổ nhân vật (hook `WndHero`), ví dụ "50 turns ahead (depth 2)". Ranked và Boss Practice không dùng ghost Solo. Khi chạy headless (test, server) không ghi file.

Đợt 3: **trình xem replay trong game**. Test: `core/src/test/java/.../ranked/test/ReplayViewerTest.java` (xem các ván live của `ChoiceReplayTest`/`AlchemyReplayTest` và một ván bot qua cầu thang, với người xem bấm dừng/tốc độ/bước ngẫu nhiên trên khung hình dài ngắn thất thường: trace phải khớp ván live và bản phát lại của server).

| Phần | Code | Ghi chú |
|---|---|---|
| Chạy từng bước | `TurnRunner.step()` | Vòng lặp `run()` cũ tách thành các bước (một lượt actor, một lệnh, hoặc một khung hình chờ sprite/callback/cửa sổ). `run()` giờ chỉ là vòng lặp gọi `step()`, nên server (A3) và trình xem dùng chung một đường code; golden digest không đổi. Chế độ `hostScenes`: InterlevelScene thật của game đổi tầng, vòng render thật chạy khung hình và callback. Thời gian chờ tính theo thời gian scene (`Game.elapsed`), không theo số khung, nên khung dài ngắn bất kỳ cho cùng kết quả. |
| Phát lại | `ranked/replay/ReplayPlayback`, `ranked/sim/ReplayInput` | Tạm dừng (đóng băng scene bằng `Game.timeScale = 0`), tốc độ x1/4 đến x8, "Bước" chạy đúng một lệnh rồi dừng lại khi nhân vật chờ lệnh tiếp. Giữa hai lệnh scene chạy thêm 0,3 giây (theo tốc độ) cho dễ theo dõi. `ReplayInput` là cách đưa lệnh vào dùng chung với `ReplayCheck` của server (lệnh `@k` vào trước lượt k+1 của nhân vật). |
| Trong game | `ranked/replay/ReplayViewer`, `ReplayControls`, hook trong `GameScene.update` | Bắt đầu run như `RankedSession.startRun` (seed, class, mode, challenges của tệp) ở slot riêng `MAX_SLOTS + 3`. Khi đang xem, `GameScene.update` gọi trình xem thay cho actor thread. Thanh điều khiển ở trên cùng: Dừng/Chạy, -, +, Bước, Thoát, và dòng tiến độ. Chạm và phím của người xem không tới game (chỉ tới thanh điều khiển); Esc/Back thoát. Cảnh giả kim (AlchemyScene) cũng được theo: hook trong `AlchemyScene.update` bước lệnh `ALCH`/`ENERGIZE` y như server, và khi đang xem thì app tạm dừng không trả nguyên liệu về (chỉ trả khi bản ghi có `ALCH PAUSE`). Đi tới scene trình xem chưa theo được (client bị tắt giữa lúc giả kim) thì dừng và về màn hình chính kèm thông báo; mặt đất và bùa hộ mệnh là kết thúc của run, xem đợt 4. Lệnh không khớp game thì dừng và báo "không khớp". |
| Không đụng hồ sơ | `Features.viewingReplay()`, `leavesProfileAlone()` | Như Boss Practice: không huy hiệu, bảng xếp hạng, bestiary/catalog, ghost; thêm không đọc/để lại bones. Thoát thì xóa slot replay, trả lại slot, class đang chọn và "last class" của người chơi. |
| Danh sách | `ranked/replay/Replays`, `WndReplays`, nút "Replays" trong khung tùy chọn ở màn chọn nhân vật | Thư mục local `replays/` (cùng kiểu `ghosts/`, `skins/`), mới nhất trước, 6 tệp một trang, tệp hỏng vẫn hiện để xóa. Chi tiết: class, mode, seed, số lệnh, cảnh báo nếu khác phiên bản game hoặc không phải Ranked. Trận Ranked tự lưu replay khi có kết quả (`RankedSession`). |
| Tệp | `ReplayFile` thêm `challenges=` và `label=` (tùy chọn) | Chỉ ghi khi khác mặc định, nên tệp cũ đọc và ghi lại y như trước. |

Đã chạy thử bản desktop thật (Xvfb, trước khi gộp giả kim): danh sách, phát 400 lệnh qua 2 tầng với tạm dừng, đổi tốc độ, bước, bấm bản đồ và phím di chuyển bị chặn, trạng thái cuối khớp bản phát lại headless; tệp hỏng dừng với thông báo; Esc và Thoát về màn hình chính; sau đó chơi ván thường bình thường.

Đợt 4: **tua, túi đồ, kết thúc thắng**. Test thêm trong `ReplayViewerTest` (tua tới các mốc 0, 1, 37, giữa, cuối và tầng sau: dừng đúng sau từng ấy lệnh với trạng thái bằng run gốc, chạy tiếp thì ra đúng cả trace; cửa sổ giữ playback; cửa sổ túi đồ và mô tả mọi món không rút RNG; mang bùa lên mặt đất và nhặt bùa kết thúc là thắng). Người xem bấm tua ngẫu nhiên cũng được thêm vào các test xem cũ.

| Phần | Code | Ghi chú |
|---|---|---|
| Tua | `ReplayPlayback.seekTo/seekNextFloor`, `ReplayViewer.seek`, `WndReplaySeek` (nút "Tua") | Tua tới: chạy x16, bỏ 0,3 giây chờ giữa hai lệnh, dừng khi nhân vật chờ lệnh sau đúng từng ấy lệnh (hoặc khi đã sang tầng khác). Tua lui: bắt đầu lại run từ đầu trong cùng phiên xem rồi tua tới, nên kết quả y như xem liền một mạch (game không lưu được trạng thái giữa chừng một cách an toàn). Lựa chọn: Đầu, -50, -10, +10, +50, Tầng sau, Cuối. Bấm Dừng trong lúc tua thì dừng ở đó. Trang giới thiệu khu vực trong InterlevelScene bị bỏ qua khi đang xem (hook 1 dòng), để tua qua tầng 6/11/16/21 không phải bấm. Tầng mới hiện mờ dần 1 giây kể cả khi đang dừng (chỉ tween/hoạt ảnh chạy, game vẫn chờ playback). |
| Túi đồ | `WndReplayBag` (nút "Túi") | Chỉ đọc: đồ đang mặc, rồi ba lô và đồ trong các túi, vàng, tầng; chạm một món hiện mô tả. Không dùng `WndBag` của game (nút dùng đồ, đặt quickslot, và nhớ tab túi mà cửa sổ gameplay mở theo). |
| Cửa sổ của trình xem | `ReplayViewer.show` | Khi cửa sổ (tua, túi đồ, mô tả) đang mở, playback đứng yên, vì lệnh trả lời cửa sổ trên cùng sẽ rơi vào nó. RNG gameplay được lưu lúc mở và đặt lại lúc đóng hết, nên đọc mô tả không thể đổi run. Cửa sổ nhận mọi chạm trừ thanh điều khiển; Esc đóng cửa sổ trên cùng trước rồi mới thoát. |
| Kết thúc thắng | `ReplayPlayback.ending`, `Game.cancelSceneSwitch` (SPD-classes, 1 chỗ `// spd-ranked: A5`) | Mang bùa lên mặt đất (`SurfaceScene`) hay nhặt/dùng bùa (`AmuletScene`) là kết thúc run: trình xem ở lại tầng đó và báo thắng, không chạy scene kết thúc (scene đó ghi chiến thắng vào hồ sơ). Lựa chọn ở AmuletScene không được ghi lại, nên nếu còn lệnh sau khi nhặt bùa thì báo số lệnh không xem được. Server (A3) giữ nguyên như cũ; Ranked kết thúc ở boss tầng 5/10/15 nên không tới bùa. |

Đã chạy thử bản desktop thật (Xvfb): tua tầng sau, túi đồ và mô tả, Esc đóng từng cửa sổ, chạy tiếp; tua lui về đầu rồi tua tới lệnh 60 cho đúng màn hình như lần đầu tới lệnh 60 (vị trí, máu, kinh nghiệm, log); tua tới cuối; Thoát về màn hình chính.

Giới hạn: replay Solo chỉ khớp nếu hồ sơ và cài đặt giống lúc ghi (game hiện chỉ tự lưu replay Ranked). Tua lui một replay dài phải chạy lại từ đầu (x16). Chưa xem được cửa sổ thông tin nhân vật/nhật ký trong lúc xem.

Còn để sau: ghost của đối thủ trong Ranked, telemetry cho run Solo (chưa có kết nối server khi chơi Solo), công cụ xuất bảng `telemetry` cho việc train.

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

Còn để sau: chặn theo khung cho đúng cả các gói có kích thước ảnh khác bản gốc, gửi mã băm skin cho server, tải gói từ trong game. Bản dịch tiếng Việt cho tab Skins và dòng Ghost đã có ở `messages/ranked/ranked_vi.properties`.
