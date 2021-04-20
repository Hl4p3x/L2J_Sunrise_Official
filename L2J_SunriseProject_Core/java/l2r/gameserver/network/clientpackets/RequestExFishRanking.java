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

import l2r.Config;
import l2r.gameserver.instancemanager.FishingChampionshipManager;
import l2r.gameserver.model.actor.instance.L2PcInstance;
import l2r.gameserver.network.serverpackets.NpcHtmlMessage;

import gr.sr.configsEngine.configs.impl.LeaderboardsConfigs;
import gr.sr.leaderboards.FishermanLeaderboard;

/**
 * Format: (ch) just a trigger
 * @author -Wooden-
 */
public final class RequestExFishRanking extends L2GameClientPacket
{
	private static final String _C__D0_18_REQUESTEXFISHRANKING = "[C] D0:18 RequestExFishRanking";
	
	@Override
	protected void readImpl()
	{
		// trigger
	}
	
	@Override
	protected void runImpl()
	{
		final L2PcInstance player = getClient().getActiveChar();
		if (player == null)
		{
			return;
		}
		
		if (Config.ALT_FISH_CHAMPIONSHIP_ENABLED)
		{
			FishingChampionshipManager.getInstance().showMidResult(player);
		}
		
		if (LeaderboardsConfigs.RANK_FISHERMAN_ENABLED)
		{
			NpcHtmlMessage htm = new NpcHtmlMessage();
			htm.setHtml(FishermanLeaderboard.getInstance().showHtm(getClient().getActiveChar().getObjectId()));
			getClient().getActiveChar().sendPacket(htm);
		}
		else
		{
			_log.info("C5: RequestExFishRanking");
		}
	}
	
	@Override
	public String getType()
	{
		return _C__D0_18_REQUESTEXFISHRANKING;
	}
}