/*
 * Copyright (C) L2J Sunrise
 * This file is part of L2J Sunrise.
 */
package l2r.gameserver.model.cubic.conditions;

import l2r.gameserver.model.actor.L2Character;
import l2r.gameserver.model.cubic.CubicInstance;

public class HealthCondition implements ICubicCondition
{
	private final int _min;
	private final int _max;
	
	public HealthCondition(int min, int max)
	{
		_min = min;
		_max = max;
	}
	
	@Override
	public boolean test(CubicInstance cubic, L2Character owner, L2Character target)
	{
		final double hpPer = target.getCurrentHpPercent();
		return (hpPer >= _min) && (hpPer <= _max);
	}
	
	@Override
	public String toString()
	{
		return getClass().getSimpleName() + " min: " + _min + " max: " + _max;
	}
}
