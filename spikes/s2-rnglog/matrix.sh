S=/c/Users/water/AppData/Local/Temp/claude/C--Workspace-20-Projects-spd-ranked--claude-worktrees-confident-ellis-3fe569/26452dff-8685-40e9-9010-1f6f61ecc145/scratchpad
cd $S; mkdir -p mx
for seed in ${SEEDS:-12345 987654321 42}; do for c in ${CLASSES:-WARRIOR MAGE ROGUE HUNTRESS DUELIST CLERIC}; do
 b=$S/mx/${seed}_${c}
 o=$(timeout 280 bash $S/s2.sh $seed $c ${TURNS:-500} ${DEPTH:-3} false $b.a.log 2>&1 | grep -E "turns=|Exception" | head -1)
 timeout 280 bash $S/s2.sh $seed $c ${TURNS:-500} ${DEPTH:-3} false $b.f.log -Dspdr.fps=60 >/dev/null 2>&1
 timeout 280 bash $S/s2.sh $seed $c ${TURNS:-500} ${DEPTH:-3} false $b.h.log -XX:+UnlockExperimentalVMOptions -XX:hashCode=2 >/dev/null 2>&1
 r1=$(cmp -s $b.a.log $b.f.log && cmp -s $b.a.log.state $b.f.log.state && echo OK || echo DIFF)
 r2=$(cmp -s $b.a.log $b.h.log && cmp -s $b.a.log.state $b.h.log.state && echo OK || echo DIFF)
 echo "$seed $c fps:$r1 hash:$r2 | $o"
done; done
