package com.shatteredpixel.shatteredpixeldungeon.ranked.replay;

import com.shatteredpixel.shatteredpixeldungeon.Chrome;
import com.shatteredpixel.shatteredpixeldungeon.ShatteredPixelDungeon;
import com.shatteredpixel.shatteredpixeldungeon.messages.Messages;
import com.shatteredpixel.shatteredpixeldungeon.ui.Icons;
import com.shatteredpixel.shatteredpixeldungeon.ui.StyledButton;
import com.shatteredpixel.shatteredpixeldungeon.windows.WndMessage;
import com.shatteredpixel.shatteredpixeldungeon.windows.WndOptions;
import com.watabou.noosa.Game;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** spd-ranked (A5): the replay list (folder replays/, newest first), a page at a time, and one replay's details. */
public final class WndReplays {

	static final int PER_PAGE = 6;

	private WndReplays(){}

	/** the Replays entry in the hero select options pane */
	public static StyledButton button(){
		StyledButton b = new StyledButton( Chrome.Type.BLANK, Messages.get( ReplayViewer.class, "button" ), 6 ){
			@Override
			protected void onClick(){
				super.onClick();
				show( 0 );
			}
		};
		b.leftJustify = true;
		b.icon( Icons.get( Icons.REPEAT ) );
		return b;
	}

	public static void show( int page ){
		List<Replays.Entry> all = Replays.list();
		if (all.isEmpty()){
			ShatteredPixelDungeon.scene().addToFront( new WndMessage( Messages.get( ReplayViewer.class, "none", Replays.DIR ) ) );
			return;
		}
		int pages = (all.size() + PER_PAGE - 1) / PER_PAGE;
		final int p = Math.max( 0, Math.min( page, pages - 1 ) );
		final List<Replays.Entry> shown = all.subList( p * PER_PAGE, Math.min( all.size(), (p + 1) * PER_PAGE ) );
		List<String> options = new ArrayList<>();
		for (Replays.Entry e : shown) options.add( label( e ) );
		final boolean newer = p > 0, older = p < pages - 1;
		if (newer) options.add( Messages.get( ReplayViewer.class, "newer" ) );
		if (older) options.add( Messages.get( ReplayViewer.class, "older" ) );

		ShatteredPixelDungeon.scene().addToFront( new WndOptions( Icons.get( Icons.REPEAT ),
				Messages.get( ReplayViewer.class, "title" ),
				Messages.get( ReplayViewer.class, "list_desc", p + 1, pages ),
				options.toArray( new String[0] ) ){
			@Override
			protected void onSelect( int index ){
				if (index < shown.size()){
					details( shown.get( index ), p );
				} else if (newer && index == shown.size()){
					show( p - 1 );
				} else {
					show( p + 1 );
				}
			}
		} );
	}

	static String label( Replays.Entry e ){
		String date = new SimpleDateFormat( "yyyy-MM-dd HH:mm", Locale.ROOT ).format( new Date( e.modified ) );
		if (e.replay == null) return date + " - " + Messages.get( ReplayViewer.class, "broken" );
		String s = date + " - " + e.replay.heroClass.title();
		if (!e.replay.label.isEmpty()) s += " - " + e.replay.label;
		return s;
	}

	private static void details( Replays.Entry e, int page ){
		if (e.replay == null){
			ShatteredPixelDungeon.scene().addToFront( new WndOptions( Icons.get( Icons.WARNING ), e.name,
					Messages.get( ReplayViewer.class, "broken_desc", e.error ),
					Messages.get( ReplayViewer.class, "delete" ), Messages.get( ReplayViewer.class, "back" ) ){
				@Override
				protected void onSelect( int index ){
					if (index == 0) Replays.delete( e.name );
					show( page );
				}
			} );
			return;
		}
		ReplayFile r = e.replay;
		StringBuilder desc = new StringBuilder( Messages.get( ReplayViewer.class, "details",
				r.heroClass.title(), r.mode.name(), r.seed, r.commands.size() ) );
		if (r.gameVersion != Game.versionCode){
			desc.append( "\n\n" ).append( Messages.get( ReplayViewer.class, "other_version", r.gameVersion, Game.versionCode ) );
		}
		if (r.mode != com.shatteredpixel.shatteredpixeldungeon.ranked.GameMode.RANKED){
			desc.append( "\n\n" ).append( Messages.get( ReplayViewer.class, "solo_warning" ) );
		}
		String blocked = ReplayViewer.cannotWatch( r );
		if (blocked != null) desc.append( "\n\n" ).append( blocked );
		ShatteredPixelDungeon.scene().addToFront( new WndOptions( Icons.get( Icons.REPEAT ), label( e ), desc.toString(),
				Messages.get( ReplayViewer.class, "watch" ), Messages.get( ReplayViewer.class, "delete" ),
				Messages.get( ReplayViewer.class, "back" ) ){
			@Override
			protected boolean enabled( int index ){
				return index != 0 || blocked == null;
			}

			@Override
			protected void onSelect( int index ){
				if (index == 0){
					ReplayViewer.start( r );
				} else if (index == 1){
					Replays.delete( e.name );
					show( page );
				} else {
					show( page );
				}
			}
		} );
	}
}
