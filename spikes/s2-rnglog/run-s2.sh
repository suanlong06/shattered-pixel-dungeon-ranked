# usage: s2.sh seed class turns depth seedUnseeded out [jvmargs...]
S=${S:-$(cd "$(dirname "$0")" && pwd)}
cd /c/Workspace/20_Projects/spd-ranked/.claude/worktrees/confident-ellis-3fe569/core/src/main/assets
a=("$@"); java "${a[@]:6}" -cp "$(tr -d '\r\n' < $S/cp1.txt)" com.shatteredpixel.shatteredpixeldungeon.ranked.test.S2Main "${a[@]:0:6}"
