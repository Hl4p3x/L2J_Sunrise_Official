package l2r.gameserver.instancemanager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import l2r.Config;
import l2r.L2DatabaseFactory;
import l2r.gameserver.ThreadPoolManager;
import l2r.gameserver.data.xml.impl.ItemData;
import l2r.gameserver.model.actor.L2Npc;
import l2r.gameserver.model.actor.instance.L2PcInstance;
import l2r.gameserver.network.SystemMessageId;
import l2r.gameserver.network.serverpackets.NpcHtmlMessage;
import l2r.gameserver.network.serverpackets.SystemMessage;

import gr.sr.utils.Rnd;

public class FishingChampionshipManager
{
	protected static final Logger LOGGER = Logger.getLogger(FishingChampionshipManager.class.getName());
	
	private static final String INSERT = "INSERT INTO fishing_championship(player_name,fish_length,rewarded) VALUES (?,?,?)";
	private static final String DELETE = "DELETE FROM fishing_championship";
	private static final String SELECT = "SELECT `player_name`, `fish_length`, `rewarded` FROM fishing_championship";
	
	private static final FishingChampionshipManager INSTANCE = new FishingChampionshipManager();
	
	public static FishingChampionshipManager getInstance()
	{
		return INSTANCE;
	}
	
	protected long _enddate = 0;
	protected final List<String> _playersName = new ArrayList<>();
	protected final List<String> _fishLength = new ArrayList<>();
	protected final List<String> _winPlayersName = new ArrayList<>();
	protected final List<String> _winFishLength = new ArrayList<>();
	protected final List<Fisher> _tmpPlayers = new ArrayList<>();
	protected final List<Fisher> _winPlayers = new ArrayList<>();
	protected double _minFishLength = 0;
	protected boolean _needRefresh = true;
	
	private FishingChampionshipManager()
	{
		restoreData();
		refreshWinResult();
		recalculateMinLength();
		
		if (_enddate <= System.currentTimeMillis())
		{
			_enddate = System.currentTimeMillis();
			new finishChamp().run();
		}
		else
		{
			ThreadPoolManager.getInstance().scheduleGeneral(new finishChamp(), _enddate - System.currentTimeMillis());
		}
	}
	
	protected void setEndOfChamp()
	{
		final Calendar finishtime = Calendar.getInstance();
		finishtime.setTimeInMillis(_enddate);
		finishtime.set(Calendar.MINUTE, 0);
		finishtime.set(Calendar.SECOND, 0);
		finishtime.add(Calendar.DAY_OF_MONTH, 6);
		finishtime.set(Calendar.DAY_OF_WEEK, 3);
		finishtime.set(Calendar.HOUR_OF_DAY, 19);
		_enddate = finishtime.getTimeInMillis();
	}
	
	private void restoreData()
	{
		_enddate = GlobalVariablesManager.getInstance().getLong("fishChampionshipEnd", 0);
		
		try (Connection con = L2DatabaseFactory.getInstance().getConnection();
			final PreparedStatement statement = con.prepareStatement(SELECT))
		{
			try (ResultSet rs = statement.executeQuery())
			{
				while (rs.next())
				{
					final int rewarded = rs.getInt("rewarded");
					if (rewarded == 0)
					{
						_tmpPlayers.add(new Fisher(rs.getString("player_name"), rs.getDouble("fish_length"), 0));
					}
					else if (rewarded > 0)
					{
						_winPlayers.add(new Fisher(rs.getString("player_name"), rs.getDouble("fish_length"), rewarded));
					}
				}
			}
		}
		catch (SQLException e)
		{
			LOGGER.log(Level.WARNING, "FishingChampionshipManager: can't restore fishing championship info: " + e.getMessage(), e);
		}
	}
	
	public synchronized void newFish(L2PcInstance pl, int lureId)
	{
		if (!Config.ALT_FISH_CHAMPIONSHIP_ENABLED)
		{
			return;
		}
		
		double len = Rnd.get(60, 89) + (Rnd.get(0, 1000) / 1000.);
		if ((lureId >= 8484) && (lureId <= 8486))
		{
			len += Rnd.get(0, 3000) / 1000.;
		}
		
		SystemMessage sm = SystemMessage.getSystemMessage(SystemMessageId.CAUGHT_FISH_S1_LENGTH);
		sm.addInt((int) len);
		pl.sendPacket(sm);
		
		if (_tmpPlayers.size() < 5)
		{
			for (Fisher fisher : _tmpPlayers)
			{
				if (fisher.getName().equalsIgnoreCase(pl.getName()))
				{
					if (fisher.getLength() < len)
					{
						fisher.setLength(len);
						pl.sendPacket(SystemMessageId.REGISTERED_IN_FISH_SIZE_RANKING);
						recalculateMinLength();
					}
					return;
				}
			}
			_tmpPlayers.add(new Fisher(pl.getName(), len, 0));
			pl.sendPacket(SystemMessageId.REGISTERED_IN_FISH_SIZE_RANKING);
			recalculateMinLength();
		}
		else if (_minFishLength < len)
		{
			for (Fisher fisher : _tmpPlayers)
			{
				if (fisher.getName().equalsIgnoreCase(pl.getName()))
				{
					if (fisher.getLength() < len)
					{
						fisher.setLength(len);
						pl.sendPacket(SystemMessageId.REGISTERED_IN_FISH_SIZE_RANKING);
						recalculateMinLength();
					}
					return;
				}
			}
			
			Fisher minFisher = null;
			double minLen = 99999.;
			for (Fisher fisher : _tmpPlayers)
			{
				if (fisher.getLength() < minLen)
				{
					minFisher = fisher;
					minLen = minFisher.getLength();
				}
			}
			_tmpPlayers.remove(minFisher);
			_tmpPlayers.add(new Fisher(pl.getName(), len, 0));
			pl.sendPacket(SystemMessageId.REGISTERED_IN_FISH_SIZE_RANKING);
			recalculateMinLength();
		}
	}
	
	private void recalculateMinLength()
	{
		double minLen = 99999.;
		for (Fisher fisher : _tmpPlayers)
		{
			if (fisher.getLength() < minLen)
			{
				minLen = fisher.getLength();
			}
		}
		_minFishLength = minLen;
	}
	
	public long getTimeRemaining()
	{
		return (_enddate - System.currentTimeMillis()) / 60000;
	}
	
	public String getWinnerName(int par)
	{
		return _winPlayersName.size() >= par ? _winPlayersName.get(par - 1) : "None";
	}
	
	public String getCurrentName(int par)
	{
		return _playersName.size() >= par ? _playersName.get(par - 1) : "None";
	}
	
	public String getFishLength(int par)
	{
		return _winFishLength.size() >= par ? _winFishLength.get(par - 1) : "0";
	}
	
	public String getCurrentFishLength(int par)
	{
		return _fishLength.size() >= par ? _fishLength.get(par - 1) : "0";
	}
	
	public boolean isWinner(String playerName)
	{
		for (String name : _winPlayersName)
		{
			if (name.equals(playerName))
			{
				return true;
			}
		}
		return false;
	}
	
	public void getReward(L2PcInstance pl)
	{
		for (Fisher fisher : _winPlayers)
		{
			if (fisher.getName().equalsIgnoreCase(pl.getName()) && (fisher.getRewardType() != 2))
			{
				int rewardCnt = 0;
				for (int x = 0; x < _winPlayersName.size(); x++)
				{
					if (_winPlayersName.get(x).equalsIgnoreCase(pl.getName()))
					{
						switch (x)
						{
							case 0:
							{
								rewardCnt = Config.ALT_FISH_CHAMPIONSHIP_REWARD_1;
								break;
							}
							case 1:
							{
								rewardCnt = Config.ALT_FISH_CHAMPIONSHIP_REWARD_2;
								break;
							}
							case 2:
							{
								rewardCnt = Config.ALT_FISH_CHAMPIONSHIP_REWARD_3;
								break;
							}
							case 3:
							{
								rewardCnt = Config.ALT_FISH_CHAMPIONSHIP_REWARD_4;
								break;
							}
							case 4:
							{
								rewardCnt = Config.ALT_FISH_CHAMPIONSHIP_REWARD_5;
								break;
							}
						}
					}
				}
				fisher.setRewardType(2);
				if (rewardCnt > 0)
				{
					pl.addItem("fishing_reward", Config.ALT_FISH_CHAMPIONSHIP_REWARD_ITEM, rewardCnt, null, true);
					
					final NpcHtmlMessage html = new NpcHtmlMessage();
					html.setFile(pl, pl.getHtmlPrefix(), "data/scripts/ai/npc/Fisherman/fish_event_reward001.htm");
					pl.sendPacket(html);
				}
			}
		}
	}
	
	public void showMidResult(L2PcInstance pl)
	{
		final NpcHtmlMessage html = new NpcHtmlMessage();
		
		if (_needRefresh)
		{
			html.setFile(pl, pl.getHtmlPrefix(), "data/scripts/ai/npc/Fisherman/fish_event003.htm");
			pl.sendPacket(html);
			
			refreshResult();
			ThreadPoolManager.getInstance().scheduleGeneral(new needRefresh(), 60000);
			return;
		}
		
		html.setFile(pl, pl.getHtmlPrefix(), "data/scripts/ai/npc/Fisherman/fish_event002.htm");
		
		String str = null;
		for (int x = 1; x <= 5; x++)
		{
			str += "<tr><td width=70 align=center>" + x + "</td>";
			str += "<td width=110 align=center>" + getCurrentName(x) + "</td>";
			str += "<td width=80 align=center>" + getCurrentFishLength(x) + "</td></tr>";
		}
		html.replace("%TABLE%", str);
		html.replace("%prizeItem%", ItemData.getInstance().getTemplate(Config.ALT_FISH_CHAMPIONSHIP_REWARD_ITEM).getName());
		html.replace("%prizeFirst%", String.valueOf(Config.ALT_FISH_CHAMPIONSHIP_REWARD_1));
		html.replace("%prizeTwo%", String.valueOf(Config.ALT_FISH_CHAMPIONSHIP_REWARD_2));
		html.replace("%prizeThree%", String.valueOf(Config.ALT_FISH_CHAMPIONSHIP_REWARD_3));
		html.replace("%prizeFour%", String.valueOf(Config.ALT_FISH_CHAMPIONSHIP_REWARD_4));
		html.replace("%prizeFive%", String.valueOf(Config.ALT_FISH_CHAMPIONSHIP_REWARD_5));
		pl.sendPacket(html);
	}
	
	public void showChampScreen(L2PcInstance pl, L2Npc npc)
	{
		final NpcHtmlMessage html = new NpcHtmlMessage();
		html.setFile(pl, pl.getHtmlPrefix(), "data/scripts/ai/npc/Fisherman/fish_event001.htm");
		
		String str = null;
		for (int x = 1; x <= 5; x++)
		{
			str += "<tr><td width=70 align=center>" + x + "</td>";
			str += "<td width=110 align=center>" + getWinnerName(x) + "</td>";
			str += "<td width=80 align=center>" + getFishLength(x) + "</td></tr>";
		}
		html.replace("%TABLE%", str);
		html.replace("%prizeItem%", ItemData.getInstance().getTemplate(Config.ALT_FISH_CHAMPIONSHIP_REWARD_ITEM).getName());
		html.replace("%prizeFirst%", String.valueOf(Config.ALT_FISH_CHAMPIONSHIP_REWARD_1));
		html.replace("%prizeTwo%", String.valueOf(Config.ALT_FISH_CHAMPIONSHIP_REWARD_2));
		html.replace("%prizeThree%", String.valueOf(Config.ALT_FISH_CHAMPIONSHIP_REWARD_3));
		html.replace("%prizeFour%", String.valueOf(Config.ALT_FISH_CHAMPIONSHIP_REWARD_4));
		html.replace("%prizeFive%", String.valueOf(Config.ALT_FISH_CHAMPIONSHIP_REWARD_5));
		html.replace("%refresh%", String.valueOf(getTimeRemaining()));
		html.replace("%objectId%", String.valueOf(npc.getObjectId()));
		pl.sendPacket(html);
	}
	
	public void shutdown()
	{
		GlobalVariablesManager.getInstance().set("fishChampionshipEnd", _enddate);
		
		try (Connection con = L2DatabaseFactory.getInstance().getConnection();
			PreparedStatement st = con.prepareStatement(DELETE))
		{
			st.execute();
			st.close();
			
			for (Fisher fisher : _winPlayers)
			{
				try (PreparedStatement statement = con.prepareStatement(INSERT))
				{
					statement.setString(1, fisher.getName());
					statement.setDouble(2, fisher.getLength());
					statement.setInt(3, fisher.getRewardType());
					statement.execute();
				}
			}
			
			for (Fisher fisher : _tmpPlayers)
			{
				try (PreparedStatement statement = con.prepareStatement(INSERT))
				{
					statement.setString(1, fisher.getName());
					statement.setDouble(2, fisher.getLength());
					statement.setInt(3, 0);
					statement.execute();
				}
			}
		}
		catch (SQLException e)
		{
			LOGGER.log(Level.WARNING, "FishingChampionshipManager: can't update infos: " + e.getMessage(), e);
		}
	}
	
	private synchronized void refreshResult()
	{
		_needRefresh = false;
		
		_playersName.clear();
		_fishLength.clear();
		
		Fisher fisher1;
		Fisher fisher2;
		
		for (int x = 0; x <= (_tmpPlayers.size() - 1); x++)
		{
			for (int y = 0; y <= (_tmpPlayers.size() - 2); y++)
			{
				fisher1 = _tmpPlayers.get(y);
				fisher2 = _tmpPlayers.get(y + 1);
				if (fisher1.getLength() < fisher2.getLength())
				{
					_tmpPlayers.set(y, fisher2);
					_tmpPlayers.set(y + 1, fisher1);
				}
			}
		}
		
		for (int x = 0; x <= (_tmpPlayers.size() - 1); x++)
		{
			_playersName.add(_tmpPlayers.get(x).getName());
			_fishLength.add(String.valueOf(_tmpPlayers.get(x).getLength()));
		}
	}
	
	protected void refreshWinResult()
	{
		_winPlayersName.clear();
		_winFishLength.clear();
		
		Fisher fisher1;
		Fisher fisher2;
		
		for (int x = 0; x <= (_winPlayers.size() - 1); x++)
		{
			for (int y = 0; y <= (_winPlayers.size() - 2); y++)
			{
				fisher1 = _winPlayers.get(y);
				fisher2 = _winPlayers.get(y + 1);
				if (fisher1.getLength() < fisher2.getLength())
				{
					_winPlayers.set(y, fisher2);
					_winPlayers.set(y + 1, fisher1);
				}
			}
		}
		
		for (int x = 0; x <= (_winPlayers.size() - 1); x++)
		{
			_winPlayersName.add(_winPlayers.get(x).getName());
			_winFishLength.add(String.valueOf(_winPlayers.get(x).getLength()));
		}
	}
	
	private class finishChamp implements Runnable
	{
		protected finishChamp()
		{
			// Do nothing
		}
		
		@Override
		public void run()
		{
			_winPlayers.clear();
			for (Fisher fisher : _tmpPlayers)
			{
				fisher.setRewardType(1);
				_winPlayers.add(fisher);
			}
			_tmpPlayers.clear();
			
			refreshWinResult();
			setEndOfChamp();
			shutdown();
			
			LOGGER.info("FishingChampionshipManager : new event period start.");
			ThreadPoolManager.getInstance().scheduleGeneral(new finishChamp(), _enddate - System.currentTimeMillis());
		}
	}
	
	private class needRefresh implements Runnable
	{
		protected needRefresh()
		{
			// Do nothing
		}
		
		@Override
		public void run()
		{
			_needRefresh = true;
		}
	}
	
	private class Fisher
	{
		private double _length;
		private final String _name;
		private int _reward;
		
		public Fisher(String name, double length, int rewardType)
		{
			_name = name;
			_length = length;
			_reward = rewardType;
		}
		
		public void setLength(double value)
		{
			_length = value;
		}
		
		public void setRewardType(int value)
		{
			_reward = value;
		}
		
		public String getName()
		{
			return _name;
		}
		
		public int getRewardType()
		{
			return _reward;
		}
		
		public double getLength()
		{
			return _length;
		}
	}
}