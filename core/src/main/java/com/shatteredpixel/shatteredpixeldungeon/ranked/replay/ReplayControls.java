package com.shatteredpixel.shatteredpixeldungeon.ranked.replay;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.messages.Messages;
import com.shatteredpixel.shatteredpixeldungeon.scenes.PixelScene;
import com.shatteredpixel.shatteredpixeldungeon.ui.RedButton;
import com.shatteredpixel.shatteredpixeldungeon.ui.RenderedTextBlock;
import com.watabou.input.PointerEvent;
import com.watabou.noosa.Camera;
import com.watabou.noosa.Group;
import com.watabou.noosa.PointerArea;

import java.util.ArrayList;
import java.util.List;

/**
 * spd-ranked (A5): the replay bar at the top of the game scene: pause, slower, faster, one input,
 * seeking, the hero's inventory, exit, and a line with progress and speed. The only thing the player can press while watching.
 */
class ReplayControls extends Group {

	private static final float TOP = 40;
	private static final float BTN_W = 26;
	private static final float BTN_H = 16;
	private static final float GAP = 2;

	private final ReplayViewer viewer;
	/** buttons per row */
	private static final int ROW = 4;

	private final Btn pause, slower, faster, step, seek, bag, exit;
	private final List<Btn> buttons = new ArrayList<>();
	private final RenderedTextBlock line;
	private String shown;

	ReplayControls( ReplayViewer viewer ){
		this.viewer = viewer;
		camera = PixelScene.uiCamera;

		pause  = add( new Btn( "" ){ @Override protected void onClick(){ viewer.togglePause(); } } );
		slower = add( new Btn( "-" ){ @Override protected void onClick(){ viewer.slower(); } } );
		faster = add( new Btn( "+" ){ @Override protected void onClick(){ viewer.faster(); } } );
		step   = add( new Btn( Messages.get( ReplayViewer.class, "step" ) ){ @Override protected void onClick(){ viewer.stepOne(); } } );
		seek   = add( new Btn( Messages.get( ReplayViewer.class, "seek" ) ){ @Override protected void onClick(){ viewer.show( new WndReplaySeek( viewer ) ); } } );
		bag    = add( new Btn( Messages.get( ReplayViewer.class, "bag" ) ){ @Override protected void onClick(){ viewer.show( new WndReplayBag( viewer ) ); } } );
		exit   = add( new Btn( Messages.get( ReplayViewer.class, "exit" ) ){ @Override protected void onClick(){ ReplayViewer.exit( null ); } } );

		line = PixelScene.renderTextBlock( 6 );
		add( line );
		refresh();
	}

	private Btn add( Btn b ){
		buttons.add( b );
		super.add( b );
		return b;
	}

	/** the buttons' press areas, so the input gate can let presses on them through */
	List<PointerArea> areas(){
		List<PointerArea> out = new ArrayList<>();
		for (Btn b : buttons) out.add( b.area() );
		return out;
	}

	boolean hits( PointerEvent e ){
		for (Btn b : buttons){
			if (b.visible && b.area().overlapsScreenPoint( (int) e.current.x, (int) e.current.y )) return true;
		}
		return false;
	}

	void refresh(){
		ReplayPlayback p = viewer.state();
		boolean over = p.ended() != null;
		pause.text( Messages.get( ReplayViewer.class, p.paused() ? "play" : "pause" ) );
		pause.enable( !over );
		step.enable( !over );
		bag.enable( Dungeon.hero != null );

		String speed = p.speed() < 1 ? "1/" + Math.round( 1 / p.speed() ) : String.valueOf( Math.round( p.speed() ) );
		String t;
		if (!over && p.seeking()){
			t = Messages.get( ReplayViewer.class, "seeking", p.done(), p.total(), Dungeon.depth );
		} else if (!over){
			t = Messages.get( ReplayViewer.class, "status", p.done(), p.total(), Dungeon.depth, speed )
					+ (p.paused() ? " " + Messages.get( ReplayViewer.class, "paused" ) : "");
		} else if (p.finished()){
			t = Messages.get( ReplayViewer.class, "finished", p.total() );
		} else if (p.ended().equals( ReplayPlayback.WON_SURFACE )){
			t = Messages.get( ReplayViewer.class, "won_surface" );
		} else if (p.ended().equals( ReplayPlayback.WON_AMULET )){
			int rest = p.total() - p.done();
			t = rest > 0 ? Messages.get( ReplayViewer.class, "won_amulet_more", Dungeon.depth, rest )
					: Messages.get( ReplayViewer.class, "won_amulet", Dungeon.depth );
		} else if (p.ended().startsWith( "hero died" )){
			t = Messages.get( ReplayViewer.class, "died", Dungeon.depth );
		} else {
			t = Messages.get( ReplayViewer.class, "stopped", p.done(), p.total(), p.ended() );
		}
		if (p.input.unsupported != null && !over) t += "\n" + Messages.get( ReplayViewer.class, "untrusted" );
		if (t.equals( shown )) return;
		shown = t;

		Camera c = camera();
		float width = c == null ? 160 : c.width;
		float y = TOP;
		for (int i = 0; i < buttons.size(); i += ROW){
			int n = Math.min( ROW, buttons.size() - i );
			float x = (width - (n * BTN_W + (n - 1) * GAP)) / 2f;
			for (Btn b : buttons.subList( i, i + n )){
				b.setRect( x, y, BTN_W, BTN_H );
				PixelScene.align( b );
				x += BTN_W + GAP;
			}
			y += BTN_H + GAP;
		}
		line.maxWidth( (int) Math.min( width - 8, 200 ) );
		line.text( t );
		line.setPos( (width - line.width()) / 2f, y );
		PixelScene.align( line );
	}

	private static class Btn extends RedButton {
		Btn( String label ){
			super( label, 6 );
		}

		PointerArea area(){
			return hotArea;
		}
	}
}
