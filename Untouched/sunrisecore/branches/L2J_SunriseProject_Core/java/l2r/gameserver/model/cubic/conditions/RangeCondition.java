/*
 * Copyright (C) L2J Sunrise
 * This file is part of L2J Sunrise.
 */
package l2r.gameserver.model.cubic.conditions;

import l2r.gameserver.model.actor.L2Character;
import l2r.gameserver.model.cubic.CubicInstance;

public class RangeCondition implements ICubicCondition
{
	private final int _range;
	
	public RangeCondition(int range)
	{
		_range = range;
	}
	
	@Override
	public boolean test(CubicInstance cubic, L2Character owner, L2Character target)
	{
		return owner.isInRadius3d(target, _range);
	}
}
