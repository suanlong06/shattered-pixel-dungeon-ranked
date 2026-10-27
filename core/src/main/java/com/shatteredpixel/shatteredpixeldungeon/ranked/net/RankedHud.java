package com.shatteredpixel.shatteredpixeldungeon.ranked.net;

import com.shatteredpixel.shatteredpixeldungeon.messages.Messages;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import com.shatteredpixel.shatteredpixeldungeon.scenes.PixelScene;
import com.shatteredpixel.shatteredpixeldungeon.ui.RenderedTextBlock;
import com.shatteredpixel.shatteredpixeldungeon.windows.WndMessage;
import com.watabou.noosa.Camera;
import com.watabou.noosa.Group;

/**
 * spd-ranked (A1 client): the opponent line in a ranked run (floor, bosses, dead, connection,
 * DECISIONS 7.3) and the result when it comes. Also drives {@link RankedSession#update()}
 * while the game scene is up, so reports go out as the run moves.
 */
public class RankedHud extends Group {

	private static final float TOP = 40;

	private final RenderedTextBlock line;
	private String shown;
	private boolean resultShown;

	public RankedHud(){
		camera = PixelScene.uiCamera;
		line = PixelScene.renderTextBlock( 6 );
		add( line );
	}

	@Override
	public void update(){
		super.update();
		RankedSession s = RankedSession.current();
		visible = s != null;
		if (s == null) return;
		s.update();

		String t = describe( s );
		if (!t.equals( shown )){
			shown = t;
			line.text( t );
			Camera c = camera();
			line.setPos( c == null ? 2 : (c.width - line.width()) / 2f, TOP );
			PixelScene.align( line );
		}
		if (s.result() != null && !resultShown){
			resultShown = true;
			GameScene.show( new WndMessage( s.result() ) );
		}
	}

	private static String describe( RankedSession s ){
		if (s.state() == RankedClient.State.RECONNECTING) return Messages.get( RankedSession.class, "reconnecting" );
		if (s.opponent() == null) return "";
		String status = s.opponentDead() ? " (" + Messages.get( RankedSession.class, "opp_dead" ) + ")"
				: !s.opponentConnected() ? " (" + Messages.get( RankedSession.class, "opp_offline" ) + ")" : "";
		return Messages.get( RankedSession.class, "opp_line", s.opponent(), s.opponentDepth(), s.opponentBosses() ) + status;
	}
}
