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

import l2r.Config;
import l2r.gameserver.enums.Race;
import l2r.gameserver.model.actor.templates.L2NpcTemplate;
import l2r.gameserver.model.base.PlayerClass;
import l2r.gameserver.model.quest.Quest;
import l2r.gameserver.model.quest.QuestState;

public final class L2VillageMasterKamaelInstance extends L2VillageMasterInstance
{
	/**
	 * Creates a village master.
	 * @param template the village master NPC template
	 */
	public L2VillageMasterKamaelInstance(L2NpcTemplate template)
	{
		super(template);
	}
	
	@Override
	protected final String getSubClassMenu(Race pRace)
	{
		if (Config.ALT_GAME_SUBCLASS_EVERYWHERE || (pRace == Race.KAMAEL))
		{
			return "data/html/villagemaster/SubClass.htm";
		}
		
		return "data/html/villagemaster/SubClass_NoKamael.htm";
	}
	
	@Override
	protected final String getSubClassFail()
	{
		return "data/html/villagemaster/SubClass_Fail_Kamael.htm";
	}
	
	@Override
	protected final boolean checkQuests(L2PcInstance player)
	{
		// Noble players can add subbclasses without quests
		if (player.isNoble())
		{
			return true;
		}
		
		QuestState qs = player.getQuestState(Quest.FATES_WHISPER);
		if ((qs == null) || !qs.isCompleted())
		{
			return false;
		}
		
		qs = player.getQuestState(Quest.SEEDS_OF_CHAOS);
		if ((qs == null) || !qs.isCompleted())
		{
			return false;
		}
		
		return true;
	}
	
	@Override
	protected final boolean checkVillageMasterRace(PlayerClass pclass)
	{
		if (pclass == null)
		{
			return false;
		}
		
		return pclass.isOfRace(Race.KAMAEL);
	}
}