package com.shatteredpixel.shatteredpixeldungeon.ranked.net;

import com.shatteredpixel.shatteredpixeldungeon.messages.Messages;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import com.shatteredpixel.shatteredpixeldungeon.scenes.PixelScene;
import com.shatteredpixel.shatteredpixeldungeon.ui.RedButton;
import com.shatteredpixel.shatteredpixeldungeon.ui.RenderedTextBlock;
import com.shatteredpixel.shatteredpixeldungeon.windows.WndMessage;
import com.shatteredpixel.shatteredpixeldungeon.windows.WndOptions;
import com.watabou.noosa.Camera;
import com.watabou.noosa.Group;

/**
 * spd-ranked (A1 client): the opponent line in a ranked run (floor, bosses, dead, connection,
 * DECISIONS 7.3), a Resign button (asks first: resigning is a loss and a strike) and the
 * result when it comes. Also drives {@link RankedSession#update()}
 * while the game scene is up, so reports go out as the run moves.
 */
public class RankedHud extends Group {

	private static final float TOP = 40;

	private final RenderedTextBlock line;
	private final RedButton resign;
	private String shown;
	private boolean resultShown;

	public RankedHud(){
		camera = PixelScene.uiCamera;
		line = PixelScene.renderTextBlock( 6 );
		add( line );

		resign = new RedButton( Messages.get( RankedSession.class, "resign" ), 6 ){
			@Override
			protected void onClick(){
				GameScene.show( new WndOptions( Messages.get( RankedSession.class, "resign" ),
						Messages.get( RankedSession.class, "resign_confirm" ),
						Messages.get( RankedSession.class, "resign_yes" ),
						Messages.get( RankedSession.class, "resign_no" ) ){
					@Override
					protected void onSelect( int index ){
						RankedSession s = RankedSession.current();
						if (index == 0 && s != null && s.inRun()) s.resign();
					}
				} );
			}
		};
		resign.setSize( 36, 14 );
		add( resign );
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
		boolean canResign = s.inRun() && s.state() == RankedClient.State.STARTED;
		resign.visible = resign.active = canResign;
		if (canResign){
			Camera c = camera();
			resign.setPos( c == null ? 2 : (c.width - resign.width()) / 2f, line.bottom() + 2 );
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
