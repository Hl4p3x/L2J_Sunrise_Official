/*
 * Copyright (C) L2J Sunrise
 * This file is part of L2J Sunrise.
 */
package l2r.gameserver.model.actor;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import l2r.gameserver.ThreadPoolManager;
import l2r.gameserver.ai.L2BoatAI;
import l2r.gameserver.ai.L2CharacterAI;
import l2r.gameserver.enums.CtrlIntention;
import l2r.gameserver.enums.InstanceType;
import l2r.gameserver.enums.TeleportWhereType;
import l2r.gameserver.instancemanager.MapRegionManager;
import l2r.gameserver.model.L2World;
import l2r.gameserver.model.Location;
import l2r.gameserver.model.VehiclePathPoint;
import l2r.gameserver.model.actor.instance.L2PcInstance;
import l2r.gameserver.model.actor.knownlist.VehicleKnownList;
import l2r.gameserver.model.actor.stat.VehicleStat;
import l2r.gameserver.model.actor.templates.L2CharTemplate;
import l2r.gameserver.model.interfaces.ILocational;
import l2r.gameserver.model.items.L2Weapon;
import l2r.gameserver.model.items.instance.L2ItemInstance;
import l2r.gameserver.model.items.type.WeaponType;
import l2r.gameserver.network.SystemMessageId;
import l2r.gameserver.network.serverpackets.ActionFailed;
import l2r.gameserver.network.serverpackets.InventoryUpdate;
import l2r.gameserver.network.serverpackets.L2GameServerPacket;

/**
 * @author DS
 */
public abstract class L2Vehicle extends L2Character
{
	public abstract L2GameServerPacket validateLocationPacket(L2PcInstance player);
	
	public abstract L2GameServerPacket getOnPacket(final L2PcInstance p0, final Location p1);
	
	public abstract L2GameServerPacket getOffPacket(final L2PcInstance p0, final Location p1);
	
	public abstract L2GameServerPacket inMovePacket(final L2PcInstance p0, final Location p1, final Location p2);
	
	public abstract L2GameServerPacket inStopMovePacket(final L2PcInstance p0);
	
	public abstract L2GameServerPacket startPacket();
	
	public abstract L2GameServerPacket checkLocationPacket();
	
	public abstract L2GameServerPacket infoPacket();
	
	protected int _dockId = 0;
	protected final List<L2PcInstance> _passengers = new CopyOnWriteArrayList<>();
	protected Location _oustLoc = null;
	private Runnable _engine = null;
	
	protected VehiclePathPoint[] _currentPath = null;
	protected int _runState = 0;
	
	/**
	 * Creates an abstract vehicle.
	 * @param template the vehicle template
	 */
	public L2Vehicle(L2CharTemplate template)
	{
		super(template);
		setInstanceType(InstanceType.L2Vehicle);
		setIsFlying(true);
	}
	
	@Override
	protected L2CharacterAI initAI()
	{
		return new L2BoatAI(this);
	}
	
	public boolean isBoat()
	{
		return false;
	}
	
	public boolean isAirShip()
	{
		return false;
	}
	
	public boolean canBeControlled()
	{
		return _engine == null;
	}
	
	public void registerEngine(Runnable r)
	{
		_engine = r;
	}
	
	public void runEngine(int delay)
	{
		if (_engine != null)
		{
			ThreadPoolManager.getInstance().scheduleGeneral(_engine, delay);
		}
	}
	
	public void executePath(VehiclePathPoint[] path)
	{
		_runState = 0;
		_currentPath = path;
		
		if ((_currentPath != null) && (_currentPath.length > 0))
		{
			final VehiclePathPoint point = _currentPath[0];
			if (point.getMoveSpeed() > 0)
			{
				getStat().setMoveSpeed(point.getMoveSpeed());
			}
			if (point.getRotationSpeed() > 0)
			{
				getStat().setRotationSpeed(point.getRotationSpeed());
			}
			
			final L2GameServerPacket startPacket = startPacket();
			if (startPacket != null)
			{
				broadcastPacket(startPacket);
			}
			
			getAI().setIntention(CtrlIntention.AI_INTENTION_MOVE_TO, new Location(point.getX(), point.getY(), point.getZ(), 0));
			return;
		}
		getAI().setIntention(CtrlIntention.AI_INTENTION_ACTIVE);
	}
	
	@Override
	public void setXYZ(final int x, final int y, final int z, final boolean MoveTask)
	{
		super.setXYZ(x, y, z, MoveTask);
		this.updatePeopleInTheBoat(x, y, z);
	}
	
	protected void updatePeopleInTheBoat(final int x, final int y, final int z)
	{
		for (final L2PcInstance player : this._passengers)
		{
			if (player != null)
			{
				player.setXYZ(x, y, z, true);
			}
		}
	}
	
	public void onEvtArrived()
	{
		moveNext();
	}
	
	public void moveNext()
	{
		if (_currentPath != null)
		{
			_runState++;
			if (_runState < _currentPath.length)
			{
				final VehiclePathPoint point = _currentPath[_runState];
				if (!isMovementDisabled())
				{
					if (point.getMoveSpeed() == 0)
					{
						teleToLocation(point.getX(), point.getY(), point.getZ(), point.getRotationSpeed(), false);
						_currentPath = null;
					}
					else
					{
						if (point.getMoveSpeed() > 0)
						{
							getStat().setMoveSpeed(point.getMoveSpeed());
						}
						if (point.getRotationSpeed() > 0)
						{
							getStat().setRotationSpeed(point.getRotationSpeed());
						}
						
						getAI().setIntention(CtrlIntention.AI_INTENTION_MOVE_TO, new Location(point.getX(), point.getY(), point.getZ(), 0));
						return;
					}
				}
			}
			else
			{
				_currentPath = null;
			}
		}
		
		runEngine(10);
	}
	
	@Override
	public void sendInfo(L2PcInstance activeChar)
	{
		if (!this.isMoving)
		{
			activeChar.sendPacket(this.infoPacket());
			return;
		}
		activeChar.sendPacket(this.infoPacket());
		activeChar.sendPacket(this.movePacket());
	}
	
	@Override
	public void initKnownList()
	{
		setKnownList(new VehicleKnownList(this));
	}
	
	@Override
	public VehicleStat getStat()
	{
		return (VehicleStat) super.getStat();
	}
	
	@Override
	public void initCharStat()
	{
		setStat(new VehicleStat(this));
	}
	
	public boolean isInDock()
	{
		return _dockId > 0;
	}
	
	public int getDockId()
	{
		return _dockId;
	}
	
	public void setInDock(int d)
	{
		_dockId = d;
	}
	
	public void setOustLoc(Location loc)
	{
		_oustLoc = loc;
	}
	
	public Location getOustLoc()
	{
		return _oustLoc != null ? _oustLoc : MapRegionManager.getInstance().getTeleToLocation(this, TeleportWhereType.TOWN);
	}
	
	public void oustPlayers()
	{
		final L2GameServerPacket checkLocation = this.checkLocationPacket();
		if (checkLocation != null)
		{
			broadcastPacket(this.infoPacket());
			broadcastPacket(checkLocation);
		}
		
		_passengers.forEach(p -> oustPlayer(p));
		_passengers.clear();
	}
	
	public void oustPlayer(L2PcInstance player)
	{
		oustPlayer(player, getOustLoc(), true);
	}
	
	public void oustPlayer(L2PcInstance player, Location loc, boolean teleport)
	{
		player.setVehicle(null);
		player.setInVehiclePosition(null);
		
		if (player.isOnline())
		{
			player.broadcastPacket(this.getOffPacket(player, loc));
			player.setLoc(loc, true);
			if (teleport)
			{
				player.teleToLocation(loc.getX(), loc.getY(), loc.getZ());
			}
		}
		else
		{
			player.setXYZInvisible(loc.getX(), loc.getY(), loc.getZ()); // disconnects handling
		}
		
		removePassenger(player);
	}
	
	public boolean addPassenger(L2PcInstance player, Location loc)
	{
		if ((player == null) || _passengers.contains(player))
		{
			return false;
		}
		
		// already in other vehicle
		if ((player.getVehicle() != null) && (player.getVehicle() != this))
		{
			return false;
		}
		
		_passengers.add(player);
		return true;
	}
	
	public void removePassenger(L2PcInstance player)
	{
		try
		{
			_passengers.remove(player);
		}
		catch (Exception e)
		{
		}
	}
	
	public void moveInBoat(final L2PcInstance activeChar, final Location ori, final Location loc)
	{
		if (activeChar.hasSummon())
		{
			activeChar.sendPacket(SystemMessageId.RELEASE_PET_ON_BOAT);
			activeChar.sendPacket(ActionFailed.STATIC_PACKET);
			return;
		}
		
		if (activeChar.isTransformed())
		{
			activeChar.sendPacket(SystemMessageId.CANT_POLYMORPH_ON_BOAT);
			activeChar.sendPacket(ActionFailed.STATIC_PACKET);
			return;
		}
		
		if (activeChar.isAttackingNow() && (activeChar.getActiveWeaponItem() != null) && (activeChar.getActiveWeaponItem().getItemType() == WeaponType.BOW))
		{
			activeChar.sendPacket(ActionFailed.STATIC_PACKET);
			return;
		}
		
		if (activeChar.isSitting() || activeChar.isMovementDisabled())
		{
			activeChar.sendPacket(ActionFailed.STATIC_PACKET);
			return;
		}
		
		if (!activeChar.isInBoat())
		{
			activeChar.setVehicle(this);
		}
		
		activeChar.setInVehiclePosition(loc);
		activeChar.broadcastPacket(this.inMovePacket(activeChar, ori, loc));
	}
	
	public boolean isEmpty()
	{
		return _passengers.isEmpty();
	}
	
	public List<L2PcInstance> getPassengers()
	{
		return _passengers;
	}
	
	public void broadcastToPassengers(L2GameServerPacket sm)
	{
		for (L2PcInstance player : _passengers)
		{
			if (player != null)
			{
				player.sendPacket(sm);
			}
		}
	}
	
	/**
	 * Consume ticket(s) and teleport player from boat if no correct ticket
	 * @param itemId Ticket itemId
	 * @param count Ticket count
	 * @param oustX
	 * @param oustY
	 * @param oustZ
	 */
	public void payForRide(int itemId, int count, int oustX, int oustY, int oustZ)
	{
		final Collection<L2PcInstance> passengers = getKnownList().getKnownPlayersInRadius(1000);
		if ((passengers != null) && !passengers.isEmpty())
		{
			L2ItemInstance ticket;
			InventoryUpdate iu;
			for (L2PcInstance player : passengers)
			{
				if (player == null)
				{
					continue;
				}
				if (player.isInBoat() && (player.getBoat() == this))
				{
					if (itemId > 0)
					{
						ticket = player.getInventory().getItemByItemId(itemId);
						if ((ticket == null) || (player.getInventory().destroyItem("Boat", ticket, count, player, this) == null))
						{
							player.sendPacket(SystemMessageId.NOT_CORRECT_BOAT_TICKET);
							player.teleToLocation(oustX, oustY, oustZ, true);
							continue;
						}
						iu = new InventoryUpdate();
						iu.addModifiedItem(ticket);
						player.sendInventoryUpdate(iu);
					}
					addPassenger(player, new Location(getX(), getY(), getZ()));
				}
			}
		}
	}
	
	@Override
	public void teleToLocation(ILocational loc, boolean allowRandomOffset)
	{
		if (isMoving())
		{
			stopMove(true, false);
		}
		
		setIsTeleporting(true);
		
		getAI().setIntention(CtrlIntention.AI_INTENTION_ACTIVE);
		
		for (L2PcInstance player : _passengers)
		{
			if (player != null)
			{
				player.teleToLocation(loc, false);
			}
		}
		
		decayMe();
		setXYZ(loc.getX(), loc.getY(), loc.getZ());
		
		// temporary fix for heading on teleports
		if (loc.getHeading() != 0)
		{
			setHeading(loc.getHeading());
		}
		
		onTeleported();
		revalidateZone(true);
	}
	
	@Override
	public void deleteMe()
	{
		_engine = null;
		
		try
		{
			if (isMoving())
			{
				stopMove(null);
			}
		}
		catch (Exception e)
		{
			_log.error("Failed stopMove().", e);
		}
		
		try
		{
			oustPlayers();
		}
		catch (Exception e)
		{
			_log.error("Failed oustPlayers().", e);
		}
		
		try
		{
			decayMe();
		}
		catch (Exception e)
		{
			_log.error("Failed decayMe().", e);
		}
		
		try
		{
			getKnownList().removeAllKnownObjects();
		}
		catch (Exception e)
		{
			_log.error("Failed cleaning knownlist.", e);
		}
		
		// Remove L2Object object from _allObjects of L2World
		L2World.getInstance().removeObject(this);
		
		super.deleteMe();
	}
	
	@Override
	public void updateAbnormalEffect()
	{
	}
	
	@Override
	public L2ItemInstance getActiveWeaponInstance()
	{
		return null;
	}
	
	@Override
	public L2Weapon getActiveWeaponItem()
	{
		return null;
	}
	
	@Override
	public L2ItemInstance getSecondaryWeaponInstance()
	{
		return null;
	}
	
	@Override
	public L2Weapon getSecondaryWeaponItem()
	{
		return null;
	}
	
	@Override
	public int getLevel()
	{
		return 0;
	}
	
	@Override
	public boolean isAutoAttackable(L2Character attacker)
	{
		return false;
	}
	
	@Override
	public void detachAI()
	{
	
	}
	
	@Override
	public boolean isVehicle()
	{
		return true;
	}
}