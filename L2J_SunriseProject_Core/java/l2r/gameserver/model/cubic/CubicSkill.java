/*
 * Copyright (C) L2J Sunrise
 * This file is part of L2J Sunrise.
 */
package l2r.gameserver.model.cubic;

import java.util.ArrayList;
import java.util.List;

import l2r.gameserver.model.StatsSet;
import l2r.gameserver.model.actor.L2Character;
import l2r.gameserver.model.cubic.conditions.ICubicCondition;
import l2r.gameserver.model.holders.SkillHolder;

public class CubicSkill extends SkillHolder implements ICubicConditionHolder
{
	private final int _triggerRate;
	private final int _successRate;
	private final boolean _canUseOnStaticObjects;
	private final CubicSkillTargetType _targetType;
	private final List<ICubicCondition> _conditions = new ArrayList<>();
	private final boolean _targetDebuff;
	
	public CubicSkill(StatsSet set)
	{
		super(set.getInt("id"), set.getInt("level"));
		_triggerRate = set.getInt("triggerRate", 100);
		_successRate = set.getInt("successRate", 100);
		_canUseOnStaticObjects = set.getBoolean("canUseOnStaticObjects", false);
		_targetType = set.getEnum("target", CubicSkillTargetType.class, CubicSkillTargetType.TARGET);
		_targetDebuff = set.getBoolean("targetDebuff", false);
	}
	
	public int getTriggerRate()
	{
		return _triggerRate;
	}
	
	public int getSuccessRate()
	{
		return _successRate;
	}
	
	public boolean canUseOnStaticObjects()
	{
		return _canUseOnStaticObjects;
	}
	
	public CubicSkillTargetType getTargetType()
	{
		return _targetType;
	}
	
	public boolean isTargetingDebuff()
	{
		return _targetDebuff;
	}
	
	@Override
	public boolean validateConditions(CubicInstance cubic, L2Character owner, L2Character target)
	{
		return (!_targetDebuff || (_targetDebuff && (target.getEffectList().getDebuffCount() > 0))) && (_conditions.isEmpty() || _conditions.stream().allMatch(condition -> condition.test(cubic, owner, target)));
	}
	
	@Override
	public void addCondition(ICubicCondition condition)
	{
		_conditions.add(condition);
	}
	
	@Override
	public String toString()
	{
		return "Cubic skill id: " + getSkillId() + " level: " + getSkillLvl() + " triggerRate: " + _triggerRate + " successRate: " + _successRate + " canUseOnStaticObjects: " + _canUseOnStaticObjects + " targetType: " + _targetType + " isTargetingDebuff: " + _targetDebuff + System.lineSeparator();
	}
}
