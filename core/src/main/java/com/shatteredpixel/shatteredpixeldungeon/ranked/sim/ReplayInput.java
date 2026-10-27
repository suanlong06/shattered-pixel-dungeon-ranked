package com.shatteredpixel.shatteredpixeldungeon.ranked.sim;

import java.util.List;

/**
 * spd-ranked (A3/A5): feeds a recorded command list back to a {@link TurnRunner}: commands given
 * while the hero waited go in when it waits again, "@k" commands right before the hero's turn k+1.
 * Used by the server check (ReplayCheck) and the in-game replay viewer, so both play a log alike.
 */
public class ReplayInput implements TurnRunner.Input {

	private final List<Command> cmds;
	private int i;
	/** the first reason the replay cannot be trusted to follow the recorded game, or null */
	public String unsupported;

	public ReplayInput( List<Command> cmds ){
		this.cmds = cmds;
	}

	/** commands handed out so far */
	public int index(){ return i; }

	public int size(){ return cmds.size(); }

	/** called before each command is handed out (and once more when the log ends) */
	protected void beforeCommand(){}

	@Override
	public Command next( TurnRunner runner ){
		beforeCommand();
		return i < cmds.size() ? take() : null;
	}

	@Override
	public Command beforeHeroActs( TurnRunner runner, int heroActs ){
		if (i >= cmds.size() || cmds.get( i ).at < 0 || cmds.get( i ).at > heroActs) return null;
		beforeCommand();
		return take();
	}

	private Command take(){
		Command c = cmds.get( i );
		if (unsupported == null){
			if (c.type == Command.Type.NOTE){
				unsupported = "client: " + c.action;
			} else if (!c.answersWindow() && CommandLog.topWindow() != null){
				unsupported = "window " + CommandLog.windowName( CommandLog.topWindow() ) + " open before command " + i;
			}
		}
		i++;
		return c;
	}
}
