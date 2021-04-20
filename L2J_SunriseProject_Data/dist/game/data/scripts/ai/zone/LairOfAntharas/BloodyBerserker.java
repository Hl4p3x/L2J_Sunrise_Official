/*
 * Copyright (C) 2004-2020 L2J DataPack
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
package ai.zone.LairOfAntharas;

import l2r.gameserver.GeoData;
import l2r.gameserver.data.SpawnTable;
import l2r.gameserver.model.L2Spawn;
import l2r.gameserver.model.Location;
import l2r.gameserver.model.actor.L2Attackable;
import l2r.gameserver.model.actor.L2Npc;
import l2r.gameserver.model.actor.instance.L2PcInstance;

import ai.npc.AbstractNpcAI;

/**
 * Bloody Berserker AI.
 * @author Maneco2
 * @version 2.6.2.0
 */
public class BloodyBerserker extends AbstractNpcAI
{
	// NPCs
	private static final int BLOODY_BERSERKER = 22855;
	
	private static final int[] BLOODY_FAMILY =
	{
		22854, // Bloody Karik
		22855, // Bloody Berserker
		22856, // Bloody Karinness
	};
	
	public BloodyBerserker()
	{
		super(BloodyBerserker.class.getSimpleName(), "ai/zone/LairOfAntharas");
		addKillId(BLOODY_BERSERKER);
		addAttackId(BLOODY_BERSERKER);
		addTeleportId(BLOODY_BERSERKER);
		addMoveFinishedId(BLOODY_BERSERKER);
	}
	
	@Override
	public String onAdvEvent(String event, L2Npc npc, L2PcInstance player)
	{
		switch (event)
		{
			case "CORE_AI":
			{
				if (npc != null)
				{
					((L2Attackable) npc).clearAggroList();
					npc.disableCoreAI(false);
					startQuestTimer("RETURN_SPAWN", 300000, npc, null);
				}
				break;
			}
			case "RETURN_SPAWN":
			{
				if (npc != null)
				{
					((L2Attackable) npc).setCanReturnToSpawnPoint(true);
				}
				break;
			}
		}
		return super.onAdvEvent(event, npc, player);
	}
	
	@Override
	public String onAttack(L2Npc npc, L2PcInstance attacker, int damage, boolean isSummon)
	{
		switch (npc.getId())
		{
			case BLOODY_BERSERKER:
			{
				final double DistSpawn = npc.calculateDistance(npc.getSpawn().getLocation(), false, false);
				if (DistSpawn > 3000)
				{
					npc.disableCoreAI(true);
					npc.teleToLocation(npc.getSpawn().getLocation());
				}
				else
				{
					if ((DistSpawn > 500) && (getRandom(100) < 1) && (npc.isInCombat()) && (!npc.isCastingNow()))
					{
						for (int object : BLOODY_FAMILY)
						{
							for (L2Spawn spawn : SpawnTable.getInstance().getSpawns(object))
							{
								final L2Npc obj = spawn.getLastSpawn();
								if ((obj != null) && !obj.isDead() && (Math.abs(npc.getZ() - obj.getZ()) < 150))
								{
									if (npc.calculateDistance(obj, false, false) > obj.getTemplate().getClanHelpRange())
									{
										if ((npc.calculateDistance(obj, false, false) < 3000) && GeoData.getInstance().canSeeTarget(npc, obj))
										{
											npc.disableCoreAI(true);
											((L2Attackable) npc).setCanReturnToSpawnPoint(false);
											addMoveToDesire(npc, new Location(obj.getX() + getRandom(-100, 100), obj.getY() + getRandom(-100, 100), obj.getZ() + 20, 0), 0);
										}
									}
								}
							}
						}
					}
				}
				break;
			}
		}
		return super.onAttack(npc, attacker, damage, isSummon);
	}
	
	@Override
	public String onKill(L2Npc npc, L2PcInstance killer, boolean isSummon)
	{
		switch (npc.getId())
		{
			case BLOODY_BERSERKER:
			{
				if (getRandom(100) < 5)
				{
					final int newZ = npc.getZ() + 20;
					addAttackDesire(addSpawn(npc.getId(), npc.getX(), npc.getY(), newZ, npc.getHeading(), false, 0), killer);
					addAttackDesire(addSpawn(npc.getId(), npc.getX(), npc.getY() - 10, newZ, npc.getHeading(), false, 0), killer);
					addAttackDesire(addSpawn(npc.getId(), npc.getX(), npc.getY() - 20, newZ, npc.getHeading(), false, 0), killer);
					addAttackDesire(addSpawn(npc.getId(), npc.getX(), npc.getY() + 10, newZ, npc.getHeading(), false, 0), killer);
					addAttackDesire(addSpawn(npc.getId(), npc.getX(), npc.getY() + 20, newZ, npc.getHeading(), false, 0), killer);
				}
				break;
			}
		}
		return super.onKill(npc, killer, isSummon);
	}
	
	@Override
	protected void onTeleport(L2Npc npc)
	{
		startQuestTimer("CORE_AI", 100, npc, null);
	}
	
	@Override
	public void onMoveFinished(L2Npc npc)
	{
		startQuestTimer("CORE_AI", 100, npc, null);
	}
}