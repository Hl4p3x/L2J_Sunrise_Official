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

package l2r.gameserver.network.clientpackets;

import l2r.gameserver.instancemanager.CHSiegeManager;
import l2r.gameserver.instancemanager.CastleManager;
import l2r.gameserver.model.entity.Castle;
import l2r.gameserver.model.entity.clanhall.SiegableHall;
import l2r.gameserver.network.serverpackets.SiegeAttackerList;

/**
 * This class ...
 * @version $Revision: 1.3.4.2 $ $Date: 2005/03/27 15:29:30 $
 */
public final class RequestSiegeAttackerList extends L2GameClientPacket
{
	private static final String _C__AB_RequestSiegeAttackerList = "[C] AB RequestSiegeAttackerList";
	
	private int _castleId;
	
	@Override
	protected void readImpl()
	{
		_castleId = readD();
	}
	
	@Override
	protected void runImpl()
	{
		Castle castle = CastleManager.getInstance().getCastleById(_castleId);
		if (castle != null)
		{
			SiegeAttackerList sal = new SiegeAttackerList(castle);
			sendPacket(sal);
		}
		else
		{
			SiegableHall hall = CHSiegeManager.getInstance().getSiegableHall(_castleId);
			if (hall != null)
			{
				SiegeAttackerList sal = new SiegeAttackerList(hall);
				sendPacket(sal);
			}
		}
	}
	
	@Override
	public String getType()
	{
		return _C__AB_RequestSiegeAttackerList;
	}
}
