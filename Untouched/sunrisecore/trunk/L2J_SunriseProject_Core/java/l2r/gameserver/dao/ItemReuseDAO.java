/*
 * Copyright (C) 2004-2017 L2J Server
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
package l2r.gameserver.dao;

import l2r.gameserver.model.actor.instance.L2PcInstance;

/**
 * Item Reuse DAO interface.
 * @author vGodFather
 */
public interface ItemReuseDAO
{
	/**
	 * Restores the item reuse for the given player.
	 * @param player the player
	 */
	void load(L2PcInstance player);
	
	/**
	 * Inserts the item reuse for the given player.
	 * @param player the player
	 */
	void insert(L2PcInstance player);
	
	/**
	 * Deletes the item reuse from the player.
	 * @param player the player
	 */
	void delete(L2PcInstance player);
}
