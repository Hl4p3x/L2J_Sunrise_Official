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

import l2r.gameserver.communitybbs.BoardsManager;

/**
 * Format SSSSSS
 * @author -Wooden-
 */
public final class RequestBBSwrite extends L2GameClientPacket
{
	private static final String _C__24_REQUESTBBSWRITE = "[C] 24 RequestBBSwrite";
	private String _url;
	private String _arg1;
	private String _arg2;
	private String _arg3;
	private String _arg4;
	private String _arg5;
	
	@Override
	protected final void readImpl()
	{
		_url = readS();
		_arg1 = readS();
		_arg2 = readS();
		_arg3 = readS();
		_arg4 = readS();
		_arg5 = readS();
	}
	
	@Override
	protected final void runImpl()
	{
		BoardsManager.getInstance().handleWriteCommands(getClient(), _url, _arg1, _arg2, _arg3, _arg4, _arg5);
	}
	
	@Override
	public final String getType()
	{
		return _C__24_REQUESTBBSWRITE;
	}
}