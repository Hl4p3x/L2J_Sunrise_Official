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
package l2r.loginserver.network.gameservercon.gameserverpackets;

import l2r.Config;
import l2r.loginserver.GameServerTable;
import l2r.loginserver.GameServerThread;
import l2r.util.network.BaseRecievePacket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author -Wooden-
 */
public class PlayerLogout extends BaseRecievePacket
{
	protected static Logger _log = LoggerFactory.getLogger(PlayerLogout.class);
	
	/**
	 * @param decrypt
	 * @param server
	 */
	public PlayerLogout(byte[] decrypt, GameServerThread server)
	{
		super(decrypt);
		String account = readS();
		
		server.removeAccountOnGameServer(account);
		if (Config.DEBUG)
		{
			_log.info("Player " + account + " logged out from gameserver [" + server.getServerId() + "] " + GameServerTable.getInstance().getServerNameById(server.getServerId()));
		}
		
		server.broadcastToTelnet("Player " + account + " disconnected from GameServer " + server.getServerId());
	}
}
