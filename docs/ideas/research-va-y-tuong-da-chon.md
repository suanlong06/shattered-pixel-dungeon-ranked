Nếu mục tiêu của bạn là **thiết kế một fork / game mới dựa trên Shattered Pixel Dungeon (SPD)**, thì cách tốt nhất không phải là hỏi “thêm tính năng gì cho hay?”, mà là tìm:

> **Người chơi đang bị đau ở đâu → tại sao hệ thống hiện tại tạo ra nỗi đau đó → nỗi đau nào là “đặc trưng roguelike” nên giữ → nỗi đau nào chỉ là friction/QoL → tính năng nào giải quyết nó mà không phá core gameplay.**

Tôi đã đối chiếu **GitHub issues của chính SPD, blog của Evan, Google Play/App Store, Reddit và các fork cộng đồng gần đây**, thay vì chỉ dựa vào một vài review.

---

# 1. Bức tranh lớn: SPD có một nghịch lý rất thú vị

SPD hiện vẫn được đánh giá rất cao: Google Play đang hiển thị khoảng **4.8/5 với 162.000+ review**, và game tiếp tục được phát triển; bản 3.3.8 được phát hành tháng 3/2026. [Google Play](https://play.google.com/store/apps/details?hl=ko\&id=com.shatteredpixel.shatteredpixeldungeon\&utm_source=chatgpt.com)

Nhưng chính những thứ làm game hấp dẫn cũng tạo ra pain point:

| Core strength | Đồng thời tạo ra pain |
|---|---|
| Permadeath | mất run dài → frustration |
| RNG | replayability → cảm giác “game quyết định thay mình” |
| Identification | khám phá → phải nhớ quá nhiều |
| Hunger | resource management → tedious |
| Inventory giới hạn | meaningful decisions → inventory Tetris |
| Alchemy | depth → micro-management |
| Nhiều item | build diversity → information overload |
| Difficulty | mastery → onboarding rất khó |
| Turn-based | chiến thuật → nhiều thao tác nhỏ |
| Random dungeon | replayability → backtracking |
| Ascension | endgame challenge → repetitive return trip |
| Secrets | discovery → wiki dependence |

Đây là điểm cực kỳ quan trọng:

**Không nên “fix” tất cả pain point.**

Một số pain chính là **gameplay**.

Một số khác chỉ là **friction**.

Ví dụ:

> “Tôi chết vì tính toán sai” = gameplay.

> “Tôi phải mở inventory 14 lần để kiểm tra cùng một potion” = friction.

Feature design tốt phải **giảm friction nhưng giữ nguyên decision-making**.

---

# 2. Pain point #1 — “Tôi không biết tại sao mình chết”

Đây có lẽ là pain point quan trọng nhất đối với người mới.

Có review mô tả game là rất khó tiếp cận; một review Google Play gần đây gọi difficulty và learning curve là rào cản lớn. [Google Play](https://play.google.com/store/apps/details?hl=ko\&id=com.shatteredpixel.shatteredpixeldungeon\&utm_source=chatgpt.com)

App Store cũng có review mô tả việc chết ở late game rồi phải bắt đầu lại nhiều lần là rất frustrating. [App Store](https://apps.apple.com/us/app/shattered-pixel-dungeon/id1563121109?see-all=reviews\&utm_source=chatgpt.com)

Một thread Reddit của người chơi chưa từng win cũng cho thấy vấn đề:

> người chơi có item/build tốt nhưng **không biết sử dụng tool đúng cách**. [Reddit](https://www.reddit.com/r/PixelDungeon/comments/1mwyqpl?utm_source=chatgpt.com)

Đây là vấn đề khác với “game quá khó”.

### Root cause

SPD có rất nhiều hidden knowledge:

- enemy attack patterns
- armor breakpoints
- weapon upgrade breakpoints
- hunger management
- potion identification
- scroll identification
- positioning
- line of sight
- door tactics
- surprise attacks
- wand interactions
- seeds
- alchemy
- subclass mechanics
- boss mechanics
- environmental interactions

Người chơi mới phải học bằng:

**chết → đọc wiki → chơi lại → chết → đọc Reddit → chơi lại.**

---

# 3. Feature cực mạnh: “Death Analysis”

Nếu tôi thiết kế fork của SPD, đây sẽ là một trong những feature đầu tiên.

Sau khi chết:

```text
RUN ENDED

Floor 17
Level 19
Cause of Death:
Gnoll Trickster

────────────────

WHAT HAPPENED

3 turns before death:
You were at 47 HP.

2 turns before death:
Trickster hit you for 18.

1 turn before death:
You moved into melee range.

Final turn:
Trickster + nearby enemy dealt 39 damage.

────────────────

MISSED OPTIONS

You had:

✓ Potion of Healing
✓ Scroll of Teleportation
✓ Ethereal Chains
✓ Invisibility

Possible escapes:
• Teleport
• Invisibility
• Chain → reposition
```

Không cần nói:

> “Bạn chơi ngu.”

Mà chỉ:

> **“Đây là những thông tin mà game biết, nhưng người chơi không biết.”**

### Vì sao mạnh?

Nó biến:

**Death → frustration**

thành:

**Death → learning**

Và quan trọng hơn:

**không làm game dễ hơn.**

---

# 4. Pain point #2 — Knowledge overload

SPD đã phải tự giải quyết vấn đề này bằng Journal.

Trong v2.5, Journal được overhaul thành:

- Notes
- custom notes
- floor-linked notes
- item-linked notes
- catalog
- bestiary
- gần 500 entries. [Shattered Pixel](https://shatteredpixel.com/blog/shattered-pixel-dungeon-v250.html?utm_source=chatgpt.com)

Điều này cho thấy **information overload thực sự là một vấn đề lớn**.

Nhưng Journal hiện tại chủ yếu là:

> “lưu kiến thức”

chứ chưa hoàn toàn là:

> **“giúp người chơi ra quyết định.”**

---

# 5. Feature: Contextual Knowledge

Thay vì bắt người chơi mở Journal:

### Khi gặp enemy lần đầu

```text
NEW ENEMY

Gnoll Trickster

Threat:
★★★☆☆

Known behavior:
• ranged attack
• retreats
• can punish pursuit

Tip:
Don't chase immediately.
```

Sau đó khi chiến đấu:

```text
Gnoll Trickster

HP 42/42

Known:
✓ Ranged
✓ Retreat
✓ Vulnerable when cornered
```

Nhưng **không hiện full wiki**.

Chỉ hiện information mà player **đã thực sự khám phá**.

Đây là khác biệt rất quan trọng.

---

# 6. Pain point #3 — Identification system tạo cognitive load

Có một GitHub issue rất đáng chú ý:

Người chơi đề nghị:

> Khi potion/scroll đã được identify, icon effect nên xuất hiện ngay trên item ở floor/shop.

Lý do được nêu thẳng là:

> phải examine scroll/potion nhiều lần rất tedious.

Issue này bị đóng “not planned”, nhưng nó là bằng chứng trực tiếp cho friction. [GitHub](https://github.com/00-Evan/shattered-pixel-dungeon/issues/1751?utm_source=chatgpt.com)

### Đây là pain point cực phù hợp để redesign.

Không cần bỏ identification.

Thay vào đó:

### “Known Item Recognition”

Nếu player đã biết:

```text
Scroll on ground

📜 Scroll of Identify
```

Nếu chưa biết:

```text
📜 Unknown Scroll
Rune: KRA
```

Ở shop:

```text
Potion

🧪 Potion of Healing
KNOWN
```

Thay vì:

```text
tap
→ inspect
→ close
→ remember
```

---

# 7. Pain point #4 — Inventory congestion

Đây là pain point **đã được chính developer thừa nhận**.

Evan từng giải thích rằng inventory limitation ban đầu được thiết kế để tạo strategic decisions.

Nhưng thực tế:

> người chơi chỉ vứt đồ xuống đất rồi quay lại lấy.

Kết luận của ông là inventory limitation trở thành **annoyance nhiều hơn gameplay**.

Sau đó SPD thay đổi bag system để giảm congestion. [Shattered Pixel](https://shatteredpixel.com/blog/whats-coming-in-shattered-pixel-dungeon-v064.html?utm_source=chatgpt.com)

Đây là một case study cực hay về game design:

> **Nếu player tìm ra cách bypass mechanic mà chỉ tạo thêm thao tác, mechanic có thể đang tạo friction chứ không tạo decision.**

---

# 8. Feature: Smart Inventory

Không phải:

> “Unlimited inventory.”

Mà:

### Inventory tự phân loại

```text
WEAPONS
ARMOR
WANDS
POTIONS
SCROLLS
FOOD
QUEST
ALCHEMY
OTHER
```

và:

```text
[Keep]
[Drop]
[Sell]
[Use]
[Alchemy]
```

Thêm:

### “Run Value”

Ví dụ:

```text
Potion of Healing
★★★★★
Critical survival item
```

```text
Scroll of Magic Mapping
★★★☆☆
Useful later
```

```text
+0 Leather Armor
★☆☆☆☆
Likely replaceable
```

Nhưng **không tự quyết định thay người chơi**.

---

# 9. Pain point #5 — Hunger

Đây là một pain point cực kỳ lâu đời.

Điều thú vị là chính Evan đã từng viết rằng:

> hunger là một trong những sticking points lớn nhất với người mới.

Ông từng thay đổi stair hunger để làm mechanic này dễ hiểu hơn. [Shattered Pixel](https://shatteredpixel.com/blog/shattered-pixel-dungeon-v024.html?utm_source=chatgpt.com)

SPD cũng từng buff food sau khi level generation khiến backtracking dài hơn và việc khám phá kỹ trở nên tốn thời gian hơn. [Shattered Pixel](https://shatteredpixel.com/blog/shattered-pixel-dungeon-v060.html?utm_source=chatgpt.com)

Và App Store hiện vẫn có review phàn nàn về starvation/RNG food availability. [App Store](https://apps.apple.com/us/app/shattered-pixel-dungeon/id1563121109?see-all=reviews\&utm_source=chatgpt.com)

---

# 10. Nhưng tôi **không** khuyên bỏ Hunger

Hunger tạo ra một trong những decision thú vị nhất:

> “Tôi có nên khám phá thêm không?”

Vấn đề là **information về resource state chưa đủ trực quan**.

### Feature:

## Food Forecast

```text
HUNGER

████████░░ 80%

Estimated:
~180 turns until Starving

Food:
2 × Ration
1 × Blandfruit

Expected safe range:
Floor 14 → 16
```

Không thay đổi mechanic.

Chỉ giúp player hiểu:

> **Tôi có đủ food không?**

---

# 11. Feature thú vị hơn: “Resource Anxiety Meter”

Không phải cheat.

Ví dụ:

```text
SURVIVAL STATE

Food        ███████░░
Healing     █████░░░░░
Escape      ████████░
Upgrade     ██████░░░░
```

Nó không nói:

> “Bạn nên làm X.”

Chỉ giúp player nhìn thấy **run economy**.

---

# 12. Pain point #6 — RNG cảm giác “unfair”

Đây là vấn đề rất nhạy.

Có người chơi cho rằng game quá khó/RNG-dependent; có người lại cho rằng game quá dễ sau khi hiểu system.

Một Reddit post năm 2026 thậm chí lập luận ngược lại rằng SPD “quá dễ” và RNG là vấn đề; trong khi nhiều review khác lại phàn nàn difficulty. [Reddit](https://www.reddit.com/r/ShatteredPD/comments/1upzro8/shattered_pixel_dungeon_isnt_a_strategic/?utm_source=chatgpt.com)

Điều này cho ta một insight:

> **“Difficulty” không phải một pain point đơn nhất.**

Nó phụ thuộc vào skill level.

---

# 13. Đừng giải quyết bằng “Easy Mode” đơn giản

Đã có feature request xin Easy Mode vì người chơi thấy game tedious và không có room for error. Issue này bị đóng “not planned”. [GitHub](https://github.com/00-Evan/shattered-pixel-dungeon/issues/1033?utm_source=chatgpt.com)

Có request khác muốn difficulty/leveling speed selectable vì người chơi thấy post-Goo quá khó. [GitHub](https://github.com/00-Evan/shattered-pixel-dungeon/issues/661?utm_source=chatgpt.com)

Nhưng nếu fork của bạn chỉ:

```text
Easy:
Enemy HP -30%
Player HP +30%
```

thì khá nhàm.

### Tôi sẽ làm:

## Adaptive Training Mode

Chỉ áp dụng **trong một số run training**, không ảnh hưởng leaderboard/normal mode.

Ví dụ:

```text
You died to:
DM-200

Would you like to retry this encounter?

[Practice]
[End Run]
```

Practice cho phép:

- restart boss
- test item
- xem combat log
- experiment

Sau đó quay về run bình thường.

Điều này đặc biệt mạnh để học boss.

---

# 14. Pain point #7 — Micro-management

Một người chơi lâu năm trong cộng đồng Pixel Dungeon từng mô tả họ thích vanilla hơn SPD vì SPD thêm rất nhiều:

- subclass upgrades
- alchemy
- micro-management
- mimic interactions

và những thứ đó đôi khi làm giảm “raw fun”. [Reddit](https://www.reddit.com/r/PixelDungeon/comments/v1naec?utm_source=chatgpt.com)

Đây là một criticism rất quan trọng.

SPD đã trở thành một game **deep**.

Nhưng:

> Depth ≠ complexity of interaction.

Một hệ thống có thể sâu nhưng ít thao tác.

---

# 15. Feature: “Intent Actions”

Ví dụ player chọn:

```text
Eat
```

Game hiểu:

```text
Eat food
→ close menu
→ consume
→ return to previous state
```

Không phải:

```text
open inventory
→ find food
→ click
→ confirm
→ close
```

Tương tự:

```text
Equip
Upgrade
Identify
Alchemy
```

Một click ít hơn nhưng **không giảm decision**.

---

# 16. Pain point #8 — Quickslot / controls

Đây là pain point vẫn còn tồn tại trong cộng đồng.

Một fork cộng đồng năm 2026 bổ sung:

- second hotbar
- 12 total slots
- enemy HP bars
- camera keybinds
- mouse tile indicator
- auto-assign talents
- improved zoom
- item note markers. [Reddit](https://www.reddit.com/r/ShatteredPD/comments/1t1wld9/spd_quality_of_life_shatter_the_streamer_now/?utm_source=chatgpt.com)

Điều đáng chú ý hơn:

Một fork khác mới đây biến SPD thành game **hoàn toàn playable bằng D-pad**, dành cho handheld không có pointer/analog stick, và nhận được hàng trăm upvotes. [Reddit](https://www.reddit.com/r/ShatteredPD/comments/1v4ia4l/i_made_shattered_playable_without_pointer_devices/?utm_source=chatgpt.com)

Điều đó cho thấy:

> **SPD không chỉ là mobile game nữa.**

Nó đang có ecosystem:

```text
Phone
Tablet
PC
Steam Deck
Retroid
Anbernic
Linux handheld
Controller
Keyboard + mouse
```

---

# 17. Feature: Universal Input Layer

Nếu bạn làm fork:

### Đừng design control theo platform.

Design:

```text
ACTION
  ↓
Input abstraction
  ↓
Touch / Mouse / Keyboard / Controller
```

Ví dụ:

```text
Select tile
Move
Interact
Use item
Wait
Inspect
Open inventory
```

Sau đó map:

| Action | Touch | Keyboard | Controller |
|---|---|---|---|
| Move | swipe/tap | WASD | D-pad |
| Inspect | hold | Tab | A |
| Wait | button | Space | Y |
| Inventory | icon | I | X |
| Quick item | hotbar | 1–6 | L/R |

Đây là infrastructure có giá trị lâu dài.

---

# 18. Pain point #9 — “Tôi biết game có nhiều content nhưng vẫn lặp”

Một review gần đây nói game rất hay nhưng **volume/content cảm nhận được có vẻ ít**. [Google Play](https://play.google.com/store/apps/details?hl=ko\&id=com.shatteredpixel.shatteredpixeldungeon\&utm_source=chatgpt.com)

Một App Store review khác yêu cầu:

- thêm playable characters
- unlockable characters
- nhiều content hơn. [App Store](https://apps.apple.com/us/app/shattered-pixel-dungeon/id1563121109?platform=iphone\&see-all=reviews\&utm_source=chatgpt.com)

Nhưng SPD đã có rất nhiều content.

Vấn đề không nhất thiết là:

> “thiếu item.”

Mà là:

> **thiếu những lý do khiến một run khác hoàn toàn run trước.**

---

# 19. Feature: Run Mutators

Thay vì thêm 100 item:

```text
Run Mutation
```

Ví dụ:

### Frozen Dungeon

- water freezes
- fire melts ice
- frost enemies mạnh hơn
- fire potion có thêm utility

### Living Dungeon

- walls shift
- rooms evolve
- plants spread

### Cursed Economy

- gold scarce
- shops dynamic
- item prices fluctuate

### Monster Migration

- enemy populations move giữa floors

Điều này tạo **content combinatorial**.

Một mechanic mới có thể tương tác với 20 mechanic cũ.

---

# 20. Pain point #10 — Build identity chưa đủ mạnh với người mới

Experienced player nhìn thấy:

```text
Ring
+ Wand
+ Armor
+ Seed
+ Artifact
```

và lập tức nghĩ:

> “Build này có thể đi theo hướng X.”

New player lại thấy:

> “Tôi có 8 món đồ.”

Đây là skill gap.

---

# 21. Feature: Build Archetype Discovery

Không phải class mới.

Game chỉ nhận diện **emergent build**.

Ví dụ:

```text
BUILD DISCOVERED

⚡ WAND CONTROL

Your current equipment suggests:

• ranged combat
• crowd control
• wand recharge

Synergies discovered:
3

Possible directions:
[Control]
[Damage]
[Mobility]
```

Không tự chọn upgrade.

Chỉ nói:

> “Bạn đang xây cái gì?”

---

# 22. Pain point #11 — Người chơi phụ thuộc Wiki

Đây là consequence tự nhiên của game depth.

Issue easy mode có người chơi nói thẳng họ mệt vì phải đọc walkthrough trên web để hiểu game. [GitHub](https://github.com/00-Evan/shattered-pixel-dungeon/issues/1033?utm_source=chatgpt.com)

Trong khi Journal đã được mở rộng lên gần 500 entries. [Shattered Pixel](https://shatteredpixel.com/blog/shattered-pixel-dungeon-v250.html?utm_source=chatgpt.com)

Điều đó gợi ý một hướng rất mạnh:

# “In-game Wiki that learns with you”

Không phải toàn bộ wiki ngay từ đầu.

Ví dụ:

```text
??? Potion

You have discovered:
✓ color
✓ taste
✓ effect after use

Still unknown:
• interaction
• alchemy recipes
```

Game trở thành:

> **knowledge acquisition system**

thay vì:

> “go Google this.”

---

# 23. Pain point #12 — Backtracking

Level generation càng phong phú → exploration càng dài.

Evan từng phải điều chỉnh food vì levelgen mới khiến backtracking và thorough exploration mất nhiều thời gian hơn. [Shattered Pixel](https://shatteredpixel.com/blog/shattered-pixel-dungeon-v060.html?utm_source=chatgpt.com)

Đây là pain point rất khác:

**Không phải combat.**

Mà là:

> “Tôi đã làm sạch floor rồi nhưng vẫn phải đi lại.”

---

# 24. Feature: Exploration Memory

Map có:

```text
✓ Cleared
✓ Looted
✓ Unexplored
? Suspicious
! Important
```

và:

### Auto-generated map annotations

```text
Floor 11

✓ Shop
✓ Alchemy pot
✓ Locked room
! Unopened chest
? Unknown room
```

Điều này **không thay thế exploration**.

Nó giảm mental bookkeeping.

---

# 25. Pain point #13 — Bosses: học bằng cách chết

Boss là nơi permadeath đặc biệt đau.

Feature:

# Boss Chronicle

Sau lần đầu gặp boss:

```text
BOSS DISCOVERED

Tengu

Known attacks:
✓ Shuriken
✓ Traps
✓ Teleport
✓ Phase transition

Attempts: 2
Best HP remaining: 21%
```

Sau khi chết:

```text
You learned:
+ Fire can clear traps
+ Position matters during phase 2
```

Đây là một **meta-learning system**, không phải meta-progression.

---

# 26. Pain point #14 — “Perfect run anxiety”

Đây là một pain point tôi cho rằng rất đáng nghiên cứu nếu bạn làm game mới.

Khi run đã kéo dài:

```text
Floor 19
Great build
Rare artifact
Good weapon
Lots of upgrades
```

người chơi bắt đầu:

> **sợ mất run.**

Điều đó có thể làm họ chơi quá an toàn.

Một review App Store mô tả đúng cảm giác:

> tới floor 15+ → bị dồn → phải restart. [App Store](https://apps.apple.com/us/app/shattered-pixel-dungeon/id1563121109?see-all=reviews\&utm_source=chatgpt.com)

---

# 27. Feature: Run Insurance — nhưng không phải revive

Không nên:

> “Save checkpoint.”

Vì nó phá permadeath.

Thay vào đó:

### Run Snapshot

Game tự lưu:

```text
Run #1834
Floor 19
Build:
+3 Long Sword
+2 Plate
Ring of...
```

Sau death:

```text
YOUR RUN ARCHIVE

Floor reached: 19
Best damage: 37
Best build: ...
```

Người chơi có cảm giác:

> **run không biến mất hoàn toàn.**

Đây có thể rất tốt cho retention mà không phá hardcore.

---

# 28. Pain point #15 — Challenge system

Đây là một case study thú vị.

Evan từng viết rằng challenge system được rất ít người sử dụng vì challenges chủ yếu là **remove gameplay**, khiến chúng kém thú vị; all-challenge cũng quá khó. Sau đó ông redesign để challenges tạo difficulty theo cách thú vị hơn. [Shattered Pixel](https://shatteredpixel.com/blog/whats-coming-in-shattered-pixel-dungeon-v064.html?utm_source=chatgpt.com)

Đây là nguyên tắc:

> **Harder ≠ remove resources.**

---

# 29. Feature: Positive/Negative Mutators

Thay vì:

```text
Food -50%
Healing -50%
```

làm:

```text
🔥 Volatile Dungeon
+ explosions chain
+ enemies also affected

🌑 Living Darkness
+ darkness expands
+ rare shadow loot

🧪 Alchemical World
+ potions interact differently
+ more alchemy
```

Difficulty đến từ:

> **thay đổi rules**

chứ không phải:

> **lấy tài nguyên của player đi.**

---

# 30. Một pain point cực lớn: “Miss” và combat variance

Một fork community list rõ:

> “better accuracy for everything — hopefully avoid fights where both miss 8 times in a row.” [Reddit](https://www.reddit.com/r/ShatteredPD/comments/1jtjqs1?utm_source=chatgpt.com)

Đây là một dạng **variance frustration**.

Cần phân biệt:

```text
Strategic uncertainty
```

với

```text
Repeated low-information randomness
```

Ví dụ:

> “Tôi có 70% chance hit.”

có strategic meaning.

Nhưng:

> miss → miss → miss → miss → miss

có thể tạo cảm giác game đang troll.

---

# 31. Feature: Combat Probability Preview

Không cần hiện số quá nhiều.

Khi inspect:

```text
CRAB

Your attack:
Hit ~72%
Expected damage: 13–21

Enemy attack:
Hit ~41%
Expected damage: 8–14
```

Hoặc chỉ:

```text
HIGH
MEDIUM
LOW
```

Tùy difficulty.

Đây là **information improvement**, không phải buff player.

---

# 32. Một insight rất quan trọng từ lịch sử SPD

Nhìn vào changelog của SPD có thể thấy Evan liên tục sửa những pain point rất cụ thể:

### 2014–2015

- Hunger clarity
- Quickslots
- Search/Examine
- Resting
- floor item visibility
- traps. [Shattered Pixel](https://shatteredpixel.com/blog/shattered-pixel-dungeon-v031.html?utm_source=chatgpt.com)

### 2018

- Inventory congestion
- challenge frustration. [Shattered Pixel](https://shatteredpixel.com/blog/whats-coming-in-shattered-pixel-dungeon-v064.html?utm_source=chatgpt.com)

### 2024

- Journal
- inventory management
- identification
- trinkets. [Shattered Pixel](https://shatteredpixel.com/blog/shattered-pixel-dungeon-v250.html?utm_source=chatgpt.com)

### 2025

- score clarity
- exploration feedback
- color accessibility. [Shattered Pixel](https://shatteredpixel.com/blog/shattered-pixel-dungeon-v310.html?utm_source=chatgpt.com)

Điều này cho ta một pattern:

> **SPD phát triển rất nhiều bằng cách giảm friction mà người chơi gặp sau khi game đã đủ sâu.**

Nếu bạn làm một fork, đây là hướng cực hợp lý.

---

# 33. Tôi sẽ chia pain point thành 4 tầng

## Tầng A — NÊN GIẢI QUYẾT

Đây là friction.

| Pain | Giải pháp |
|---|---|
| inventory tedious | Smart Inventory |
| unidentified items khó nhớ | Known Item Recognition |
| backtracking | Exploration Memory |
| UI thao tác nhiều | Intent Actions |
| controller | Universal Input |
| information overload | Contextual Knowledge |
| combat uncertainty | Probability Preview |
| không hiểu chết vì gì | Death Analysis |

**Đây là nhóm tôi ưu tiên cao nhất.**

---

# 34. Tầng B — NÊN GIẢI QUYẾT NHƯNG KHÔNG XÓA CORE

| Pain | Giải pháp |
|---|---|
| Hunger anxiety | Food Forecast |
| RNG frustration | Run statistics |
| Boss frustration | Boss Practice |
| Wiki dependence | Progressive Codex |
| Build confusion | Build Discovery |

Mục tiêu:

> **make player smarter, not character stronger.**

Đây là triết lý tôi rất khuyến nghị cho game của bạn.

---

# 35. Tầng C — CONTENT PAIN

| Pain | Feature |
|---|---|
| runs feel repetitive | Run Mutators |
| endgame repetitive | Endless/Deep Mode |
| muốn character mới | New Archetypes |
| dungeon thiếu variety | Environmental Variants |
| boss repetition | Boss Variants |
| ít reason replay | Daily/Seed Challenges |

---

# 36. Tầng D — Không nên “fix” quá mức

Đây là những thứ tôi sẽ **giữ**:

- Permadeath
- Resource scarcity
- Identification
- RNG
- Hunger
- positioning
- limited upgrades
- risk/reward
- irreversible decisions

Nếu bỏ chúng:

> bạn có thể tạo một game dễ chơi hơn nhưng **không còn là SPD-like nữa**.

---

# 37. Feature tôi thấy có tiềm năng nhất: “Run Intelligence”

Nếu mục tiêu của bạn là làm một game **SPD 2.0**, tôi sẽ gom rất nhiều ý tưởng ở trên thành một hệ thống duy nhất:

# RUN INTELLIGENCE

Trong mỗi run game âm thầm ghi nhận:

```text
COMBAT
  damage dealt
  damage received
  misses
  escapes
  positioning mistakes

RESOURCE
  food
  healing
  gold
  scrolls
  potions

EXPLORATION
  rooms
  secrets
  backtracking

BUILD
  equipment
  upgrades
  synergies

DEATH
  exact sequence
  available alternatives
```

Sau run:

```text
━━━━━━━━━━━━━━━━
RUN REPORT
━━━━━━━━━━━━━━━━

Floor reached       19
Enemies defeated    143
Damage dealt        2,831
Damage taken        1,944

Food efficiency     ★★★★☆
Combat efficiency   ★★★☆☆
Exploration         ★★★★★
Resource usage      ★★☆☆☆

YOUR BIGGEST RISK

You frequently entered
combat below 35% HP.

────────────────

NEW KNOWLEDGE

✓ Potion timing
✓ DM-200 positioning
✓ Food conservation

────────────────

BUILD

⚔ Melee
🛡 Defensive
🌿 Sustain

Strong synergy: 7
```

**Không có score “Bạn chơi tốt/xấu”.**

Chỉ:

> **“Đây là dữ liệu của run.”**

---

# 38. Và đây mới là thứ có thể tạo lợi thế cực lớn

Nếu bạn làm game mới, hãy cho player:

## Compare Runs

```text
RUN #127

Floor 18
Death: DM-300
Food: low
Healing: 2
```

so với:

```text
RUN #134

Floor 21
Death: Yog
Food: healthy
Healing: 5
```

Player tự phát hiện:

> “À, vấn đề của mình không phải combat. Mình dùng food quá sớm.”

Đây là cách biến roguelike thành **learning game**.

---

# 39. Một hệ thống còn mạnh hơn: “Personal Knowledge Graph”

Game có thể ghi:

```text
Player discovered:

Potion A → Healing
Scroll B → Upgrade
Enemy X → Fire vulnerability
Weapon Y → strong with armor
```

Sau 50 runs:

```text
YOUR KNOWLEDGE

Potions      91%
Scrolls      84%
Enemies      76%
Alchemy      63%
Bosses       100%
```

Không phải progression.

Không tăng damage.

Không unlock sức mạnh.

Chỉ:

> **player progression = knowledge progression.**

Đây là một trong những cách hay nhất để làm game khó nhưng ít frustrating hơn.

---

# 40. Tôi cũng rất thích một feature từ cộng đồng

Một fork 2026 có:

> **item note marker**

Khi item có custom note, item hiển thị icon để người chơi biết nó có note. [Reddit](https://www.reddit.com/r/ShatteredPD/comments/1t1wld9/spd_quality_of_life_shatter_the_streamer_now/?utm_source=chatgpt.com)

Đây tưởng là feature nhỏ nhưng cực kỳ đúng hướng.

Tôi sẽ nâng cấp nó thành:

## Personal Memory System

Player có thể note:

```text
+2 Ring
"Save for late game"
```

```text
Potion
"Confirmed healing"
```

```text
Wand
"Good vs DM-200"
```

và note tự xuất hiện ở:

- inventory
- floor
- shop
- journal
- death report.

---

# 41. Feature roadmap tôi đề xuất cho fork của bạn

Nếu đây là project **Shattered Pixel Dungeon-inspired**, tôi sẽ không bắt đầu bằng 20 class mới.

Tôi sẽ làm theo thứ tự:

### Phase 1 — Reduce friction

1. Smart Inventory
2. Known Item Recognition
3. Better Quickslot
4. Intent Actions
5. Universal Controller/Input
6. Exploration Memory

### Phase 2 — Turn death into learning

7. Death Analysis
8. Combat Log
9. Boss Chronicle
10. Run Archive
11. Probability Preview

### Phase 3 — Reduce Wiki dependence

12. Progressive Codex
13. Contextual Knowledge
14. Personal Notes
15. Build Discovery

### Phase 4 — Replayability

16. Run Mutators
17. Boss Variants
18. Environmental Variants
19. Daily Seeds
20. Endless Mode

### Phase 5 — New gameplay

21. New classes
22. New subclasses
23. New artifacts
24. New regions
25. New quests
26. New bosses

---

# 42. Nếu chỉ được chọn **10 feature**

Tôi sẽ chọn:

| # | Feature | Giải quyết |
|---|---|---|
| 1 | **Death Analysis** | “Tại sao tôi chết?” |
| 2 | **Progressive Codex** | Wiki dependence |
| 3 | **Smart Inventory** | Inventory pain |
| 4 | **Known Item Recognition** | Identification friction |
| 5 | **Exploration Memory** | Backtracking |
| 6 | **Combat Probability** | RNG frustration |
| 7 | **Boss Practice** | Learning boss |
| 8 | **Run Intelligence** | Self-improvement |
| 9 | **Run Mutators** | Repetition |
| 10 | **Universal Input** | Mobile/PC/handheld |

Điểm chung của 10 feature này:

> **Chúng không đơn giản làm player mạnh hơn.**

Chúng làm player:

**hiểu → quyết định → học → chơi lại tốt hơn.**

---

# 43. Insight quan trọng nhất cho project của bạn

Sau khi nhìn cả lịch sử development của SPD + GitHub issues + Reddit + reviews, tôi cho rằng **khoảng trống thú vị nhất không phải là “SPD nhưng nhiều content hơn”.**

Thị trường đã có rất nhiều Pixel Dungeon forks thêm:

> class → weapon → enemy → artifact → dungeon.

Khoảng trống thú vị hơn là:

# **SPD + Learning System**

Một roguelike mà:

> **mỗi lần chết thực sự dạy bạn một thứ.**

Core loop sẽ thành:

```text
PLAY
 ↓
MAKE DECISIONS
 ↓
DIE
 ↓
UNDERSTAND WHY
 ↓
UPDATE PERSONAL KNOWLEDGE
 ↓
TRY AGAIN
 ↓
MAKE BETTER DECISIONS
```

thay vì:

```text
PLAY
 ↓
DIE
 ↓
GOOGLE
 ↓
READ WIKI
 ↓
TRY AGAIN
```

Đó là sự khác biệt rất lớn.

---

## Một lưu ý quan trọng về dữ liệu

Đây không phải khảo sát định lượng toàn bộ cộng đồng SPD. Reddit/GitHub issues có **selection bias**: người gặp vấn đề thường có xu hướng viết issue/post hơn người hài lòng. Vì vậy tôi dùng chúng để tìm **pain patterns**, sau đó đối chiếu với những thay đổi mà chính developer đã thực hiện qua nhiều năm. Những pain như inventory, hunger clarity, challenges, journal, UI và identification có bằng chứng đặc biệt mạnh vì chúng xuất hiện cả trong community feedback **lẫn lịch sử thiết kế chính thức của game**. [Shattered Pixel](https://shatteredpixel.com/blog/shattered-pixel-dungeon-v250.html?utm_source=chatgpt.com)

Nếu mục tiêu của bạn là **dùng research này để xây fork SPD bằng DeepSeek Harness**, bước tiếp theo rất đáng làm là tôi có thể đi sâu thêm một tầng: **crawl/đào hàng trăm GitHub issues + Reddit posts → clustering thành 20–30 “Jobs to Be Done” → severity × frequency × implementation cost → cuối cùng tạo Product Requirements Document (PRD) cho từng feature**, đặc biệt có thể ưu tiên những feature **dễ code trực tiếp vào source của SPD**.

---

# ý tưởng đã chọn:
hầu hết là những ý tưởng nếu là người làm thì sẽ tốn rất nhiều thời gian và công sức kiểm thử, nên AI hỗ trợ mạnh:
Feature cực mạnh: “Death Analysis”
Feature: Contextual Knowledge
“Known Item Recognition”: nhận diện và hiển thị vật phẩm đã biết như scoll hoặc Potion để không cần phải kiểm tra kho đồ hay kiểm tra vật phẩm thủ công liên tục. (có thể sau khi nhận diện thì biến thành hình dạng khác cố định hoặc hiển thị icon/chữ để xác định nhanh)
Feature: Smart Inventory - Inventory tự phân loại, Thêm: “Run Value” (item ★★★☆☆).
Feature: Food Forecast. HUNGER ████████░░ 80%
Estimated: ~180 turns (until Starving)
Adaptive Training Mode: để người chơi Practice boss, test item, xem lại combat tạo từ log, experiment. sau đó quay về run bình thường.

và cần tìm hiểu thêm về 
Quickslot / controls
Đây là pain point vẫn còn tồn tại trong cộng đồng.
Một fork cộng đồng năm 2026 bổ sung:
- second hotbar
- 12 total slots
- enemy HP bars
- camera keybinds
- mouse tile indicator
- auto-assign talents
- improved zoom
- item note markers.

Một fork 2026 có: item note marker - Khi item có custom note, item hiển thị icon để người chơi biết nó có note. và note tự xuất hiện ở inventory, floor, shop.

các chế độ chơi có thể thêm vào ngoài solo 1/1.
Feature: Build Archetype Discovery.
“In-game Wiki that learns with you” - knowledge acquisition system.
Feature: Exploration Memory và Auto-generated map annotations - được tạo ra khi người dùng phát hiện ra chứ không hiển thị ngay từ đầu.
Feature: Combat Probability Preview (Hit ~72% hoặc là high, damage: 13–21)

nghiên cứu thêm các chế độ chơi mới qua: Shattered Pixel Dungeon đã có các hạng mục speedrun trên Speedrun.com, gồm các loại run khác nhau, phiên bản và phân loại seed.
Dungeon League — Giải đấu dungeon theo tuần
Run Ghost để xem lại.
Ranked Seasons
hồ sơ cá nhân, ghi lại chỉ số thắng thua và các chỉ số đặc biệt để người chơi so sánh lẫn nhau.
phòng chat chung trực quan dễ thao tác.
Community Challenges
WEEKLY DUNGEON LEAGUE - phù hợp với người có tg dài để đi hết 1 lượt chạy. 
Backend và xác minh thành tích