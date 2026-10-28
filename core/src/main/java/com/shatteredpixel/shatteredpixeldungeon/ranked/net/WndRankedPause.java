package com.shatteredpixel.shatteredpixeldungeon.ranked.net;

import com.shatteredpixel.shatteredpixeldungeon.messages.Messages;
import com.shatteredpixel.shatteredpixeldungeon.scenes.PixelScene;
import com.shatteredpixel.shatteredpixeldungeon.ui.RedButton;
import com.shatteredpixel.shatteredpixeldungeon.ui.RenderedTextBlock;
import com.shatteredpixel.shatteredpixeldungeon.ui.Window;

/**
 * spd-ranked (L19): shown while this player's pause runs. It blocks the hero (any open window
 * does), counts the budget down and closes when the server ends the pause. The opponent's
 * clock keeps running meanwhile, which the text says.
 */
public class WndRankedPause extends Window {

	private static final int WIDTH = 120;

	private final RenderedTextBlock text;
	private long shownSec = -1;

	public WndRankedPause(){
		super();
		RenderedTextBlock title = PixelScene.renderTextBlock( Messages.get( RankedSession.class, "pause_title" ), 9 );
		title.hardlight( TITLE_COLOR );
		title.maxWidth( WIDTH );
		title.setPos( 0, 0 );
		add( title );

		text = PixelScene.renderTextBlock( 6 );
		text.maxWidth( WIDTH );
		add( text );
		text.text( Messages.get( RankedSession.class, "pause_text", 60 ) );
		text.setPos( 0, title.bottom() + 4 );

		RedButton resume = new RedButton( Messages.get( RankedSession.class, "pause_resume" ) ){
			@Override
			protected void onClick(){
				RankedSession s = RankedSession.current();
				if (s != null) s.unpause();
			}
		};
		resume.setRect( 0, text.bottom() + 6, WIDTH, 18 );
		add( resume );
		resize( WIDTH, (int)resume.bottom() );
	}

	@Override
	public void update(){
		super.update();
		RankedSession s = RankedSession.current();
		if (s == null || !s.paused() || !s.inRun()){
			hide();
			return;
		}
		long sec = (s.pauseLeftMs() + 999) / 1000;
		if (sec != shownSec){
			shownSec = sec;
			text.text( Messages.get( RankedSession.class, "pause_text", sec ) );
		}
	}

	// the pause ends only through the server: back does what Resume does
	@Override
	public void onBackPressed(){
		RankedSession s = RankedSession.current();
		if (s != null) s.unpause();
	}
}
