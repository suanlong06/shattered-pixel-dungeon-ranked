/**
 * spd-ranked: everything this fork adds on top of SPD lives here (T0.1).
 *
 * <ul>
 *   <li>{@code ranked} itself: run-wide rules and state ({@link com.shatteredpixel.shatteredpixeldungeon.ranked.Features},
 *       {@link com.shatteredpixel.shatteredpixeldungeon.ranked.CleanProfile},
 *       {@link com.shatteredpixel.shatteredpixeldungeon.ranked.RankedDraws},
 *       {@link com.shatteredpixel.shatteredpixeldungeon.ranked.Determinism}).</li>
 *   <li>{@code ranked.sim}: headless runner, command log and replay (T0.8).</li>
 *   <li>Later features get their own subpackage (for example {@code ranked.qol}, {@code ranked.net}).</li>
 * </ul>
 *
 * Hook convention for SPD's own files (keeps upstream merges small):
 * <ul>
 *   <li>A hook is one line, or one {@code if} that returns early, calling into this package.
 *       Logic stays here, never in the SPD file.</li>
 *   <li>Every changed line carries {@code // spd-ranked} (optionally {@code : <task> <why>}),
 *       on the line itself or on the line above a short block.
 *       {@code git grep "spd-ranked"} lists every hook after an upstream merge.</li>
 *   <li>Use the fully qualified class name in the hook instead of adding an import, so the
 *       import block of the SPD file stays untouched.</li>
 *   <li>A hook must not change behaviour in Solo unless the task says so: check
 *       {@link com.shatteredpixel.shatteredpixeldungeon.ranked.Features#isRanked()} or a
 *       {@link com.shatteredpixel.shatteredpixeldungeon.ranked.Feature} flag first.</li>
 * </ul>
 */
package com.shatteredpixel.shatteredpixeldungeon.ranked;
