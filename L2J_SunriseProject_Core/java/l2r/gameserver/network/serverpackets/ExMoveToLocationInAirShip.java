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

import l2r.gameserver.model.Location;
import l2r.gameserver.model.actor.instance.L2PcInstance;

public class ExMoveToLocationInAirShip extends L2GameServerPacket
{
	private final int _charObjId;
	private final int _airShipId;
	private final Location _origin;
	private final Location _destination;
	
	public ExMoveToLocationInAirShip(final L2PcInstance cha, final Location origin, final Location destination)
	{
		this._charObjId = cha.getObjectId();
		this._airShipId = cha.getAirShip().getObjectId();
		this._origin = origin;
		this._destination = destination;
	}
	
	@Override
	protected void writeImpl()
	{
		writeC(0xfe);
		writeH(0x6D);
		writeD(_charObjId);
		writeD(_airShipId);
		writeLoc(_destination);
		writeLoc(_origin);
	}
}