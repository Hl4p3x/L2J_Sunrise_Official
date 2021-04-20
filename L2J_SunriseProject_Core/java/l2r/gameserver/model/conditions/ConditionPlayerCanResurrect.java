/*
 * Copyright (C) 2004-2015 L2J Server
 *
 * This file is part of L2J Server.
 *
 * L2J Server is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * L2J Server is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package l2r.gameserver.model.conditions;

import l2r.gameserver.enums.ZoneIdType;
import l2r.gameserver.instancemanager.SiegeManager;
import l2r.gameserver.instancemanager.TerritoryWarManager;
import l2r.gameserver.model.L2Clan;
import l2r.gameserver.model.actor.L2Character;
import l2r.gameserver.model.actor.L2Summon;
import l2r.gameserver.model.actor.instance.L2PcInstance;
import l2r.gameserver.model.entity.Siege;
import l2r.gameserver.model.skills.L2Skill;
import l2r.gameserver.model.stats.Env;
import l2r.gameserver.network.SystemMessageId;
import l2r.gameserver.network.serverpackets.SystemMessage;

/**
 * Player Can Resurrect condition implementation.
 * @author UnAfraid
 */
public class ConditionPlayerCanResurrect extends Condition
{
	private final boolean _val;
	
	public ConditionPlayerCanResurrect(boolean val)
	{
		_val = val;
	}
	
	@Override
	public boolean testImpl(Env env)
	{
		L2Skill skill = env.getSkill();
		L2Character effector = env.getCharacter();
		L2Character effected = env.getTarget();
		
		// Need skill rework for fix that properly
		if (skill.getAffectRange() > 0)
		{
			return true;
		}
		if (effected == null)
		{
			return false;
		}
		boolean canResurrect = true;
		
		if (effected.isPlayer())
		{
			final L2PcInstance player = effected.getActingPlayer();
			if (!player.isDead())
			{
				canResurrect = false;
				if (effector.isPlayer())
				{
					final SystemMessage msg = SystemMessage.getSystemMessage(SystemMessageId.S1_CANNOT_BE_USED);
					msg.addSkillName(skill);
					effector.sendPacket(msg);
				}
			}
			else if (player.isResurrectionBlocked())
			{
				canResurrect = false;
				if (effector.isPlayer())
				{
					effector.sendPacket(SystemMessageId.REJECT_RESURRECTION);
				}
			}
			else if (player.isReviveRequested())
			{
				canResurrect = false;
				if (effector.isPlayer())
				{
					effector.sendPacket(SystemMessageId.RES_HAS_ALREADY_BEEN_PROPOSED);
				}
			}
			else if (skill.getId() != 2393) // Blessed Scroll of Battlefield Resurrection
			{
				if (player.isInsideZone(ZoneIdType.SIEGE) && effector.isInsideZone(ZoneIdType.SIEGE))
				{
					final Siege siege = SiegeManager.getInstance().getSiege(player);
					final boolean twWar = TerritoryWarManager.getInstance().isTWInProgress();
					if ((siege != null) && siege.isInProgress())
					{
						final L2Clan clan = player.getClan();
						if (clan == null)
						{
							canResurrect = false;
							if (effector.isPlayer())
							{
								effector.sendPacket(SystemMessageId.CANNOT_BE_RESURRECTED_DURING_SIEGE);
							}
						}
						else if (siege.checkIsDefender(clan) && (siege.getControlTowerCount() == 0))
						{
							canResurrect = false;
							if (effector.isPlayer())
							{
								effector.sendPacket(SystemMessageId.TOWER_DESTROYED_NO_RESURRECTION);
							}
						}
						else if (siege.checkIsAttacker(clan) && (siege.getAttackerClan(clan).getNumFlags() == 0))
						{
							canResurrect = false;
							if (effector.isPlayer())
							{
								effector.sendPacket(SystemMessageId.NO_RESURRECTION_WITHOUT_BASE_CAMP);
							}
						}
						else
						{
							canResurrect = false;
							if (effector.isPlayer())
							{
								effector.sendPacket(SystemMessageId.CANNOT_BE_RESURRECTED_DURING_SIEGE);
							}
						}
					}
					else if (twWar)
					{
						final L2Clan clan = player.getClan();
						if (clan == null)
						{
							canResurrect = false;
							if (effector.isPlayer())
							{
								effector.sendPacket(SystemMessageId.CANNOT_BE_RESURRECTED_DURING_SIEGE);
							}
						}
						else if (TerritoryWarManager.getInstance().getHQForClan(player.getClan()) == null)
						{
							canResurrect = false;
							if (effector.isPlayer())
							{
								effector.sendPacket(SystemMessageId.NO_RESURRECTION_WITHOUT_BASE_CAMP);
							}
						}
						else
						{
							canResurrect = false;
							if (effector.isPlayer())
							{
								effector.sendPacket(SystemMessageId.CANNOT_BE_RESURRECTED_DURING_SIEGE);
							}
						}
					}
				}
			}
		}
		else if (effected.isSummon())
		{
			final L2Summon summon = (L2Summon) effected;
			final L2PcInstance player = summon.getOwner();
			if (!summon.isDead())
			{
				canResurrect = false;
				if (effector.isPlayer())
				{
					final SystemMessage msg = SystemMessage.getSystemMessage(SystemMessageId.S1_CANNOT_BE_USED);
					msg.addSkillName(skill);
					effector.sendPacket(msg);
				}
			}
			else if (summon.isResurrectionBlocked())
			{
				canResurrect = false;
				if (effector.isPlayer())
				{
					effector.sendPacket(SystemMessageId.REJECT_RESURRECTION);
				}
			}
			else if ((player != null) && player.isRevivingPet())
			{
				canResurrect = false;
				if (effector.isPlayer())
				{
					effector.sendPacket(SystemMessageId.RES_HAS_ALREADY_BEEN_PROPOSED); // Resurrection is already been proposed.
				}
			}
		}
		return (_val == canResurrect);
	}
}