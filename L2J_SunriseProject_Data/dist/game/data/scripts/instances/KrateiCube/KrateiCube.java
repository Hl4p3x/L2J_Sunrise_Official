package instances.KrateiCube;

import java.util.Calendar;
import java.util.List;
import java.util.StringTokenizer;
import java.util.concurrent.CopyOnWriteArrayList;

import l2r.gameserver.ThreadPoolManager;
import l2r.gameserver.data.SpawnTable;
import l2r.gameserver.enums.QuickVarType;
import l2r.gameserver.instancemanager.KrateiCubeManager;
import l2r.gameserver.instancemanager.ZoneManager;
import l2r.gameserver.model.L2Spawn;
import l2r.gameserver.model.actor.L2Npc;
import l2r.gameserver.model.actor.instance.L2PcInstance;
import l2r.gameserver.model.entity.olympiad.OlympiadManager;
import l2r.gameserver.model.zone.type.L2EffectZone;
import l2r.gameserver.network.SystemMessageId;
import l2r.gameserver.network.clientpackets.Say2;
import l2r.gameserver.network.serverpackets.CreatureSay;
import l2r.gameserver.network.serverpackets.ExPVPMatchCCMyRecord;
import l2r.gameserver.network.serverpackets.ExPVPMatchCCRetire;
import l2r.gameserver.network.serverpackets.ExShowScreenMessage;
import l2r.gameserver.network.serverpackets.SystemMessage;
import l2r.util.Rnd;

import ai.npc.AbstractNpcAI;

/**
 * @author vGodFather
 */
public class KrateiCube extends AbstractNpcAI
{
	protected static boolean debug = false; // set true to enable debug: enter every time && only 30s into waiting room
	
	// Npc Manager
	public L2Npc entranceManager;
	
	// store watcher type for zone/room
	protected static L2Npc[][] watchers = new L2Npc[3][25];
	protected static List<L2Npc> spawned = new CopyOnWriteArrayList<>();
	
	// Id for Npcs
	private static final int ENTRANCE_ID = 32503;
	private static final int MATCH1_ID = 32504;
	private static final int MATCH2_ID = 32505;
	private static final int MATCH3_ID = 32506;
	private static final int REDWATCHER = 18601;
	private static final int BLUEWATCHER = 18602;
	
	private static final int TURNED = 18582; // Rare Creature
	
	//@formatter:off
	
	private static final int[][][] ZONE_BUFFS = { {{4474,4}},{{5742,4}},{{5743,1}}, {{4474,4},{5742,4}}, {{4474,4},{5743,1}}, {{5742,4},{5743,1}}, {{4474,4},{5742,4},{5743,1}} };
	private static final int[][] ZONE_DEBUFFS = { {4150,6},{4148,5},{4625,5} };
	
	// id for Mobs
	private static final int[] MOB_ID =
	{
		18579,18580,18581,18582,18583,18584,18585,18586,18587,18588,18589,18590,18591,18592,18593,18594,18595,18596,18597,18598,18599,18600
	};
	
	protected static final int[][] offset =
	{
		{ 0,0 }, // zone 0, map 1715 - level 70
		{ 33300, -1000 }, // zone 1, map 1815 - level 76
		{ 0, 65408	} // zone 2, map 1717 - level 80
	};
	
	// Locations for Spawn mobs
	protected static int[][][] MOB_SPAWN =
	{
		{{-77663, -85716, -8365},{-77701, -85948, -8365},{-77940, -86090, -8365},{-78142, -85934, -8365},{-78180, -85659, -8365}},
		{{-79653, -85689, -8365},{-79698, -86017, -8365},{-80003, -86025, -8365},{-80102, -85880, -8365},{-80061, -85603, -8365}},
		{{-81556, -85765, -8365},{-81794, -85528, -8365},{-82111, -85645, -8365},{-82044, -85928, -8364},{-81966, -86116, -8365}},
		{{-83750, -85882, -8365},{-84079, -86021, -8365},{-84123, -85663, -8365},{-83841, -85748, -8364},{-83951, -86120, -8365}},
		{{-85785, -85943, -8364},{-86088, -85626, -8365},{-85698, -85678, -8365},{-86154, -85879, -8365},{-85934, -85961, -8365}},
		{{-85935, -84131, -8365},{-86058, -83921, -8365},{-85841, -83684, -8364},{-86082, -83557, -8365},{-85680, -83816, -8365}},
		{{-84128, -83747, -8365},{-83877, -83597, -8365},{-83609, -83946, -8365},{-83911, -83955, -8364},{-83817, -83888, -8364}},
		{{-82039, -83971, -8365},{-81815, -83972, -8365},{-81774, -83742, -8364},{-81996, -83733, -8364},{-82124, -83589, -8365}},
		{{-80098, -83862, -8365},{-79973, -84058, -8365},{-79660, -83848, -8365},{-79915, -83570, -8365},{-79803, -83832, -8364}},
		{{-78023, -84066, -8365},{-77869, -83891, -8364},{-77674, -83757, -8365},{-77861, -83540, -8365},{-78107, -83660, -8365}},
		{{-77876, -82141, -8365},{-77674, -81822, -8365},{-77885, -81607, -8365},{-78078, -81779, -8365},{-78071, -81874, -8365}},
		{{-79740, -81636, -8365},{-80094, -81713, -8365},{-80068, -82004, -8365},{-79677, -81987, -8365},{-79891, -81734, -8364}},
		{{-81703, -81748, -8365},{-81857, -81661, -8364},{-82058, -81863, -8365},{-81816, -82011, -8365},{-81600, -81809, -8365}},
		{{-83669, -82007, -8365},{-83815, -81965, -8365},{-84121, -81805, -8365},{-83962, -81626, -8365},{-83735, -81625, -8365}},
		{{-85708, -81838, -8365},{-86062, -82009, -8365},{-86129, -81814, -8365},{-85957, -81634, -8365},{-85929, -81460, -8365}},
		{{-86160, -79933, -8365},{-85766, -80061, -8365},{-85723, -79691, -8365},{-85922, -79623, -8365},{-85941, -79879, -8364}},
		{{-84082, -79638, -8365},{-83923, -80082, -8365},{-83687, -79778, -8365},{-83863, -79619, -8365},{-83725, -79942, -8365}},
		{{-81963, -80020, -8365},{-81731, -79707, -8365},{-81957, -79589, -8365},{-82151, -79788, -8365},{-81837, -79868, -8364}},
		{{-80093, -80020, -8365},{-80160, -79716, -8365},{-79727, -79699, -8365},{-79790, -80049, -8365},{-79942, -79594, -8365}},
		{{-78113, -79658, -8365},{-77967, -80022, -8365},{-77692, -79779, -8365},{-77728, -79603, -8365},{-78078, -79857, -8365}},
		{{-77648, -77923, -8365},{-77714, -77742, -8365},{-78109, -77640, -8365},{-78114, -77904, -8365},{-77850, -77816, -8364}},
		{{-79651, -77492, -8365},{-79989, -77613, -8365},{-80134, -77981, -8365},{-79759, -78011, -8365},{-79644, -77779, -8365}},
		{{-81672, -77966, -8365},{-81867, -77536, -8365},{-82129, -77926, -8365},{-82057, -78064, -8365},{-82114, -77608, -8365}},
		{{-83938, -77574, -8365},{-84129, -77924, -8365},{-83909, -78111, -8365},{-83652, -78006, -8365},{-83855, -77756, -8364}},
		{{-85660, -78078, -8365},{-85842, -77649, -8365},{-85989, -77556, -8365},{-86075, -77783, -8365},{-86074, -78132, -8365}}
	};
	// Locations for Spawn Watchers
	protected static final int[][] WATCHER_SPAWN =
	{
		{-77906, -85809, -8362, 34826}, {-79903, -85807, -8364, 32652}, {-81904, -85807, -8364, 32839}, {-83901, -85806, -8364, 33336},
		{-85903, -85807, -8364, 32571}, {-77904, -83808, -8364, 32933}, {-79904, -83807, -8364, 33055}, {-81905, -83810, -8364, 32767},
		{-83903, -83807, -8364, 32676}, {-85899, -83807, -8364, 33005}, {-77903, -81808, -8364, 32664}, {-79906, -81807, -8364, 32647},
		{-81901, -81808, -8364, 33724}, {-83905, -81805, -8364, 32926}, {-85907, -81809, -8364, 34248}, {-77904, -79807, -8364, 32905},
		{-79905, -79807, -8364, 32767}, {-81908, -79808, -8364, 32767}, {-83907, -79806, -8364, 32767}, {-85912, -79806, -8364, 29025},
		{-77905, -77808, -8364, 32767}, {-79902, -77805, -8364, 32767}, {-81904, -77808, -8364, 32478}, {-83904, -77808, -8364, 32698},
		{-85904, -77807, -8364, 32612}
	};
	// Set of monster id depending on quantity
	protected static final int[][][] MONSTER_SETS =
	{
		{	// level 70 - zone 0
			{ 18580, 18581 },						// Cursed Girl 71, Gardener 72
			{ 18581, 18583, 18583 }, 				// Gardener 72, Housekeeper 74, Housekeeper 74
			{ 18580, 18583, 18584, 18579 },			// Cursed Girl 71, Housekeeper 74, Turned Boy 75, Hunting Ground Keeper 70
			{ 18580, 18581, 18583, 18584, 18587 }	// Cursed Girl 71, Gardener 72, Housekeeper 74, Turned Boy 75, Gardener 72
		},
		{	// level 76 - zone 1
			{ 18585, 18586 },						// Cursed Girl 75, Gardener 75
			{ 18587, 18592, 18592 }, 				// Begrudged Maid 76, Kratei's Steward 80, Kratei's Steward 80
			{ 18591, 18586, 18592, 18592 },			// Begrudged Boy 79, Gardener 75, Kratei's Steward 80, Kratei's Steward 80
			{ 18586, 18586, 18585, 18590, 18588 }	// Gardener 75, Gardener 75, Cursed Girl 75, Baroness' Employee 80, Hunting Ground Keeper 77
		},
		{	// level 80 - zone 2
			{ 18593, 18594 },						// Housekeeper 80, Mansion Guide 81
			{ 18594, 18600, 18600 }, 				// Mansion Guide 81, Kratei's Steward 86, Kratei's Steward 86
			{ 18595, 18596, 18600, 18600 },			// Baroness' Employee 82, Cursed Gardener 83, Kratei's Steward 86, Kratei's Steward 86
			{ 18596, 18596, 18597, 18598, 18599 }	// Cursed Gardener 83, Cursed Gardener 83, Begrudged Maid 84,Cursed Head Maid 85, Mansion Manager 85
		},
	};
	//@formatter:on
	
	public enum Event
	{
		none,
		tele_task,
		start_task,
		end_task,
		reg_task,
		count_task,
		point_task,
		managermsg_task,
		spawn_task,
		respawn_watcher
	}
	
	public class TaskManager implements Runnable
	{
		Event event;
		L2PcInstance player;
		int points;
		int krateiLevel;
		String msg;
		L2Npc npc;
		
		public TaskManager(Event event)
		{
			this.event = event;
		}
		
		public TaskManager(Event event, L2Npc npc)
		{
			this.event = event;
			this.npc = npc;
		}
		
		public TaskManager(Event event, String msg)
		{
			this.event = event;
			this.msg = msg;
		}
		
		public TaskManager(Event event, L2PcInstance player, int points)
		{
			this.event = event;
			this.player = player;
			this.points = points;
		}
		
		public TaskManager(Event event, L2Npc npc, int krateiLevel)
		{
			this.event = event;
			this.npc = npc;
			this.krateiLevel = krateiLevel;
		}
		
		@Override
		public void run()
		{
			switch (event)
			{
				case spawn_task:
					for (int zone = 0; zone <= 2; zone++)
					{
						int zoneId = ((zone + 1) * 1000) + 1;
						// Spawn Watchers
						for (int i = 0; i <= 24; i++)
						{
							int loc[] = WATCHER_SPAWN[i];
							int _id = (Rnd.get(2) == 0 ? REDWATCHER : BLUEWATCHER);
							watchers[zone][i] = addSpawn(_id, loc[0] + offset[zone][0], loc[1] + offset[zone][1], loc[2], loc[3], true, 0);
							setZoneSkill(zoneId + i, (_id == BLUEWATCHER));
						}
						// Spawn Monsters
						for (int i = 0; i <= 24; i++)
						{
							int qnty = Rnd.get(2, 5);
							for (int j = 0; j < qnty; j++)
							{
								int pos[] = MOB_SPAWN[i][Rnd.get(0, 4)];
								L2Npc mob = addSpawn(MONSTER_SETS[zone][qnty - 2][j], pos[0] + offset[zone][0], pos[1] + offset[zone][1], pos[2], Rnd.get(32767), true, 0);
								mob.getSpawn().startRespawn();
								spawned.add(mob);
							}
						}
					}
					break;
				case respawn_watcher:
					// Respawn Watchers and change room buff/debuff in every rooms of that level
					int zoneId = ((krateiLevel + 1) * 1000) + 1;
					for (int i = 0; i <= 24; i++)
					{
						L2Npc thisNpc = watchers[krateiLevel][i];
						int _id = thisNpc.getId();
						int[] loc =
						{
							thisNpc.getX(),
							thisNpc.getY(),
							thisNpc.getZ(),
							thisNpc.getHeading()
						};
						thisNpc.deleteMe();
						L2Spawn spawn = thisNpc.getSpawn();
						SpawnTable.getInstance().deleteSpawn(spawn, true);
						if (_id == REDWATCHER)
						{
							watchers[krateiLevel][i] = addSpawn(BLUEWATCHER, loc[0], loc[1], loc[2], loc[3], false, 0);
							setZoneSkill(zoneId + i, true);
						}
						else
						{
							watchers[krateiLevel][i] = addSpawn(REDWATCHER, loc[0], loc[1], loc[2], loc[3], false, 0);
							setZoneSkill(zoneId + i, false);
						}
					}
					break;
				case managermsg_task:
					entranceManager.broadcastPacket(new CreatureSay(entranceManager.getObjectId(), Say2.ALL, entranceManager.getName(), msg));
					break;
				case point_task:
					if ((player == null) || (points == 0))
					{
						return;
					}
					KrateiCubeManager.getInstance().addPoints(player, points);
					break;
				case count_task:
					if (player == null)
					{
						return;
					}
					int step = (debug) ? 1 : 60;
					for (int i = (debug) ? 30 : 180; i > 0; i -= step)
					{
						player.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.S1).addString(i + " second(s) before the match begins."));
						player.sendPacket(new ExShowScreenMessage(1, -1, 2, 0, 0, 0, 0, true, ((step > 1) ? 5 : 1) * 1000, false, "The match will start in " + i + " second(s)!"));
						
						switch (i)
						{
							case 60:
								step = 30;
								break;
							case 30:
								step = 10;
								break;
							case 10:
								step = 1;
								break;
						}
						try
						{
							Thread.sleep(step * 1000);
						}
						catch (InterruptedException e)
						{
							_log.warn("Error in Count Task thread sleep: ", e);
						}
					}
					player.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.S1).addString("Begin the match!"));
					break;
				case tele_task:
					KrateiCubeManager.getInstance().setIsRegTime(false);
					String message = "The event was canceled due to lack of participants.";
					
					if (KrateiCubeManager.getInstance().getNQnty() != 0)
					{
						message = "The match will begin shortly.";
						for (L2PcInstance pl : KrateiCubeManager.getInstance().getNPlayers())
						{
							KrateiCubeManager.getInstance().teleWait(pl);
							// Start the countdown
							ThreadPoolManager.getInstance().scheduleGeneral(new TaskManager(Event.count_task, pl, 0), 10);
						}
						// Tele players and start
						ThreadPoolManager.getInstance().scheduleGeneral(new TaskManager(Event.start_task), (debug) ? 30 * 1000 : 3 * 60 * 1000);
					}
					ThreadPoolManager.getInstance().scheduleGeneral(new TaskManager(Event.managermsg_task, message), 10);
					// Start the registration check
					ThreadPoolManager.getInstance().scheduleGeneral(new TaskManager(Event.reg_task), (debug) ? 30 * 1000 : 3 * 60 * 1000);
					break;
				case reg_task:
					int _min = Calendar.getInstance().get(Calendar.MINUTE);
					int _diff = 27 - _min;
					int _diff2 = 57 - _min;
					int _time, c1, c2, min;
					if (KrateiCubeManager.getInstance().isInProgress(0) || KrateiCubeManager.getInstance().isInProgress(1) || KrateiCubeManager.getInstance().isInProgress(2))
					{
						c1 = 0;
						c2 = 30;
					}
					else
					{
						c1 = 20;
						c2 = 50;
					}
					if ((debug || ((_min >= c1) && (_min < 27))) || ((_min >= c2) && (_min < 57)))
					{
						KrateiCubeManager.getInstance().setIsRegTime(true);
						// Get the remaining time to end of registration
						_time = (_diff > 0 ? _diff : _diff2);
						min = _min + _time;
						entranceManager.broadcastPacket(new CreatureSay(entranceManager.getObjectId(), Say2.ALL, entranceManager.getName(), "Registration for the next match will end at " + min + " minutes after the hour"));
						// Start the countdown to end of registration and
						// teleport players to the waiting room
						ThreadPoolManager.getInstance().scheduleGeneral(new TaskManager(Event.tele_task), (debug) ? 60000 : _time * 60 * 1000);
					}
					else
					{
						KrateiCubeManager.getInstance().setIsRegTime(false);
						// Start the check on next minute
						ThreadPoolManager.getInstance().scheduleGeneral(new TaskManager(Event.reg_task), 60 * 1000);
					}
					break;
				case start_task:
					KrateiCubeManager.getInstance().clearQntyCanReward();
					if (KrateiCubeManager.getInstance().getNQnty() > 0)
					{
						KrateiCubeManager.getInstance().setIsInProgress((KrateiCubeManager.getInstance().getNQnty(0) != 0), 0);
						KrateiCubeManager.getInstance().setIsInProgress((KrateiCubeManager.getInstance().getNQnty(1) != 0), 1);
						KrateiCubeManager.getInstance().setIsInProgress((KrateiCubeManager.getInstance().getNQnty(2) != 0), 2);
						
						KrateiCubeManager.getInstance().clearCPlayers();
						for (L2PcInstance pl : KrateiCubeManager.getInstance().getNPlayers())
						{
							pl.sendPacket(new ExPVPMatchCCMyRecord(0));
							KrateiCubeManager.getInstance().addCPlayer(pl);
							KrateiCubeManager.getInstance().initPPoints(pl);
							KrateiCubeManager.getInstance().setCanReward(pl, true);
							KrateiCubeManager.getInstance().teleArena(pl);
						}
						KrateiCubeManager.getInstance().clearNPlayers();
					}
					// ThreadPoolManager.getInstance().scheduleGeneral(new TaskManager(Event.doors_task), 10);
					ThreadPoolManager.getInstance().scheduleGeneral(new TaskManager(Event.spawn_task), 10);
					ThreadPoolManager.getInstance().scheduleGeneral(new TaskManager(Event.end_task), 20 * 60 * 1000);
					break;
				case end_task:
					// ThreadPoolManager.getInstance().scheduleGeneral(new TaskManager(Event.close_doors), 10);
					KrateiCubeManager.getInstance().setIsInProgress(false, 0);
					KrateiCubeManager.getInstance().setIsInProgress(false, 1);
					KrateiCubeManager.getInstance().setIsInProgress(false, 2);
					KrateiCubeManager.getInstance().calcQntyCanReward();
					KrateiCubeManager.getInstance().calcRewardAndTele();
					// despawn watchers
					for (int z = 0; z < 3; z++)
					{
						for (int i = 0; i <= 24; i++)
						{
							L2Npc thisNpc = watchers[z][i];
							thisNpc.deleteMe();
							L2Spawn spawn = thisNpc.getSpawn();
							SpawnTable.getInstance().deleteSpawn(spawn, true);
						}
					}
					// despawn mobs
					for (L2Npc thisNpc : spawned)
					{
						thisNpc.deleteMe();
						L2Spawn spawn = thisNpc.getSpawn();
						SpawnTable.getInstance().deleteSpawn(spawn, true);
					}
					spawned.clear();
					break;
			}
		}
	}
	
	@Override
	public String onAdvEvent(String event, L2Npc npc, L2PcInstance player)
	{
		String htmltext = event;
		
		// Loc for teleport to Fantasy Isle
		if (event.equalsIgnoreCase("Tele"))
		{
			player.teleToLocation(-59193, -56893, -2039, false);
			htmltext = null;
		}
		else if (event.equalsIgnoreCase("TeleArena"))
		{
			KrateiCubeManager.getInstance().teleArena(player);
			htmltext = null;
		}
		else if (event.equalsIgnoreCase("Forfeit"))
		{
			KrateiCubeManager.getInstance().setCanReward(player, false);
			KrateiCubeManager.getInstance().removeCPlayer(player);
			player.sendPacket(new ExPVPMatchCCRetire());
			player.teleToLocation(-70585, -71061, -1421, true);
			htmltext = null;
		}
		else if (event.equalsIgnoreCase("TryEnter"))
		{
			int enterLevel = -1;
			// Check if Player is level 70+
			if (player.getLevel() < 70)
			{
				player.sendMessage("Only players in level 70 or more can join this event");
				return null;
			}
			if ((player.getLevel() >= 70) && (player.getLevel() < 76))
			{
				enterLevel = 0;
			}
			else if ((player.getLevel() >= 76) && (player.getLevel() < 80))
			{
				enterLevel = 1;
			}
			else if (player.getLevel() >= 80)
			{
				enterLevel = 2;
			}
			
			// Check if is not full
			if (KrateiCubeManager.getInstance().getNQnty(enterLevel) == 25)
			{
				player.sendMessage("The limit of players registered already reached the limit");
				return null;
			}
			if (!KrateiCubeManager.getInstance().isRegTime())
			{
				player.sendMessage("Come back when is registration time");
				return null;
			}
			// Check if Event Started
			if (KrateiCubeManager.getInstance().isInProgress(enterLevel))
			{
				return "2.htm";
			}
			
			// Go to TryRegister
			htmltext = null;
			this.startQuestTimer("TryRegister", 1, npc, player);
		}
		else if (event.equalsIgnoreCase("TryRegister"))
		{
			// Check if Player is level 70+
			if (player.getLevel() < 70)
			{
				player.sendMessage("Only players in level 70 or more can join this event");
				return null;
			}
			
			// Check if in Party
			if (player.isInParty())
			{
				return "10.htm";
			}
			// Check if more than 80% of inventory is used
			if (!player.isInventoryUnder90(false))
			{
				return "9.htm";
			}
			// Check if has Cursed Weapon Equipped
			if (player.isCursedWeaponEquipped())
			{
				player.sendMessage("You can not enter with a Cursed Weapon!");
				return null;
			}
			// Check if in Chaotic Stage
			if (player.getKarma() > 0)
			{
				player.sendMessage("You can not enter in Chaotic Stage!");
				return null;
			}
			
			// Check if is in Olympiad
			if (OlympiadManager.getInstance().isRegistered(player))
			{
				player.sendMessage("You can not join KrateisCube while participating on Olympiad.");
				return null;
			}
			// Check if is already registered for Krateis
			if (KrateiCubeManager.getInstance().isRegistered(player))
			{
				return "5.htm";
			}
			// If no issues go to HTML with range(lvl)
			return "3.htm";
		}
		else if (event.startsWith("Register"))
		{
			StringTokenizer st = new StringTokenizer(event, " ");
			st.nextToken(); // "Register"
			int k_level = Integer.parseInt(st.nextToken()); // Get parameter
															// "1", "2" or "3"
			// Check if is in Registration time again
			// To avoid players to exploit
			if (!KrateiCubeManager.getInstance().isRegTime())
			{
				if (!KrateiCubeManager.getInstance().isInProgress(k_level - 1))
				{
					return "8.htm";
				}
				player.sendMessage("Come back when is registration time");
				return null;
			}
			int c_level = player.getLevel(); // get the L2PcInstance's level
			if ((c_level >= 70) && (c_level <= 75) && (k_level != 1))
			{
				return "7.htm";
			}
			else if ((c_level >= 76) && (c_level <= 79) && (k_level != 2))
			{
				return "7.htm";
			}
			else if ((c_level >= 80) && (k_level != 3))
			{
				return "7.htm";
			}
			KrateiCubeManager.getInstance().registerPlayer(player, k_level - 1);
			// If no issues go to Successful registration HTML
			return "4.htm";
		}
		else if (event.equalsIgnoreCase("Cancel"))
		{
			if (KrateiCubeManager.getInstance().isRegistered(player))
			{
				KrateiCubeManager.getInstance().removePlayer(player);
				return "6.htm";
			}
			player.sendMessage("You are not registered for this event.");
			return null;
		}
		return htmltext;
	}
	
	@Override
	public String onFirstTalk(L2Npc npc, L2PcInstance player)
	{
		switch (npc.getId())
		{
			case MATCH1_ID:
			case MATCH2_ID:
			case MATCH3_ID:
				if (KrateiCubeManager.getInstance().isInProgress(player.getQuickVarI(QuickVarType.KRATEI_CUBE_LVL.getCommand(), -1)) && KrateiCubeManager.getInstance().isKrateiParticipant(player))
				{
					return "game.htm";
				}
			default:
				return "main.htm";
		}
	}
	
	@Override
	public String onAttack(L2Npc npc, L2PcInstance attacker, int damage, boolean isPet)
	{
		ThreadPoolManager.getInstance().scheduleGeneral(new TaskManager(Event.respawn_watcher, npc, attacker.getQuickVarI(QuickVarType.KRATEI_CUBE_LVL.getCommand(), -1)), 5 * 1000);
		return null;
	}
	
	@Override
	public String onKill(L2Npc npc, L2PcInstance killer, boolean isPet)
	{
		if (npc.getId() == TURNED)
		{
			ThreadPoolManager.getInstance().scheduleGeneral(new TaskManager(Event.point_task, killer, 50), 10);
		}
		else
		{
			ThreadPoolManager.getInstance().scheduleGeneral(new TaskManager(Event.point_task, killer, 3), 10);
		}
		npc.getSpawn().setRespawnDelay(KrateiCubeManager.getInstance().getPlayerAverageKillTime(killer));
		return null;
	}
	
	protected void setZoneSkill(int zoneId, boolean isBuff)
	{
		L2EffectZone zone = ZoneManager.getInstance().getZoneById(zoneId, L2EffectZone.class);
		zone.clearSkills();
		zone.setParameter("showDangerIcon", "false");
		if (isBuff)
		{
			zone.setParameter("reuse", "15000");
			int[][] skills = ZONE_BUFFS[Rnd.get(7)];
			
			for (int[] sk : skills)
			{
				zone.addSkill(sk[0], sk[1]);
			}
		}
		else
		{
			zone.setParameter("reuse", "6000");
			int[] sk = ZONE_DEBUFFS[Rnd.get(3)];
			zone.addSkill(sk[0], sk[1]);
		}
		zone.startNow();
	}
	
	public KrateiCube()
	{
		super(KrateiCube.class.getSimpleName(), "instances");
		
		addAttackId(REDWATCHER);
		addAttackId(BLUEWATCHER);
		addKillId(TURNED);
		addKillId(MOB_ID);
		addStartNpc(ENTRANCE_ID);
		addFirstTalkId(ENTRANCE_ID);
		addFirstTalkId(MATCH1_ID);
		addFirstTalkId(MATCH2_ID);
		addFirstTalkId(MATCH3_ID);
		addTalkId(ENTRANCE_ID);
		addTalkId(MATCH1_ID);
		addTalkId(MATCH2_ID);
		addTalkId(MATCH3_ID);
		
		// Spawn the Entrance Manager
		entranceManager = addSpawn(ENTRANCE_ID, -70585, -71061, -1421, 63813, false, 0);
		// Start the registration
		ThreadPoolManager.getInstance().scheduleGeneral(new TaskManager(Event.reg_task), 10);
		
		_log.info("KrateiCube: Successfully loaded!");
	}
}
