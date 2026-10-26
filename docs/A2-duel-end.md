# A2: Luật kết thúc trận Duel

Code: `core/src/main/java/.../ranked/duel/MatchEnd.java`, test: `core/src/test/java/.../ranked/duel/MatchEndTest.java`. Theo mặc định L19 trong `DECISIONS.md`.

Trận là cuộc đua song song từ tầng 1 tới boss cuối (`finalBossDepth`, mặc định 15, theo D1). Server gọi `MatchEnd.judge(a, b, now, rules)` mỗi khi nhận báo cáo. Mọi mốc thời gian là thời gian trận do server đo (ms).

Thứ tự xét, điều đầu tiên khớp sẽ quyết định:

1. **Bỏ cuộc**: người bỏ cuộc thua. Cả hai bỏ thì hòa.
2. **Mất kết nối** quá `disconnectLimitMs` (5 phút; đúng bằng 5 phút vẫn được RESUME): người đó thua. Cả hai thì hòa.
3. **Hạ boss cuối**: người hạ trước thắng, kể cả khi đối thủ chết sau đó. Hạ cùng lúc thì ít lượt hơn thắng, bằng nữa thì hòa.
4. **Chết là thua**: một người chết, người kia còn sống thì người sống thắng ngay.
5. **Cả hai chết**: xuống sâu hơn thắng; bằng tầng thì chết muộn hơn thắng; chết cùng lúc thì chơi nhiều lượt hơn thắng; còn lại hòa.
6. **Hết giờ** (`timeLimitMs`) khi cả hai còn sống: sâu hơn thắng; rồi hạ nhiều boss hơn; rồi ít lượt hơn (nhanh hơn); còn lại hòa.
7. Không khớp điều nào: trận tiếp tục (`ONGOING`).

Ghi chú:
- Giới hạn tổng 90 phút là **tạm**, chờ số đo bot S5 rồi đổi trong `Rules` (dữ liệu, không sửa logic).
- Kết quả đối xứng: đổi chỗ A và B thì kết quả đổi theo; test kiểm điều này ở mọi ca.
- Logic thuần, không gọi `Random` và không đọc trạng thái game, nên không ảnh hưởng digest.
- Phần A1 lo việc thu thập báo cáo (tầng, lượt, chết, hạ boss) và chống báo cáo giả (A3).
