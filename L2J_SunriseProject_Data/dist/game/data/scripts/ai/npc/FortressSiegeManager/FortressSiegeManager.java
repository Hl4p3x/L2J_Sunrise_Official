/*
 * Copyright (C) 2004-2015 L2J DataPack
 * 
 * This file is part of L2J DataPack.
 * 
 * L2J DataPack is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * L2J DataPack is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package ai.npc.FortressSiegeManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import l2r.gameserver.instancemanager.FortSiegeManager;
import l2r.gameserver.model.ClanPrivilege;
import l2r.gameserver.model.L2Clan;
import l2r.gameserver.model.actor.L2Npc;
import l2r.gameserver.model.actor.instance.L2PcInstance;
import l2r.gameserver.model.entity.Castle;
import l2r.gameserver.model.entity.Fort;
import l2r.gameserver.network.SystemMessageId;
import l2r.gameserver.network.serverpackets.NpcHtmlMessage;
import l2r.gameserver.network.serverpackets.SystemMessage;

import ai.npc.AbstractNpcAI;

/**
 * Fortress Siege Manager AI.
 * @author St3eT
 */
public final class FortressSiegeManager extends AbstractNpcAI
{
	// NPCs
	private static final int[] MANAGERS =
	{
		35659, // Shanty Fortress
		35690, // Southern Fortress
		35728, // Hive Fortress
		35759, // Valley Fortress
		35797, // Ivory Fortress
		35828, // Narsell Fortress
		35859, // Bayou Fortress
		35897, // White Sands Fortress
		35928, // Borderland Fortress
		35966, // Swamp Fortress
		36004, // Archaic Fortress
		36035, // Floran Fortress
		36073, // Cloud Mountain
		36111, // Tanor Fortress
		36142, // Dragonspine Fortress
		36173, // Antharas's Fortress
		36211, // Western Fortress
		36249, // Hunter's Fortress
		36287, // Aaru Fortress
		36318, // Demon Fortress
		36356, // Monastic Fortress
	};
	
	// TODO: vGodFather unhardcode me
	private static Map<Integer, List<Integer>> _relatedFortress = new HashMap<>();
	
	static
	{
		for (int i = 1; i <= 9; i++)
		{
			if (_relatedFortress.get(i) == null)
			{
				_relatedFortress.put(i, new ArrayList<>());
			}
		}
		
		// Gludio
		_relatedFortress.get(1).add(101); // Shanty Fortress
		_relatedFortress.get(1).add(102); // Southern Fortress
		_relatedFortress.get(1).add(112); // Floran Fortress
		_relatedFortress.get(1).add(113); // Cloud Mountain Fortress
		
		// Dion
		_relatedFortress.get(2).add(103); // Hive Fortress
		_relatedFortress.get(2).add(112); // Floran Fortress
		_relatedFortress.get(2).add(114); // Tanor Fortress
		_relatedFortress.get(2).add(115); // Dragonspine Fortress
		
		// Giran
		_relatedFortress.get(3).add(104); // Valley Fortress
		_relatedFortress.get(3).add(114); // Tanor Fortress
		_relatedFortress.get(3).add(116); // Antharas Fortress
		_relatedFortress.get(3).add(118); // Hunters Fortress
		_relatedFortress.get(3).add(119); // Aaru Fortress
		
		// Oren
		_relatedFortress.get(4).add(105); // Ivory Fortress
		_relatedFortress.get(4).add(113); // Cloud Mountain Fortress
		_relatedFortress.get(4).add(115); // Dragonspine Fortress
		_relatedFortress.get(4).add(116); // Antharas Fortress
		_relatedFortress.get(4).add(117); // Western Fortress
		
		// Aden
		_relatedFortress.get(5).add(106); // Narsell Fortress
		_relatedFortress.get(5).add(107); // Bayou Fortress
		_relatedFortress.get(5).add(117); // Western Fortress
		_relatedFortress.get(5).add(118); // Hunters Fortress
		
		// Innadril
		_relatedFortress.get(6).add(108); // White Sands Fortress
		_relatedFortress.get(6).add(119); // Aaru Fortress
		
		// Goddard
		_relatedFortress.get(7).add(109); // Borderland Fortress
		_relatedFortress.get(7).add(117); // Western Fortress
		_relatedFortress.get(7).add(120); // Demon Fortress
		
		// Rune
		_relatedFortress.get(8).add(110); // Swamp Fortress
		_relatedFortress.get(8).add(120); // Demon Fortress
		_relatedFortress.get(8).add(121); // Monastic Fortress
		
		// Schuttgard
		_relatedFortress.get(9).add(111); // Archaic Fortress
		_relatedFortress.get(9).add(121); // Monastic Fortress
	}
	
	public FortressSiegeManager()
	{
		super(FortressSiegeManager.class.getSimpleName(), "ai/npc");
		addStartNpc(MANAGERS);
		addTalkId(MANAGERS);
		addFirstTalkId(MANAGERS);
	}
	
	@Override
	public String onAdvEvent(String event, L2Npc npc, L2PcInstance player)
	{
		String htmltext = null;
		switch (event)
		{
			case "FortressSiegeManager-11.html":
			case "FortressSiegeManager-13.html":
			case "FortressSiegeManager-14.html":
			case "FortressSiegeManager-15.html":
			case "FortressSiegeManager-16.html":
			{
				return htmltext = event;
			}
			case "register":
			{
				if (player.getClan() == null)
				{
					htmltext = "FortressSiegeManager-02.html";
				}
				else
				{
					final L2Clan clan = player.getClan();
					final Fort fortress = npc.getFort();
					final Castle castle = npc.getCastle();
					
					if (clan.getFortId() == fortress.getResidenceId())
					{
						final NpcHtmlMessage html = new NpcHtmlMessage(npc.getObjectId());
						html.setHtml(getHtm(player, player.getHtmlPrefix(), "FortressSiegeManager-12.html"));
						html.replace("%clanName%", fortress.getOwnerClan().getName());
						return html.getHtml();
					}
					else if (!player.hasClanPrivilege(ClanPrivilege.CS_MANAGE_SIEGE))
					{
						htmltext = "FortressSiegeManager-10.html";
					}
					else if ((clan.getLevel() < FortSiegeManager.getInstance().getSiegeClanMinLevel()))
					{
						htmltext = "FortressSiegeManager-04.html";
					}
					else if ((player.getClan().getCastleId() == castle.getResidenceId()) && (fortress.getFortState() == 2))
					{
						htmltext = "FortressSiegeManager-18.html";
					}
					else if ((clan.getCastleId() != 0) && (!_relatedFortress.get(clan.getCastleId()).contains(fortress.getResidenceId())) && FortSiegeManager.getInstance().canRegisterJustTerritory())
					{
						htmltext = "FortressSiegeManager-17.html";
					}
					else if ((fortress.getTimeTillRebelArmy() > 0) && (fortress.getTimeTillRebelArmy() <= 7200))
					{
						htmltext = "FortressSiegeManager-19.html";
					}
					else
					{
						switch (npc.getFort().getSiege().addAttacker(player, true))
						{
							case 1:
							{
								htmltext = "FortressSiegeManager-03.html";
								break;
							}
							case 2:
							{
								htmltext = "FortressSiegeManager-07.html";
								break;
							}
							case 3:
							{
								htmltext = "FortressSiegeManager-06.html";
								break;
							}
							case 4:
							{
								final SystemMessage sm = SystemMessage.getSystemMessage(SystemMessageId.REGISTERED_TO_S1_FORTRESS_BATTLE);
								sm.addString(npc.getFort().getName());
								player.sendPacket(sm);
								htmltext = "FortressSiegeManager-05.html";
								break;
							}
						}
					}
				}
				break;
			}
			case "cancel":
			{
				if (player.getClan() == null)
				{
					htmltext = "FortressSiegeManager-02.html";
				}
				else
				{
					final L2Clan clan = player.getClan();
					final Fort fortress = npc.getFort();
					
					if (clan.getFortId() == fortress.getResidenceId())
					{
						final NpcHtmlMessage html = new NpcHtmlMessage(npc.getObjectId());
						html.setHtml(getHtm(player, player.getHtmlPrefix(), "FortressSiegeManager-12.html"));
						html.replace("%clanName%", fortress.getOwnerClan().getName());
						return html.getHtml();
					}
					else if (!player.hasClanPrivilege(ClanPrivilege.CS_MANAGE_SIEGE))
					{
						htmltext = "FortressSiegeManager-10.html";
					}
					else if (!FortSiegeManager.getInstance().checkIsRegistered(clan, fortress.getResidenceId()))
					{
						htmltext = "FortressSiegeManager-09.html";
					}
					else
					{
						fortress.getSiege().removeAttacker(player.getClan());
						htmltext = "FortressSiegeManager-08.html";
					}
				}
				break;
			}
			case "warInfo":
			{
				htmltext = npc.getFort().getSiege().getAttackerClans().isEmpty() ? "FortressSiegeManager-20.html" : "FortressSiegeManager-21.html";
			}
		}
		return htmltext;
	}
	
	@Override
	public String onFirstTalk(L2Npc npc, L2PcInstance player)
	{
		final Fort fortress = npc.getFort();
		final int fortOwner = fortress.getOwnerClan() == null ? 0 : fortress.getOwnerClan().getId();
		if (fortOwner == 0)
		{
			return "FortressSiegeManager.html";
		}
		final NpcHtmlMessage html = new NpcHtmlMessage(npc.getObjectId());
		html.setHtml(getHtm(player, player.getHtmlPrefix(), "FortressSiegeManager-01.html"));
		html.replace("%clanName%", fortress.getOwnerClan().getName());
		html.replace("%objectId%", npc.getObjectId());
		return html.getHtml();
	}
}
