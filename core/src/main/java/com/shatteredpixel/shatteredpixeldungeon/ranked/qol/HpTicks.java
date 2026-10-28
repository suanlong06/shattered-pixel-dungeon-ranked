package com.shatteredpixel.shatteredpixeldungeon.ranked.qol;

import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.ranked.Feature;
import com.shatteredpixel.shatteredpixeldungeon.ranked.Features;
import com.shatteredpixel.shatteredpixeldungeon.ui.HealthBar;
import com.watabou.noosa.Camera;
import com.watabou.noosa.ColorBlock;
import com.watabou.noosa.Gizmo;
import com.watabou.noosa.Group;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * spd-ranked (C6): dark marks on an enemy's HP bar, one every {@link #step} HP, so the player can
 * count how much HP is left. The step depends only on the enemy's max HP, so the same enemy always
 * has the same marks. Only reads HP and HT, like the bar itself; purely visual.
 */
public class HpTicks extends Group {

	private static final int COLOR = 0x99000000;
	/** marks closer than this many screen pixels are not drawn */
	private static final float MIN_GAP = 3;

	/** HP between two marks: 5 up to 30 max HP, then 10, 25, 50, 100 */
	public static int step( int maxHP ){
		if (maxHP <= 30)  return 5;
		if (maxHP <= 100) return 10;
		if (maxHP <= 250) return 25;
		if (maxHP <= 500) return 50;
		return 100;
	}

	/** number of marks inside a bar whose full width is {@code scale} HP (marks at step, 2*step... below scale) */
	public static int count( int maxHP, float scale ){
		if (scale <= 0) return 0;
		int s = step( maxHP );
		return (int)Math.ceil( scale / s ) - 1;
	}

	private int maxHP;
	private float scale;

	/** hook in the HP bars of enemies (CharHealthIndicator, TargetHealthIndicator, WndInfoMob) after level(c) */
	public static void update( HealthBar bar, Char c ){
		WeakReference<HpTicks> ref = BARS.get( bar );
		HpTicks t = ref == null ? null : ref.get();
		if (c == null || !Features.enabled( Feature.HP_TICKS )){
			if (t != null) t.visible = false;
			return;
		}
		if (t == null){
			t = (HpTicks) bar.add( new HpTicks() );
			BARS.put( bar, new WeakReference<>( t ) );
		}
		t.visible = true;
		t.maxHP = c.HT;
		//same scale as HealthBar.level(Char)
		t.scale = Math.max( c.HP + c.shielding(), c.HT );
	}

	private static final Map<HealthBar, WeakReference<HpTicks>> BARS = new WeakHashMap<>();

	@Override
	public void update(){
		super.update();
		for (Gizmo g : members) if (g != null) g.visible = false;
		if (!(parent instanceof HealthBar)) return;
		HealthBar bar = (HealthBar) parent;

		Camera cam = bar.camera();
		float zoom = cam == null ? 1 : cam.zoom;
		int n = count( maxHP, scale );
		if (n <= 0 || bar.width() * zoom / (n + 1) < MIN_GAP) return;

		float px = 1f / zoom;
		int s = step( maxHP );
		for (int i = 0; i < n; i++){
			ColorBlock b;
			if (i < members.size()){
				b = (ColorBlock) members.get( i );
			} else {
				b = new ColorBlock( 1, 1, COLOR );
				add( b );
			}
			b.size( px, bar.height() );
			b.x = bar.left() + bar.width() * (s * (i + 1)) / scale;
			b.y = bar.top();
			b.visible = true;
		}
	}
}
