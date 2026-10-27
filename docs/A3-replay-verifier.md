# A3: xác minh kết quả bằng phát lại (đợt 1)

Server không ghi rating từ lời báo của client nữa. Mỗi client gửi log lệnh của mình; khi `MatchEnd.judge` ra kết quả, server phát lại log của cả hai bên trên nền tất định T0.8 (headless, không GL) rồi so với báo cáo cuối của từng bên. Chỉ khi khớp thì mới tính rating.

Chạy test: `./gradlew :ranked-server:test` (26 ca, khoảng 10 giây) và `./gradlew :core:test` (lint và golden digest vẫn xanh).

## Giao thức (phiên bản 2)
- Frame mới `LOG`, `{from, cmds, cls}`: `cmds` là các lệnh T0.8 (`CELL c`, `REST f`, `SEARCH`, `ITEM i action`), mỗi dòng một lệnh; `from` là chỉ số của lệnh đầu; `cls` là class nhân vật, bắt buộc ở phần đầu tiên và không được đổi.
- Phần bắt đầu sau cuối log bị từ chối với `ERROR {message: "log gap", expected}`. Lệnh đã nhận rồi thì bỏ qua, nên client gửi lại thoải mái sau `RESUME`; `RESUMED` có thêm `logCount`.
- **Quy tắc cho client**: trước mỗi `PROGRESS`, `DIED`, `CLEARED` phải gửi `LOG` đưa log tới đúng thời điểm đó. Server phát lại tới hết log và đòi khớp đúng với báo cáo cuối.
- Định nghĩa các số client phải báo (để khớp phát lại): `depth` = `Statistics.deepestFloor`; `turns` = `(long) Statistics.duration`; `bosses` = số boss đã hạ trong các tầng 5/10/15; `CLEARED` khi boss tầng `finalBossDepth` chết.
- Ván phải bắt đầu như `ReplayCheck.start`: chế độ Ranked, seed từ `START`, không daily, không custom seed (client dùng slot riêng; slot chỉ là tên file, không ảnh hưởng gameplay).
- Log tối đa 500.000 lệnh mỗi bên (`Config.maxLogCommands`).

## Phía client (đã nối vào client A1)
- `RankedSession` bật `CommandLog` khi nhận `START`, gửi ngay một `LOG` rỗng có class, và trước mỗi báo cáo thì gửi các lệnh mới. `NetFrame.PROTOCOL` lên 2.
- Client chỉ báo cáo khi hero đang chờ lệnh (hoặc đã chết), đúng chỗ phát lại dừng, để `turns` và tầng khớp.
- `RankedClient` giữ cả log của trận; sau `RESUMED` gửi lại từ `logCount` rồi mới gửi báo cáo đang chờ.
- Đếm boss ở `ReplayCheck` dùng cùng luật với `ProgressTracker` của client.

## Luồng trên server
`RUNNING` → (judge ra kết quả) → `VERIFYING` → `FINISHED` hoặc `VOIDED`. Trong lúc `VERIFYING` không nhận báo cáo mới, và client chưa nhận `RESULT`.

| Kết quả judge | Xử lý |
|---|---|
| `FORFEIT`, `DISCONNECT` | server tự thấy, chấp nhận không cần phát lại |
| mọi lý do khác | phát lại cả hai bên |

Mỗi bên được so: số lệnh dùng hết, chết hay sống, đã clear chưa, tầng sâu nhất, số boss, số lượt. Kết quả:

| Kết quả | Khi nào | Mặc định |
|---|---|---|
| `ACCEPT` | khớp cả hai bên | ghi rating như A1 |
| `REJECT` | lệch mà phát lại không gặp giới hạn nào; class lạ; dòng lệnh không đọc được | void `REJECTED`, không rating, không strike |
| `UNVERIFIABLE` | lệch và phát lại gặp giới hạn T0.8 (bên dưới) | void `UNVERIFIABLE`; bật `-Dspdr.acceptUnverifiable=true` để tính kết quả báo cáo |

Log và kết quả phát lại của từng bên được lưu ở bảng `replay` (schema 2) để xem lại khi tranh chấp.

`ReplayVerifier` chạy phát lại trên một luồng riêng (game là trạng thái static nên mỗi lần một ván); `Hub` không bị khóa trong lúc chờ. `ServerMain` bật nó mặc định; `-Dspdr.verify=none` quay về chấp nhận mọi kết quả như A1. Asset của game nằm trên classpath của server nên chạy được từ thư mục bất kỳ.

## Giới hạn (theo T0.8) và cách xử lý an toàn
Những gì T0.8 chưa phát lại được thì ghi vào `Facts.unsupported` thay vì đoán:
1. **Cửa sổ còn mở khi lệnh tiếp tục** (chọn talent, nâng cấp, shop, `WndOptions`/`WndBag`): lựa chọn trong cửa sổ chưa là lệnh, nên phát lại có thể khác ván thật.
2. **Chuyển tầng không phải cầu thang** (rơi hố, hồi sinh, Return, Teleport): runner dừng `unsupported scene switch`.
3. **Lệnh không áp dụng được** (ví dụ chỉ số item không còn) hoặc runner dừng vì lý do khác ngoài hết lệnh và chết.

Nếu phát lại vẫn khớp báo cáo thì vẫn `ACCEPT` dù có cửa sổ mở (ví dụ đọc biển báo). Chỉ khi lệch **và** có giới hạn thì mới `UNVERIFIABLE`. Mặc định void nên rating không bao giờ được ghi từ một ván chưa kiểm chứng. Hệ quả: tới khi T0.8 có lệnh `CHOICE` và đủ kiểu chuyển tầng, nhiều trận thật sẽ bị void. Đây là chủ ý; có cờ `acceptUnverifiable` nếu muốn chơi thử.

Các giới hạn khác:
- Thời gian (ai clear trước, ai chết sau) là giờ server lúc nhận báo cáo; phát lại không kiểm chứng được tốc độ chơi thật, chỉ kiểm chứng *điều gì* đã xảy ra.
- Chưa có test phủ boss (bot T0.8 chưa qua được boss); đếm boss dựa vào `Statistics.bossScores` tăng ít nhất 500 khi boss chết.
- Chưa chạy trên ART và chưa triển khai lên A53 (A7, cần hỏi user).

## Test (`ReplayVerifierTest`)
Một người chơi giả chỉ đọc trạng thái (khám phá bằng chạm ô, đánh, nghỉ, xuống cầu thang) tạo log thật tới tầng 2. Kiểm: log thật được `ACCEPT`; phát lại nhiều lần cho cùng trace; báo sâu hơn, ít lượt hơn, log cắt ngắn, boss chưa đánh, lệnh rác đều `REJECT`; lệnh không áp dụng được là `UNVERIFIABLE`; bỏ cuộc không cần phát lại; một trận đầy đủ qua `Hub` (log gửi hai phần chồng nhau, hết giờ) chỉ có rating khi báo cáo đúng, báo sai thì void `REJECTED`. `ClientInteropTest`: client thật gửi log, mất mạng, gửi tiếp khi offline, RESUME; server nhận đủ log không trùng không thiếu.
