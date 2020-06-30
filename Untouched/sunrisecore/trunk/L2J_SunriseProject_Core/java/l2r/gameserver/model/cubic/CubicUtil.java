/*
 * Copyright (C) L2J Sunrise
 * This file is part of L2J Sunrise.
 */
package l2r.gameserver.model.cubic;

import java.util.List;

import l2r.gameserver.GeoData;
import l2r.gameserver.instancemanager.DuelManager;
import l2r.gameserver.model.L2Party;
import l2r.gameserver.model.actor.L2Character;
import l2r.gameserver.model.actor.instance.L2PcInstance;
import l2r.gameserver.taskmanager.AttackStanceTaskManager;

/**
 * @author vGodFather
 */
public class CubicUtil
{
	public static L2Character selectTargetToAttack(final L2PcInstance owner, final CubicInstance cubic)
	{
		try
		{
			if (owner.getTarget() == null)
			{
				return null;
			}
			
			if (!owner.getTarget().isCharacter())
			{
				return null;
			}
			
			final L2Character ownerTarget = (L2Character) owner.getTarget();
			if ((ownerTarget == null) || owner.equals(ownerTarget) || owner.equals(ownerTarget.getActingPlayer()))
			{
				return null;
			}
			
			if (!GeoData.getInstance().canSeeTarget(owner, ownerTarget))
			{
				return null;
			}
			
			if (!AttackStanceTaskManager.getInstance().hasAttackStanceTask(owner))
			{
				return null;
			}
			
			if (!AttackStanceTaskManager.getInstance().hasAttackStanceTask(ownerTarget))
			{
				return null;
			}
			
			if (!ownerTarget.isDead() && ownerTarget.isAutoAttackable(owner))
			{
				return ownerTarget;
			}
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
		return null;
	}
	
	public static L2Character selectTargetToHeal(final L2PcInstance owner, final CubicInstance cubic)
	{
		L2Character target = null;
		
		double percentleft = 0;
		// if (cubic.getTemplate().getTargetType())
		// {
		// percentleft = cubic.getCubTemplate().getTargetType().getArg(0); // minimum hp
		// }
		// else
		// {
		// percentleft = 100.0d;
		// }
		
		L2Party party = owner.getParty();
		
		// if owner is in a duel but not in a party duel, then it is the same as he does not have a party
		if (owner.isInDuel())
		{
			if (!DuelManager.getInstance().getDuel(owner.getDuelId()).isPartyDuel())
			{
				party = null;
			}
		}
		
		if ((party != null) && !owner.isInOlympiadMode())
		{
			List<L2PcInstance> partyList = party.getMembers();
			for (L2Character partyMember : partyList)
			{
				if (!partyMember.isDead())
				{
					// if party member not dead, check if he is in castrange of heal cubic
					if (isInCubicRange(owner, partyMember))
					{
						// member is in cubic casting range, check if he need heal and if he have
						// the lowest HP
						if (partyMember.getCurrentHp() < partyMember.getMaxHp())
						{
							if (percentleft > (partyMember.getCurrentHp() / partyMember.getMaxHp()))
							{
								percentleft = (partyMember.getCurrentHp() / partyMember.getMaxHp());
								target = partyMember;
							}
						}
					}
				}
				if (partyMember.getSummon() != null)
				{
					if (partyMember.getSummon().isDead())
					{
						continue;
					}
					
					// if party member's pet not dead, check if it is in castrange of heal cubic
					if (!isInCubicRange(owner, partyMember.getSummon()))
					{
						continue;
					}
					
					// member's pet is in cubic casting range, check if he need heal and if he have
					// the lowest HP
					if (partyMember.getSummon().getCurrentHp() < partyMember.getSummon().getMaxHp())
					{
						if (percentleft > (partyMember.getSummon().getCurrentHp() / partyMember.getSummon().getMaxHp()))
						{
							percentleft = (partyMember.getSummon().getCurrentHp() / partyMember.getSummon().getMaxHp());
							target = partyMember.getSummon();
						}
					}
				}
			}
		}
		else
		{
			if (owner.getCurrentHp() < owner.getMaxHp())
			{
				percentleft = (owner.getCurrentHp() / owner.getMaxHp());
				target = owner;
			}
			if (owner.getSummon() != null)
			{
				if (!owner.getSummon().isDead() && (owner.getSummon().getCurrentHp() < owner.getSummon().getMaxHp()) && (percentleft > (owner.getSummon().getCurrentHp() / owner.getSummon().getMaxHp())) && isInCubicRange(owner, owner.getSummon()))
				{
					target = owner.getSummon();
				}
			}
		}
		
		return target;
	}
	
	public static boolean isInCubicRange(L2PcInstance owner, L2Character target)
	{
		if ((owner == null) || (target == null))
		{
			return false;
		}
		
		final double x = (owner.getX() - target.getX());
		final double y = (owner.getY() - target.getY());
		final double z = (owner.getZ() - target.getZ());
		
		return (((x * x) + (y * y) + (z * z)) <= 810000); // range 900 x 900 = 810000
	}
}
