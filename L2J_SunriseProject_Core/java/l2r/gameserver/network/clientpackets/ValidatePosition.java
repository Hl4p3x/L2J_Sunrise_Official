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

import l2r.gameserver.GeoData;
import l2r.gameserver.model.Location;
import l2r.gameserver.model.actor.instance.L2PcInstance;

/**
 * This class ...
 * @version $Revision: 1.13.4.7 $ $Date: 2005/03/27 15:29:30 $
 */
public class ValidatePosition extends L2GameClientPacket
{
	private static final String _C__59_VALIDATEPOSITION = "[C] 59 ValidatePosition";
	
	private final Location _loc = new Location();
	
	private int _boatObjectId;
	private Location _lastClientPosition;
	private Location _lastServerPosition;
	
	@Override
	protected void readImpl()
	{
		_loc.setX(readD());
		_loc.setY(readD());
		_loc.setZ(readD());
		_loc.setHeading(readD());
		
		_boatObjectId = readD();
	}
	
	@Override
	protected void runImpl()
	{
		final L2PcInstance activeChar = getClient().getActiveChar();
		if ((activeChar == null) || activeChar.isTeleporting() || activeChar.isCastingNow() || activeChar.inObserverMode())
		{
			return;
		}
		
		_lastClientPosition = activeChar.getLastClientPosition();
		_lastServerPosition = activeChar.getLastServerPosition();
		
		if (_lastClientPosition == null)
		{
			_lastClientPosition = activeChar.getLocation();
		}
		if (_lastServerPosition == null)
		{
			_lastServerPosition = activeChar.getLocation();
		}
		
		if ((activeChar.getX() == 0) && (activeChar.getY() == 0) && (activeChar.getZ() == 0))
		{
			correctPosition(activeChar);
			return;
		}
		
		if (activeChar.isFlyingMounted())
		{
			if (_loc.getX() > -166168)
			{
				activeChar.untransform();
				return;
			}
			
			if ((_loc.getZ() <= 0) || (_loc.getZ() >= 6000))
			{
				activeChar.teleToLocation(activeChar.getLocation().setZAndGet(Math.min(5950, Math.max(50, _loc.getZ()))));
				return;
			}
		}
		
		double diff = Math.sqrt(activeChar.getPlanDistanceSq(_loc.getX(), _loc.getY()));
		int dz = Math.abs(_loc.getZ() - activeChar.getZ());
		int h = _lastServerPosition.getZ() - activeChar.getZ();
		
		if (_boatObjectId > 0)
		{
			if (activeChar.isInBoat())
			{
				activeChar.setHeading(_loc.getHeading());
				activeChar.getBoat().validateLocationPacket(activeChar);
			}
			activeChar.setLastClientPosition(_loc.setH(activeChar.getHeading()));
			activeChar.setLastServerPosition(activeChar.getLocation());
			return;
		}
		
		if (activeChar.isFalling())
		{
			diff = 0;
			dz = 0;
			h = 0;
		}
		
		if (h >= 256)
		{
			activeChar.falling(h);
		}
		else if (dz >= (activeChar.isFlying() ? 1024 : 512))
		{
			if (activeChar.getIncorrectValidateCount() >= 3)
			{
				activeChar.teleToClosestTown();
			}
			else
			{
				activeChar.teleToLocation(activeChar.getLocation());
				activeChar.setIncorrectValidateCount(activeChar.getIncorrectValidateCount() + 1);
			}
		}
		else if (dz >= 256)
		{
			activeChar.validateLocation(0);
		}
		else if ((_loc.getZ() < -30000) || (_loc.getZ() > 30000))
		{
			if (activeChar.getIncorrectValidateCount() >= 3)
			{
				activeChar.teleToClosestTown();
			}
			else
			{
				correctPosition(activeChar);
				activeChar.setIncorrectValidateCount(activeChar.getIncorrectValidateCount() + 1);
			}
		}
		else if (diff > 1024)
		{
			if (activeChar.getIncorrectValidateCount() >= 3)
			{
				activeChar.teleToClosestTown();
			}
			else
			{
				activeChar.teleToLocation(activeChar.getLocation());
				activeChar.setIncorrectValidateCount(activeChar.getIncorrectValidateCount() + 1);
			}
		}
		else if (diff > 256)
		{
			activeChar.validateLocation(1);
		}
		else
		{
			activeChar.setIncorrectValidateCount(0);
		}
		
		activeChar.setLastClientPosition(_loc.setH(activeChar.getHeading()));
		activeChar.setLastServerPosition(activeChar.getLocation());
	}
	
	private void correctPosition(L2PcInstance activeChar)
	{
		if (activeChar.isGM())
		{
			activeChar.sendMessage("Server loc: " + activeChar.getLocation());
			activeChar.sendMessage("Correcting position...");
		}
		if ((_lastServerPosition.getX() != 0) && (_lastServerPosition.getY() != 0) && (_lastServerPosition.getZ() != 0))
		{
			if (GeoData.getInstance().getNSWE(_lastServerPosition.getX(), _lastServerPosition.getY(), _lastServerPosition.getZ(), activeChar.getInstanceId()) == GeoData.getInstance().getNSWE_ALL())
			{
				activeChar.teleToLocation(_lastServerPosition);
			}
			else
			{
				activeChar.teleToClosestTown();
			}
		}
		else if ((_lastClientPosition.getX() != 0) && (_lastClientPosition.getY() != 0) && (_lastClientPosition.getZ() != 0))
		{
			if (GeoData.getInstance().getNSWE(_lastClientPosition.getX(), _lastClientPosition.getY(), _lastClientPosition.getZ(), activeChar.getInstanceId()) == GeoData.getInstance().getNSWE_ALL())
			{
				activeChar.teleToLocation(_lastClientPosition);
			}
			else
			{
				activeChar.teleToClosestTown();
			}
		}
		else
		{
			activeChar.teleToClosestTown();
		}
	}
	
	@Override
	public String getType()
	{
		return _C__59_VALIDATEPOSITION;
	}
}
