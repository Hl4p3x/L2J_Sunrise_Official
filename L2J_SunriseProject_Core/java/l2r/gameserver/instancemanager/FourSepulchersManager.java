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
package l2r.gameserver.instancemanager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledFuture;

import l2r.Config;
import l2r.L2DatabaseFactory;
import l2r.gameserver.ThreadPoolManager;
import l2r.gameserver.data.SpawnTable;
import l2r.gameserver.data.xml.impl.DoorData;
import l2r.gameserver.instancemanager.tasks.FourSepulchersChangeAttackTimeTask;
import l2r.gameserver.instancemanager.tasks.FourSepulchersChangeCoolDownTimeTask;
import l2r.gameserver.instancemanager.tasks.FourSepulchersChangeEntryTimeTask;
import l2r.gameserver.instancemanager.tasks.FourSepulchersChangeWarmUpTimeTask;
import l2r.gameserver.model.L2Spawn;
import l2r.gameserver.model.actor.L2Npc;
import l2r.gameserver.model.actor.instance.L2DoorInstance;
import l2r.gameserver.model.actor.instance.L2PcInstance;
import l2r.gameserver.model.actor.instance.L2SepulcherMonsterInstance;
import l2r.gameserver.model.actor.instance.L2SepulcherNpcInstance;
import l2r.gameserver.model.items.instance.L2ItemInstance;
import l2r.gameserver.model.quest.Quest;
import l2r.gameserver.model.quest.QuestState;
import l2r.gameserver.network.NpcStringId;
import l2r.gameserver.network.SystemMessageId;
import l2r.gameserver.network.serverpackets.NpcHtmlMessage;
import l2r.gameserver.util.Util;
import l2r.util.Rnd;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Zoey76: TODO: Use Location DTO instead of array of int.
 * @author sandman, improved by vGodFather
 */
public final class FourSepulchersManager
{
	private static final Logger _log = LoggerFactory.getLogger(FourSepulchersManager.class);
	
	private static final int QUEST_ID = 620;
	
	private static final int ENTRANCE_PASS = 7075;
	private static final int USED_PASS = 7261;
	private static final int CHAPEL_KEY = 7260;
	private static final int ANTIQUE_BROOCH = 7262;
	
	private boolean _firstTimeRun;
	private boolean _inEntryTime = false;
	private boolean _inWarmUpTime = false;
	private boolean _inAttackTime = false;
	private boolean _inCoolDownTime = false;
	
	private ScheduledFuture<?> _changeCoolDownTimeTask = null;
	private ScheduledFuture<?> _changeEntryTimeTask = null;
	private ScheduledFuture<?> _changeWarmUpTimeTask = null;
	private ScheduledFuture<?> _changeAttackTimeTask = null;
	
	// @formatter:off
	private final int[][] _startHallSpawn =
	{
		{ 181632, -85587, -7218 },
		{ 179963, -88978, -7218 },
		{ 173217, -86132, -7218 },
		{ 175608, -82296, -7218 }
	};
	
	private final int[][][] _shadowSpawnLoc =
	{
		{
			{ 25339, 191231, -85574, -7216, 33380 },
			{ 25349, 189534, -88969, -7216, 32768 },
			{ 25346, 173195, -76560, -7215, 49277 },
			{ 25342, 175591, -72744, -7215, 49317 }
		},
		{
			{ 25342, 191231, -85574, -7216, 33380 },
			{ 25339, 189534, -88969, -7216, 32768 },
			{ 25349, 173195, -76560, -7215, 49277 },
			{ 25346, 175591, -72744, -7215, 49317 }
		},
		{
			{ 25346, 191231, -85574, -7216, 33380 },
			{ 25342, 189534, -88969, -7216, 32768 },
			{ 25339, 173195, -76560, -7215, 49277 },
			{ 25349, 175591, -72744, -7215, 49317 }
		},
		{
			{ 25349, 191231, -85574, -7216, 33380 },
			{ 25346, 189534, -88969, -7216, 32768 },
			{ 25342, 173195, -76560, -7215, 49277 },
			{ 25339, 175591, -72744, -7215, 49317 }
		},
	};
	// @formatter:on
	
	protected Map<Integer, Boolean> _archonSpawned = new ConcurrentHashMap<>();
	protected Map<Integer, Boolean> _hallInUse = new ConcurrentHashMap<>();
	protected Map<Integer, L2PcInstance> _challengers = new ConcurrentHashMap<>();
	protected Map<Integer, int[]> _startHallSpawns = new HashMap<>();
	protected Map<Integer, Integer> _hallGateKeepers = new HashMap<>();
	protected Map<Integer, Integer> _keyBoxNpc = new HashMap<>();
	protected Map<Integer, Integer> _victim = new HashMap<>();
	protected Map<Integer, L2Spawn> _executionerSpawns = new HashMap<>();
	protected Map<Integer, L2Spawn> _keyBoxSpawns = new HashMap<>();
	protected Map<Integer, L2Spawn> _mysteriousBoxSpawns = new HashMap<>();
	protected Map<Integer, L2Spawn> _shadowSpawns = new HashMap<>();
	protected Map<Integer, List<L2Spawn>> _dukeFinalMobs = new HashMap<>();
	protected Map<Integer, List<L2SepulcherMonsterInstance>> _dukeMobs = new HashMap<>();
	protected Map<Integer, List<L2Spawn>> _emperorsGraveNpcs = new HashMap<>();
	protected Map<Integer, List<L2Spawn>> _magicalMonsters = new HashMap<>();
	protected Map<Integer, List<L2Spawn>> _physicalMonsters = new HashMap<>();
	protected Map<Integer, List<L2SepulcherMonsterInstance>> _viscountMobs = new HashMap<>();
	
	protected List<L2Spawn> _physicalSpawns;
	protected List<L2Spawn> _magicalSpawns;
	protected List<L2Spawn> _managers;
	protected List<L2Spawn> _dukeFinalSpawns;
	protected List<L2Spawn> _emperorsGraveSpawns;
	protected List<L2Npc> _allMobs = new CopyOnWriteArrayList<>();
	
	private long _attackTimeEnd = 0;
	private long _coolDownTimeEnd = 0;
	private long _entryTimeEnd = 0;
	private long _warmUpTimeEnd = 0;
	
	private final byte _newCycleMin = 55;
	
	public void init()
	{
		if (_changeCoolDownTimeTask != null)
		{
			_changeCoolDownTimeTask.cancel(true);
		}
		if (_changeEntryTimeTask != null)
		{
			_changeEntryTimeTask.cancel(true);
		}
		if (_changeWarmUpTimeTask != null)
		{
			_changeWarmUpTimeTask.cancel(true);
		}
		if (_changeAttackTimeTask != null)
		{
			_changeAttackTimeTask.cancel(true);
		}
		
		_changeCoolDownTimeTask = null;
		_changeEntryTimeTask = null;
		_changeWarmUpTimeTask = null;
		_changeAttackTimeTask = null;
		
		_inEntryTime = false;
		_inWarmUpTime = false;
		_inAttackTime = false;
		_inCoolDownTime = false;
		
		_firstTimeRun = true;
		initFixedInfo();
		loadMysteriousBox();
		initKeyBoxSpawns();
		loadPhysicalMonsters();
		loadMagicalMonsters();
		initLocationShadowSpawns();
		initExecutionerSpawns();
		loadDukeMonsters();
		loadEmperorsGraveMonsters();
		spawnManagers();
		timeSelector();
	}
	
	// phase select on server launch
	protected void timeSelector()
	{
		timeCalculator();
		long currentTime = Calendar.getInstance().getTimeInMillis();
		// if current time >= time of entry beginning and if current time < time of entry beginning + time of entry end
		if ((currentTime >= _coolDownTimeEnd) && (currentTime < _entryTimeEnd)) // entry time check
		{
			clean();
			_changeEntryTimeTask = ThreadPoolManager.getInstance().scheduleGeneral(new FourSepulchersChangeEntryTimeTask(), 0);
			_log.info(getClass().getSimpleName() + ": Beginning in Entry time");
		}
		else if ((currentTime >= _entryTimeEnd) && (currentTime < _warmUpTimeEnd)) // warmup time check
		{
			clean();
			_changeWarmUpTimeTask = ThreadPoolManager.getInstance().scheduleGeneral(new FourSepulchersChangeWarmUpTimeTask(), 0);
			_log.info(getClass().getSimpleName() + ": Beginning in WarmUp time");
		}
		else if ((currentTime >= _warmUpTimeEnd) && (currentTime < _attackTimeEnd)) // attack time check
		{
			clean();
			_changeAttackTimeTask = ThreadPoolManager.getInstance().scheduleGeneral(new FourSepulchersChangeAttackTimeTask(), 0);
			_log.info(getClass().getSimpleName() + ": Beginning in Attack time");
		}
		else
		// else cooldown time and without cleanup because it's already implemented
		{
			_changeCoolDownTimeTask = ThreadPoolManager.getInstance().scheduleGeneral(new FourSepulchersChangeCoolDownTimeTask(), 0);
			_log.info(getClass().getSimpleName() + ": Beginning in Cooldown time");
		}
	}
	
	// phase end times calculator
	protected void timeCalculator()
	{
		Calendar tmp = Calendar.getInstance();
		if (tmp.get(Calendar.MINUTE) < _newCycleMin)
		{
			tmp.set(Calendar.HOUR, Calendar.getInstance().get(Calendar.HOUR) - 1);
		}
		tmp.set(Calendar.MINUTE, _newCycleMin);
		_coolDownTimeEnd = tmp.getTimeInMillis();
		_entryTimeEnd = _coolDownTimeEnd + (Config.FS_TIME_ENTRY * 60000L);
		_warmUpTimeEnd = _entryTimeEnd + (Config.FS_TIME_WARMUP * 60000L);
		_attackTimeEnd = _warmUpTimeEnd + (Config.FS_TIME_ATTACK * 60000L);
	}
	
	public void clean()
	{
		for (int i = 31921; i < 31925; i++)
		{
			int[] Location = _startHallSpawns.get(i);
			GrandBossManager.getInstance().getZone(Location[0], Location[1], Location[2]).oustAllPlayers();
		}
		
		deleteAllMobs();
		
		closeAllDoors();
		
		_hallInUse.clear();
		_hallInUse.put(31921, false);
		_hallInUse.put(31922, false);
		_hallInUse.put(31923, false);
		_hallInUse.put(31924, false);
		
		for (int npcId : _archonSpawned.keySet())
		{
			_archonSpawned.put(npcId, false);
		}
	}
	
	protected void spawnManagers()
	{
		_managers = new CopyOnWriteArrayList<>();
		
		for (int npcId = 31921; npcId <= 31924; npcId++)
		{
			try
			{
				final L2Spawn spawnDat = new L2Spawn(npcId);
				spawnDat.setAmount(1);
				spawnDat.setRespawnDelay(60);
				switch (npcId)
				{
					case 31921: // conquerors
						spawnDat.setX(181061);
						spawnDat.setY(-85595);
						spawnDat.setZ(-7200);
						spawnDat.setHeading(-32584);
						break;
					case 31922: // emperors
						spawnDat.setX(179292);
						spawnDat.setY(-88981);
						spawnDat.setZ(-7200);
						spawnDat.setHeading(-33272);
						break;
					case 31923: // sages
						spawnDat.setX(173202);
						spawnDat.setY(-87004);
						spawnDat.setZ(-7200);
						spawnDat.setHeading(-16248);
						break;
					case 31924: // judges
						spawnDat.setX(175606);
						spawnDat.setY(-82853);
						spawnDat.setZ(-7200);
						spawnDat.setHeading(-16248);
						break;
				}
				_managers.add(spawnDat);
				SpawnTable.getInstance().addNewSpawn(spawnDat, false);
				spawnDat.doSpawn();
				spawnDat.startRespawn();
				_log.info(getClass().getSimpleName() + ": spawned " + spawnDat.getTemplate().getName());
			}
			catch (Exception e)
			{
				_log.warn(getClass().getSimpleName() + ": Error while spawning managers: " + e.getMessage(), e);
			}
		}
	}
	
	protected void initFixedInfo()
	{
		_startHallSpawns.put(31921, _startHallSpawn[0]);
		_startHallSpawns.put(31922, _startHallSpawn[1]);
		_startHallSpawns.put(31923, _startHallSpawn[2]);
		_startHallSpawns.put(31924, _startHallSpawn[3]);
		
		_hallInUse.put(31921, false);
		_hallInUse.put(31922, false);
		_hallInUse.put(31923, false);
		_hallInUse.put(31924, false);
		
		_hallGateKeepers.put(31925, 25150012);
		_hallGateKeepers.put(31926, 25150013);
		_hallGateKeepers.put(31927, 25150014);
		_hallGateKeepers.put(31928, 25150015);
		_hallGateKeepers.put(31929, 25150016);
		_hallGateKeepers.put(31930, 25150002);
		_hallGateKeepers.put(31931, 25150003);
		_hallGateKeepers.put(31932, 25150004);
		_hallGateKeepers.put(31933, 25150005);
		_hallGateKeepers.put(31934, 25150006);
		_hallGateKeepers.put(31935, 25150032);
		_hallGateKeepers.put(31936, 25150033);
		_hallGateKeepers.put(31937, 25150034);
		_hallGateKeepers.put(31938, 25150035);
		_hallGateKeepers.put(31939, 25150036);
		_hallGateKeepers.put(31940, 25150022);
		_hallGateKeepers.put(31941, 25150023);
		_hallGateKeepers.put(31942, 25150024);
		_hallGateKeepers.put(31943, 25150025);
		_hallGateKeepers.put(31944, 25150026);
		
		_keyBoxNpc.put(18120, 31455);
		_keyBoxNpc.put(18121, 31455);
		_keyBoxNpc.put(18122, 31455);
		_keyBoxNpc.put(18123, 31455);
		_keyBoxNpc.put(18124, 31456);
		_keyBoxNpc.put(18125, 31456);
		_keyBoxNpc.put(18126, 31456);
		_keyBoxNpc.put(18127, 31456);
		_keyBoxNpc.put(18128, 31457);
		_keyBoxNpc.put(18129, 31457);
		_keyBoxNpc.put(18130, 31457);
		_keyBoxNpc.put(18131, 31457);
		_keyBoxNpc.put(18149, 31458);
		_keyBoxNpc.put(18150, 31459);
		_keyBoxNpc.put(18151, 31459);
		_keyBoxNpc.put(18152, 31459);
		_keyBoxNpc.put(18153, 31459);
		_keyBoxNpc.put(18154, 31460);
		_keyBoxNpc.put(18155, 31460);
		_keyBoxNpc.put(18156, 31460);
		_keyBoxNpc.put(18157, 31460);
		_keyBoxNpc.put(18158, 31461);
		_keyBoxNpc.put(18159, 31461);
		_keyBoxNpc.put(18160, 31461);
		_keyBoxNpc.put(18161, 31461);
		_keyBoxNpc.put(18162, 31462);
		_keyBoxNpc.put(18163, 31462);
		_keyBoxNpc.put(18164, 31462);
		_keyBoxNpc.put(18165, 31462);
		_keyBoxNpc.put(18183, 31463);
		_keyBoxNpc.put(18184, 31464);
		_keyBoxNpc.put(18212, 31465);
		_keyBoxNpc.put(18213, 31465);
		_keyBoxNpc.put(18214, 31465);
		_keyBoxNpc.put(18215, 31465);
		_keyBoxNpc.put(18216, 31466);
		_keyBoxNpc.put(18217, 31466);
		_keyBoxNpc.put(18218, 31466);
		_keyBoxNpc.put(18219, 31466);
		
		_victim.put(18150, 18158);
		_victim.put(18151, 18159);
		_victim.put(18152, 18160);
		_victim.put(18153, 18161);
		_victim.put(18154, 18162);
		_victim.put(18155, 18163);
		_victim.put(18156, 18164);
		_victim.put(18157, 18165);
	}
	
	private void loadMysteriousBox()
	{
		_mysteriousBoxSpawns.clear();
		
		try (Connection con = L2DatabaseFactory.getInstance().getConnection();
			PreparedStatement ps = con.prepareStatement("SELECT id, count, npc_templateid, locx, locy, locz, heading, respawn_delay, key_npc_id FROM four_sepulchers_spawnlist Where spawntype = ? ORDER BY id"))
		{
			ps.setInt(1, 0);
			try (ResultSet rs = ps.executeQuery())
			{
				while (rs.next())
				{
					final L2Spawn spawnDat = new L2Spawn(rs.getInt("npc_templateid"));
					spawnDat.setAmount(rs.getInt("count"));
					spawnDat.setX(rs.getInt("locx"));
					spawnDat.setY(rs.getInt("locy"));
					spawnDat.setZ(rs.getInt("locz"));
					spawnDat.setHeading(rs.getInt("heading"));
					spawnDat.setRespawnDelay(rs.getInt("respawn_delay"));
					SpawnTable.getInstance().addNewSpawn(spawnDat, false);
					int keyNpcId = rs.getInt("key_npc_id");
					_mysteriousBoxSpawns.put(keyNpcId, spawnDat);
				}
			}
			_log.info(getClass().getSimpleName() + ": loaded " + _mysteriousBoxSpawns.size() + " Mysterious-Box spawns.");
		}
		catch (Exception e)
		{
			_log.warn(getClass().getSimpleName() + ": Spawn could not be initialized: " + e.getMessage(), e);
		}
	}
	
	private void initKeyBoxSpawns()
	{
		for (Entry<Integer, Integer> keyNpc : _keyBoxNpc.entrySet())
		{
			try
			{
				final L2Spawn spawnDat = new L2Spawn(keyNpc.getValue());
				spawnDat.setAmount(1);
				spawnDat.setX(0);
				spawnDat.setY(0);
				spawnDat.setZ(0);
				spawnDat.setHeading(0);
				spawnDat.setRespawnDelay(3600);
				SpawnTable.getInstance().addNewSpawn(spawnDat, false);
				_keyBoxSpawns.put(keyNpc.getKey(), spawnDat);
			}
			catch (Exception e)
			{
				_log.warn(getClass().getSimpleName() + ": Spawn could not be initialized: " + e.getMessage(), e);
			}
		}
	}
	
	private void loadEmperorsGraveMonsters()
	{
		_emperorsGraveNpcs.clear();
		int count = loadSpawn(_emperorsGraveSpawns, _emperorsGraveNpcs, 6);
		_log.info(getClass().getSimpleName() + ": loaded " + count + " Emperor's grave NPCs.");
	}
	
	private void loadDukeMonsters()
	{
		_dukeFinalMobs.clear();
		_archonSpawned.clear();
		int count = loadSpawn(_dukeFinalSpawns, _dukeFinalMobs, 5);
		for (Integer npcId : _dukeFinalMobs.keySet())
		{
			_archonSpawned.put(npcId, false);
		}
		_log.info(getClass().getSimpleName() + ": loaded " + count + " Church of duke monsters.");
	}
	
	private void loadMagicalMonsters()
	{
		_magicalMonsters.clear();
		int count = loadSpawn(_magicalSpawns, _magicalMonsters, 2);
		_log.info(getClass().getSimpleName() + ": loaded " + count + " Magical monsters.");
	}
	
	private void loadPhysicalMonsters()
	{
		_physicalMonsters.clear();
		int count = loadSpawn(_physicalSpawns, _physicalMonsters, 1);
		_log.info(getClass().getSimpleName() + ": loaded " + count + " Physical monsters.");
	}
	
	private int loadSpawn(List<L2Spawn> spawns, Map<Integer, List<L2Spawn>> monsters, int type)
	{
		int loaded = 0;
		try (Connection con = L2DatabaseFactory.getInstance().getConnection();
			PreparedStatement ps1 = con.prepareStatement("SELECT Distinct key_npc_id FROM four_sepulchers_spawnlist Where spawntype = ? ORDER BY key_npc_id"))
		{
			ps1.setInt(1, type);
			try (ResultSet rs1 = ps1.executeQuery();
				PreparedStatement ps2 = con.prepareStatement("SELECT id, count, npc_templateid, locx, locy, locz, heading, respawn_delay, key_npc_id FROM four_sepulchers_spawnlist Where key_npc_id = ? and spawntype = ? ORDER BY id"))
			{
				while (rs1.next())
				{
					int keyNpcId = rs1.getInt("key_npc_id");
					
					ps2.setInt(1, keyNpcId);
					ps2.setInt(2, type);
					try (ResultSet rs2 = ps2.executeQuery())
					{
						spawns = new ArrayList<>();
						while (rs2.next())
						{
							final L2Spawn spawnDat = new L2Spawn(rs2.getInt("npc_templateid"));
							spawnDat.setAmount(rs2.getInt("count"));
							spawnDat.setX(rs2.getInt("locx"));
							spawnDat.setY(rs2.getInt("locy"));
							spawnDat.setZ(rs2.getInt("locz"));
							spawnDat.setHeading(rs2.getInt("heading"));
							spawnDat.setRespawnDelay(rs2.getInt("respawn_delay"));
							SpawnTable.getInstance().addNewSpawn(spawnDat, false);
							spawns.add(spawnDat);
							loaded++;
						}
					}
					ps2.clearParameters();
					monsters.put(keyNpcId, spawns);
				}
			}
		}
		catch (Exception e)
		{
			_log.warn(getClass().getSimpleName() + ": Spawn could not be initialized: " + e.getMessage(), e);
		}
		return loaded;
	}
	
	protected void initLocationShadowSpawns()
	{
		int locNo = Rnd.get(4);
		final int[] gateKeeper =
		{
			31929,
			31934,
			31939,
			31944
		};
		
		_shadowSpawns.clear();
		
		for (int i = 0; i <= 3; i++)
		{
			try
			{
				final L2Spawn spawnDat = new L2Spawn(_shadowSpawnLoc[locNo][i][0]);
				spawnDat.setAmount(1);
				spawnDat.setX(_shadowSpawnLoc[locNo][i][1]);
				spawnDat.setY(_shadowSpawnLoc[locNo][i][2]);
				spawnDat.setZ(_shadowSpawnLoc[locNo][i][3]);
				spawnDat.setHeading(_shadowSpawnLoc[locNo][i][4]);
				SpawnTable.getInstance().addNewSpawn(spawnDat, false);
				_shadowSpawns.put(gateKeeper[i], spawnDat);
			}
			catch (Exception e)
			{
				_log.error("Error on InitLocationShadowSpawns", e);
			}
		}
	}
	
	protected void initExecutionerSpawns()
	{
		for (int keyNpcId : _victim.keySet())
		{
			try
			{
				final L2Spawn spawnDat = new L2Spawn(_victim.get(keyNpcId));
				spawnDat.setAmount(1);
				spawnDat.setX(0);
				spawnDat.setY(0);
				spawnDat.setZ(0);
				spawnDat.setHeading(0);
				spawnDat.setRespawnDelay(3600);
				SpawnTable.getInstance().addNewSpawn(spawnDat, false);
				_executionerSpawns.put(keyNpcId, spawnDat);
			}
			catch (Exception e)
			{
				_log.warn(getClass().getSimpleName() + ": Spawn could not be initialized: " + e.getMessage(), e);
			}
		}
	}
	
	public ScheduledFuture<?> getChangeAttackTimeTask()
	{
		return _changeAttackTimeTask;
	}
	
	public void setChangeAttackTimeTask(ScheduledFuture<?> task)
	{
		_changeAttackTimeTask = task;
	}
	
	public ScheduledFuture<?> getChangeCoolDownTimeTask()
	{
		return _changeCoolDownTimeTask;
	}
	
	public void setChangeCoolDownTimeTask(ScheduledFuture<?> task)
	{
		_changeCoolDownTimeTask = task;
	}
	
	public ScheduledFuture<?> getChangeEntryTimeTask()
	{
		return _changeEntryTimeTask;
	}
	
	public void setChangeEntryTimeTask(ScheduledFuture<?> task)
	{
		_changeEntryTimeTask = task;
	}
	
	public ScheduledFuture<?> getChangeWarmUpTimeTask()
	{
		return _changeWarmUpTimeTask;
	}
	
	public void setChangeWarmUpTimeTask(ScheduledFuture<?> task)
	{
		_changeWarmUpTimeTask = task;
	}
	
	public long getAttackTimeEnd()
	{
		return _attackTimeEnd;
	}
	
	public void setAttackTimeEnd(long attackTimeEnd)
	{
		_attackTimeEnd = attackTimeEnd;
	}
	
	public byte getCycleMin()
	{
		return _newCycleMin;
	}
	
	public long getEntrytTimeEnd()
	{
		return _entryTimeEnd;
	}
	
	public void setEntryTimeEnd(long entryTimeEnd)
	{
		_entryTimeEnd = entryTimeEnd;
	}
	
	public long getWarmUpTimeEnd()
	{
		return _warmUpTimeEnd;
	}
	
	public void setWarmUpTimeEnd(long warmUpTimeEnd)
	{
		_warmUpTimeEnd = warmUpTimeEnd;
	}
	
	public boolean isAttackTime()
	{
		return _inAttackTime;
	}
	
	public void setIsAttackTime(boolean attackTime)
	{
		_inAttackTime = attackTime;
	}
	
	public boolean isCoolDownTime()
	{
		return _inCoolDownTime;
	}
	
	public void setIsCoolDownTime(boolean isCoolDownTime)
	{
		_inCoolDownTime = isCoolDownTime;
	}
	
	public boolean isEntryTime()
	{
		return _inEntryTime;
	}
	
	public void setIsEntryTime(boolean entryTime)
	{
		_inEntryTime = entryTime;
	}
	
	public boolean isFirstTimeRun()
	{
		return _firstTimeRun;
	}
	
	public void setIsFirstTimeRun(boolean isFirstTimeRun)
	{
		_firstTimeRun = isFirstTimeRun;
	}
	
	public boolean isWarmUpTime()
	{
		return _inWarmUpTime;
	}
	
	public void setIsWarmUpTime(boolean isWarmUpTime)
	{
		_inWarmUpTime = isWarmUpTime;
	}
	
	public synchronized void tryEntry(L2Npc npc, L2PcInstance player)
	{
		Quest hostQuest = QuestManager.getInstance().getQuest(QUEST_ID);
		if (hostQuest == null)
		{
			_log.warn(getClass().getSimpleName() + ": Couldn't find quest: " + QUEST_ID);
			return;
		}
		int npcId = npc.getId();
		switch (npcId)
		{
			// ID ok
			case 31921:
			case 31922:
			case 31923:
			case 31924:
				break;
			// ID not ok
			default:
				if (!player.isGM())
				{
					_log.warn("Player " + player.getName() + "(" + player.getObjectId() + ") tried to cheat in four sepulchers.");
					Util.handleIllegalPlayerAction(player, "Warning!! Character " + player.getName() + " tried to enter four sepulchers with invalid npc id.", Config.DEFAULT_PUNISH);
				}
				return;
		}
		
		if (_hallInUse.get(npcId).booleanValue())
		{
			showHtmlFile(player, npcId + "-FULL.htm", npc, null);
			return;
		}
		
		if (Config.FS_PARTY_MEMBER_COUNT > 1)
		{
			if (!player.isInParty() || (player.getParty().getMemberCount() < Config.FS_PARTY_MEMBER_COUNT))
			{
				showHtmlFile(player, npcId + "-SP.htm", npc, null);
				return;
			}
			
			if (!player.getParty().isLeader(player))
			{
				showHtmlFile(player, npcId + "-NL.htm", npc, null);
				return;
			}
			
			for (L2PcInstance mem : player.getParty().getMembers())
			{
				QuestState qs = mem.getQuestState(hostQuest.getName());
				if ((qs == null) || (!qs.isStarted() && !qs.isCompleted()))
				{
					showHtmlFile(player, npcId + "-NS.htm", npc, mem);
					return;
				}
				if (mem.getInventory().getItemByItemId(ENTRANCE_PASS) == null)
				{
					showHtmlFile(player, npcId + "-SE.htm", npc, mem);
					return;
				}
				
				if (player.getWeightPenalty() >= 3)
				{
					mem.sendPacket(SystemMessageId.INVENTORY_LESS_THAN_80_PERCENT);
					return;
				}
			}
		}
		else if ((Config.FS_PARTY_MEMBER_COUNT <= 1) && player.isInParty())
		{
			if (!player.getParty().isLeader(player))
			{
				showHtmlFile(player, npcId + "-NL.htm", npc, null);
				return;
			}
			for (L2PcInstance mem : player.getParty().getMembers())
			{
				QuestState qs = mem.getQuestState(hostQuest.getName());
				if ((qs == null) || (!qs.isStarted() && !qs.isCompleted()))
				{
					showHtmlFile(player, npcId + "-NS.htm", npc, mem);
					return;
				}
				if (mem.getInventory().getItemByItemId(ENTRANCE_PASS) == null)
				{
					showHtmlFile(player, npcId + "-SE.htm", npc, mem);
					return;
				}
				
				if (player.getWeightPenalty() >= 3)
				{
					mem.sendPacket(SystemMessageId.INVENTORY_LESS_THAN_80_PERCENT);
					return;
				}
			}
		}
		else
		{
			QuestState qs = player.getQuestState(hostQuest.getName());
			if ((qs == null) || (!qs.isStarted() && !qs.isCompleted()))
			{
				showHtmlFile(player, npcId + "-NS.htm", npc, player);
				return;
			}
			if (player.getInventory().getItemByItemId(ENTRANCE_PASS) == null)
			{
				showHtmlFile(player, npcId + "-SE.htm", npc, player);
				return;
			}
			
			if (player.getWeightPenalty() >= 3)
			{
				player.sendPacket(SystemMessageId.INVENTORY_LESS_THAN_80_PERCENT);
				return;
			}
		}
		
		if (!isEntryTime())
		{
			showHtmlFile(player, npcId + "-NE.htm", npc, null);
			return;
		}
		
		showHtmlFile(player, npcId + "-OK.htm", npc, null);
		
		entry(npcId, player);
	}
	
	private void entry(int npcId, L2PcInstance player)
	{
		int[] Location = _startHallSpawns.get(npcId);
		int driftx;
		int drifty;
		
		if (Config.FS_PARTY_MEMBER_COUNT > 1)
		{
			final List<L2PcInstance> members = new LinkedList<>();
			for (L2PcInstance mem : player.getParty().getMembers())
			{
				if (!mem.isDead() && Util.checkIfInRange(700, player, mem, true))
				{
					members.add(mem);
				}
			}
			
			for (L2PcInstance mem : members)
			{
				GrandBossManager.getInstance().getZone(Location[0], Location[1], Location[2]).allowPlayerEntry(mem, 30);
				driftx = Rnd.get(-80, 80);
				drifty = Rnd.get(-80, 80);
				mem.teleToLocation(Location[0] + driftx, Location[1] + drifty, Location[2]);
				mem.destroyItemByItemId("Quest", ENTRANCE_PASS, 1, mem, true);
				if (mem.getInventory().getItemByItemId(ANTIQUE_BROOCH) == null)
				{
					mem.addItem("Quest", USED_PASS, 1, mem, true);
				}
				
				L2ItemInstance hallsKey = mem.getInventory().getItemByItemId(CHAPEL_KEY);
				if (hallsKey != null)
				{
					mem.destroyItemByItemId("Quest", CHAPEL_KEY, hallsKey.getCount(), mem, true);
				}
			}
			
			_challengers.put(npcId, player);
			
			_hallInUse.put(npcId, true);
		}
		else if ((Config.FS_PARTY_MEMBER_COUNT <= 1) && player.isInParty())
		{
			final List<L2PcInstance> members = new LinkedList<>();
			for (L2PcInstance mem : player.getParty().getMembers())
			{
				if (!mem.isDead() && Util.checkIfInRange(700, player, mem, true))
				{
					members.add(mem);
				}
			}
			
			for (L2PcInstance mem : members)
			{
				GrandBossManager.getInstance().getZone(Location[0], Location[1], Location[2]).allowPlayerEntry(mem, 30);
				driftx = Rnd.get(-80, 80);
				drifty = Rnd.get(-80, 80);
				mem.teleToLocation(Location[0] + driftx, Location[1] + drifty, Location[2]);
				mem.destroyItemByItemId("Quest", ENTRANCE_PASS, 1, mem, true);
				if (mem.getInventory().getItemByItemId(ANTIQUE_BROOCH) == null)
				{
					mem.addItem("Quest", USED_PASS, 1, mem, true);
				}
				
				L2ItemInstance hallsKey = mem.getInventory().getItemByItemId(CHAPEL_KEY);
				if (hallsKey != null)
				{
					mem.destroyItemByItemId("Quest", CHAPEL_KEY, hallsKey.getCount(), mem, true);
				}
			}
			
			_challengers.put(npcId, player);
			
			_hallInUse.put(npcId, true);
		}
		else
		{
			GrandBossManager.getInstance().getZone(Location[0], Location[1], Location[2]).allowPlayerEntry(player, 30);
			driftx = Rnd.get(-80, 80);
			drifty = Rnd.get(-80, 80);
			player.teleToLocation(Location[0] + driftx, Location[1] + drifty, Location[2]);
			player.destroyItemByItemId("Quest", ENTRANCE_PASS, 1, player, true);
			if (player.getInventory().getItemByItemId(ANTIQUE_BROOCH) == null)
			{
				player.addItem("Quest", USED_PASS, 1, player, true);
			}
			
			L2ItemInstance hallsKey = player.getInventory().getItemByItemId(CHAPEL_KEY);
			if (hallsKey != null)
			{
				player.destroyItemByItemId("Quest", CHAPEL_KEY, hallsKey.getCount(), player, true);
			}
			
			_challengers.put(npcId, player);
			
			_hallInUse.put(npcId, true);
		}
	}
	
	public void spawnMysteriousBox(int npcId)
	{
		if (!isAttackTime())
		{
			return;
		}
		
		L2Spawn spawnDat = _mysteriousBoxSpawns.get(npcId);
		if (spawnDat != null)
		{
			_allMobs.add(spawnDat.doSpawn());
			spawnDat.stopRespawn();
		}
	}
	
	public void spawnMonster(int npcId)
	{
		if (!isAttackTime())
		{
			return;
		}
		
		final List<L2SepulcherMonsterInstance> mobs = new CopyOnWriteArrayList<>();
		final List<L2Spawn> monsterList;
		if (Rnd.get(2) == 0)
		{
			monsterList = _physicalMonsters.get(npcId);
		}
		else
		{
			monsterList = _magicalMonsters.get(npcId);
		}
		
		if (monsterList != null)
		{
			boolean spawnKeyBoxMob = false;
			boolean spawnedKeyBoxMob = false;
			
			for (L2Spawn spawnDat : monsterList)
			{
				if (spawnedKeyBoxMob)
				{
					spawnKeyBoxMob = false;
				}
				else
				{
					switch (npcId)
					{
						case 31469:
						case 31474:
						case 31479:
						case 31484:
							if (Rnd.get(48) == 0)
							{
								spawnKeyBoxMob = true;
							}
							break;
						default:
							spawnKeyBoxMob = false;
					}
				}
				
				L2SepulcherMonsterInstance mob = null;
				
				if (spawnKeyBoxMob)
				{
					try
					{
						final L2Spawn keyBoxMobSpawn = new L2Spawn(18149);
						keyBoxMobSpawn.setAmount(1);
						keyBoxMobSpawn.setLocation(spawnDat.getLocation());
						keyBoxMobSpawn.setRespawnDelay(3600);
						SpawnTable.getInstance().addNewSpawn(keyBoxMobSpawn, false);
						mob = (L2SepulcherMonsterInstance) keyBoxMobSpawn.doSpawn();
						keyBoxMobSpawn.stopRespawn();
					}
					catch (Exception e)
					{
						_log.warn("FourSepulchersManager.SpawnMonster: Spawn could not be initialized: " + e.getMessage(), e);
					}
					
					spawnedKeyBoxMob = true;
				}
				else
				{
					mob = (L2SepulcherMonsterInstance) spawnDat.doSpawn();
					spawnDat.stopRespawn();
				}
				
				if (mob != null)
				{
					mob.mysteriousBoxId = npcId;
					switch (npcId)
					{
						case 31469:
						case 31474:
						case 31479:
						case 31484:
						case 31472:
						case 31477:
						case 31482:
						case 31487:
							mobs.add(mob);
					}
					_allMobs.add(mob);
				}
			}
			
			switch (npcId)
			{
				case 31469:
				case 31474:
				case 31479:
				case 31484:
					_viscountMobs.put(npcId, mobs);
					break;
				case 31472:
				case 31477:
				case 31482:
				case 31487:
					_dukeMobs.put(npcId, mobs);
					break;
			}
		}
	}
	
	public synchronized boolean isViscountMobsAnnihilated(int npcId)
	{
		List<L2SepulcherMonsterInstance> mobs = _viscountMobs.get(npcId);
		if (mobs == null)
		{
			return true;
		}
		
		for (L2SepulcherMonsterInstance mob : mobs)
		{
			if (!mob.isDead())
			{
				return false;
			}
		}
		
		return true;
	}
	
	public synchronized boolean isDukeMobsAnnihilated(int npcId)
	{
		List<L2SepulcherMonsterInstance> mobs = _dukeMobs.get(npcId);
		if (mobs == null)
		{
			return true;
		}
		
		for (L2SepulcherMonsterInstance mob : mobs)
		{
			if (!mob.isDead())
			{
				return false;
			}
		}
		
		return true;
	}
	
	public void spawnKeyBox(L2Npc npc)
	{
		if (!isAttackTime())
		{
			return;
		}
		
		L2Spawn spawnDat = _keyBoxSpawns.get(npc.getId());
		if (spawnDat != null)
		{
			spawnDat.setAmount(1);
			spawnDat.setX(npc.getX());
			spawnDat.setY(npc.getY());
			spawnDat.setZ(npc.getZ());
			spawnDat.setHeading(npc.getHeading());
			spawnDat.setRespawnDelay(3600);
			_allMobs.add(spawnDat.doSpawn());
			spawnDat.stopRespawn();
		}
	}
	
	public void spawnExecutionerOfHalisha(L2Npc npc)
	{
		if (!isAttackTime())
		{
			return;
		}
		
		L2Spawn spawnDat = _executionerSpawns.get(npc.getId());
		if (spawnDat != null)
		{
			spawnDat.setAmount(1);
			spawnDat.setX(npc.getX());
			spawnDat.setY(npc.getY());
			spawnDat.setZ(npc.getZ());
			spawnDat.setHeading(npc.getHeading());
			spawnDat.setRespawnDelay(3600);
			_allMobs.add(spawnDat.doSpawn());
			spawnDat.stopRespawn();
		}
	}
	
	public void spawnArchonOfHalisha(int npcId)
	{
		if (!isAttackTime())
		{
			return;
		}
		
		if (_archonSpawned.get(npcId))
		{
			return;
		}
		
		List<L2Spawn> monsterList = _dukeFinalMobs.get(npcId);
		if (monsterList != null)
		{
			for (L2Spawn spawnDat : monsterList)
			{
				L2SepulcherMonsterInstance mob = (L2SepulcherMonsterInstance) spawnDat.doSpawn();
				spawnDat.stopRespawn();
				
				if (mob != null)
				{
					mob.mysteriousBoxId = npcId;
					_allMobs.add(mob);
				}
			}
			_archonSpawned.put(npcId, true);
		}
	}
	
	public void spawnEmperorsGraveNpc(int npcId)
	{
		if (!isAttackTime())
		{
			return;
		}
		
		List<L2Spawn> monsterList = _emperorsGraveNpcs.get(npcId);
		if (monsterList != null)
		{
			for (L2Spawn spawnDat : monsterList)
			{
				_allMobs.add(spawnDat.doSpawn());
				spawnDat.stopRespawn();
			}
		}
	}
	
	public void locationShadowSpawns()
	{
		int locNo = Rnd.get(4);
		final int[] gateKeeper =
		{
			31929,
			31934,
			31939,
			31944
		};
		
		for (int i = 0; i <= 3; i++)
		{
			int keyNpcId = gateKeeper[i];
			L2Spawn spawnDat = _shadowSpawns.get(keyNpcId);
			spawnDat.setX(_shadowSpawnLoc[locNo][i][1]);
			spawnDat.setY(_shadowSpawnLoc[locNo][i][2]);
			spawnDat.setZ(_shadowSpawnLoc[locNo][i][3]);
			spawnDat.setHeading(_shadowSpawnLoc[locNo][i][4]);
			_shadowSpawns.put(keyNpcId, spawnDat);
		}
	}
	
	public void spawnShadow(int npcId)
	{
		if (!isAttackTime())
		{
			return;
		}
		
		L2Spawn spawnDat = _shadowSpawns.get(npcId);
		if (spawnDat != null)
		{
			L2SepulcherMonsterInstance mob = (L2SepulcherMonsterInstance) spawnDat.doSpawn();
			spawnDat.stopRespawn();
			
			if (mob != null)
			{
				mob.mysteriousBoxId = npcId;
				_allMobs.add(mob);
			}
		}
	}
	
	public void deleteAllMobs()
	{
		for (L2Npc mob : _allMobs)
		{
			try
			{
				if (mob.getSpawn() != null)
				{
					mob.getSpawn().stopRespawn();
				}
				mob.deleteMe();
			}
			catch (Exception e)
			{
				_log.error(getClass().getSimpleName() + ": Failed deleting mob.", e);
			}
		}
		_allMobs.clear();
	}
	
	protected void closeAllDoors()
	{
		for (int doorId : _hallGateKeepers.values())
		{
			try
			{
				L2DoorInstance door = DoorData.getInstance().getDoor(doorId);
				if (door != null)
				{
					door.closeMe();
				}
				else
				{
					_log.warn(getClass().getSimpleName() + ": Attempted to close undefined door. doorId: " + doorId);
				}
			}
			catch (Exception e)
			{
				_log.error(getClass().getSimpleName() + ": Failed closing door", e);
			}
		}
	}
	
	protected byte minuteSelect(byte min)
	{
		if (((double) min % 5) != 0) // if doesn't divides on 5 fully
		{
			// mad table for selecting proper minutes...
			// may be there is a better way to do this
			switch (min)
			{
				case 6:
				case 7:
					min = 5;
					break;
				case 8:
				case 9:
				case 11:
				case 12:
					min = 10;
					break;
				case 13:
				case 14:
				case 16:
				case 17:
					min = 15;
					break;
				case 18:
				case 19:
				case 21:
				case 22:
					min = 20;
					break;
				case 23:
				case 24:
				case 26:
				case 27:
					min = 25;
					break;
				case 28:
				case 29:
				case 31:
				case 32:
					min = 30;
					break;
				case 33:
				case 34:
				case 36:
				case 37:
					min = 35;
					break;
				case 38:
				case 39:
				case 41:
				case 42:
					min = 40;
					break;
				case 43:
				case 44:
				case 46:
				case 47:
					min = 45;
					break;
				case 48:
				case 49:
				case 51:
				case 52:
					min = 50;
					break;
				case 53:
				case 54:
				case 56:
				case 57:
					min = 55;
					break;
			}
		}
		return min;
	}
	
	public void managerSay(byte min)
	{
		// for attack phase, sending message every 5 minutes
		if (_inAttackTime)
		{
			if (min < 5)
			{
				return; // do not shout when < 5 minutes
			}
			
			min = minuteSelect(min);
			
			// vGodFather: proper messages
			String msg = min + " minute(s) have passed."; // now this is a
			// proper message^^
			
			if (min == 90)
			{
				msg = "Game over. The teleport will appear momentarily.";
			}
			
			for (L2Spawn temp : _managers)
			{
				if (temp == null)
				{
					_log.warn(getClass().getSimpleName() + ": managerSay(): manager is null");
					continue;
				}
				if (!(temp.getLastSpawn() instanceof L2SepulcherNpcInstance))
				{
					_log.warn(getClass().getSimpleName() + ": managerSay(): manager is not Sepulcher instance");
					continue;
				}
				// hall not used right now, so its manager will not tell you anything :)
				// if you don't need this - delete next two lines.
				if (!_hallInUse.get(temp.getId()).booleanValue())
				{
					continue;
				}
				
				((L2SepulcherNpcInstance) temp.getLastSpawn()).sayInShout(msg);
			}
		}
		else if (_inEntryTime)
		{
			NpcStringId msg1 = NpcStringId.YOU_MAY_NOW_ENTER_THE_SEPULCHER;
			NpcStringId msg2 = NpcStringId.IF_YOU_PLACE_YOUR_HAND_ON_THE_STONE_STATUE_IN_FRONT_OF_EACH_SEPULCHER_YOU_WILL_BE_ABLE_TO_ENTER;
			for (L2Spawn temp : _managers)
			{
				if (temp == null)
				{
					_log.warn(getClass().getSimpleName() + ": Something goes wrong in managerSay()...");
					continue;
				}
				if (!(temp.getLastSpawn() instanceof L2SepulcherNpcInstance))
				{
					_log.warn(getClass().getSimpleName() + ": Something goes wrong in managerSay()...");
					continue;
				}
				((L2SepulcherNpcInstance) temp.getLastSpawn()).sayInShout(msg1);
				((L2SepulcherNpcInstance) temp.getLastSpawn()).sayInShout(msg2);
			}
		}
	}
	
	public Map<Integer, Integer> getHallGateKeepers()
	{
		return _hallGateKeepers;
	}
	
	public void showHtmlFile(L2PcInstance player, String file, L2Npc npc, L2PcInstance member)
	{
		final NpcHtmlMessage html = new NpcHtmlMessage(npc.getObjectId());
		html.setFile(player, player.getHtmlPrefix(), "data/html/SepulcherNpc/" + file);
		if (member != null)
		{
			html.replace("%member%", member.getName());
		}
		player.sendPacket(html);
	}
	
	public static final FourSepulchersManager getInstance()
	{
		return SingletonHolder._instance;
	}
	
	private static class SingletonHolder
	{
		protected static final FourSepulchersManager _instance = new FourSepulchersManager();
	}
}
