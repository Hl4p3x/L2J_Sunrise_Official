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

import l2r.gameserver.model.skills.L2Skill;
import l2r.gameserver.model.stats.Env;

/**
 * The Class ConditionTargetActiveSkillId.
 */
public class ConditionTargetActiveSkillId extends Condition
{
	private final int _skillId;
	private final int _skillLevel;
	
	/**
	 * Instantiates a new condition target active skill id.
	 * @param skillId the skill id
	 */
	public ConditionTargetActiveSkillId(int skillId)
	{
		_skillId = skillId;
		_skillLevel = -1;
	}
	
	/**
	 * Instantiates a new condition target active skill id.
	 * @param skillId the skill id
	 * @param skillLevel the skill level
	 */
	public ConditionTargetActiveSkillId(int skillId, int skillLevel)
	{
		_skillId = skillId;
		_skillLevel = skillLevel;
	}
	
	@Override
	public boolean testImpl(Env env)
	{
		for (L2Skill sk : env.getTarget().getAllSkills())
		{
			if (sk != null)
			{
				if (sk.getId() == _skillId)
				{
					if ((_skillLevel == -1) || (_skillLevel <= sk.getLevel()))
					{
						return true;
					}
				}
			}
		}
		return false;
	}
}
