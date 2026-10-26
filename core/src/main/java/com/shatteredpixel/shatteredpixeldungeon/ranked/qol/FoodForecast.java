package com.shatteredpixel.shatteredpixeldungeon.ranked.qol;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Hunger;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Shadows;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.WellFed;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.food.Food;
import com.shatteredpixel.shatteredpixeldungeon.items.trinkets.SaltCube;
import com.shatteredpixel.shatteredpixeldungeon.messages.Messages;

/**
 * spd-ranked (B5): how many turns until Hungry / Starving, and how long the food in the pack lasts.
 * Mirrors {@link Hunger#act()} of v4.0.2: +1 hunger per turn, divided by 1.5 under Shadows,
 * times the Salt Cube multiplier; nothing while Well Fed or on a locked (boss) floor.
 * Only reads state: never draws from the RNG (L10 default: turn counts + food on hand).
 */
public final class FoodForecast {

	/** hunger gained per turn right now; 0 when hunger is paused */
	public final float rate;
	/** current hunger, 0 (full) .. {@link Hunger#STARVING} */
	public final float level;
	/** turns until Hungry / Starving at the current rate; 0 if already there, -1 if paused */
	public final int toHungry, toStarving;
	/** total energy of the food carried (all bags) */
	public final float foodEnergy;
	public final int foodItems;

	private FoodForecast( float rate, float level, float foodEnergy, int foodItems ){
		this.rate = rate;
		this.level = level;
		this.toHungry = turns( Hunger.HUNGRY - level, rate );
		this.toStarving = turns( Hunger.STARVING - level, rate );
		this.foodEnergy = foodEnergy;
		this.foodItems = foodItems;
	}

	static int turns( float left, float rate ){
		if (left <= 0) return 0;
		if (rate <= 0) return -1;
		return (int)Math.ceil( left / rate );
	}

	public static float rate( Hero hero ){
		if (hero.buff( WellFed.class ) != null) return 0;
		if (Dungeon.level != null && Dungeon.level.locked) return 0;
		float delay = 1f;
		if (hero.buff( Shadows.class ) != null) delay *= 1.5f;
		delay /= SaltCube.hungerGainMultiplier();
		return 1f / delay;
	}

	public static FoodForecast of( Hero hero ){
		Hunger h = hero.buff( Hunger.class );
		float level = h == null ? 0 : h.hunger();
		float energy = 0;
		int n = 0;
		for (Item i : hero.belongings){
			if (i instanceof Food){
				energy += ((Food) i).energy * i.quantity();
				n += i.quantity();
			}
		}
		return new FoodForecast( h == null ? 0 : rate( hero ), level, energy, n );
	}

	/** turns the carried food buys at the current rate (normal rate if paused) */
	public int foodTurns(){
		return (int)(foodEnergy / (rate > 0 ? rate : 1f));
	}

	/** one line for the hero window, e.g. "Hungry in 120, starving in 270 | food 2 (+600)" */
	public String summary(){
		String s;
		if (toStarving == 0)    s = Messages.get( this, "starving_now" );
		else if (toHungry < 0)  s = Messages.get( this, "paused" );
		else if (toHungry == 0) s = Messages.get( this, "starving_in", toStarving );
		else                    s = Messages.get( this, "hungry", toHungry, toStarving );
		return Messages.get( this, "food", s, foodItems, foodTurns() );
	}
}
