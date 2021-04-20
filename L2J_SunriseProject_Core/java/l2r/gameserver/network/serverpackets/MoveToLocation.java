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
package l2r.gameserver.network.serverpackets;

import l2r.Config;
import l2r.gameserver.model.Location;
import l2r.gameserver.model.actor.L2Character;

public final class MoveToLocation extends L2GameServerPacket
{
	private final int _charObjId;
	
	private int _client_z_shift;
	
	private final Location _current;
	private Location _destination;
	
	public MoveToLocation(L2Character cha)
	{
		_charObjId = cha.getObjectId();
		
		_current = cha.getLocation();
		_destination = cha.getDestination();
		
		if (!cha.isFlying())
		{
			_client_z_shift = Config.CLIENT_SHIFTZ;
		}
		if (cha.isInWater())
		{
			_client_z_shift += Config.CLIENT_SHIFTZ;
		}
		
		if (_destination == null)
		{
			_destination = _current;
		}
	}
	
	public MoveToLocation(int objectId, Location from, Location to)
	{
		_charObjId = objectId;
		_current = from;
		_destination = to;
	}
	
	@Override
	protected final void writeImpl()
	{
		writeC(0x2f);
		
		writeD(_charObjId);
		
		writeD(_destination.getX());
		writeD(_destination.getY());
		writeD(_destination.getZ() + _client_z_shift);
		
		writeD(_current.getX());
		writeD(_current.getY());
		writeD(_current.getZ() + _client_z_shift);
	}
}
