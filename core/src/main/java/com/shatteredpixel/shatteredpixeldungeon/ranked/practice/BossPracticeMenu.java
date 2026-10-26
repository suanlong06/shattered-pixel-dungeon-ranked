package com.shatteredpixel.shatteredpixeldungeon.ranked.practice;

import com.shatteredpixel.shatteredpixeldungeon.GamesInProgress;
import com.shatteredpixel.shatteredpixeldungeon.ShatteredPixelDungeon;
import com.shatteredpixel.shatteredpixeldungeon.messages.Messages;
import com.shatteredpixel.shatteredpixeldungeon.ui.Icons;
import com.shatteredpixel.shatteredpixeldungeon.ui.StyledButton;
import com.shatteredpixel.shatteredpixeldungeon.windows.WndMessage;
import com.shatteredpixel.shatteredpixeldungeon.windows.WndOptions;
import com.shatteredpixel.shatteredpixeldungeon.Chrome;

/** spd-ranked (B6): the Boss Practice entry in the hero select options pane. */
public final class BossPracticeMenu {

	private BossPracticeMenu(){}

	public static StyledButton button(){
		StyledButton b = new StyledButton( Chrome.Type.BLANK, Messages.get( BossPractice.class, "button" ), 6 ){
			@Override
			protected void onClick(){
				super.onClick();
				if (GamesInProgress.selectedClass == null){
					ShatteredPixelDungeon.scene().addToFront( new WndMessage( Messages.get( BossPractice.class, "need_class" ) ) );
					return;
				}
				BossPractice.Boss[] bosses = BossPractice.Boss.values();
				String[] names = new String[bosses.length];
				for (int i = 0; i < bosses.length; i++) names[i] = Messages.get( bosses[i], bosses[i].name() );
				ShatteredPixelDungeon.scene().addToFront( new WndOptions( Icons.get( Icons.SKULL ),
						Messages.get( BossPractice.class, "title" ),
						Messages.get( BossPractice.class, "desc" ),
						names ){
					@Override
					protected void onSelect( int index ){
						BossPractice.start( bosses[index] );
					}
				} );
			}
		};
		b.leftJustify = true;
		b.icon( Icons.get( Icons.SKULL ) );
		return b;
	}
}
