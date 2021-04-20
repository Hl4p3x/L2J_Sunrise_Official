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
package l2r.gameserver.model.actor.instance;

import l2r.gameserver.enums.InstanceType;
import l2r.gameserver.instancemanager.AirShipManager;
import l2r.gameserver.model.Location;
import l2r.gameserver.model.actor.templates.L2CharTemplate;
import l2r.gameserver.network.serverpackets.ExAirShipInfo;
import l2r.gameserver.network.serverpackets.ExGetOffAirShip;
import l2r.gameserver.network.serverpackets.ExGetOnAirShip;
import l2r.gameserver.network.serverpackets.ExMoveToLocationAirShip;
import l2r.gameserver.network.serverpackets.ExMoveToLocationInAirShip;
import l2r.gameserver.network.serverpackets.ExStopMoveAirShip;
import l2r.gameserver.network.serverpackets.ExStopMoveInAirShip;
import l2r.gameserver.network.serverpackets.ExValidateLocationInAirShip;
import l2r.gameserver.network.serverpackets.L2GameServerPacket;

/**
 * Flying airships. Very similar to Maktakien boats (see L2BoatInstance) but these do fly :P
 * @author DrHouse, DS
 */
public class L2AirShipInstance extends L2BoatInstance
{
	public L2AirShipInstance(L2CharTemplate template)
	{
		super(template);
		setInstanceType(InstanceType.L2AirShipInstance);
		setIsFlying(true);
	}
	
	@Override
	public boolean isAirShip()
	{
		return true;
	}
	
	public boolean isOwner(L2PcInstance player)
	{
		return false;
	}
	
	public int getOwnerId()
	{
		return 0;
	}
	
	public boolean isCaptain(L2PcInstance player)
	{
		return false;
	}
	
	public int getCaptainId()
	{
		return 0;
	}
	
	public int getHelmObjectId()
	{
		return 0;
	}
	
	public int getHelmItemId()
	{
		return 0;
	}
	
	public boolean setCaptain(L2PcInstance player)
	{
		return false;
	}
	
	public int getFuel()
	{
		return 0;
	}
	
	public void setFuel(int f)
	{
	
	}
	
	public int getMaxFuel()
	{
		return 0;
	}
	
	public void setMaxFuel(int mf)
	{
	
	}
	
	@Override
	public int getId()
	{
		return 0;
	}
	
	@Override
	public L2GameServerPacket movePacket()
	{
		return new ExMoveToLocationAirShip(this);
	}
	
	@Override
	public L2GameServerPacket stopMovePacket()
	{
		return new ExStopMoveAirShip(this);
	}
	
	@Override
	public boolean addPassenger(L2PcInstance player, Location loc)
	{
		if (!super.addPassenger(player, loc))
		{
			return false;
		}
		
		player.setVehicle(this);
		player.setInVehiclePosition(loc);
		player.broadcastPacket(getOnPacket(player, loc));
		player.getKnownList().removeAllKnownObjects();
		player.setXYZ(loc);
		player.revalidateZone(true);
		player.stopMove(null);
		return true;
	}
	
	@Override
	public void deleteMe()
	{
		super.deleteMe();
		AirShipManager.getInstance().removeAirShip(this);
	}
	
	@Override
	public L2GameServerPacket validateLocationPacket(L2PcInstance player)
	{
		return new ExValidateLocationInAirShip(player);
	}
	
	@Override
	public L2GameServerPacket getOnPacket(final L2PcInstance player, final Location location)
	{
		return new ExGetOnAirShip(player, this);
	}
	
	@Override
	public L2GameServerPacket getOffPacket(final L2PcInstance player, final Location location)
	{
		return new ExGetOffAirShip(player, this, location.getX(), location.getY(), location.getZ());
	}
	
	@Override
	public L2GameServerPacket inMovePacket(final L2PcInstance player, final Location src, final Location desc)
	{
		return new ExMoveToLocationInAirShip(player, src, desc);
	}
	
	@Override
	public L2GameServerPacket inStopMovePacket(final L2PcInstance player)
	{
		return new ExStopMoveInAirShip(player, getObjectId());
	}
	
	@Override
	public L2GameServerPacket startPacket()
	{
		return null;
	}
	
	@Override
	public L2GameServerPacket checkLocationPacket()
	{
		return null;
	}
	
	@Override
	public L2GameServerPacket infoPacket()
	{
		return new ExAirShipInfo(this);
	}
}