/*
 * This file is part of the L2J Sunrise project.
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package handlers.playeractions;

import l2r.Config;
import l2r.gameserver.data.sql.BotReportTable;
import l2r.gameserver.handler.IPlayerActionHandler;
import l2r.gameserver.model.actor.instance.L2PcInstance;
import l2r.gameserver.model.holders.ActionDataHolder;

/**
 * Bot Report button player action handler.
 * @author Nik
 */
public final class BotReport implements IPlayerActionHandler
{
	@Override
	public void useAction(L2PcInstance activeChar, ActionDataHolder data, boolean ctrlPressed, boolean shiftPressed)
	{
		if (Config.BOTREPORT_ENABLE)
		{
			BotReportTable.getInstance().reportBot(activeChar);
		}
		else
		{
			activeChar.sendMessage("This feature is disabled.");
		}
	}
}
