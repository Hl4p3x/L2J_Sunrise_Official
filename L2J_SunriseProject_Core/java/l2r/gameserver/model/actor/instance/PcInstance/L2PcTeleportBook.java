package l2r.gameserver.model.actor.instance.PcInstance;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import l2r.gameserver.GameTimeController;
import l2r.gameserver.ThreadPoolManager;
import l2r.gameserver.dao.factory.impl.DAOFactory;
import l2r.gameserver.enums.CtrlIntention;
import l2r.gameserver.enums.ZoneIdType;
import l2r.gameserver.model.TeleportBookmark;
import l2r.gameserver.model.actor.instance.L2PcInstance;
import l2r.gameserver.network.SystemMessageId;
import l2r.gameserver.network.serverpackets.ExGetBookMarkInfoPacket;
import l2r.gameserver.network.serverpackets.MagicSkillUse;
import l2r.gameserver.network.serverpackets.SetupGauge;
import l2r.gameserver.network.serverpackets.SystemMessage;
import l2r.gameserver.util.Broadcast;

import gr.sr.player.PcExtention;

/**
 * @author vGodFather
 */
public class L2PcTeleportBook extends PcExtention
{
	private int _bookmarkslot = 0; // The Teleport Bookmark Slot
	
	private final Map<Integer, TeleportBookmark> _tpbookmarks = new ConcurrentHashMap<>();
	
	public L2PcTeleportBook(L2PcInstance activeChar)
	{
		super(activeChar);
	}
	
	public void teleportBookmarkModify(int id, int icon, String tag, String name)
	{
		final TeleportBookmark bookmark = _tpbookmarks.get(id);
		if (bookmark != null)
		{
			bookmark.setIcon(icon);
			bookmark.setTag(tag);
			bookmark.setName(name);
			
			DAOFactory.getInstance().getTeleportBookmarkDAO().update(getChar(), id, icon, tag, name);
		}
		
		getChar().sendPacket(new ExGetBookMarkInfoPacket(getChar()));
	}
	
	public void teleportBookmarkDelete(int id)
	{
		if (_tpbookmarks.remove(id) != null)
		{
			DAOFactory.getInstance().getTeleportBookmarkDAO().delete(getChar(), id);
			
			getChar().sendPacket(new ExGetBookMarkInfoPacket(getChar()));
		}
	}
	
	public void teleportBookmarkGo(int id)
	{
		if (!teleportBookmarkCondition(0))
		{
			return;
		}
		
		int consumableScroll = 0;
		
		if (getChar().getInventory().getInventoryItemCount(13016, 0) > 0)
		{
			consumableScroll = 13016;
		}
		else if (getChar().getInventory().getInventoryItemCount(13302, 0) > 0)
		{
			consumableScroll = 13302;
		}
		else if (getChar().getInventory().getInventoryItemCount(20025, 0) > 0)
		{
			consumableScroll = 20025;
		}
		else
		{
			getChar().sendPacket(SystemMessageId.YOU_CANNOT_TELEPORT_BECAUSE_YOU_DO_NOT_HAVE_A_TELEPORT_ITEM);
			return;
		}
		
		final SystemMessage sm = SystemMessage.getSystemMessage(SystemMessageId.S1_DISAPPEARED);
		sm.addItemName(consumableScroll);
		getChar().sendPacket(sm);
		
		final TeleportBookmark bookmark = _tpbookmarks.get(id);
		if (bookmark != null)
		{
			getChar().destroyItem("Consume", getChar().getInventory().getItemByItemId(consumableScroll).getObjectId(), 1, null, false);
			// getChar().teleToLocation(bookmark, false);
			
			getChar().forceIsCasting(GameTimeController.getInstance().getGameTicks() + (20000 / GameTimeController.MILLIS_IN_TICK));
			
			getChar().getAI().setIntention(CtrlIntention.AI_INTENTION_IDLE);
			
			getChar().setTarget(getChar());
			getChar().disableAllSkills();
			
			MagicSkillUse msk = new MagicSkillUse(getChar(), 2588, 1, 20000, 0);
			Broadcast.toSelfAndKnownPlayersInRadius(getChar(), msk, 900);
			SetupGauge sg = new SetupGauge(SetupGauge.BLUE, 20000);
			getChar().sendPacket(sg);
			
			// continue execution later
			getChar().setSkillCast(ThreadPoolManager.getInstance().scheduleGeneral(new EscapeFinalizer(getChar(), bookmark), 20000));
		}
		getChar().sendPacket(new ExGetBookMarkInfoPacket(getChar()));
	}
	
	private static class EscapeFinalizer implements Runnable
	{
		private final L2PcInstance _activeChar;
		private final TeleportBookmark _bookmark;
		
		protected EscapeFinalizer(L2PcInstance activeChar, TeleportBookmark bookmark)
		{
			_activeChar = activeChar;
			_bookmark = bookmark;
		}
		
		@Override
		public void run()
		{
			if (_activeChar.isDead())
			{
				return;
			}
			
			_activeChar.setIsIn7sDungeon(false);
			_activeChar.enableAllSkills();
			_activeChar.setIsCastingNow(false);
			_activeChar.teleToLocation(_bookmark, false);
		}
	}
	
	public boolean teleportBookmarkCondition(int type)
	{
		if (getChar().isInCombat())
		{
			getChar().sendPacket(SystemMessageId.YOU_CANNOT_USE_MY_TELEPORTS_DURING_A_BATTLE);
			return false;
		}
		else if (getChar().isInSiege() || (getChar().getSiegeState() != 0))
		{
			getChar().sendPacket(SystemMessageId.YOU_CANNOT_USE_MY_TELEPORTS_WHILE_PARTICIPATING);
			return false;
		}
		else if (getChar().isInDuel())
		{
			getChar().sendPacket(SystemMessageId.YOU_CANNOT_USE_MY_TELEPORTS_DURING_A_DUEL);
			return false;
		}
		else if (getChar().isFlying())
		{
			getChar().sendPacket(SystemMessageId.YOU_CANNOT_USE_MY_TELEPORTS_WHILE_FLYING);
			return false;
		}
		else if (getChar().isInOlympiadMode())
		{
			getChar().sendPacket(SystemMessageId.YOU_CANNOT_USE_MY_TELEPORTS_WHILE_PARTICIPATING_IN_AN_OLYMPIAD_MATCH);
			return false;
		}
		else if (getChar().isParalyzed())
		{
			getChar().sendPacket(SystemMessageId.YOU_CANNOT_USE_MY_TELEPORTS_WHILE_YOU_ARE_PARALYZED);
			return false;
		}
		else if (getChar().isDead())
		{
			getChar().sendPacket(SystemMessageId.YOU_CANNOT_USE_MY_TELEPORTS_WHILE_YOU_ARE_DEAD);
			return false;
		}
		else if ((type == 1) && (getChar().isIn7sDungeon() || (getChar().isInParty() && getChar().getParty().isInDimensionalRift())))
		{
			getChar().sendPacket(SystemMessageId.YOU_CANNOT_USE_MY_TELEPORTS_TO_REACH_THIS_AREA);
			return false;
		}
		else if (getChar().isInWater())
		{
			getChar().sendPacket(SystemMessageId.YOU_CANNOT_USE_MY_TELEPORTS_UNDERWATER);
			return false;
		}
		else if ((type == 1) && (getChar().isInsideZone(ZoneIdType.SIEGE) || getChar().isInsideZone(ZoneIdType.CLAN_HALL) || getChar().isInsideZone(ZoneIdType.JAIL) || getChar().isInsideZone(ZoneIdType.CASTLE) || getChar().isInsideZone(ZoneIdType.NO_SUMMON_FRIEND) || getChar().isInsideZone(ZoneIdType.FORT)))
		{
			getChar().sendPacket(SystemMessageId.YOU_CANNOT_USE_MY_TELEPORTS_TO_REACH_THIS_AREA);
			return false;
		}
		else if (getChar().isInsideZone(ZoneIdType.NO_BOOKMARK) || getChar().isInBoat() || getChar().isInAirShip())
		{
			if (type == 0)
			{
				getChar().sendPacket(SystemMessageId.YOU_CANNOT_USE_MY_TELEPORTS_IN_THIS_AREA);
			}
			else if (type == 1)
			{
				getChar().sendPacket(SystemMessageId.YOU_CANNOT_USE_MY_TELEPORTS_TO_REACH_THIS_AREA);
			}
			return false;
		}
		/*
		 * TODO: Instant Zone still not implemented else if (isInsideZone(ZoneId.INSTANT)) { sendPacket(SystemMessage.getSystemMessage(2357)); return; }
		 */
		else
		{
			return true;
		}
	}
	
	public void teleportBookmarkAdd(int x, int y, int z, int icon, String tag, String name)
	{
		if (!teleportBookmarkCondition(1) || getChar().isTeleporting())
		{
			return;
		}
		
		if (_tpbookmarks.size() >= _bookmarkslot)
		{
			getChar().sendPacket(SystemMessageId.YOU_HAVE_NO_SPACE_TO_SAVE_THE_TELEPORT_LOCATION);
			return;
		}
		
		if (getChar().getInventory().getInventoryItemCount(20033, 0) == 0)
		{
			getChar().sendPacket(SystemMessageId.YOU_CANNOT_BOOKMARK_THIS_LOCATION_BECAUSE_YOU_DO_NOT_HAVE_A_MY_TELEPORT_FLAG);
			return;
		}
		
		int id;
		for (id = 1; id <= _bookmarkslot; ++id)
		{
			if (!_tpbookmarks.containsKey(id))
			{
				break;
			}
		}
		_tpbookmarks.put(id, new TeleportBookmark(id, x, y, z, icon, tag, name));
		
		getChar().destroyItem("Consume", getChar().getInventory().getItemByItemId(20033).getObjectId(), 1, null, false);
		
		final SystemMessage sm = SystemMessage.getSystemMessage(SystemMessageId.S1_DISAPPEARED);
		sm.addItemName(20033);
		getChar().sendPacket(sm);
		
		DAOFactory.getInstance().getTeleportBookmarkDAO().insert(getChar(), id, x, y, z, icon, tag, name);
		
		getChar().sendPacket(new ExGetBookMarkInfoPacket(getChar()));
	}
	
	public int getBookMarkSlot()
	{
		return _bookmarkslot;
	}
	
	public void setBookMarkSlot(int slot)
	{
		_bookmarkslot = slot;
		getChar().sendPacket(new ExGetBookMarkInfoPacket(getChar()));
	}
	
	public Collection<TeleportBookmark> getTeleportBookmarks()
	{
		return _tpbookmarks.values();
	}
	
	public Map<Integer, TeleportBookmark> getTpbookmarks()
	{
		return _tpbookmarks;
	}
	
	public int getBookmarkslot()
	{
		return _bookmarkslot;
	}
}
