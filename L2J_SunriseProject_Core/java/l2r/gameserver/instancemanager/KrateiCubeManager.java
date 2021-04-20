package l2r.gameserver.instancemanager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import l2r.Config;
import l2r.L2DatabaseFactory;
import l2r.gameserver.ThreadPoolManager;
import l2r.gameserver.data.xml.impl.SkillData;
import l2r.gameserver.enums.MessageType;
import l2r.gameserver.enums.QuickVarType;
import l2r.gameserver.enums.ZoneIdType;
import l2r.gameserver.model.L2Party;
import l2r.gameserver.model.actor.L2Character;
import l2r.gameserver.model.actor.L2Summon;
import l2r.gameserver.model.actor.instance.L2PcInstance;
import l2r.gameserver.model.actor.instance.L2PetInstance;
import l2r.gameserver.network.SystemMessageId;
import l2r.gameserver.network.serverpackets.ExPVPMatchCCMyRecord;
import l2r.gameserver.network.serverpackets.ExPVPMatchCCRecord;
import l2r.gameserver.network.serverpackets.SystemMessage;
import l2r.util.Rnd;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author vGodFather
 */
public class KrateiCubeManager
{
	private final static Logger LOG = LoggerFactory.getLogger(KrateiCubeManager.class);
	
	private static final String GET_PLAYED_MATCH = "SELECT played_matchs, total_kills, total_coins FROM krateis_cube WHERE charId=?";
	private static final String SAVE_PLAYED_MATCH = "INSERT INTO krateis_cube (charId,played_matchs,total_kills,total_coins) VALUES (?,?,?,?)";
	private static final String UPDATE_PLAYED_MATCH = "UPDATE krateis_cube SET played_matchs = ?, total_kills = ?, total_coins = ? WHERE charId = ?";
	
	// Check Conditions
	private static boolean _isRegPeriod;
	private static boolean[] _isInProgress =
	{
		false,
		false,
		false
	};
	
	// Lists of Participants Players mainly used by registration
	private final List<L2PcInstance> cPlayers = new CopyOnWriteArrayList<>(); // for current players
	private final List<L2PcInstance> nPlayers = new CopyOnWriteArrayList<>(); // for next players
	
	// Map of Participants points
	private final Map<Integer, PlayerScore> pScores = new ConcurrentHashMap<>();
	// Map of if Participants can be Rewarded
	private final Map<L2PcInstance, Boolean> pReward = new ConcurrentHashMap<>();
	// Map of Participants average kill times
	private final Map<Integer, PlayerKillStat> pKillAvg = new ConcurrentHashMap<>();
	
	private int _playerTotalKill = 0;
	private double _playerTotalCoin = 0;
	
	// Reward Item: Fantasy Isle Coin
	private static final int COIN = 13067;
	
	// Amount of players that can be reward.
	@SuppressWarnings("unused")
	private int qntyCanReward = 0;
	
	//@formatter:off
	// Quantity of XP and SP reward per kill point
	// Level 70 - 75 = 1800XP and 180SP per point.	Example: lvl 76 - 79  -  You kill 7 players and 150 monsters,
	// Level 76 - 79 = 2100XP and 210SP per point.	that will get you 520 points, (7*10) + (150*3) = 520
	// Level 80 - 85 = 2740XP and 274SP per point.	(520 points * 2.100 XP) + (520 points * 210 SP) = 1.092.000 XP & 109.200 SP
	private static final int[] XPSP = { 1800, 2100, 2740 };
	private static final int[][] offset = {
		{ 0,0 }, // zone 0, map 1715 - level 70
		{ 33300, -1000 }, // zone 1, map 1815 - level 76
		{ 0, 65408	} // zone 2, map 1717 - level 80
	};
	// Location for waiting room
	private static final int[] LOC_WAIT = { -87117, -81739, -8320 };
	// Locations for spawn players
	private static final int[][] LOC_SPAWN = {
		{ -77906, -85809, -8362 }, { -79903, -85807, -8364 }, { -81904, -85807, -8364 },
		{ -83901, -85806, -8364 }, { -85903, -85807, -8364 }, { -77904, -83808, -8364 },
		{ -79904, -83807, -8364 }, { -81905, -83810, -8364 }, { -83903, -83807, -8364 },
		{ -85899, -83807, -8364 }, { -77903, -81808, -8364 }, { -79906, -81807, -8364 },
		{ -81901, -81808, -8364 }, { -83905, -81805, -8364 }, { -85907, -81809, -8364 },
		{ -77904, -79807, -8364 }, { -79905, -79807, -8364 }, { -81908, -79808, -8364 },
		{ -83907, -79806, -8364 }, { -85912, -79806, -8364 }, { -77905, -77808, -8364 },
		{ -79902, -77805, -8364 }, { -81904, -77808, -8364 }, { -83904, -77808, -8364 },
		{ -85904, -77807, -8364 }
	};
	// Id of buffs that players receive when enter (retail order)
	private static final int[][] BUFFS = {
		{ 1086, 2 }, // Haste
		{ 1204, 2 }, // Wind Walk
		{ 1059, 3 }, // Empower
		{ 1085, 3 }, // Acumen
		{ 1078, 6 }, // Concentration
		{ 1068, 3 }, // Might
		{ 1240, 3 }, // Guidance
		{ 1077, 3 }, // Focus
		{ 1242, 3 }, // Death Whisper
		{ 1062, 2 } // Berserker Spirit
	};
	//@formatter:on
	
	public void calcRewardAndTele()
	{
		final ExPVPMatchCCRecord record = new ExPVPMatchCCRecord(2, sortCPlayersByPoint());
		for (L2PcInstance player : getCPlayers())
		{
			if (getCanReward(player))
			{
				int points = pScores.get(player.getObjectId()).getPoints();
				long base;
				int krateiLevel = Math.min(0, player.getQuickVarI(QuickVarType.KRATEI_CUBE_LVL.getCommand(), -1));
				base = XPSP[krateiLevel];
				base = base * points;
				player.addExpAndSp((int) (base * Config.RATE_XP), (int) ((base / 10) * Config.RATE_SP));
				pReward.remove(player);
				player.teleToLocation(-70310, -71034, -1411, true);
				player.setInsideZone(ZoneIdType.PVP, false);
				if (player.getSummon() != null)
				{
					player.getSummon().setInsideZone(ZoneIdType.PVP, false);
					player.getSummon().teleToLocation(-70310, -71034, -1411, true);
				}
				player.sendPacket(record);
			}
		}
		
		if (getCQnty() < 5)
		{
			// 1-4 participants = 10 Fantasy Coin for each
			for (L2PcInstance player : getCPlayers())
			{
				int playedMatchs = getPlayedMatchs(player);
				if (playedMatchs == 0)
				{
					savePlayedMatchs(player);
				}
				playedMatchs++;
				int amount = 10;
				player.getInventory().addItem("KrateiCube Reward", COIN, amount, player, player);
				player.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.EARNED_S2_S1_S).addItemName(COIN).addLong(amount));
				updatePlayedMatchs(player, playedMatchs, amount);
				removeCPlayer(player);
			}
		}
		else
		{
			int amount = 40;
			List<PlayerScore> sorted = sortCPlayersByPoint();
			for (PlayerScore ps : sorted)
			{
				if (amount <= 0)
				{
					amount = 10;
				}
				L2PcInstance player = ps.getPlayer();
				int playedMatchs = getPlayedMatchs(player);
				if (playedMatchs == 0)
				{
					savePlayedMatchs(player);
				}
				playedMatchs++;
				player.getInventory().addItem("KrateiCube Reward", COIN, amount, player, player);
				player.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.EARNED_S2_S1_S).addItemName(COIN).addLong(amount));
				updatePlayedMatchs(player, playedMatchs, amount);
				removeCPlayer(player);
				amount -= 20;
			}
		}
	}
	
	public void clearPlayer(L2PcInstance player)
	{
		// If dead, set alive
		if (player.isDead())
		{
			player.setIsDead(false);
		}
		// Untransform if transformed
		if (player.isTransformed())
		{
			player.untransform();
		}
		// Abort cast if player is casting
		if (player.isCastingNow())
		{
			player.abortCast();
		}
		// Stand up if player is sitting
		if (player.isSitting())
		{
			player.standUp();
		}
		player.setTarget(null);
		// Force the character to be visible
		player.setInvisible(false);
		// Remove Summon's Buffs
		if (player.getSummon() != null)
		{
			L2Summon summon = player.getSummon();
			summon.stopAllEffects();
			if (summon instanceof L2PetInstance)
			{
				summon.unSummon(player);
			}
		}
		
		// TODO check this function
		// stop any cubic that has been given by other player.
		// player.getCubicManagement().stopCubicsByOthers();
		
		// Remove player from his party
		if (player.getParty() != null)
		{
			L2Party party = player.getParty();
			party.removePartyMember(player, MessageType.None);
		}
		// Remove Agathion
		if (player.getAgathionId() > 0)
		{
			player.setAgathionId(0);
		}
		player.broadcastUserInfo();
	}
	
	public void teleArena(L2PcInstance player)
	{
		// Remove Buffs
		player.stopAllEffectsExceptThoseThatLastThroughDeath();
		// Stand up if player is sitting
		if (player.isSitting())
		{
			player.standUp();
		}
		player.setTarget(null);
		player.clearSouls();
		player.clearCharges();
		// Force the character to be visible
		player.setInvisible(false);
		
		// TODO check this function
		// stop any cubic that has been given by other player.
		// player.getCubicManagement().stopCubicsByOthers();
		
		int[] loc = LOC_SPAWN[Rnd.get(25)];
		int krateiLevel = player.getQuickVarI(QuickVarType.KRATEI_CUBE_LVL.getCommand(), -1);
		for (int[] _buffs : BUFFS)
		{
			SkillData.getInstance().getInfo(_buffs[0], _buffs[1]).getEffects(player, player);
		}
		// Heal Player fully
		player.setCurrentCp(player.getMaxCp());
		player.setCurrentHp(player.getMaxHp());
		player.setCurrentMp(player.getMaxMp());
		player.broadcastUserInfo();
		// teleport to arena
		if (player.getSummon() != null)
		{
			L2Summon summon = player.getSummon();
			summon.teleToLocation(loc[0] + offset[krateiLevel][0], loc[1] + offset[krateiLevel][1], loc[2], true);
			summon.setInsideZone(ZoneIdType.PVP, true);
		}
		player.teleToLocation(loc[0] + offset[krateiLevel][0], loc[1] + offset[krateiLevel][1], loc[2], true);
		player.setInsideZone(ZoneIdType.PVP, true);
	}
	
	public void teleWait(L2PcInstance player)
	{
		clearPlayer(player);
		int krateiLevel = player.getQuickVarI(QuickVarType.KRATEI_CUBE_LVL.getCommand(), -1);
		player.teleToLocation(LOC_WAIT[0] + offset[krateiLevel][0], LOC_WAIT[1] + offset[krateiLevel][1], LOC_WAIT[2], true);
	}
	
	public List<PlayerScore> sortCPlayersByPoint()
	{
		List<PlayerScore> sorted = new ArrayList<>(pScores.values());
		Collections.sort(sorted, (p1, p2) -> new Integer(p1._points).compareTo(new Integer(p2._points)));
		return sorted;
	}
	
	public void onDisconnect(L2PcInstance player)
	{
		if (cPlayers.contains(player))
		{
			removeCPlayer(player);
		}
		else if (nPlayers.contains(player))
		{
			removePlayer(player);
		}
	}
	
	public void onPlayerKill(L2Character killer, L2PcInstance killed)
	{
		int krateiLevel = killed.getQuickVarI(QuickVarType.KRATEI_CUBE_LVL.getCommand(), -1);
		if (!_isInProgress[krateiLevel])
		{
			return;
		}
		
		L2PcInstance player;
		if (killer instanceof L2Summon)
		{
			player = ((L2Summon) killer).getOwner();
			if (player == null)
			{
				return;
			}
		}
		if (killer instanceof L2PcInstance)
		{
			player = (L2PcInstance) killer;
			if (isKrateiParticipant(player) && isKrateiParticipant(killed))
			{
				addPoints(player, 10);
			}
		}
		ThreadPoolManager.getInstance().scheduleGeneral(new Revive(killed, LOC_WAIT[0] + offset[krateiLevel][0], LOC_WAIT[1] + offset[krateiLevel][1], LOC_WAIT[2]), 5000);
	}
	
	public int getCQnty()
	{
		return cPlayers.size();
	}
	
	public int getNQnty()
	{
		return nPlayers.size();
	}
	
	public int getNQnty(int krateiLevel)
	{
		int NQty = 0;
		for (L2PcInstance pc : nPlayers)
		{
			if (pc.getQuickVarI(QuickVarType.KRATEI_CUBE_LVL.getCommand(), -1) == krateiLevel)
			{
				NQty++;
			}
		}
		return NQty;
	}
	
	public List<L2PcInstance> getCPlayers()
	{
		return cPlayers;
	}
	
	public List<L2PcInstance> getCPlayers(int krateiLevel)
	{
		List<L2PcInstance> retList = new CopyOnWriteArrayList<>();
		
		for (L2PcInstance pc : cPlayers)
		{
			if (pc.getQuickVarI(QuickVarType.KRATEI_CUBE_LVL.getCommand(), -1) == krateiLevel)
			{
				retList.add(pc);
			}
		}
		return retList;
	}
	
	public List<L2PcInstance> getNPlayers()
	{
		return nPlayers;
	}
	
	public int getPlayerAverageKillTime(L2PcInstance player)
	{
		if (!pKillAvg.containsKey(player.getObjectId()) || (pKillAvg.get(player.getObjectId()) == null) || (pKillAvg.get(player.getObjectId()).getAverageKillTime() > 60000))
		{
			return 60000; // 1 Minute maximum respawn time
		}
		return pKillAvg.get(player.getObjectId()).getAverageKillTime();
	}
	
	public void addPoints(L2PcInstance player, int points)
	{
		pKillAvg.get(player.getObjectId()).increaseKillAndCalculateRespawn();
		pScores.get(player.getObjectId()).increasePoints(points);
		player.sendPacket(new ExPVPMatchCCMyRecord(pScores.get(player.getObjectId()).getPoints()));
	}
	
	public void registerPlayer(L2PcInstance player, int krateiLevel)
	{
		nPlayers.add(player);
		player.setQuickVar(QuickVarType.KRATEI_CUBE_LVL.getCommand(), krateiLevel);
	}
	
	public void removePlayer(L2PcInstance player)
	{
		nPlayers.remove(player);
		player.deleteQuickVar(QuickVarType.KRATEI_CUBE_LVL.getCommand());
	}
	
	public void clearNPlayers()
	{
		nPlayers.clear();
	}
	
	public void clearCPlayers()
	{
		cPlayers.clear();
	}
	
	public void initPPoints(L2PcInstance player)
	{
		pKillAvg.put(player.getObjectId(), new PlayerKillStat(player));
		pScores.put(player.getObjectId(), new PlayerScore(player));
	}
	
	public void addCPlayer(L2PcInstance player)
	{
		cPlayers.add(player);
	}
	
	public void removeCPlayer(L2PcInstance player)
	{
		cPlayers.remove(player);
		pReward.remove(player);
		pKillAvg.remove(player.getObjectId());
		pScores.remove(player.getObjectId());
	}
	
	public boolean isRegistered(L2PcInstance player)
	{
		return nPlayers.contains(player);
	}
	
	public boolean isKrateiParticipant(L2PcInstance player)
	{
		return cPlayers.contains(player);
	}
	
	public boolean isRegTime()
	{
		return _isRegPeriod;
	}
	
	public void setIsRegTime(boolean b)
	{
		_isRegPeriod = b;
	}
	
	public boolean isInProgress(int krateiLevel)
	{
		if ((krateiLevel >= 0) && (krateiLevel < 3))
		{
			return _isInProgress[krateiLevel];
		}
		
		return false;
	}
	
	public void setIsInProgress(boolean b, int krateiLevel)
	{
		_isInProgress[krateiLevel] = b;
	}
	
	public boolean getCanReward(L2PcInstance player)
	{
		return pReward.get(player) ? true : false;
	}
	
	public void setCanReward(L2PcInstance player, boolean b)
	{
		pReward.put(player, b);
	}
	
	public void calcQntyCanReward()
	{
		for (L2PcInstance pl : cPlayers)
		{
			if (getCanReward(pl))
			{
				qntyCanReward++;
			}
		}
	}
	
	public void clearQntyCanReward()
	{
		qntyCanReward = 0;
	}
	
	public static class PlayerKillStat
	{
		private final int ownerId;
		private int kills;
		private long lastKillTime;
		private int avgKillTime;
		
		public PlayerKillStat(L2PcInstance player)
		{
			ownerId = player.getObjectId();
			kills = 0;
			lastKillTime = System.currentTimeMillis();
			avgKillTime = 0;
		}
		
		public void increaseKillAndCalculateRespawn()
		{
			long prevKillTime = lastKillTime;
			lastKillTime = System.currentTimeMillis();
			kills++;
			long currKillTime = lastKillTime - prevKillTime;
			avgKillTime = (int) ((avgKillTime + currKillTime) / kills);
		}
		
		public int getAverageKillTime()
		{
			return avgKillTime;
		}
		
		public int getOwnerId()
		{
			return ownerId;
		}
		
		public int getKills()
		{
			return kills;
		}
	}
	
	public static class PlayerScore
	{
		private final L2PcInstance _player;
		protected int _points;
		
		public PlayerScore(L2PcInstance player)
		{
			_player = player;
			_points = 0;
		}
		
		public L2PcInstance getPlayer()
		{
			return _player;
		}
		
		public void increasePoints(int pointsToAdd)
		{
			_points += pointsToAdd;
		}
		
		public int getObjectId()
		{
			return _player.getObjectId();
		}
		
		public String getName()
		{
			return _player.getName();
		}
		
		public int getPoints()
		{
			return _points;
		}
	}
	
	public static final KrateiCubeManager getInstance()
	{
		return SingletonHolder._instance;
	}
	
	private static class SingletonHolder
	{
		protected static final KrateiCubeManager _instance = new KrateiCubeManager();
	}
	
	private void updatePlayedMatchs(L2PcInstance player, int playedMatchs, double amount)
	{
		Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection();
			PreparedStatement statement = con.prepareStatement(UPDATE_PLAYED_MATCH);
			
			statement.setInt(1, playedMatchs);
			statement.setInt(2, _playerTotalKill + pKillAvg.get(player.getObjectId()).getKills());
			statement.setDouble(3, _playerTotalCoin + amount);
			statement.setInt(4, player.getObjectId());
			statement.execute();
		}
		catch (Exception e)
		{
			LOG.warn("Could not update character played Krateis Cube matchs", e);
		}
	}
	
	private void savePlayedMatchs(L2PcInstance player)
	{
		Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection();
			PreparedStatement statement = con.prepareStatement(SAVE_PLAYED_MATCH);
			
			statement.setInt(1, player.getObjectId());
			statement.setInt(2, 0);
			statement.setInt(3, pKillAvg.get(player.getObjectId()).getKills());
			statement.setDouble(4, 0);
			statement.execute();
		}
		catch (Exception e)
		{
			LOG.warn("Could not store character krateis cube played matchs", e);
		}
	}
	
	private int getPlayedMatchs(L2PcInstance player)
	{
		Connection con = null;
		int playedMatchs = 0;
		
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection();
			PreparedStatement statement = con.prepareStatement(GET_PLAYED_MATCH);
			
			statement.setInt(1, player.getObjectId());
			
			ResultSet rset = statement.executeQuery();
			while (rset.next())
			{
				playedMatchs = rset.getInt("played_matchs");
				_playerTotalKill = rset.getInt("total_kills");
				_playerTotalCoin = rset.getDouble("total_coins");
			}
		}
		catch (Exception e)
		{
			LOG.warn("Could not get player total Krateis Cube played matchs", e);
		}
		return playedMatchs;
	}
	
	public class Revive implements Runnable
	{
		L2PcInstance _player;
		int _X, _Y, _Z;
		
		public Revive(L2PcInstance player, int x, int y, int z)
		{
			_player = player;
			_X = x;
			_Y = y;
			_Z = z;
		}
		
		@Override
		public void run()
		{
			if (_player == null)
			{
				return;
			}
			
			L2Summon summon = _player.getSummon();
			
			if (summon != null)
			{
				summon.unSummon(_player);
			}
			
			_player.teleToLocation(_X, _Y, _Z, true);
			_player.doRevive();
		}
	}
}
