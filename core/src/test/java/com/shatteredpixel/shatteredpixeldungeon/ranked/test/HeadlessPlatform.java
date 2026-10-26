package com.shatteredpixel.shatteredpixeldungeon.ranked.test;

import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.watabou.utils.PlatformSupport;

/** PlatformSupport for the headless runner: no fonts (text is never drawn), no system UI. */
public final class HeadlessPlatform extends PlatformSupport {
	@Override public void updateDisplaySize(){}
	@Override public void updateSystemUI(){}
	@Override public boolean connectedToUnmeteredNetwork(){ return false; }
	@Override public boolean supportsVibration(){ return false; }
	@Override public void setupFontGenerators(int pageSize, boolean systemFont){}
	@Override protected FreeTypeFontGenerator getGeneratorForString(String input){ return null; }
	@Override public String[] splitforTextBlock(String text, boolean multiline){
		return multiline ? text.split("(?<=\n)|(?=\n)| ") : text.split(" ");
	}
}
