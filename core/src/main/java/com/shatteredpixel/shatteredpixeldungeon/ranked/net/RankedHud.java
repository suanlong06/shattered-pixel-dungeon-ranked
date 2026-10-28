package com.shatteredpixel.shatteredpixeldungeon.ranked.net;

import com.shatteredpixel.shatteredpixeldungeon.messages.Messages;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import com.shatteredpixel.shatteredpixeldungeon.scenes.PixelScene;
import com.shatteredpixel.shatteredpixeldungeon.ui.RedButton;
import com.shatteredpixel.shatteredpixeldungeon.ui.RenderedTextBlock;
import com.shatteredpixel.shatteredpixeldungeon.utils.GLog;
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
	private final RedButton pause, reroll;
	private WndRankedPause pauseWnd;
	private WndOptions voteWnd;
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

		// L19: pause stops only our own clock
		pause = new RedButton( Messages.get( RankedSession.class, "pause" ), 6 ){
			@Override
			protected void onClick(){
				RankedSession s = RankedSession.current();
				if (s != null && s.canPause()) s.pause();
			}
		};
		pause.setSize( 36, 14 );
		add( pause );

		// L6: ask the opponent for a new seed, only up to floor 5
		reroll = new RedButton( Messages.get( RankedSession.class, "reroll" ), 6 ){
			@Override
			protected void onClick(){
				GameScene.show( new WndOptions( Messages.get( RankedSession.class, "reroll" ),
						Messages.get( RankedSession.class, "reroll_confirm" ),
						Messages.get( RankedSession.class, "reroll_yes" ),
						Messages.get( RankedSession.class, "reroll_no" ) ){
					@Override
					protected void onSelect( int index ){
						RankedSession s = RankedSession.current();
						if (index == 0 && s != null && s.canVoteReroll()) s.voteReroll( true );
					}
				} );
			}
		};
		reroll.setSize( 36, 14 );
		add( reroll );
	}

	@Override
	public void update(){
		super.update();
		RankedSession s = RankedSession.current();
		// a replay being watched is not the match in progress
		visible = s != null && !com.shatteredpixel.shatteredpixeldungeon.ranked.Features.viewingReplay();
		if (!visible) return;
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
		pause.visible = pause.active = canResign && s.canPause();
		pause.text( Messages.get( RankedSession.class, "pause_left", (s.pauseLeftMs() + 999) / 1000 ) );
		reroll.visible = reroll.active = canResign && s.canVoteReroll();
		if (canResign){
			// the buttons shown, side by side under the opponent line
			Camera c = camera();
			float w = 0;
			for (RedButton b : new RedButton[]{resign, pause, reroll}) if (b.visible) w += b.width() + 2;
			float x = (c == null ? 2 : (c.width - w) / 2f) + 1;
			for (RedButton b : new RedButton[]{resign, pause, reroll}){
				if (!b.visible) continue;
				b.setPos( x, line.bottom() + 2 );
				x += b.width() + 2;
			}
		}

		if (s.paused() && (pauseWnd == null || pauseWnd.parent == null)){
			pauseWnd = new WndRankedPause();
			GameScene.show( pauseWnd );
		}
		if (s.theirVote() && !s.myVote() && (voteWnd == null || voteWnd.parent == null)){
			voteWnd = new WndOptions( Messages.get( RankedSession.class, "reroll" ),
					Messages.get( RankedSession.class, "reroll_asked", s.opponent() ),
					Messages.get( RankedSession.class, "reroll_accept" ),
					Messages.get( RankedSession.class, "reroll_decline" ) ){
				@Override
				protected void onSelect( int index ){
					RankedSession s = RankedSession.current();
					if (s != null && s.theirVote()) s.voteReroll( index == 0 );
				}
				@Override
				public void onBackPressed(){
					super.onBackPressed();
					RankedSession s = RankedSession.current();
					if (s != null && s.theirVote()) s.voteReroll( false );
				}
			};
			GameScene.show( voteWnd );
		} else if (!s.theirVote() && voteWnd != null && voteWnd.parent != null){
			voteWnd.hide();
		}
		String ended = s.takeVoteEnded();
		if (ended != null){
			GLog.w( Messages.get( RankedSession.class, "reroll_ended_" + ended.toLowerCase( java.util.Locale.ROOT ) ) );
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
		if (s.opponentPaused()) status += " (" + Messages.get( RankedSession.class, "opp_paused" ) + ")";
		if (s.myVote()) status += " - " + Messages.get( RankedSession.class, "reroll_waiting" );
		return Messages.get( RankedSession.class, "opp_line", s.opponent(), s.opponentDepth(), s.opponentBosses() ) + status;
	}
}
