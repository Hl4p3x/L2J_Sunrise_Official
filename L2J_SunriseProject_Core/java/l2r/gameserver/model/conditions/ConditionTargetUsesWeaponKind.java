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

import l2r.gameserver.model.items.L2Weapon;
import l2r.gameserver.model.stats.Env;

/**
 * The Class ConditionTargetUsesWeaponKind.
 * @author mkizub
 */
public class ConditionTargetUsesWeaponKind extends Condition
{
	private final int _weaponMask;
	
	/**
	 * Instantiates a new condition target uses weapon kind.
	 * @param weaponMask the weapon mask
	 */
	public ConditionTargetUsesWeaponKind(int weaponMask)
	{
		_weaponMask = weaponMask;
	}
	
	@Override
	public boolean testImpl(Env env)
	{
		if (env.getTarget() == null)
		{
			return false;
		}
		
		L2Weapon item = env.getTarget().getActiveWeaponItem();
		if (item == null)
		{
			return false;
		}
		
		return (item.getItemType().mask() & _weaponMask) != 0;
	}
}
