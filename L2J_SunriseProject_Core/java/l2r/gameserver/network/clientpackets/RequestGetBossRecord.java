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

import java.util.Map;

import l2r.gameserver.instancemanager.RaidBossPointsManager;
import l2r.gameserver.model.actor.instance.L2PcInstance;
import l2r.gameserver.network.serverpackets.ExGetBossRecord;

/**
 * Format: (ch) d
 * @author -Wooden-
 */
public class RequestGetBossRecord extends L2GameClientPacket
{
	private static final String _C__D0_40_REQUESTGETBOSSRECORD = "[C] D0:40 RequestGetBossRecord";
	private int _bossId;
	
	@Override
	protected void readImpl()
	{
		_bossId = readD();
	}
	
	@Override
	protected void runImpl()
	{
		L2PcInstance activeChar = getClient().getActiveChar();
		if (activeChar == null)
		{
			return;
		}
		
		if (_bossId != 0)
		{
			_log.info("C5: RequestGetBossRecord: d: " + _bossId + " ActiveChar: " + activeChar); // should be always 0, log it if isnt 0 for furture research
		}
		
		int points = RaidBossPointsManager.getInstance().getPointsByOwnerId(activeChar.getObjectId());
		int ranking = RaidBossPointsManager.getInstance().calculateRanking(activeChar.getObjectId());
		
		Map<Integer, Integer> list = RaidBossPointsManager.getInstance().getList(activeChar);
		
		// trigger packet
		activeChar.sendPacket(new ExGetBossRecord(ranking, points, list));
	}
	
	@Override
	public String getType()
	{
		return _C__D0_40_REQUESTGETBOSSRECORD;
	}
	
	@Override
	protected boolean triggersOnActionRequest()
	{
		return false;
	}
}