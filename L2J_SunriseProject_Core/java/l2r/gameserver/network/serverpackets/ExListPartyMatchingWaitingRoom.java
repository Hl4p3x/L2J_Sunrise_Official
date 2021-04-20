/*
 * Copyright (C) 2004-2020 L2J Server
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

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import l2r.gameserver.instancemanager.InstanceManager;
import l2r.gameserver.instancemanager.MapRegionManager;
import l2r.gameserver.model.PartyMatchWaitingList;
import l2r.gameserver.model.actor.instance.L2PcInstance;

/**
 * ExListPartyMatchingWaitingRoom server packet.
 * @author Zoey76
 */
public class ExListPartyMatchingWaitingRoom extends L2GameServerPacket
{
	
	// Maximum size supported by client 500.
	private static final int TOTAL = 64;
	
	private final int totalMathingPlayers;
	
	private final List<L2PcInstance> players;
	
	public ExListPartyMatchingWaitingRoom(int page, int minLevel, int maxLevel, Set<Integer> classes, String filter)
	{
		final List<L2PcInstance> matchingPlayers = PartyMatchWaitingList.getInstance().findPlayers(minLevel, maxLevel, classes, filter);
		this.totalMathingPlayers = matchingPlayers.size();
		this.players = matchingPlayers.stream() //
		.skip((page - 1) * TOTAL) //
		.limit(page * TOTAL) //
		.collect(Collectors.toList());
	}
	
	@Override
	protected void writeImpl()
	{
		writeC(0xFE);
		writeH(0x36);
		writeD(totalMathingPlayers);
		writeD(players.size());
		for (L2PcInstance player : players)
		{
			writeS(player.getName());
			writeD(player.getActiveClass());
			writeD(player.getLevel());
			writeD(MapRegionManager.getInstance().getMapRegion(player).getBbs());
			final Map<Integer, Long> instances = InstanceManager.getInstance().getAllInstanceTimes(player.getObjectId());
			writeD(instances.size());
			for (Integer id : instances.keySet())
			{
				writeD(id);
			}
		}
	}
}