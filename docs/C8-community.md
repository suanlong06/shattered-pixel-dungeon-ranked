# C8 — Cộng đồng: Weekly League, hồ sơ người chơi, chat sảnh

Phần server và API client cho ba mảng đầu của C8. Code: `ranked-server/.../server/Community.java`,
`CommunityStore.java`; client: `RankedClient` (`leagueStart`, `leagueSubmit`, `leagueBoard`, `profile`,
`chat`, `chatHistory`, `chatReport`). Test: `CommunityTest`, `ClientInteropTest.leagueProfileAndChat`,
`ReplayVerifierTest.leagueRunsReplayLikeADuelSide`.

Chưa làm: màn hình trong game (cửa sổ League, hồ sơ, khung chat) và chế độ chơi League phía client
(chạy seed tuần với bộ luật Ranked sạch, ghi log, nộp khi chết hoặc hạ boss cuối). Run Ghost, Ranked
Seasons (đã có rating theo mùa ở A1), Community Challenges và xác minh thành tích để lượt sau.

## Weekly League

- Tuần bắt đầu thứ Hai 00:00 UTC (`Community.week(now)`).
- Mỗi tuần một seed lấy từ seed pool A4 (cùng pool với Duel), đánh dấu `used_by = league-W<tuần>`,
  nên seed đó không bao giờ ra trong trận Duel. Seed được lấy lần đầu có người hỏi.
- Mỗi người **một lượt mỗi tuần**. `LEAGUE_START` tiêu lượt đó và trả seed; hỏi lại khi chưa nộp (mất
  kết nối) vẫn là cùng lượt, cùng seed. Không thể bắt đầu khi đang trong trận Duel.
- Chơi đơn đến boss cuối (`finalBossDepth` của luật Duel, mặc định 15). Class tự chọn, bảng có ghi class.
- Nộp: `LEAGUE_LOG {week, from, cls, cmds}` (chia nhiều frame như `LOG` của Duel) rồi
  `LEAGUE_SUBMIT {week, depth, turns, bosses, dead, cleared}`. Hạn nộp: hết tuần + 1 giờ.
- Server replay log trên seed tuần (A3, cùng worker với Duel). Chỉ run `ACCEPTED` lên bảng.
  `UNVERIFIABLE` (thứ replay chưa theo được) không lên bảng, trừ khi bật `-Dspdr.acceptUnverifiable=true`.
  Server khởi động lại giữa lúc xác minh thì xác minh lại từ log đã lưu.
- Xếp hạng: hạ boss cuối trước, rồi tầng sâu hơn, nhiều boss hơn, **ít lượt (turn) hơn**, nộp sớm hơn.
  Dùng turn chứ không dùng giờ thực vì turn được replay xác minh.

## Hồ sơ người chơi

`PROFILE {playerId}` hoặc `{name}`, bỏ trống là của mình. Trả: tên, ngày tạo, rating từng mùa và nền
tảng (rating, RD, số trận), thắng/thua/hòa mùa này, 10 trận gần nhất (đối thủ, kết quả, lý do), 10 tuần
League gần nhất (hạng / số người). Không bao giờ trả device key (đó là thông tin đăng nhập).

## Chat sảnh

- Một phòng chung. `CHAT {text}` gửi cho mọi người đã HELLO, kể cả người gửi.
- **Người đang trong trận Duel (chuẩn bị, đếm ngược, đang chạy) không gửi và không nhận chat**, để chat
  không thành kênh mách nước giữa hai đối thủ hay từ người xem.
- 1 đến 200 ký tự; ký tự điều khiển bị bỏ, khoảng trắng gộp lại. Tối đa 1 tin / 1,5 giây và 10 tin / phút.
- `CHAT_HISTORY` trả 50 tin gần nhất (chỉ trong bộ nhớ, không lưu ổ đĩa).
- `CHAT_REPORT {playerId, at}` lưu báo cáo kèm nội dung lấy từ bản sao của server (người báo cáo không
  thể bịa lời). Chủ server khóa chat bằng bảng `chat_mute` (`CommunityStore.mute`).
- Chặn người khác là việc của client.

## Dữ liệu

Bảng `league_week`, `league_entry`, `chat_report`, `chat_mute` tạo bằng `IF NOT EXISTS` trên cùng file
SQLite, không đổi `user_version` của Storage, để schema lõi Ranked đổi độc lập.

## Cần chủ repo xác nhận (đang dùng mặc định)

1. Một lượt mỗi tuần hay lấy lượt tốt nhất trong nhiều lượt.
2. Xếp theo turn (đã xác minh) hay theo thời gian thực.
3. Chat có mở cho người đang chơi League không (hiện: mở, vì seed tuần là chung cho mọi người).
