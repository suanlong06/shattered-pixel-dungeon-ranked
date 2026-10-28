package com.shatteredpixel.shatteredpixeldungeon.ranked.replay;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.messages.Messages;
import com.shatteredpixel.shatteredpixeldungeon.ui.Icons;
import com.shatteredpixel.shatteredpixeldungeon.windows.WndOptions;

/** spd-ranked (A5): where to seek the replay to, by inputs or to the next floor; it pauses there. */
class WndReplaySeek extends WndOptions {

	static final int SMALL = 10, BIG = 50;

	private final ReplayViewer viewer;

	WndReplaySeek( ReplayViewer viewer ){
		super( Icons.get( Icons.REPEAT ), Messages.get( ReplayViewer.class, "seek_title" ),
				Messages.get( ReplayViewer.class, "seek_desc", viewer.state().done(), viewer.state().total(), Dungeon.depth ),
				options( viewer.state().ended() != null ) );
		this.viewer = viewer;
	}

	/** back, then forward while the replay still plays */
	private static String[] options( boolean over ){
		String[] back = {
				Messages.get( ReplayViewer.class, "seek_start" ),
				Messages.get( ReplayViewer.class, "seek_back", BIG ),
				Messages.get( ReplayViewer.class, "seek_back", SMALL ) };
		if (over) return back;
		return new String[]{ back[0], back[1], back[2],
				Messages.get( ReplayViewer.class, "seek_forward", SMALL ),
				Messages.get( ReplayViewer.class, "seek_forward", BIG ),
				Messages.get( ReplayViewer.class, "seek_floor" ),
				Messages.get( ReplayViewer.class, "seek_end" ) };
	}

	@Override
	protected void onSelect( int index ){
		int at = viewer.state().applied();
		switch (index){
			case 0: viewer.seek( 0 ); break;
			case 1: viewer.seek( at - BIG ); break;
			case 2: viewer.seek( at - SMALL ); break;
			case 3: viewer.seek( at + SMALL ); break;
			case 4: viewer.seek( at + BIG ); break;
			case 5: viewer.seekNextFloor(); break;
			default: viewer.seek( viewer.state().total() ); break;
		}
	}
}
