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
package l2r.gameserver.network;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import l2r.Config;
import l2r.L2DatabaseFactory;
import l2r.gameserver.LoginServerThread;
import l2r.gameserver.LoginServerThread.SessionKey;
import l2r.gameserver.ThreadPoolManager;
import l2r.gameserver.data.sql.CharNameTable;
import l2r.gameserver.data.sql.ClanTable;
import l2r.gameserver.data.xml.impl.SecondaryAuthData;
import l2r.gameserver.enums.PcCondOverride;
import l2r.gameserver.enums.ZoneIdType;
import l2r.gameserver.instancemanager.AntiFeedManager;
import l2r.gameserver.model.CharSelectInfoPackage;
import l2r.gameserver.model.L2Clan;
import l2r.gameserver.model.L2World;
import l2r.gameserver.model.actor.instance.L2PcInstance;
import l2r.gameserver.model.entity.olympiad.OlympiadManager;
import l2r.gameserver.network.serverpackets.L2GameServerPacket;
import l2r.gameserver.network.serverpackets.ServerClose;
import l2r.gameserver.security.SecondaryPasswordAuth;
import l2r.gameserver.util.FloodProtectors;
import l2r.gameserver.util.Util;

import gr.sr.interf.SunriseEvents;
import gr.sr.main.PacketsDebugger;
import gr.sr.protection.Protection;
import gr.sr.securityEngine.SecurityActions;
import gr.sr.securityEngine.SecurityType;

import com.l2jserver.mmocore.MMOClient;
import com.l2jserver.mmocore.MMOConnection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents a client connected on Game Server.
 * @author KenM
 */
public class L2GameClient extends MMOClient<MMOConnection<L2GameClient>>
{
	protected static final Logger _log = LoggerFactory.getLogger(L2GameClient.class);
	protected static final java.util.logging.Logger _logAccounting = java.util.logging.Logger.getLogger("accounting");
	
	/**
	 * @author KenM
	 */
	public static enum GameClientState
	{
		/** Client has just connected . */
		CONNECTED,
		/** Client has authed but doesn't has character attached to it yet. */
		AUTHED,
		/** Client has selected a char and is in game. */
		IN_GAME
	}
	
	private GameClientState _state;
	
	// Info
	private InetAddress _addr;
	private String _accountName;
	private SessionKey _sessionId;
	private L2PcInstance _activeChar;
	private final ReentrantLock _activeCharLock = new ReentrantLock();
	private SecondaryPasswordAuth _secondaryAuth;
	
	private boolean _isAuthedGG;
	private final long _connectionStartTime;
	private List<CharSelectInfoPackage> _charSlotMapping = null;
	
	// flood protectors
	private final FloodProtectors _floodProtectors = new FloodProtectors(this);
	
	// Task
	protected final ScheduledFuture<?> _autoSaveInDB;
	protected ScheduledFuture<?> _cleanupTask = null;
	
	// Crypt
	private final GameCrypt _crypt;
	
	private boolean _isDetached = false;
	
	private boolean _protocol;
	
	private int[][] trace;
	
	public L2GameClient(MMOConnection<L2GameClient> con)
	{
		super(con);
		_state = GameClientState.CONNECTED;
		_connectionStartTime = System.currentTimeMillis();
		_crypt = new GameCrypt();
		
		if (Config.CHAR_STORE_INTERVAL > 0)
		{
			_autoSaveInDB = ThreadPoolManager.getInstance().scheduleGeneralAtFixedRate(new AutoSaveTask(), 300000L, (Config.CHAR_STORE_INTERVAL * 60000L));
		}
		else
		{
			_autoSaveInDB = null;
		}
		
		try
		{
			_addr = con != null ? con.getInetAddress() : InetAddress.getLocalHost();
		}
		catch (UnknownHostException e)
		{
			throw new Error("Unable to determine localhost address.");
		}
	}
	
	public byte[] enableCrypt()
	{
		byte[] key = BlowFishKeygen.getRandomKey();
		_crypt.setKey(key);
		
		if (Protection.isProtectionOn())
		{
			key = Protection.getKey(key);
		}
		
		return key;
	}
	
	public GameClientState getState()
	{
		return _state;
	}
	
	public void setState(GameClientState state)
	{
		_state = state;
	}
	
	/**
	 * For loaded offline traders returns localhost address.
	 * @return cached connection IP address, for checking detached clients.
	 */
	public InetAddress getConnectionAddress()
	{
		return _addr;
	}
	
	public long getConnectionStartTime()
	{
		return _connectionStartTime;
	}
	
	@Override
	public boolean decrypt(ByteBuffer buf, int size)
	{
		_crypt.decrypt(buf.array(), buf.position(), size);
		return true;
	}
	
	@Override
	public boolean encrypt(final ByteBuffer buf, final int size)
	{
		_crypt.encrypt(buf.array(), buf.position(), size);
		buf.position(buf.position() + size);
		return true;
	}
	
	public L2PcInstance getActiveChar()
	{
		return _activeChar;
	}
	
	public void setActiveChar(L2PcInstance pActiveChar)
	{
		_activeChar = pActiveChar;
	}
	
	public ReentrantLock getActiveCharLock()
	{
		return _activeCharLock;
	}
	
	public FloodProtectors getFloodProtectors()
	{
		return _floodProtectors;
	}
	
	public void setGameGuardOk(boolean val)
	{
		_isAuthedGG = val;
	}
	
	public boolean isAuthedGG()
	{
		return _isAuthedGG;
	}
	
	public void setAccountName(String pAccountName)
	{
		_accountName = pAccountName;
		
		if (SecondaryAuthData.getInstance().isEnabled())
		{
			_secondaryAuth = new SecondaryPasswordAuth(this);
		}
	}
	
	public String getAccountName()
	{
		return _accountName;
	}
	
	public void setSessionId(SessionKey sk)
	{
		_sessionId = sk;
	}
	
	public SessionKey getSessionId()
	{
		return _sessionId;
	}
	
	public void sendPacket(L2GameServerPacket gsp)
	{
		if (_isDetached || (gsp == null))
		{
			return;
		}
		
		// Packets from invisible chars sends only to GMs
		if (gsp.isInvisible() && (getActiveChar() != null) && !getActiveChar().canOverrideCond(PcCondOverride.SEE_ALL_PLAYERS))
		{
			return;
		}
		
		PacketsDebugger.checkDebugger(gsp);
		
		getConnection().sendPacket(gsp);
		gsp.runImpl();
	}
	
	public boolean isDetached()
	{
		return _isDetached;
	}
	
	public void setDetached(boolean b)
	{
		_isDetached = b;
	}
	
	/**
	 * Method to handle character deletion
	 * @param charslot
	 * @return a byte:
	 *         <li>-1: Error: No char was found for such charslot, caught exception, etc...
	 *         <li>0: character is not member of any clan, proceed with deletion
	 *         <li>1: character is member of a clan, but not clan leader
	 *         <li>2: character is clan leader
	 */
	public byte markToDeleteChar(int charslot)
	{
		int objid = getObjectIdForSlot(charslot);
		
		if (objid < 0)
		{
			return -1;
		}
		
		try (Connection con = L2DatabaseFactory.getInstance().getConnection();
			PreparedStatement statement = con.prepareStatement("SELECT clanId FROM characters WHERE charId=?"))
		{
			statement.setInt(1, objid);
			byte answer = 0;
			try (ResultSet rs = statement.executeQuery())
			{
				int clanId = rs.next() ? rs.getInt(1) : 0;
				if (clanId != 0)
				{
					L2Clan clan = ClanTable.getInstance().getClan(clanId);
					
					if (clan == null)
					{
						answer = 0; // jeezes!
					}
					else if (clan.getLeaderId() == objid)
					{
						answer = 2;
					}
					else
					{
						answer = 1;
					}
				}
				
				// Setting delete time
				if (answer == 0)
				{
					if (Config.DELETE_DAYS == 0)
					{
						deleteCharByObjId(objid);
					}
					else
					{
						try (PreparedStatement ps2 = con.prepareStatement("UPDATE characters SET deletetime=? WHERE charId=?"))
						{
							ps2.setLong(1, System.currentTimeMillis() + (Config.DELETE_DAYS * 86400000L)); // 24*60*60*1000 = 86400000
							ps2.setInt(2, objid);
							ps2.execute();
						}
					}
					
					LogRecord record = new LogRecord(Level.WARNING, "Delete");
					record.setParameters(new Object[]
					{
						objid,
						this
					});
					_logAccounting.log(record);
					
				}
			}
			return answer;
		}
		catch (Exception e)
		{
			_log.error("Error updating delete time of character.", e);
			return -1;
		}
	}
	
	/**
	 * Save the L2PcInstance to the database.
	 */
	public void saveCharToDisk()
	{
		try
		{
			if (getActiveChar() != null)
			{
				getActiveChar().store();
				getActiveChar().storeRecommendations();
				if (Config.UPDATE_ITEMS_ON_CHAR_STORE)
				{
					getActiveChar().getInventory().updateDatabase();
					getActiveChar().getWarehouse().updateDatabase();
				}
			}
		}
		catch (Exception e)
		{
			_log.error("Error saving character..", e);
		}
	}
	
	public void markRestoredChar(int charslot)
	{
		final int objid = getObjectIdForSlot(charslot);
		if (objid < 0)
		{
			return;
		}
		
		try (Connection con = L2DatabaseFactory.getInstance().getConnection();
			PreparedStatement statement = con.prepareStatement("UPDATE characters SET deletetime=0 WHERE charId=?"))
		{
			statement.setInt(1, objid);
			statement.execute();
		}
		catch (Exception e)
		{
			_log.error("Error restoring character.", e);
		}
		
		final LogRecord record = new LogRecord(Level.WARNING, "Restore");
		record.setParameters(new Object[]
		{
			objid,
			this
		});
		_logAccounting.log(record);
	}
	
	public static void deleteCharByObjId(int objid)
	{
		if (objid < 0)
		{
			return;
		}
		
		CharNameTable.getInstance().removeName(objid);
		
		try (Connection con = L2DatabaseFactory.getInstance().getConnection())
		{
			try (PreparedStatement ps = con.prepareStatement("DELETE FROM character_contacts WHERE charId=? OR contactId=?"))
			{
				ps.setInt(1, objid);
				ps.setInt(2, objid);
				ps.execute();
			}
			
			try (PreparedStatement ps = con.prepareStatement("DELETE FROM character_friends WHERE charId=? OR friendId=?"))
			{
				ps.setInt(1, objid);
				ps.setInt(2, objid);
				ps.execute();
			}
			
			try (PreparedStatement ps = con.prepareStatement("DELETE FROM character_hennas WHERE charId=?"))
			{
				ps.setInt(1, objid);
				ps.execute();
			}
			
			try (PreparedStatement ps = con.prepareStatement("DELETE FROM character_macroses WHERE charId=?"))
			{
				ps.setInt(1, objid);
				ps.execute();
			}
			
			try (PreparedStatement ps = con.prepareStatement("DELETE FROM character_quests WHERE charId=?"))
			{
				ps.setInt(1, objid);
				ps.execute();
			}
			
			try (PreparedStatement ps = con.prepareStatement("DELETE FROM character_quest_global_data WHERE charId=?"))
			{
				ps.setInt(1, objid);
				ps.executeUpdate();
			}
			
			try (PreparedStatement ps = con.prepareStatement("DELETE FROM character_recipebook WHERE charId=?"))
			{
				ps.setInt(1, objid);
				ps.execute();
			}
			
			try (PreparedStatement ps = con.prepareStatement("DELETE FROM character_shortcuts WHERE charId=?"))
			{
				ps.setInt(1, objid);
				ps.execute();
			}
			
			try (PreparedStatement ps = con.prepareStatement("DELETE FROM character_skills WHERE charId=?"))
			{
				ps.setInt(1, objid);
				ps.execute();
			}
			
			try (PreparedStatement ps = con.prepareStatement("DELETE FROM character_skills_save WHERE charId=?"))
			{
				ps.setInt(1, objid);
				ps.execute();
			}
			
			try (PreparedStatement ps = con.prepareStatement("DELETE FROM character_subclasses WHERE charId=?"))
			{
				ps.setInt(1, objid);
				ps.execute();
			}
			
			try (PreparedStatement ps = con.prepareStatement("DELETE FROM heroes WHERE charId=?"))
			{
				ps.setInt(1, objid);
				ps.execute();
			}
			
			try (PreparedStatement ps = con.prepareStatement("DELETE FROM olympiad_nobles WHERE charId=?"))
			{
				ps.setInt(1, objid);
				ps.execute();
			}
			
			try (PreparedStatement ps = con.prepareStatement("DELETE FROM seven_signs WHERE charId=?"))
			{
				ps.setInt(1, objid);
				ps.execute();
			}
			
			try (PreparedStatement ps = con.prepareStatement("DELETE FROM pets WHERE item_obj_id IN (SELECT object_id FROM items WHERE items.owner_id=?)"))
			{
				ps.setInt(1, objid);
				ps.execute();
			}
			
			try (PreparedStatement ps = con.prepareStatement("DELETE FROM item_attributes WHERE itemId IN (SELECT object_id FROM items WHERE items.owner_id=?)"))
			{
				ps.setInt(1, objid);
				ps.execute();
			}
			
			try (PreparedStatement ps = con.prepareStatement("DELETE FROM items WHERE owner_id=?"))
			{
				ps.setInt(1, objid);
				ps.execute();
			}
			
			try (PreparedStatement ps = con.prepareStatement("DELETE FROM merchant_lease WHERE player_id=?"))
			{
				ps.setInt(1, objid);
				ps.execute();
			}
			
			try (PreparedStatement ps = con.prepareStatement("DELETE FROM character_raid_points WHERE charId=?"))
			{
				ps.setInt(1, objid);
				ps.execute();
			}
			
			try (PreparedStatement ps = con.prepareStatement("DELETE FROM character_reco_bonus WHERE charId=?"))
			{
				ps.setInt(1, objid);
				ps.execute();
			}
			
			try (PreparedStatement ps = con.prepareStatement("DELETE FROM character_instance_time WHERE charId=?"))
			{
				ps.setInt(1, objid);
				ps.execute();
			}
			
			try (PreparedStatement ps = con.prepareStatement("DELETE FROM character_variables WHERE charId=?"))
			{
				ps.setInt(1, objid);
				ps.execute();
			}
			
			try (PreparedStatement ps = con.prepareStatement("DELETE FROM characters WHERE charId=?"))
			{
				ps.setInt(1, objid);
				ps.execute();
			}
			
			if (Config.L2JMOD_ALLOW_WEDDING)
			{
				try (PreparedStatement ps = con.prepareStatement("DELETE FROM mods_wedding WHERE player1Id = ? OR player2Id = ?"))
				{
					ps.setInt(1, objid);
					ps.setInt(2, objid);
					ps.execute();
				}
			}
			
			// vGodFather
			try
			{
				try (PreparedStatement ps = con.prepareStatement("DELETE FROM bbs_favorite WHERE playerId=?"))
				{
					ps.setInt(1, objid);
					ps.execute();
				}
				
				try (PreparedStatement ps = con.prepareStatement("DELETE FROM achievements WHERE owner_id=?"))
				{
					ps.setInt(1, objid);
					ps.execute();
				}
				
				try (PreparedStatement ps = con.prepareStatement("DELETE FROM aio_scheme_profiles_buffs WHERE charId=?"))
				{
					ps.setInt(1, objid);
					ps.execute();
				}
				
				try (PreparedStatement ps = con.prepareStatement("DELETE FROM character_item_mall_transactions WHERE charId=?"))
				{
					ps.setInt(1, objid);
					ps.execute();
				}
				
				try (PreparedStatement ps = con.prepareStatement("DELETE FROM character_mail WHERE charId=?"))
				{
					ps.setInt(1, objid);
					ps.execute();
				}
				
				try (PreparedStatement ps = con.prepareStatement("DELETE FROM sunrise_variables WHERE obj_id=?"))
				{
					ps.setInt(1, objid);
					ps.execute();
				}
			}
			catch (Exception e)
			{
				// do nothing
			}
		}
		catch (Exception e)
		{
			_log.error("Error deleting character.", e);
		}
	}
	
	public L2PcInstance loadCharFromDisk(int charslot)
	{
		final int objId = getObjectIdForSlot(charslot);
		if (objId < 0)
		{
			return null;
		}
		
		L2PcInstance character = L2World.getInstance().getPlayer(objId);
		if (character != null)
		{
			// exploit prevention, should not happens in normal way
			_log.info("Attempt of double login: " + character.getName() + "(" + objId + ") " + getAccountName());
			if (character.getClient() != null)
			{
				character.getClient().closeNow();
			}
			else
			{
				character.deleteMe();
			}
			return null;
		}
		
		character = L2PcInstance.load(objId);
		if (character != null)
		{
			// preinit some values for each login
			character.setRunning(); // running is default
			character.standUp(); // standing is default
			
			character.refreshOverloaded();
			character.refreshExpertisePenalty();
			character.setOnlineStatus(true, false);
		}
		else
		{
			_log.error("could not restore in slot: " + charslot);
		}
		
		// setCharacter(character);
		return character;
	}
	
	/**
	 * @param list
	 */
	public void setCharSelection(List<CharSelectInfoPackage> list)
	{
		_charSlotMapping = list;
	}
	
	public CharSelectInfoPackage getCharSelection(int charslot)
	{
		if ((_charSlotMapping == null) || (charslot < 0) || (charslot >= _charSlotMapping.size()))
		{
			return null;
		}
		return _charSlotMapping.get(charslot);
	}
	
	public SecondaryPasswordAuth getSecondaryAuth()
	{
		return _secondaryAuth;
	}
	
	public void close(L2GameServerPacket gsp)
	{
		if (isConnected())
		{
			getConnection().close(gsp);
		}
	}
	
	/**
	 * @param charslot
	 * @return
	 */
	private int getObjectIdForSlot(int charslot)
	{
		final CharSelectInfoPackage info = getCharSelection(charslot);
		if (info == null)
		{
			_log.warn(toString() + " tried to delete Character in slot " + charslot + " but no characters exits at that slot.");
			return -1;
		}
		return info.getObjectId();
	}
	
	@Override
	protected void onForcedDisconnection()
	{
		if ((getActiveChar() != null) && getActiveChar().isFarmBot())
		{
			SecurityActions.startSecurity(getActiveChar(), SecurityType.ANTIBOT_SYSTEM);
		}
		LogRecord record = new LogRecord(Level.WARNING, "Disconnected abnormally");
		record.setParameters(new Object[]
		{
			this
		});
		_logAccounting.log(record);
	}
	
	@Override
	protected void onDisconnection()
	{
		// no long running tasks here, do it async
		try
		{
			ThreadPoolManager.getInstance().executeGeneral(new DisconnectTask());
			Protection.doDisconection(this);
		}
		catch (RejectedExecutionException e)
		{
			// server is closing
		}
	}
	
	/**
	 * Close client connection with {@link ServerClose} packet
	 */
	public void closeNow()
	{
		_isDetached = true; // prevents more packets execution
		close(ServerClose.STATIC_PACKET);
		synchronized (this)
		{
			if (_cleanupTask != null)
			{
				cancelCleanup();
			}
			_cleanupTask = ThreadPoolManager.getInstance().scheduleGeneral(new CleanupTask(), 0); // instant
		}
	}
	
	/**
	 * Produces the best possible string representation of this client.
	 */
	@Override
	public String toString()
	{
		try
		{
			final InetAddress address = getConnection().getInetAddress();
			switch (getState())
			{
				case CONNECTED:
					return "[IP: " + (address == null ? "disconnected" : address.getHostAddress()) + "]";
				case AUTHED:
					return "[Account: " + getAccountName() + " - IP: " + (address == null ? "disconnected" : address.getHostAddress()) + "]";
				case IN_GAME:
					return "[Character: " + (getActiveChar() == null ? "disconnected" : getActiveChar().getName() + "[" + getActiveChar().getObjectId() + "]") + " - Account: " + getAccountName() + " - IP: " + (address == null ? "disconnected" : address.getHostAddress()) + "]";
				default:
					throw new IllegalStateException("Missing state on switch");
			}
		}
		catch (NullPointerException e)
		{
			return "[Character read failed due to disconnect]";
		}
	}
	
	protected class DisconnectTask implements Runnable
	{
		@Override
		public void run()
		{
			boolean fast = true;
			try
			{
				if ((getActiveChar() != null) && !isDetached())
				{
					getActiveChar().storeZoneRestartLimitTime();
					setDetached(true);
					if (offlineMode(getActiveChar()))
					{
						getActiveChar().leaveParty();
						OlympiadManager.getInstance().unRegisterNoble(getActiveChar());
						
						// If the L2PcInstance has Pet, unsummon it
						if (getActiveChar().hasSummon())
						{
							getActiveChar().getSummon().setRestoreSummon(true);
							
							getActiveChar().getSummon().unSummon(getActiveChar());
							// Dead pet wasn't unsummoned, broadcast npcinfo changes (pet will be without owner name - means owner offline)
							if (getActiveChar().getSummon() != null)
							{
								getActiveChar().getSummon().broadcastNpcInfo(0);
							}
						}
						
						if (Config.OFFLINE_SET_NAME_COLOR)
						{
							getActiveChar().getAppearance().setNameColor(Config.OFFLINE_NAME_COLOR);
							getActiveChar().broadcastUserInfo();
						}
						
						if (getActiveChar().getOfflineStartTime() == 0)
						{
							getActiveChar().setOfflineStartTime(System.currentTimeMillis());
						}
						
						final LogRecord record = new LogRecord(Level.INFO, "Entering offline mode");
						record.setParameters(new Object[]
						{
							L2GameClient.this
						});
						_logAccounting.log(record);
						
						return;
					}
					fast = !getActiveChar().isInCombat() && !getActiveChar().isLocked();
				}
				cleanMe(fast);
			}
			catch (Exception e1)
			{
				_log.warn("Error while disconnecting client.", e1);
			}
		}
	}
	
	/**
	 * @param player the player to be check.
	 * @return {@code true} if the player is allowed to remain as off-line shop.
	 */
	protected boolean offlineMode(L2PcInstance player)
	{
		if (player.isInOlympiadMode() || player.isFestivalParticipant() || player.isJailed() || (player.getVehicle() != null))
		{
			return false;
		}
		
		boolean canSetShop = false;
		
		if (SunriseEvents.isInEvent(player))
		{
			return false;
		}
		
		switch (player.getPrivateStoreType())
		{
			case SELL:
			case PACKAGE_SELL:
			case BUY:
			{
				canSetShop = Config.OFFLINE_TRADE_ENABLE;
				break;
			}
			case MANUFACTURE:
			{
				canSetShop = Config.OFFLINE_TRADE_ENABLE;
				break;
			}
			default:
			{
				canSetShop = Config.OFFLINE_CRAFT_ENABLE && player.isInCraftMode();
				break;
			}
		}
		
		if (Config.OFFLINE_MODE_IN_PEACE_ZONE && !player.isInsideZone(ZoneIdType.PEACE))
		{
			canSetShop = false;
		}
		return canSetShop;
	}
	
	public void cleanMe(boolean fast)
	{
		try
		{
			synchronized (this)
			{
				if (_cleanupTask == null)
				{
					_cleanupTask = ThreadPoolManager.getInstance().scheduleGeneral(new CleanupTask(), fast ? 5 : 15000L);
				}
			}
		}
		catch (Exception e1)
		{
			_log.warn("Error during cleanup.", e1);
		}
	}
	
	protected class CleanupTask implements Runnable
	{
		@Override
		public void run()
		{
			try
			{
				// we are going to manually save the char bellow thus we can force the cancel
				if (_autoSaveInDB != null)
				{
					_autoSaveInDB.cancel(true);
					// ThreadPoolManager.getInstance().removeGeneral((Runnable) _autoSaveInDB);
				}
				
				if (getActiveChar() != null) // this should only happen on connection loss
				{
					if (getActiveChar().isLocked())
					{
						_log.warn("Player " + getActiveChar().getName() + " still performing subclass actions during disconnect.");
					}
					
					// prevent closing again
					getActiveChar().setClient(null);
					
					if (getActiveChar().isOnline())
					{
						getActiveChar().deleteMe();
						AntiFeedManager.getInstance().onDisconnect(L2GameClient.this);
					}
				}
				setActiveChar(null);
			}
			catch (Exception e1)
			{
				_log.warn("Error while cleanup client.", e1);
			}
			finally
			{
				LoginServerThread.getInstance().sendLogout(getAccountName());
			}
		}
	}
	
	protected class AutoSaveTask implements Runnable
	{
		@Override
		public void run()
		{
			try
			{
				L2PcInstance player = getActiveChar();
				if ((player != null) && player.isOnline()) // safety precaution
				{
					saveCharToDisk();
					if (player.hasSummon())
					{
						player.getSummon().store();
					}
				}
			}
			catch (Exception e)
			{
				_log.error("Error on AutoSaveTask.", e);
			}
		}
	}
	
	public boolean isProtocolOk()
	{
		return _protocol;
	}
	
	public void setProtocolOk(boolean b)
	{
		_protocol = b;
	}
	
	public boolean handleCheat(String punishment)
	{
		if (_activeChar != null)
		{
			Util.handleIllegalPlayerAction(_activeChar, toString() + ": " + punishment, Config.DEFAULT_PUNISH);
			return true;
		}
		
		java.util.logging.Logger _logAudit = java.util.logging.Logger.getLogger("audit");
		_logAudit.log(Level.INFO, "AUDIT: Client " + toString() + " kicked for reason: " + punishment);
		closeNow();
		return false;
	}
	
	private int _failedPackets = 0;
	
	/**
	 * Counts buffer underflow exceptions.
	 */
	public void onBufferUnderflow()
	{
		if (_failedPackets++ >= 10)
		{
			if (_state == GameClientState.CONNECTED) // in CONNECTED state kick client immediately
			{
				if (Config.PACKET_HANDLER_DEBUG)
				{
					_log.error("Client " + toString() + " - Disconnected, too many buffer underflows in non-authed state.");
				}
				closeNow();
			}
		}
	}
	
	/**
	 * Counts unknown packets
	 */
	public void onUnknownPacket()
	{
		if (_state == GameClientState.CONNECTED) // in CONNECTED state kick client immediately
		{
			if (Config.PACKET_HANDLER_DEBUG)
			{
				_log.error("Client " + toString() + " - Disconnected, too many unknown packets in non-authed state.");
			}
			closeNow();
		}
	}
	
	public void setClientTracert(int[][] tracert)
	{
		trace = tracert;
	}
	
	public int[][] getTrace()
	{
		return trace;
	}
	
	private boolean cancelCleanup()
	{
		final Future<?> task = _cleanupTask;
		if (task != null)
		{
			_cleanupTask = null;
			return task.cancel(true);
		}
		return false;
	}
	
	// vGodFather: this will prevent some rare cases of client crash
	// when create char
	private boolean _isCharCreation = false;
	
	public void setCharCreation(boolean isCharCreation)
	{
		_isCharCreation = isCharCreation;
	}
	
	public boolean isCharCreation()
	{
		return _isCharCreation;
	}
	
	// Protection
	private String _playerName = "";
	private String _loginName = "";
	private int _playerId = 0;
	private String _hwid = "";
	private int revision = 0;
	
	public final String getPlayerName()
	{
		return _playerName;
	}
	
	public void setPlayerName(String name)
	{
		_playerName = name;
	}
	
	public void setPlayerId(int plId)
	{
		_playerId = plId;
	}
	
	public int getPlayerId()
	{
		return _playerId;
	}
	
	public final String getHWID()
	{
		return _hwid;
	}
	
	public void setHWID(String hwid)
	{
		_hwid = hwid;
	}
	
	public void setRevision(int revision)
	{
		this.revision = revision;
	}
	
	public int getRevision()
	{
		return revision;
	}
	
	public final String getLoginName()
	{
		return _loginName;
	}
	
	public void setLoginName(String name)
	{
		_loginName = name;
	}
}
