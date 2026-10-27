package com.shatteredpixel.shatteredpixeldungeon.ranked.net;

import com.shatteredpixel.shatteredpixeldungeon.Chrome;
import com.shatteredpixel.shatteredpixeldungeon.GamesInProgress;
import com.shatteredpixel.shatteredpixeldungeon.ShatteredPixelDungeon;
import com.shatteredpixel.shatteredpixeldungeon.messages.Messages;
import com.shatteredpixel.shatteredpixeldungeon.scenes.PixelScene;
import com.shatteredpixel.shatteredpixeldungeon.ui.Icons;
import com.shatteredpixel.shatteredpixeldungeon.ui.RedButton;
import com.shatteredpixel.shatteredpixeldungeon.ui.RenderedTextBlock;
import com.shatteredpixel.shatteredpixeldungeon.ui.StyledButton;
import com.shatteredpixel.shatteredpixeldungeon.ui.Window;
import com.shatteredpixel.shatteredpixeldungeon.windows.WndMessage;

/** spd-ranked (A1 client): the ranked queue, from connecting to the match start. */
public class WndRankedQueue extends Window {

	private static final int WIDTH = 130;
	private static final int MARGIN = 4;
	private static final int BTN_H = 18;

	private final RankedSession session;
	private final RenderedTextBlock text;
	private final RedButton accept;
	private final RedButton cancel;
	private String shown;

	/** the Ranked Duel entry in the hero select options pane */
	public static StyledButton button(){
		// a match left running when the app closed takes the button's place until it is over
		final boolean rejoin = RankedSession.canRejoin();
		StyledButton b = new StyledButton( Chrome.Type.BLANK, Messages.get( RankedSession.class, rejoin ? "rejoin" : "button" ), 6 ){
			@Override
			protected void onClick(){
				super.onClick();
				if (rejoin && RankedSession.canRejoin()){
					ShatteredPixelDungeon.scene().addToFront( new WndRankedQueue( RankedSession.rejoin() ) );
					return;
				}
				if (GamesInProgress.selectedClass == null){
					ShatteredPixelDungeon.scene().addToFront( new WndMessage( Messages.get( RankedSession.class, "need_class" ) ) );
					return;
				}
				ShatteredPixelDungeon.scene().addToFront( new WndRankedQueue( RankedSession.queue() ) );
			}
		};
		b.leftJustify = true;
		b.icon( Icons.get( Icons.RANKINGS ) );
		return b;
	}

	public WndRankedQueue( RankedSession session ){
		super();
		this.session = session;

		text = PixelScene.renderTextBlock( 6 );
		text.maxWidth( WIDTH - 2 * MARGIN );
		add( text );

		accept = new RedButton( Messages.get( RankedSession.class, "accept" ) ){
			@Override
			protected void onClick(){
				session.accept();
				enable( false );
			}
		};
		add( accept );

		cancel = new RedButton( Messages.get( RankedSession.class, "cancel" ) ){
			@Override
			protected void onClick(){
				hide();
			}
		};
		add( cancel );

		refresh();
	}

	@Override
	public void update(){
		super.update();
		session.update();
		refresh();
	}

	private void refresh(){
		String s = describe();
		boolean found = session.state() == RankedClient.State.MATCH_FOUND;
		if (s.equals( shown ) && accept.visible == found) return;
		shown = s;
		text.text( s );
		text.setPos( MARGIN, MARGIN );
		accept.visible = accept.active = found;
		float y = text.bottom() + MARGIN;
		if (found){
			accept.setRect( MARGIN, y, WIDTH - 2 * MARGIN, BTN_H );
			y = accept.bottom() + 2;
		}
		cancel.setRect( MARGIN, y, WIDTH - 2 * MARGIN, BTN_H );
		resize( WIDTH, (int)cancel.bottom() + MARGIN );
	}

	private String describe(){
		StringBuilder s = new StringBuilder();
		switch (session.state()){
			case CONNECTING: s.append( Messages.get( RankedSession.class, "connecting" ) ); break;
			case IDLE: case QUEUED:
				s.append( Messages.get( RankedSession.class, "queued", session.rating() ) ); break;
			case MATCH_FOUND:
				s.append( Messages.get( RankedSession.class, "found", session.opponent(), session.opponentRating() ) ); break;
			case RECONNECTING: s.append( Messages.get( RankedSession.class, "rejoining" ) ); break;
			case STARTED: s.append( Messages.get( RankedSession.class, "starting" ) ); break;
			default: s.append( Messages.get( RankedSession.class, "offline" ) ); break;
		}
		if (session.error() != null){
			s.append( "\n\n" ).append( Messages.get( RankedSession.class, "error", session.error() ) );
		}
		return s.toString();
	}

	@Override
	public void hide(){
		super.hide();
		// once the run has started the window is gone with the scene; cancel only before that
		if (!session.inRun()) session.cancel();
	}
}
