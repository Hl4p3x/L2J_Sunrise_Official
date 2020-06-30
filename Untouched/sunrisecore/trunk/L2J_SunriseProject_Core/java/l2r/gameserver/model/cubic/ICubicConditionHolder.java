/*
 * Copyright (C) L2J Sunrise
 * This file is part of L2J Sunrise.
 */
package l2r.gameserver.model.cubic;

import l2r.gameserver.model.actor.L2Character;
import l2r.gameserver.model.cubic.conditions.ICubicCondition;

public interface ICubicConditionHolder
{
	public boolean validateConditions(CubicInstance cubic, L2Character owner, L2Character target);
	
	public void addCondition(ICubicCondition condition);
}
