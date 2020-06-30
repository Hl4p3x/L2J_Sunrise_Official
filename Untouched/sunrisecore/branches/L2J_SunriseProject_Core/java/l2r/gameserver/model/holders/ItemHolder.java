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
package l2r.gameserver.model.holders;

import l2r.gameserver.model.interfaces.IIdentifiable;

/**
 * Holder for item id, count, fromMob.
 * @author UnAfraid, vGodFather
 */
public class ItemHolder implements IIdentifiable
{
	private final int _id;
	private final long _count;
	private boolean _fromMob = false;
	
	public ItemHolder(int id, long count)
	{
		_id = id;
		_count = count;
		_fromMob = false;
	}
	
	public ItemHolder(int id, long count, boolean fromMob)
	{
		_id = id;
		_count = count;
		_fromMob = fromMob;
	}
	
	/**
	 * @return the item/object identifier.
	 */
	@Override
	public int getId()
	{
		return _id;
	}
	
	/**
	 * @return the item count.
	 */
	public long getCount()
	{
		return _count;
	}
	
	public boolean isFromMob()
	{
		return _fromMob;
	}
	
	public void setFromMob(boolean fromMob)
	{
		_fromMob = fromMob;
	}
	
	@Override
	public String toString()
	{
		return getClass().getSimpleName() + ": Id: " + _id + " Count: " + _count;
	}
}
