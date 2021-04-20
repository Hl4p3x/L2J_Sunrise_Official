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
package l2r.gameserver.model.actor.stat;

import l2r.Config;
import l2r.gameserver.data.xml.impl.ExperienceData;
import l2r.gameserver.data.xml.impl.PetData;
import l2r.gameserver.enums.ZoneIdType;
import l2r.gameserver.instancemanager.ZoneManager;
import l2r.gameserver.model.actor.L2Playable;
import l2r.gameserver.model.actor.instance.L2PcInstance;
import l2r.gameserver.model.actor.instance.L2PetInstance;
import l2r.gameserver.model.events.EventDispatcher;
import l2r.gameserver.model.events.impl.character.playable.OnPlayableExpChanged;
import l2r.gameserver.model.events.returns.TerminateReturn;
import l2r.gameserver.model.zone.type.L2SwampZone;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PlayableStat extends CharStat
{
	protected static final Logger _log = LoggerFactory.getLogger(PlayableStat.class);
	
	public PlayableStat(L2Playable activeChar)
	{
		super(activeChar);
	}
	
	public boolean addExp(long value)
	{
		if (((getExp() + value) < 0) || ((value > 0) && (getExp() == (getExpForLevel(getMaxLevel()) - 1))))
		{
			return true;
		}
		
		// vGodFather moved this after first exp check
		final TerminateReturn term = EventDispatcher.getInstance().notifyEvent(new OnPlayableExpChanged(getActiveChar(), getExp(), getExp() + value), getActiveChar(), TerminateReturn.class);
		if ((term != null) && term.terminate())
		{
			return false;
		}
		
		if ((getExp() + value) >= getExpForLevel(getMaxLevel()))
		{
			value = getExpForLevel(getMaxLevel()) - 1 - getExp();
		}
		
		setExp(getExp() + value);
		
		byte minimumLevel = 1;
		if (getActiveChar() instanceof L2PetInstance)
		{
			// get minimum level from L2NpcTemplate
			minimumLevel = (byte) PetData.getInstance().getPetMinLevel(((L2PetInstance) getActiveChar()).getTemplate().getId());
		}
		
		byte level = minimumLevel; // minimum level
		
		for (byte tmp = level; tmp <= getMaxLevel(); tmp++)
		{
			if (getExp() >= getExpForLevel(tmp))
			{
				continue;
			}
			level = --tmp;
			break;
		}
		if ((level != getLevel()) && (level >= minimumLevel))
		{
			addLevel((byte) (level - getLevel()), value);
		}
		
		return true;
	}
	
	public boolean removeExp(long value)
	{
		if ((getExp() - value) < 0)
		{
			value = getExp() - 1;
		}
		
		setExp(getExp() - value);
		
		byte minimumLevel = 1;
		if (getActiveChar() instanceof L2PetInstance)
		{
			// get minimum level from L2NpcTemplate
			minimumLevel = (byte) PetData.getInstance().getPetMinLevel(((L2PetInstance) getActiveChar()).getTemplate().getId());
		}
		byte level = minimumLevel;
		
		for (byte tmp = level; tmp <= getMaxLevel(); tmp++)
		{
			if (getExp() >= getExpForLevel(tmp))
			{
				continue;
			}
			level = --tmp;
			break;
		}
		if ((level != getLevel()) && (level >= minimumLevel))
		{
			addLevel((byte) (level - getLevel()), value);
		}
		return true;
	}
	
	public boolean addExpAndSp(long addToExp, int addToSp)
	{
		boolean expAdded = false;
		boolean spAdded = false;
		if (addToExp >= 0)
		{
			expAdded = addExp(addToExp);
		}
		if (addToSp >= 0)
		{
			spAdded = addSp(addToSp);
		}
		
		return expAdded || spAdded;
	}
	
	public boolean removeExpAndSp(long removeExp, int removeSp)
	{
		boolean expRemoved = false;
		boolean spRemoved = false;
		if (removeExp > 0)
		{
			expRemoved = removeExp(removeExp);
		}
		if (removeSp > 0)
		{
			spRemoved = removeSp(removeSp);
		}
		
		return expRemoved || spRemoved;
	}
	
	public boolean addLevel(byte value)
	{
		return addLevel(value, 0);
	}
	
	public boolean addLevel(byte value, long exp)
	{
		if ((getLevel() + value) > (getMaxLevel() - 1))
		{
			if (getLevel() < (getMaxLevel() - 1))
			{
				value = (byte) (getMaxLevel() - 1 - getLevel());
			}
			else
			{
				return false;
			}
		}
		
		byte tmpLvl = getActiveChar().isPlayer() && !getActiveChar().isGM() ? getLevel() : -1;
		
		boolean levelIncreased = ((getLevel() + value) > getLevel());
		value += getLevel();
		setLevel(value);
		
		// vGodFather: just in case to see what happens
		if ((tmpLvl > 84) && (getLevel() <= 2))
		{
			_log.warn("Player: " + getActiveChar().getName() + " abnormally deleveled to: " + getLevel() + "(" + exp + ")", new IllegalArgumentException());
			setLevel(tmpLvl);
			return false;
		}
		
		// Sync up exp with current level
		if ((getExp() >= getExpForLevel(getLevel() + 1)) || (getExpForLevel(getLevel()) > getExp()))
		{
			setExp(getExpForLevel(getLevel()));
		}
		
		if (!levelIncreased && (getActiveChar() instanceof L2PcInstance) && !((L2PcInstance) (getActiveChar())).isGM() && Config.DECREASE_SKILL_LEVEL)
		{
			((L2PcInstance) (getActiveChar())).checkPlayerSkills();
		}
		
		if (!levelIncreased)
		{
			return false;
		}
		
		getActiveChar().getStatus().setCurrentHp(getActiveChar().getStat().getMaxHp());
		getActiveChar().getStatus().setCurrentMp(getActiveChar().getStat().getMaxMp());
		
		return true;
	}
	
	public boolean addSp(int value)
	{
		if (value < 0)
		{
			_log.warn("wrong usage");
			return false;
		}
		int currentSp = getSp();
		if (currentSp == Integer.MAX_VALUE)
		{
			return false;
		}
		
		if (currentSp > (Integer.MAX_VALUE - value))
		{
			value = Integer.MAX_VALUE - currentSp;
		}
		
		setSp(currentSp + value);
		return true;
	}
	
	public boolean removeSp(int value)
	{
		int currentSp = getSp();
		if (currentSp < value)
		{
			value = currentSp;
		}
		setSp(getSp() - value);
		return true;
	}
	
	public long getExpForLevel(int level)
	{
		return level;
	}
	
	@Override
	public double getRunSpeed()
	{
		if (getActiveChar().isInsideZone(ZoneIdType.SWAMP))
		{
			final L2SwampZone zone = ZoneManager.getInstance().getZone(getActiveChar(), L2SwampZone.class);
			if (zone != null)
			{
				return super.getRunSpeed() * zone.getMoveBonus();
			}
		}
		return super.getRunSpeed();
	}
	
	@Override
	public double getWalkSpeed()
	{
		if (getActiveChar().isInsideZone(ZoneIdType.SWAMP))
		{
			final L2SwampZone zone = ZoneManager.getInstance().getZone(getActiveChar(), L2SwampZone.class);
			if (zone != null)
			{
				return super.getWalkSpeed() * zone.getMoveBonus();
			}
		}
		return super.getWalkSpeed();
	}
	
	@Override
	public L2Playable getActiveChar()
	{
		return (L2Playable) super.getActiveChar();
	}
	
	public int getMaxLevel()
	{
		return ExperienceData.getInstance().getMaxLevel();
	}
}
