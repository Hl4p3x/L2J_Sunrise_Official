/*
 * Copyright (C) L2J Sunrise
 * This file is part of L2J Sunrise.
 */
package l2r.gameserver.model.cubic.conditions;

import l2r.gameserver.model.actor.L2Character;
import l2r.gameserver.model.cubic.CubicInstance;

public class HpCondition implements ICubicCondition
{
	private final HpConditionType _type;
	private final int _hpPer;
	
	public HpCondition(HpConditionType type, int hpPer)
	{
		_type = type;
		_hpPer = hpPer;
	}
	
	@Override
	public boolean test(CubicInstance cubic, L2Character owner, L2Character target)
	{
		final double hpPer = target.getCurrentHpPercent();
		switch (_type)
		{
			case GREATER:
			{
				return hpPer > _hpPer;
			}
			case LESSER:
			{
				return hpPer < _hpPer;
			}
		}
		return false;
	}
	
	@Override
	public String toString()
	{
		return getClass().getSimpleName() + " chance: " + _hpPer;
	}
	
	public static enum HpConditionType
	{
		GREATER,
		LESSER;
	}
}
