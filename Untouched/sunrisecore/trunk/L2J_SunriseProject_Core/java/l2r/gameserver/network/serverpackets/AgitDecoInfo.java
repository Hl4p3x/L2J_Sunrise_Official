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

import l2r.gameserver.model.entity.ClanHall;
import l2r.gameserver.model.entity.ClanHall.ClanHallFunction;
import l2r.gameserver.model.entity.clanhall.AuctionableHall;

/**
 * @author Steuf
 */
public class AgitDecoInfo extends L2GameServerPacket
{
	private final AuctionableHall _clanHall;
	private ClanHallFunction _function;
	
	public AgitDecoInfo(AuctionableHall ClanHall)
	{
		_clanHall = ClanHall;
	}
	
	//@formatter:off
	/*
	 * Packet send, must be confirmed
	 	writeC(0xf7);
		writeD(0); // clanhall id
		writeC(0); // FUNC_RESTORE_HP (Fireplace)
		writeC(0); // FUNC_RESTORE_MP (Carpet)
		writeC(0); // FUNC_RESTORE_MP (Statue)
		writeC(0); // FUNC_RESTORE_EXP (Chandelier)
		writeC(0); // FUNC_TELEPORT (Mirror)
		writeC(0); // Crytal
		writeC(0); // Curtain
		writeC(0); // FUNC_ITEM_CREATE (Magic Curtain)
		writeC(0); // FUNC_SUPPORT
		writeC(0); // FUNC_SUPPORT (Flag)
		writeC(0); // Front Platform
		writeC(0); // FUNC_ITEM_CREATE
		writeD(0);
		writeD(0);
	 */
	//@formatter:on
	@Override
	protected final void writeImpl()
	{
		writeC(0xfd);
		writeD(_clanHall.getId()); // clanhall id
		// FUNC_RESTORE_HP
		_function = _clanHall.getFunction(ClanHall.FUNC_RESTORE_HP);
		if ((_function == null) || (_function.getLvl() == 0))
		{
			writeC(0);
		}
		else if (((_clanHall.getGrade() == 0) && (_function.getLvl() < 220)) || ((_clanHall.getGrade() == 1) && (_function.getLvl() < 160)) || ((_clanHall.getGrade() == 2) && (_function.getLvl() < 260)) || ((_clanHall.getGrade() == 3) && (_function.getLvl() < 300)))
		{
			writeC(1);
		}
		else
		{
			writeC(2);
		}
		// FUNC_RESTORE_MP
		_function = _clanHall.getFunction(ClanHall.FUNC_RESTORE_MP);
		if ((_function == null) || (_function.getLvl() == 0))
		{
			writeC(0);
			writeC(0);
		}
		else if ((((_clanHall.getGrade() == 0) || (_clanHall.getGrade() == 1)) && (_function.getLvl() < 25)) || ((_clanHall.getGrade() == 2) && (_function.getLvl() < 30)) || ((_clanHall.getGrade() == 3) && (_function.getLvl() < 40)))
		{
			writeC(1);
			writeC(1);
		}
		else
		{
			writeC(2);
			writeC(2);
		}
		// FUNC_RESTORE_EXP
		_function = _clanHall.getFunction(ClanHall.FUNC_RESTORE_EXP);
		if ((_function == null) || (_function.getLvl() == 0))
		{
			writeC(0);
		}
		else if (((_clanHall.getGrade() == 0) && (_function.getLvl() < 25)) || ((_clanHall.getGrade() == 1) && (_function.getLvl() < 30)) || ((_clanHall.getGrade() == 2) && (_function.getLvl() < 40)) || ((_clanHall.getGrade() == 3) && (_function.getLvl() < 50)))
		{
			writeC(1);
		}
		else
		{
			writeC(2);
		}
		// FUNC_TELEPORT
		_function = _clanHall.getFunction(ClanHall.FUNC_TELEPORT);
		if ((_function == null) || (_function.getLvl() == 0))
		{
			writeC(0);
		}
		else if (_function.getLvl() < 2)
		{
			writeC(1);
		}
		else
		{
			writeC(2);
		}
		writeC(0);
		// CURTAINS
		_function = _clanHall.getFunction(ClanHall.FUNC_DECO_CURTAINS);
		if ((_function == null) || (_function.getLvl() == 0))
		{
			writeC(0);
		}
		else if (_function.getLvl() <= 1)
		{
			writeC(1);
		}
		else
		{
			writeC(2);
		}
		// FUNC_ITEM_CREATE
		_function = _clanHall.getFunction(ClanHall.FUNC_ITEM_CREATE);
		if ((_function == null) || (_function.getLvl() == 0))
		{
			writeC(0);
		}
		else if (((_clanHall.getGrade() == 0) && (_function.getLvl() < 2)) || (_function.getLvl() < 3))
		{
			writeC(1);
		}
		else
		{
			writeC(2);
		}
		// FUNC_SUPPORT
		_function = _clanHall.getFunction(ClanHall.FUNC_SUPPORT);
		if ((_function == null) || (_function.getLvl() == 0))
		{
			writeC(0);
			writeC(0);
		}
		else if (((_clanHall.getGrade() == 0) && (_function.getLvl() < 2)) || ((_clanHall.getGrade() == 1) && (_function.getLvl() < 4)) || ((_clanHall.getGrade() == 2) && (_function.getLvl() < 5)) || ((_clanHall.getGrade() == 3) && (_function.getLvl() < 8)))
		{
			writeC(1);
			writeC(1);
		}
		else
		{
			writeC(2);
			writeC(2);
		}
		// Front Plateform
		_function = _clanHall.getFunction(ClanHall.FUNC_DECO_FRONTPLATEFORM);
		if ((_function == null) || (_function.getLvl() == 0))
		{
			writeC(0);
		}
		else if (_function.getLvl() <= 1)
		{
			writeC(1);
		}
		else
		{
			writeC(2);
		}
		// FUNC_ITEM_CREATE
		_function = _clanHall.getFunction(ClanHall.FUNC_ITEM_CREATE);
		if ((_function == null) || (_function.getLvl() == 0))
		{
			writeC(0);
		}
		else if (((_clanHall.getGrade() == 0) && (_function.getLvl() < 2)) || (_function.getLvl() < 3))
		{
			writeC(1);
		}
		else
		{
			writeC(2);
		}
		writeD(0);
		writeD(0);
	}
}
