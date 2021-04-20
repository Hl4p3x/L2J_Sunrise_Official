/*
 * Copyright (C) L2J Sunrise
 * This file is part of L2J Sunrise.
 */
package l2r.gameserver.model.cubic;

import java.util.Comparator;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import l2r.gameserver.ThreadPoolManager;
import l2r.gameserver.model.L2Object;
import l2r.gameserver.model.L2Party;
import l2r.gameserver.model.actor.L2Character;
import l2r.gameserver.model.actor.instance.L2PcInstance;
import l2r.gameserver.model.skills.L2Skill;
import l2r.gameserver.model.stats.Formulas;
import l2r.gameserver.network.serverpackets.MagicSkillUse;
import l2r.util.Rnd;

public class CubicInstance
{
	private final L2PcInstance _owner;
	private final L2PcInstance _caster;
	private final CubicTemplate _template;
	private ScheduledFuture<?> _skillUseTask;
	private ScheduledFuture<?> _expireTask;
	
	protected long nextLaunch = 0;
	private int usedCount = 0;
	
	public CubicInstance(L2PcInstance owner, L2PcInstance caster, CubicTemplate template)
	{
		_owner = owner;
		_caster = caster;
		_template = template;
		activate();
	}
	
	private void activate()
	{
		_skillUseTask = ThreadPoolManager.getInstance().scheduleGeneralAtFixedRate(this::tryToUseSkill, 0, _template.getDelay() * 1000, TimeUnit.MILLISECONDS);
		_expireTask = ThreadPoolManager.getInstance().scheduleGeneral(this::deactivate, _template.getDuration() * 1000, TimeUnit.MILLISECONDS);
	}
	
	public void deactivate()
	{
		if (_skillUseTask != null)
		{
			_skillUseTask.cancel(true);
			_skillUseTask = null;
		}
		
		if (_expireTask != null)
		{
			_expireTask.cancel(true);
			_expireTask = null;
		}
		
		_owner.getCubics().remove(_template.getId());
		_owner.broadcastUserInfo();
	}
	
	private void tryToUseSkill()
	{
		if (System.currentTimeMillis() < nextLaunch)
		{
			return;
		}
		
		final double random = Rnd.nextDouble() * 100;
		double commulativeChance = 0;
		for (CubicSkill cubicSkill : _template.getSkills())
		{
			if ((commulativeChance += cubicSkill.getTriggerRate()) > random)
			{
				final L2Skill skill = cubicSkill.getSkill();
				final int successRate = cubicSkill.getSuccessRate() * 10;
				if ((skill != null) && (Rnd.get(1000) < successRate))
				{
					final L2Character target = findTarget(cubicSkill);
					if (target != null)
					{
						if (usedCount > _template.getMaxCount())
						{
							if (!_template.isUseUp())
							{
								nextLaunch = System.currentTimeMillis() + (_template.getDelay() * 1000 * 5); // delay * 5
							}
							else
							{
								deactivate();
								return;
							}
						}
						else
						{
							nextLaunch = System.currentTimeMillis() + (_template.getDelay() * 1000);
						}
						
						usedCount++;
						
						_caster.broadcastPacket(new MagicSkillUse(_owner, target, skill.getDisplayId(), skill.getDisplayLevel(), skill.getHitTime(), skill.getReuseDelay()));
						
						if (skill.isOffensive() || skill.isDebuff())
						{
							final byte shld = Formulas.calcShldUse(_owner, target, skill);
							if (Formulas.calcSkillSuccess(_owner, target, skill, shld, false, false, false))
							{
								skill.getEffects(_owner, target);
							}
						}
						else
						{
							skill.getEffects(_owner, target);
						}
						break;
					}
				}
			}
		}
	}
	
	private L2Character findTarget(CubicSkill cubicSkill)
	{
		L2Character target = null;
		switch (_template.getTargetType())
		{
			case BY_SKILL:
			{
				final L2Skill skill = cubicSkill.getSkill();
				if (skill != null)
				{
					target = getTargetByType(cubicSkill);
				}
				break;
			}
			case TARGET:
			{
				target = getTargetByType(cubicSkill);
				break;
			}
			case HEAL:
			{
				final L2Party party = _owner.getParty();
				if (party != null)
				{
					target = party.getMembers().stream().filter(member -> cubicSkill.validateConditions(this, _owner, member) && member.isInRadius3d(_owner, cubicSkill.getSkill().getCastRange())).sorted(Comparator.comparingInt(L2Character::getCurrentHpPercent)).findFirst().orElse(null);
					break;
				}
				if (cubicSkill.validateConditions(this, _owner, _owner))
				{
					target = _owner;
				}
				break;
			}
		}
		
		if ((target != null) && _template.validateConditions(this, _owner, target))
		{
			return target;
		}
		return null;
	}
	
	private L2Character getTargetByType(CubicSkill cubicSkill)
	{
		switch (cubicSkill.getTargetType())
		{
			case HEAL:
			{
				final L2Party party = _owner.getParty();
				if (party != null)
				{
					return party.getMembers().stream().filter(member -> cubicSkill.validateConditions(this, _owner, member) && member.isInRadius3d(_owner, cubicSkill.getSkill().getCastRange())).sorted(Comparator.comparingInt(L2Character::getCurrentHpPercent)).findFirst().orElse(null);
				}
				if (cubicSkill.validateConditions(this, _owner, _owner))
				{
					return _owner;
				}
				break;
			}
			case MASTER:
			{
				if (cubicSkill.validateConditions(this, _owner, _owner))
				{
					return _owner;
				}
				break;
			}
			case TARGET:
			{
				final L2Object possibleTarget = CubicUtil.selectTargetToAttack(_owner, CubicInstance.this);
				if ((possibleTarget != null) && possibleTarget.isCharacter())
				{
					if (cubicSkill.validateConditions(this, _owner, (L2Character) possibleTarget))
					{
						return (L2Character) possibleTarget;
					}
				}
				break;
			}
		}
		
		return null;
	}
	
	/**
	 * @return the {@link L2Character} that owns this cubic
	 */
	public L2Character getOwner()
	{
		return _owner;
	}
	
	/**
	 * @return the {@link L2Character} that casted this cubic
	 */
	public L2Character getCaster()
	{
		return _caster;
	}
	
	/**
	 * @return {@code true} if cubic is casted from someone else but the owner, {@code false}
	 */
	public boolean isGivenByOther()
	{
		return _caster != _owner;
	}
	
	/**
	 * @return the {@link CubicTemplate} of this cubic
	 */
	public CubicTemplate getTemplate()
	{
		return _template;
	}
}
