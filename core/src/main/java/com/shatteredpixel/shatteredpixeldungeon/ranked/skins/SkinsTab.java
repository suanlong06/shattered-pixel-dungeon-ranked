package com.shatteredpixel.shatteredpixeldungeon.ranked.skins;

import com.shatteredpixel.shatteredpixeldungeon.ShatteredPixelDungeon;
import com.shatteredpixel.shatteredpixeldungeon.messages.Messages;
import com.shatteredpixel.shatteredpixeldungeon.scenes.PixelScene;
import com.shatteredpixel.shatteredpixeldungeon.ui.RedButton;
import com.shatteredpixel.shatteredpixeldungeon.ui.RenderedTextBlock;
import com.shatteredpixel.shatteredpixeldungeon.ui.Window;
import com.watabou.noosa.ColorBlock;
import com.watabou.noosa.ui.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * spd-ranked (A6): the Skins tab of the settings window. Lists the packs in the local
 * {@code skins/} folder; choosing one reloads the scene so every texture is loaded again.
 */
public class SkinsTab extends Component {

	private static final int GAP = 2;
	private static final int BTN_HEIGHT = 16;

	private RenderedTextBlock title;
	private ColorBlock sep;
	private RenderedTextBlock info;
	//filled in createChildren(), which Component's constructor calls before field initializers run
	private List<RedButton> buttons;

	@Override
	protected void createChildren(){
		title = PixelScene.renderTextBlock( Messages.get( this, "title" ), 9 );
		title.hardlight( Window.TITLE_COLOR );
		add( title );

		sep = new ColorBlock( 1, 1, 0xFF000000 );
		add( sep );

		List<SkinPack> packs = Skins.installed();
		info = PixelScene.renderTextBlock( Messages.get( this, packs.isEmpty() ? "none_installed" : "info", Skins.DIR ), 6 );
		add( info );

		buttons = new ArrayList<>();
		buttons.add( button( null ) );
		for (SkinPack p : packs) buttons.add( button( p ) );
		for (RedButton b : buttons) add( b );
	}

	private RedButton button( final SkinPack pack ){
		SkinPack cur = Skins.active();
		boolean selected = pack == null ? cur == null : cur != null && cur.id.equals( pack.id );
		String label = pack == null ? Messages.get( this, "original" )
				: pack.author.isEmpty() ? pack.name : Messages.get( this, "by", pack.name, pack.author );
		RedButton b = new RedButton( label, 8 ){
			@Override
			protected void onClick(){
				Skins.select( pack );
				ShatteredPixelDungeon.seamlessResetScene();
			}
		};
		if (selected) b.textColor( Window.TITLE_COLOR );
		return b;
	}

	@Override
	protected void layout(){
		title.setPos( (width - title.width()) / 2, y + GAP );
		sep.size( width, 1 );
		sep.y = title.bottom() + 3 * GAP;

		info.maxWidth( (int) width );
		info.setPos( 0, sep.y + 1 + GAP );

		float pos = info.bottom() + 2 * GAP;
		for (RedButton b : buttons){
			b.setRect( 0, pos, width, BTN_HEIGHT );
			pos += BTN_HEIGHT + GAP;
		}
		height = pos - y;
	}
}
