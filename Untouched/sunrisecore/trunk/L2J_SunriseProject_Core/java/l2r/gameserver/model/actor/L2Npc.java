/*
 * Copyright (C) L2J Sunrise
 * This file is part of L2J Sunrise.
 */
package l2r.gameserver.model.actor;

import static l2r.gameserver.enums.CtrlIntention.AI_INTENTION_ACTIVE;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import l2r.Config;
import l2r.gameserver.ItemsAutoDestroy;
import l2r.gameserver.SevenSigns;
import l2r.gameserver.SevenSignsFestival;
import l2r.gameserver.ThreadPoolManager;
import l2r.gameserver.cache.HtmCache;
import l2r.gameserver.data.xml.impl.ItemData;
import l2r.gameserver.data.xml.impl.NpcPersonalAIData;
import l2r.gameserver.enums.AIType;
import l2r.gameserver.enums.IllegalActionPunishmentType;
import l2r.gameserver.enums.InstanceType;
import l2r.gameserver.enums.ShotType;
import l2r.gameserver.enums.Team;
import l2r.gameserver.handler.BypassHandler;
import l2r.gameserver.handler.IBypassHandler;
import l2r.gameserver.instancemanager.CHSiegeManager;
import l2r.gameserver.instancemanager.CastleManager;
import l2r.gameserver.instancemanager.FortManager;
import l2r.gameserver.instancemanager.TownManager;
import l2r.gameserver.instancemanager.WalkingManager;
import l2r.gameserver.model.L2Object;
import l2r.gameserver.model.L2Spawn;
import l2r.gameserver.model.L2World;
import l2r.gameserver.model.Location;
import l2r.gameserver.model.actor.instance.L2ClanHallManagerInstance;
import l2r.gameserver.model.actor.instance.L2DoormenInstance;
import l2r.gameserver.model.actor.instance.L2MerchantInstance;
import l2r.gameserver.model.actor.instance.L2PcInstance;
import l2r.gameserver.model.actor.instance.L2TeleporterInstance;
import l2r.gameserver.model.actor.instance.L2TrainerInstance;
import l2r.gameserver.model.actor.instance.L2WarehouseInstance;
import l2r.gameserver.model.actor.knownlist.NpcKnownList;
import l2r.gameserver.model.actor.stat.NpcStat;
import l2r.gameserver.model.actor.status.NpcStatus;
import l2r.gameserver.model.actor.templates.L2NpcTemplate;
import l2r.gameserver.model.entity.Castle;
import l2r.gameserver.model.entity.Fort;
import l2r.gameserver.model.entity.clanhall.SiegableHall;
import l2r.gameserver.model.entity.olympiad.Olympiad;
import l2r.gameserver.model.events.EventDispatcher;
import l2r.gameserver.model.events.EventType;
import l2r.gameserver.model.events.impl.character.npc.OnNpcCanBeSeen;
import l2r.gameserver.model.events.impl.character.npc.OnNpcEventReceived;
import l2r.gameserver.model.events.impl.character.npc.OnNpcSkillFinished;
import l2r.gameserver.model.events.impl.character.npc.OnNpcSpawn;
import l2r.gameserver.model.events.impl.character.npc.OnNpcTeleport;
import l2r.gameserver.model.events.returns.TerminateReturn;
import l2r.gameserver.model.holders.ItemHolder;
import l2r.gameserver.model.items.L2Item;
import l2r.gameserver.model.items.L2Weapon;
import l2r.gameserver.model.items.instance.L2ItemInstance;
import l2r.gameserver.model.skills.L2Skill;
import l2r.gameserver.model.variables.NpcVariables;
import l2r.gameserver.model.zone.type.L2TownZone;
import l2r.gameserver.network.SystemMessageId;
import l2r.gameserver.network.serverpackets.AbstractNpcInfo;
import l2r.gameserver.network.serverpackets.ActionFailed;
import l2r.gameserver.network.serverpackets.ExChangeNpcState;
import l2r.gameserver.network.serverpackets.MagicSkillUse;
import l2r.gameserver.network.serverpackets.NpcHtmlMessage;
import l2r.gameserver.network.serverpackets.NpcSay;
import l2r.gameserver.network.serverpackets.ServerObjectInfo;
import l2r.gameserver.network.serverpackets.SocialAction;
import l2r.gameserver.taskmanager.DecayTaskManager;
import l2r.gameserver.util.Broadcast;
import l2r.gameserver.util.Util;
import l2r.util.Rnd;

import gr.sr.antibotEngine.AntibotSystem;
import gr.sr.configsEngine.configs.impl.CustomServerConfigs;
import gr.sr.configsEngine.configs.impl.PremiumServiceConfigs;
import gr.sr.datatables.FakePcsTable;
import gr.sr.interf.SunriseEvents;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class represents a Non-Player-Character in the world.<br>
 * It can be a monster or a friendly character.<br>
 * It also uses a template to fetch some static values.<br>
 * The templates are hardcoded in the client, so we can rely on them.<br>
 * L2Npc:
 * <ul>
 * <li>L2Attackable</li>
 * <li>L2BoxInstance</li>
 * </ul>
 */
public class L2Npc extends L2Character
{
	/** The interaction distance of the L2NpcInstance(is used as offset in MovetoLocation method) */
	public static final int INTERACTION_DISTANCE = 150;
	/** Maximum distance where the drop may appear given this NPC position. */
	public static final int RANDOM_ITEM_DROP_LIMIT = 70;
	/** The L2Spawn object that manage this L2NpcInstance */
	private L2Spawn _spawn;
	/** The flag to specify if this L2NpcInstance is busy */
	private boolean _isBusy = false;
	/** The busy message for this L2NpcInstance */
	private String _busyMessage = "";
	/** True if endDecayTask has already been called */
	volatile boolean _isDecayed = false;
	/** The castle index in the array of L2Castle this L2NpcInstance belongs to */
	private int _castleIndex = -2;
	/** The fortress index in the array of L2Fort this L2NpcInstance belongs to */
	private int _fortIndex = -2;
	private boolean _isInTown = false;
	/** True if this L2Npc is autoattackable **/
	private boolean _isAutoAttackable = false;
	/** Time of last social packet broadcast */
	private long _lastSocialBroadcast = 0;
	/** Minimum interval between social packets */
	private final int MINIMUM_SOCIAL_INTERVAL = 10000;
	/** Support for random animation switching */
	private boolean _isRandomAnimationEnabled = true;
	private boolean _isTalking = true;
	protected RandomAnimationTask _rAniTask = null;
	private int _currentLHandId; // normally this shouldn't change from the template, but there exist exceptions
	private int _currentRHandId; // normally this shouldn't change from the template, but there exist exceptions
	private int _currentEnchant; // normally this shouldn't change from the template, but there exist exceptions
	private double _currentCollisionHeight; // used for npc grow effect skills
	private double _currentCollisionRadius; // used for npc grow effect skills
	
	private int _soulshotamount = 0;
	private int _spiritshotamount = 0;
	private int _displayEffect = 0;
	private FakePc _fakePc = null;
	private boolean _isRunner = false;
	
	/**
	 * The character that summons this NPC.
	 */
	private L2Character _summoner = null;
	
	private boolean _tempInvis = false;
	
	private int _shotsMask = 0;
	
	private int _killingBlowWeaponId;
	/** Map of summoned NPCs by this NPC. */
	private volatile Map<Integer, L2Npc> _summonedNpcs = null;
	
	public FakePc getFakePc()
	{
		return _fakePc;
	}
	
	public int getSoulShotChance()
	{
		return getTemplate().getSoulShotChance();
	}
	
	public int getSpiritShotChance()
	{
		return getTemplate().getSpiritShotChance();
	}
	
	public int getEnemyRange()
	{
		return getTemplate().getEnemyRange();
	}
	
	public String getEnemyClan()
	{
		return getTemplate().getEnemyClan();
	}
	
	public int getClanHelpRange()
	{
		return getTemplate().getClanHelpRange();
	}
	
	public String getClan()
	{
		return getTemplate().getClan();
	}
	
	public int getMinSkillChance()
	{
		return getTemplate().getMinSkillChance();
	}
	
	public int getMaxSkillChance()
	{
		return getTemplate().getMaxSkillChance();
	}
	
	/**
	 * Verifies if the NPC can cast a skill given the minimum and maximum skill chances.
	 * @return {@code true} if the NPC has chances of casting a skill
	 */
	public boolean hasSkillChance()
	{
		return Rnd.get(100) < Rnd.get(getMinSkillChance(), getMaxSkillChance());
	}
	
	public int getCanMove()
	{
		return getTemplate().getCanMove();
	}
	
	public int isChaos()
	{
		return getTemplate().isChaos();
	}
	
	public int getCanDodge()
	{
		return getTemplate().getDodge();
	}
	
	public List<L2Skill> getLongRangeSkills()
	{
		return getTemplate().getLongRangeSkills();
	}
	
	public List<L2Skill> getShortRangeSkills()
	{
		return getTemplate().getShortRangeSkills();
	}
	
	public int getSwitchRangeChance()
	{
		return getTemplate().getSwitchRangeChance();
	}
	
	/** Task launching the function onRandomAnimation() */
	protected static class RandomAnimationTask implements Runnable
	{
		private static final Logger LOG = LoggerFactory.getLogger(RandomAnimationTask.class);
		private final L2Npc _npc;
		
		protected RandomAnimationTask(L2Npc npc)
		{
			_npc = npc;
		}
		
		@Override
		public void run()
		{
			try
			{
				if (_npc.isMob())
				{
					// Cancel further animation timers until intention is changed to ACTIVE again.
					if (_npc.getAI().getIntention() != AI_INTENTION_ACTIVE)
					{
						return;
					}
				}
				else
				{
					if (!_npc.isInActiveRegion())
					{
						return;
					}
				}
				
				if (!(_npc.isDead() || _npc.isStunned() || _npc.isSleeping() || _npc.isParalyzed()))
				{
					_npc.onRandomAnimation(Rnd.get(2, 3));
				}
				
				_npc.startRandomAnimationTimer();
			}
			catch (Exception e)
			{
				if (Config.DEBUG)
				{
					LOG.error("There has been an error trying to perform a random animation for NPC {}!", _npc, e);
				}
			}
		}
	}
	
	/**
	 * Send a packet SocialAction to all L2PcInstance in the _KnownPlayers of the L2NpcInstance and create a new RandomAnimation Task.
	 * @param animationId
	 */
	public void onRandomAnimation(int animationId)
	{
		// Send a packet SocialAction to all L2PcInstance in the _KnownPlayers of the L2NpcInstance
		long now = System.currentTimeMillis();
		if ((System.currentTimeMillis() - _lastSocialBroadcast) > MINIMUM_SOCIAL_INTERVAL)
		{
			_lastSocialBroadcast = now;
			broadcastPacket(new SocialAction(getObjectId(), animationId));
		}
	}
	
	/**
	 * Create a RandomAnimation Task that will be launched after the calculated delay.
	 */
	public void startRandomAnimationTimer()
	{
		if (!hasRandomAnimation())
		{
			return;
		}
		
		int minWait = isMob() ? Config.MIN_MONSTER_ANIMATION : Config.MIN_NPC_ANIMATION;
		int maxWait = isMob() ? Config.MAX_MONSTER_ANIMATION : Config.MAX_NPC_ANIMATION;
		
		// Calculate the delay before the next animation
		int interval = Rnd.get(minWait, maxWait) * 1000;
		
		// Create a RandomAnimation Task that will be launched after the calculated delay
		_rAniTask = new RandomAnimationTask(this);
		ThreadPoolManager.getInstance().scheduleGeneral(_rAniTask, interval);
	}
	
	/**
	 * @return true if the server allows Random Animation.
	 */
	public boolean hasRandomAnimation()
	{
		return ((Config.MAX_NPC_ANIMATION > 0) && _isRandomAnimationEnabled && !getAiType().equals(AIType.CORPSE));
	}
	
	/**
	 * Switches random Animation state into val.
	 * @param val needed state of random animation
	 */
	public void setRandomAnimationEnabled(boolean val)
	{
		_isRandomAnimationEnabled = val;
	}
	
	/**
	 * @return {@code true}, if random animation is enabled, {@code false} otherwise.
	 */
	public boolean isRandomAnimationEnabled()
	{
		return _isRandomAnimationEnabled;
	}
	
	/**
	 * Creates a NPC.
	 * @param template the NPC template
	 */
	public L2Npc(L2NpcTemplate template)
	{
		// Call the L2Character constructor to set the _template of the L2Character, copy skills from template to object
		// and link _calculators to NPC_STD_CALCULATOR
		super(template);
		setInstanceType(InstanceType.L2Npc);
		initCharStatusUpdateValues();
		
		// initialize the "current" equipment
		_currentLHandId = getTemplate().getLHandId();
		_currentRHandId = getTemplate().getRHandId();
		_currentEnchant = Config.ENABLE_RANDOM_ENCHANT_EFFECT ? Rnd.get(4, 21) : getTemplate().getWeaponEnchant();
		
		// initialize the "current" collisions
		_currentCollisionHeight = getTemplate().getfCollisionHeight();
		_currentCollisionRadius = getTemplate().getfCollisionRadius();
		
		if (template == null)
		{
			_log.error("No template for Npc. Please check your datapack is setup correctly.");
			return;
		}
		
		_fakePc = FakePcsTable.getInstance().getFakePc(template.getId());
		// Set the name of the L2Character
		setName(template.getName());
	}
	
	@Override
	public NpcKnownList getKnownList()
	{
		return (NpcKnownList) super.getKnownList();
	}
	
	@Override
	public void initKnownList()
	{
		setKnownList(new NpcKnownList(this));
	}
	
	@Override
	public NpcStat getStat()
	{
		return (NpcStat) super.getStat();
	}
	
	@Override
	public void initCharStat()
	{
		setStat(new NpcStat(this));
	}
	
	@Override
	public NpcStatus getStatus()
	{
		return (NpcStatus) super.getStatus();
	}
	
	@Override
	public void initCharStatus()
	{
		setStatus(new NpcStatus(this));
	}
	
	/** Return the L2NpcTemplate of the L2NpcInstance. */
	@Override
	public final L2NpcTemplate getTemplate()
	{
		return (L2NpcTemplate) super.getTemplate();
	}
	
	/**
	 * Gets the NPC ID.
	 * @return the NPC ID
	 */
	@Override
	public int getId()
	{
		return getTemplate().getId();
	}
	
	@Override
	public boolean canBeAttacked()
	{
		return Config.ALT_ATTACKABLE_NPCS;
	}
	
	/**
	 * <B><U> Concept</U> :</B><br>
	 * If a NPC belongs to a Faction, other NPC of the faction inside the Faction range will help it if it's attacked
	 * @return the faction Identifier of this L2NpcInstance contained in the L2NpcTemplate.
	 */
	public final String getFactionId()
	{
		return getClan();
	}
	
	public final String getClans()
	{
		return getClan();
	}
	
	/**
	 * Return the Level of this L2NpcInstance contained in the L2NpcTemplate.
	 */
	@Override
	public final int getLevel()
	{
		return getTemplate().getLevel();
	}
	
	/**
	 * @return True if the L2NpcInstance is aggressive (ex : L2MonsterInstance in function of aggroRange).
	 */
	public boolean isAggressive()
	{
		return false;
	}
	
	// vGodFather
	boolean _aggroRemoved = false;
	
	public void removeAggro(boolean isAggressive)
	{
		_aggroRemoved = isAggressive;
	}
	
	/**
	 * @return the Aggro Range of this L2NpcInstance contained in the L2NpcTemplate.
	 */
	public int getAggroRange()
	{
		if (_aggroRemoved)
		{
			return 0;
		}
		
		return getTemplate().getAggroRange();
	}
	
	/**
	 * @return the Faction Range of this L2NpcInstance contained in the L2NpcTemplate.
	 */
	public int getFactionRange()
	{
		return getClanHelpRange();
	}
	
	public boolean isInMyClan(L2Npc npc)
	{
		return (npc.getFactionId() != null) && !npc.getFactionId().equals(getFactionId());
	}
	
	/**
	 * Return True if this L2NpcInstance is undead in function of the L2NpcTemplate.
	 */
	@Override
	public boolean isUndead()
	{
		return getTemplate().isUndead();
	}
	
	// vGodFather: retail like undying option implementation
	@Override
	public boolean isUndying()
	{
		return getTemplate().isUndying();
	}
	
	/**
	 * Send a packet NpcInfo with state of abnormal effect to all L2PcInstance in the _KnownPlayers of the L2NpcInstance.
	 */
	@Override
	public void updateAbnormalEffect()
	{
		// Send a Server->Client packet NpcInfo with state of abnormal effect to all L2PcInstance in the _KnownPlayers of the L2NpcInstance
		Collection<L2PcInstance> plrs = getKnownList().getKnownPlayers().values();
		for (L2PcInstance player : plrs)
		{
			if ((player == null) || !isVisibleFor(player))
			{
				continue;
			}
			if (getRunSpeed() == 0)
			{
				player.sendPacket(new ServerObjectInfo(this, player));
			}
			else
			{
				player.sendPacket(new AbstractNpcInfo.NpcInfo(this, player));
			}
		}
	}
	
	@Override
	public boolean isAutoAttackable(L2Character attacker)
	{
		return _isAutoAttackable;
	}
	
	public void setAutoAttackable(boolean flag)
	{
		_isAutoAttackable = flag;
	}
	
	/**
	 * @return the Identifier of the item in the left hand of this L2NpcInstance contained in the L2NpcTemplate.
	 */
	public int getLeftHandItem()
	{
		return _currentLHandId;
	}
	
	/**
	 * @return the Identifier of the item in the right hand of this L2NpcInstance contained in the L2NpcTemplate.
	 */
	public int getRightHandItem()
	{
		return _currentRHandId;
	}
	
	public int getEnchantEffect()
	{
		return _currentEnchant;
	}
	
	/**
	 * @return the busy status of this L2NpcInstance.
	 */
	public final boolean isBusy()
	{
		return _isBusy;
	}
	
	/**
	 * @param isBusy the busy status of this L2Npc
	 */
	public void setBusy(boolean isBusy)
	{
		_isBusy = isBusy;
	}
	
	/**
	 * @return the busy message of this L2NpcInstance.
	 */
	public final String getBusyMessage()
	{
		return _busyMessage;
	}
	
	/**
	 * @param message the busy message of this L2Npc.
	 */
	public void setBusyMessage(String message)
	{
		_busyMessage = message;
	}
	
	/**
	 * @return true if this L2Npc instance can be warehouse manager.
	 */
	public boolean isWarehouse()
	{
		return false;
	}
	
	public boolean canTarget(L2PcInstance player)
	{
		if (player.isOutOfControl())
		{
			player.sendPacket(ActionFailed.STATIC_PACKET);
			return false;
		}
		if (player.isLockedTarget() && (player.getLockedTarget() != this))
		{
			player.sendPacket(SystemMessageId.FAILED_CHANGE_TARGET);
			player.sendPacket(ActionFailed.STATIC_PACKET);
			return false;
		}
		if (isTempInvis())
		{
			if (!player.isGM())
			{
				Util.handleIllegalPlayerAction(player, "Player " + player.getName() + " used a 3rd party program, and received a punishment.", IllegalActionPunishmentType.JAIL);
			}
		}
		// TODO: More checks...
		
		return true;
	}
	
	public boolean canInteract(L2PcInstance player)
	{
		if (player.isCastingNow() || player.isCastingSimultaneouslyNow())
		{
			return false;
		}
		else if (player.isDead() || player.isFakeDeath())
		{
			return false;
		}
		else if (player.isSitting())
		{
			return false;
		}
		else if (player.isInStoreMode())
		{
			return false;
		}
		else if (!isInsideRadius(player, INTERACTION_DISTANCE, true, false))
		{
			return false;
		}
		else if ((player.getInstanceId() != getInstanceId()) && (player.getInstanceId() != -1))
		{
			return false;
		}
		else if (isBusy())
		{
			return false;
		}
		return true;
	}
	
	/**
	 * @return the L2Castle this L2NpcInstance belongs to.
	 */
	public final Castle getCastle()
	{
		// Get castle this NPC belongs to (excluding L2Attackable)
		if (_castleIndex < 0)
		{
			L2TownZone town = TownManager.getTown(getX(), getY(), getZ());
			
			if (town != null)
			{
				_castleIndex = CastleManager.getInstance().getCastleIndex(town.getTaxById());
			}
			
			if (_castleIndex < 0)
			{
				_castleIndex = CastleManager.getInstance().findNearestCastleIndex(this);
			}
			else
			{
				_isInTown = true; // Npc was spawned in town
			}
		}
		
		if (_castleIndex < 0)
		{
			return null;
		}
		
		return CastleManager.getInstance().getCastles().get(_castleIndex);
	}
	
	/**
	 * Verify if the given player is this NPC's lord.
	 * @param player the player to check
	 * @return {@code true} if the player is clan leader and owner of a castle of fort that this NPC belongs to, {@code false} otherwise
	 */
	public boolean isMyLord(L2PcInstance player)
	{
		if (player.isClanLeader())
		{
			final int castleId = getCastle() != null ? getCastle().getResidenceId() : -1;
			final int fortId = getFort() != null ? getFort().getResidenceId() : -1;
			return (player.getClan().getCastleId() == castleId) || (player.getClan().getFortId() == fortId);
		}
		return false;
	}
	
	public final SiegableHall getConquerableHall()
	{
		return CHSiegeManager.getInstance().getNearbyClanHall(getX(), getY(), 10000);
	}
	
	/**
	 * Return closest castle in defined distance
	 * @param maxDistance long
	 * @return Castle
	 */
	public final Castle getCastle(long maxDistance)
	{
		int index = CastleManager.getInstance().findNearestCastleIndex(this, maxDistance);
		
		if (index < 0)
		{
			return null;
		}
		
		return CastleManager.getInstance().getCastles().get(index);
	}
	
	/**
	 * @return the L2Fort this L2NpcInstance belongs to.
	 */
	public final Fort getFort()
	{
		// Get Fort this NPC belongs to (excluding L2Attackable)
		if (_fortIndex < 0)
		{
			Fort fort = FortManager.getInstance().getFort(getX(), getY(), getZ());
			if (fort != null)
			{
				_fortIndex = FortManager.getInstance().getFortIndex(fort.getResidenceId());
			}
			
			if (_fortIndex < 0)
			{
				_fortIndex = FortManager.getInstance().findNearestFortIndex(this);
			}
		}
		
		if (_fortIndex < 0)
		{
			return null;
		}
		
		return FortManager.getInstance().getForts().get(_fortIndex);
	}
	
	/**
	 * Return closest Fort in defined distance
	 * @param maxDistance long
	 * @return Fort
	 */
	public final Fort getFort(long maxDistance)
	{
		int index = FortManager.getInstance().findNearestFortIndex(this, maxDistance);
		
		if (index < 0)
		{
			return null;
		}
		
		return FortManager.getInstance().getForts().get(index);
	}
	
	public final boolean getIsInTown()
	{
		if (_castleIndex < 0)
		{
			getCastle();
		}
		
		return _isInTown;
	}
	
	/**
	 * Open a quest or chat window on client with the text of the L2NpcInstance in function of the command.<br>
	 * <B><U> Example of use </U> :</B>
	 * <ul>
	 * <li>Client packet : RequestBypassToServer</li>
	 * </ul>
	 * @param player
	 * @param command The command string received from client
	 */
	public void onBypassFeedback(L2PcInstance player, String command)
	{
		// if (canInteract(player))
		{
			if (isBusy() && (getBusyMessage().length() > 0))
			{
				player.sendPacket(ActionFailed.STATIC_PACKET);
				
				NpcHtmlMessage html = new NpcHtmlMessage(getObjectId());
				html.setFile(player, player.getHtmlPrefix(), "data/html/npcbusy.htm");
				html.replace("%busymessage%", getBusyMessage());
				html.replace("%npcname%", getName());
				html.replace("%playername%", player.getName());
				player.sendPacket(html);
			}
			else
			{
				IBypassHandler handler = BypassHandler.getInstance().getHandler(command);
				if (handler != null)
				{
					handler.useBypass(command, player, this);
				}
				else
				{
					_log.info(getClass().getSimpleName() + ": Unknown NPC bypass: \"" + command + "\" NpcId: " + getId());
				}
			}
		}
	}
	
	/**
	 * Return null (regular NPCs don't have weapons instancies).
	 */
	@Override
	public L2ItemInstance getActiveWeaponInstance()
	{
		// regular NPCs dont have weapons instancies
		return null;
	}
	
	/**
	 * Return the weapon item equiped in the right hand of the L2NpcInstance or null.
	 */
	@Override
	public L2Weapon getActiveWeaponItem()
	{
		// Get the weapon identifier equiped in the right hand of the L2NpcInstance
		int weaponId = getTemplate().getRHandId();
		
		if (weaponId < 1)
		{
			return null;
		}
		
		// Get the weapon item equiped in the right hand of the L2NpcInstance
		L2Item item = ItemData.getInstance().getTemplate(getTemplate().getRHandId());
		
		if (!(item instanceof L2Weapon))
		{
			return null;
		}
		
		return (L2Weapon) item;
	}
	
	/**
	 * Return null (regular NPCs don't have weapons instancies).
	 */
	@Override
	public L2ItemInstance getSecondaryWeaponInstance()
	{
		// regular NPCs dont have weapons instancies
		return null;
	}
	
	/**
	 * Return the weapon item equiped in the left hand of the L2NpcInstance or null.
	 */
	@Override
	public L2Weapon getSecondaryWeaponItem()
	{
		// Get the weapon identifier equiped in the right hand of the L2NpcInstance
		int weaponId = getTemplate().getLHandId();
		
		if (weaponId < 1)
		{
			return null;
		}
		
		// Get the weapon item equiped in the right hand of the L2NpcInstance
		L2Item item = ItemData.getInstance().getTemplate(getTemplate().getLHandId());
		
		if (!(item instanceof L2Weapon))
		{
			return null;
		}
		
		return (L2Weapon) item;
	}
	
	/**
	 * Send a Server->Client packet NpcHtmlMessage to the L2PcInstance in order to display the message of the L2NpcInstance.
	 * @param player The L2PcInstance who talks with the L2NpcInstance
	 * @param content The text of the L2NpcMessage
	 */
	public void insertObjectIdAndShowChatWindow(L2PcInstance player, String content)
	{
		// Send a Server->Client packet NpcHtmlMessage to the L2PcInstance in order to display the message of the L2NpcInstance
		content = content.replaceAll("%objectId%", String.valueOf(getObjectId()));
		NpcHtmlMessage npcReply = new NpcHtmlMessage(getObjectId());
		npcReply.setHtml(content);
		player.sendPacket(npcReply);
	}
	
	/**
	 * <B><U Format of the pathfile</U>:</B>
	 * <ul>
	 * <li>if the file exists on the server (page number = 0) : <B>data/html/default/12006.htm</B> (npcId-page number)</li>
	 * <li>if the file exists on the server (page number > 0) : <B>data/html/default/12006-1.htm</B> (npcId-page number)</li>
	 * <li>if the file doesn't exist on the server : <B>data/html/npcdefault.htm</B> (message : "I have nothing to say to you")</li>
	 * </ul>
	 * @param npcId The Identifier of the L2NpcInstance whose text must be display
	 * @param val The number of the page to display
	 * @return the pathfile of the selected HTML file in function of the npcId and of the page number.
	 */
	public String getHtmlPath(int npcId, int val)
	{
		final String pom = val == 0 ? "" + npcId : npcId + "-" + val;
		final String temp = "data/html/default/" + pom + ".htm";
		
		if (!Config.LAZY_CACHE)
		{
			// If not running lazy cache the file must be in the cache or it doesnt exist
			if (HtmCache.getInstance().contains(temp))
			{
				return temp;
			}
		}
		else
		{
			if (HtmCache.getInstance().isLoadable(temp))
			{
				return temp;
			}
		}
		
		// If the file is not found, the standard message "I have nothing to say to you" is returned
		return "data/html/npcdefault.htm";
	}
	
	public void showChatWindow(L2PcInstance player)
	{
		showChatWindow(player, 0);
	}
	
	/**
	 * Returns true if html exists
	 * @param player
	 * @param type
	 * @return boolean
	 */
	private boolean showPkDenyChatWindow(L2PcInstance player, String type)
	{
		String html = HtmCache.getInstance().getHtm(player, player.getHtmlPrefix(), "data/html/" + type + "/" + getId() + "-pk.htm");
		
		if (html != null)
		{
			NpcHtmlMessage pkDenyMsg = new NpcHtmlMessage(getObjectId());
			pkDenyMsg.setHtml(html);
			player.sendPacket(pkDenyMsg);
			player.sendPacket(ActionFailed.STATIC_PACKET);
			return true;
		}
		
		return false;
	}
	
	/**
	 * Open a chat window on client with the text of the L2NpcInstance.<br>
	 * <B><U>Actions</U>:</B>
	 * <ul>
	 * <li>Get the text of the selected HTML file in function of the npcId and of the page number</li>
	 * <li>Send a Server->Client NpcHtmlMessage containing the text of the L2NpcInstance to the L2PcInstance</li>
	 * <li>Send a Server->Client ActionFailed to the L2PcInstance in order to avoid that the client wait another packet</li>
	 * </ul>
	 * @param player The L2PcInstance that talk with the L2NpcInstance
	 * @param val The number of the page of the L2NpcInstance to display
	 */
	public void showChatWindow(L2PcInstance player, int val)
	{
		if (!isTalking())
		{
			player.sendPacket(ActionFailed.STATIC_PACKET);
			return;
		}
		if (player.isCursedWeaponEquipped() && (!(player.getTarget() instanceof L2ClanHallManagerInstance) || !(player.getTarget() instanceof L2DoormenInstance)))
		{
			player.setTarget(player);
			return;
		}
		if (player.getKarma() > 0)
		{
			if (!Config.ALT_GAME_KARMA_PLAYER_CAN_SHOP && (this instanceof L2MerchantInstance))
			{
				if (showPkDenyChatWindow(player, "merchant"))
				{
					return;
				}
			}
			else if (!Config.ALT_GAME_KARMA_PLAYER_CAN_USE_GK && (this instanceof L2TeleporterInstance))
			{
				if (showPkDenyChatWindow(player, "teleporter"))
				{
					return;
				}
			}
			else if (!Config.ALT_GAME_KARMA_PLAYER_CAN_USE_WAREHOUSE && (this instanceof L2WarehouseInstance))
			{
				if (showPkDenyChatWindow(player, "warehouse"))
				{
					return;
				}
			}
		}
		
		if (getTemplate().isType("L2Auctioneer") && (val == 0))
		{
			return;
		}
		
		int npcId = getTemplate().getId();
		
		/* For use with Seven Signs implementation */
		String filename = SevenSigns.SEVEN_SIGNS_HTML_PATH;
		int sealAvariceOwner = SevenSigns.getInstance().getSealOwner(SevenSigns.SEAL_AVARICE);
		int sealGnosisOwner = SevenSigns.getInstance().getSealOwner(SevenSigns.SEAL_GNOSIS);
		int playerCabal = SevenSigns.getInstance().getPlayerCabal(player.getObjectId());
		int compWinner = SevenSigns.getInstance().getCabalHighestScore();
		
		switch (npcId)
		{
			case 31127: //
			case 31128: //
			case 31129: // Dawn Festival Guides
			case 31130: //
			case 31131: //
				filename += "festival/dawn_guide.htm";
				break;
			case 31137: //
			case 31138: //
			case 31139: // Dusk Festival Guides
			case 31140: //
			case 31141: //
				filename += "festival/dusk_guide.htm";
				break;
			case 31092: // Black Marketeer of Mammon
				filename += "blkmrkt_1.htm";
				break;
			case 31113: // Merchant of Mammon
				if (Config.ALT_STRICT_SEVENSIGNS)
				{
					switch (compWinner)
					{
						case SevenSigns.CABAL_DAWN:
							if ((playerCabal != compWinner) || (playerCabal != sealAvariceOwner))
							{
								player.sendPacket(SystemMessageId.CAN_BE_USED_BY_DAWN);
								player.sendPacket(ActionFailed.STATIC_PACKET);
								return;
							}
							break;
						case SevenSigns.CABAL_DUSK:
							if ((playerCabal != compWinner) || (playerCabal != sealAvariceOwner))
							{
								player.sendPacket(SystemMessageId.CAN_BE_USED_BY_DUSK);
								player.sendPacket(ActionFailed.STATIC_PACKET);
								return;
							}
							break;
						default:
							player.sendPacket(SystemMessageId.SSQ_COMPETITION_UNDERWAY);
							return;
					}
				}
				filename += "mammmerch_1.htm";
				break;
			case 31126: // Blacksmith of Mammon
				if (Config.ALT_STRICT_SEVENSIGNS)
				{
					switch (compWinner)
					{
						case SevenSigns.CABAL_DAWN:
							if ((playerCabal != compWinner) || (playerCabal != sealGnosisOwner))
							{
								player.sendPacket(SystemMessageId.CAN_BE_USED_BY_DAWN);
								player.sendPacket(ActionFailed.STATIC_PACKET);
								return;
							}
							break;
						case SevenSigns.CABAL_DUSK:
							if ((playerCabal != compWinner) || (playerCabal != sealGnosisOwner))
							{
								player.sendPacket(SystemMessageId.CAN_BE_USED_BY_DUSK);
								player.sendPacket(ActionFailed.STATIC_PACKET);
								return;
							}
							break;
						default:
							player.sendPacket(SystemMessageId.SSQ_COMPETITION_UNDERWAY);
							return;
					}
				}
				filename += "mammblack_1.htm";
				break;
			case 31132:
			case 31133:
			case 31134:
			case 31135:
			case 31136: // Festival Witches
			case 31142:
			case 31143:
			case 31144:
			case 31145:
			case 31146:
				filename += "festival/festival_witch.htm";
				break;
			case 31688:
				if (player.isNoble())
				{
					filename = Olympiad.OLYMPIAD_HTML_PATH + "noble_main.htm";
				}
				else
				{
					filename = (getHtmlPath(npcId, val));
				}
				break;
			case 31690:
			case 31769:
			case 31770:
			case 31771:
			case 31772:
				if (player.isHero() || player.isNoble())
				{
					filename = Olympiad.OLYMPIAD_HTML_PATH + "hero_main.htm";
				}
				else
				{
					filename = (getHtmlPath(npcId, val));
				}
				break;
			case 36402:
				if (player.getOlympiadBuffCount() > 0)
				{
					filename = (player.getOlympiadBuffCount() == Config.ALT_OLY_MAX_BUFFS ? Olympiad.OLYMPIAD_HTML_PATH + "olympiad_buffs.htm" : Olympiad.OLYMPIAD_HTML_PATH + "olympiad_5buffs.htm");
				}
				else
				{
					filename = Olympiad.OLYMPIAD_HTML_PATH + "olympiad_nobuffs.htm";
				}
				break;
			case 30298: // Blacksmith Pinter
				if (player.isAcademyMember())
				{
					filename = (getHtmlPath(npcId, 1));
				}
				else
				{
					filename = (getHtmlPath(npcId, val));
				}
				break;
			default:
				if ((npcId >= 31865) && (npcId <= 31918))
				{
					if (val == 0)
					{
						filename += "rift/GuardianOfBorder.htm";
					}
					else
					{
						filename += "rift/GuardianOfBorder-" + val + ".htm";
					}
					break;
				}
				if (((npcId >= 31093) && (npcId <= 31094)) || ((npcId >= 31172) && (npcId <= 31201)) || ((npcId >= 31239) && (npcId <= 31254)))
				{
					return;
				}
				// Get the text of the selected HTML file in function of the npcId and of the page number
				filename = (getHtmlPath(npcId, val));
				break;
		}
		
		if (SunriseEvents.onNpcAction(player, this))
		{
			player.sendPacket(ActionFailed.STATIC_PACKET);
			return;
		}
		
		// Send a Server->Client NpcHtmlMessage containing the text of the L2NpcInstance to the L2PcInstance
		NpcHtmlMessage html = new NpcHtmlMessage(getObjectId());
		html.setFile(player, player.getHtmlPrefix(), filename);
		
		if (this instanceof L2MerchantInstance)
		{
			if (Config.LIST_PET_RENT_NPC.contains(npcId))
			{
				html.replace("_Quest", "_RentPet\">Rent Pet</a><br><a action=\"bypass -h npc_%objectId%_Quest");
			}
		}
		
		html.replace("%objectId%", String.valueOf(getObjectId()));
		html.replace("%festivalMins%", SevenSignsFestival.getInstance().getTimeToNextFestivalStr());
		player.sendPacket(html);
		
		// Send a Server->Client ActionFailed to the L2PcInstance in order to avoid that the client wait another packet
		player.sendPacket(ActionFailed.STATIC_PACKET);
	}
	
	/**
	 * Open a chat window on client with the text specified by the given file name and path, relative to the datapack root.
	 * @param player The L2PcInstance that talk with the L2NpcInstance
	 * @param filename The filename that contains the text to send
	 */
	public void showChatWindow(L2PcInstance player, String filename)
	{
		// Send a Server->Client NpcHtmlMessage containing the text of the L2NpcInstance to the L2PcInstance
		NpcHtmlMessage html = new NpcHtmlMessage(getObjectId());
		html.setFile(player, player.getHtmlPrefix(), filename);
		html.replace("%objectId%", String.valueOf(getObjectId()));
		player.sendPacket(html);
		
		// Send a Server->Client ActionFailed to the L2PcInstance in order to avoid that the client wait another packet
		player.sendPacket(ActionFailed.STATIC_PACKET);
	}
	
	/**
	 * @param isPremium
	 * @return the Exp Reward of this L2NpcInstance contained in the L2NpcTemplate (modified by RATE_XP).
	 */
	public int getExpReward(boolean isPremium)
	{
		if (isPremium)
		{
			return (int) (getTemplate().getRewardExp() * PremiumServiceConfigs.PREMIUM_RATE_XP);
		}
		return (int) (getTemplate().getRewardExp() * Config.RATE_XP);
	}
	
	/**
	 * @param isPremium
	 * @return the SP Reward of this L2NpcInstance contained in the L2NpcTemplate (modified by RATE_SP).
	 */
	public int getSpReward(boolean isPremium)
	{
		if (isPremium)
		{
			return (int) (getTemplate().getRewardSp() * PremiumServiceConfigs.PREMIUM_RATE_SP);
		}
		return (int) (getTemplate().getRewardSp() * Config.RATE_SP);
	}
	
	/**
	 * Kill the L2NpcInstance (the corpse disappeared after 7 seconds).<br>
	 * <B><U>Actions</U>:</B>
	 * <ul>
	 * <li>Create a DecayTask to remove the corpse of the L2NpcInstance after 7 seconds</li>
	 * <li>Set target to null and cancel Attack or Cast</li>
	 * <li>Stop movement</li>
	 * <li>Stop HP/MP/CP Regeneration task</li>
	 * <li>Stop all active skills effects in progress on the L2Character</li>
	 * <li>Send the Server->Client packet StatusUpdate with current HP and MP to all other L2PcInstance to inform</li>
	 * <li>Notify L2Character AI</li>
	 * </ul>
	 * @param killer The L2Character who killed it
	 */
	@Override
	public boolean doDie(L2Character killer)
	{
		// Antibot farm system
		AntibotSystem.sendFarmBotSignal(killer);
		
		if (!super.doDie(killer))
		{
			return false;
		}
		
		// normally this wouldn't really be needed, but for those few exceptions,
		// we do need to reset the weapons back to the initial template weapon.
		_currentLHandId = getTemplate().getLHandId();
		_currentRHandId = getTemplate().getRHandId();
		_currentCollisionHeight = getTemplate().getfCollisionHeight();
		_currentCollisionRadius = getTemplate().getfCollisionRadius();
		
		final L2Weapon weapon = (killer != null) ? killer.getActiveWeaponItem() : null;
		_killingBlowWeaponId = (weapon != null) ? weapon.getId() : 0;
		
		DecayTaskManager.getInstance().add(this);
		return true;
	}
	
	/**
	 * Set the spawn of the L2NpcInstance.
	 * @param spawn The L2Spawn that manage the L2NpcInstance
	 */
	public void setSpawn(L2Spawn spawn)
	{
		_spawn = spawn;
	}
	
	@Override
	public void onSpawn()
	{
		super.onSpawn();
		
		// Recharge shots
		_soulshotamount = getTemplate().getSoulShot();
		_spiritshotamount = getTemplate().getSpiritShot();
		_killingBlowWeaponId = 0;
		
		if (isTeleporting())
		{
			EventDispatcher.getInstance().notifyEventAsync(new OnNpcTeleport(this), this);
		}
		else
		{
			EventDispatcher.getInstance().notifyEventAsync(new OnNpcSpawn(this), this);
		}
		
		if (!isTeleporting())
		{
			WalkingManager.getInstance().onSpawn(this);
		}
	}
	
	/**
	 * Remove the L2NpcInstance from the world and update its spawn object (for a complete removal use the deleteMe method).<br>
	 * <B><U>Actions</U>:</B>
	 * <ul>
	 * <li>Remove the L2NpcInstance from the world when the decay task is launched</li>
	 * <li>Decrease its spawn counter</li>
	 * <li>Manage Siege task (killFlag, killCT)</li>
	 * </ul>
	 * <FONT COLOR=#FF0000><B> <U>Caution</U> : This method DOESN'T REMOVE the object from _allObjects of L2World </B></FONT><BR>
	 * <FONT COLOR=#FF0000><B> <U>Caution</U> : This method DOESN'T SEND Server->Client packets to players</B></FONT>
	 */
	@Override
	public void onDecay()
	{
		if (isDecayed())
		{
			return;
		}
		
		setDecayed(true);
		
		// Remove the L2NpcInstance from the world when the decay task is launched
		super.onDecay();
		
		// Decrease its spawn counter
		if (_spawn != null)
		{
			_spawn.decreaseCount(this);
		}
		
		// Notify Walking Manager
		WalkingManager.getInstance().onDeath(this);
		
		// Removes itself from the summoned list.
		final L2Character summoner = getSummoner();
		if ((summoner != null) && summoner.isNpc())
		{
			((L2Npc) summoner).removeSummonedNpc(getObjectId());
		}
	}
	
	/**
	 * Remove PROPERLY the L2NpcInstance from the world.<br>
	 * <B><U>Actions</U>:</B>
	 * <ul>
	 * <li>Remove the L2NpcInstance from the world and update its spawn object</li>
	 * <li>Remove all L2Object from _knownObjects and _knownPlayer of the L2NpcInstance then cancel Attack or Cast and notify AI</li>
	 * <li>Remove L2Object object from _allObjects of L2World</li>
	 * </ul>
	 * <FONT COLOR=#FF0000><B><U>Caution</U>: This method DOESN'T SEND Server->Client packets to players</B></FONT><br>
	 * UnAfraid: TODO: Add Listener here
	 */
	@Override
	public void deleteMe()
	{
		try
		{
			onDecay();
		}
		catch (Exception e)
		{
			_log.error("Failed decayMe().", e);
		}
		try
		{
			if (_fusionSkill != null)
			{
				abortCast();
			}
			
			for (L2Character character : getKnownList().getKnownCharacters())
			{
				if ((character.getFusionSkill() != null) && (character.getFusionSkill().getTarget() == this))
				{
					character.abortCast();
				}
			}
		}
		catch (Exception e)
		{
			_log.error("deleteMe()", e);
		}
		
		// Remove all L2Object from _knownObjects and _knownPlayer of the L2Character then cancel Attack or Cast and notify AI
		try
		{
			getKnownList().removeAllKnownObjects();
		}
		catch (Exception e)
		{
			_log.error("Failed removing cleaning knownlist.", e);
		}
		
		// Remove L2Object object from _allObjects of L2World
		L2World.getInstance().removeObject(this);
		
		super.deleteMe();
	}
	
	/**
	 * @return the L2Spawn object that manage this L2NpcInstance.
	 */
	public L2Spawn getSpawn()
	{
		return _spawn;
	}
	
	@Override
	public String toString()
	{
		return getClass().getSimpleName() + ":" + getName() + "(" + getId() + ")" + "[" + getObjectId() + "]";
	}
	
	public boolean isDecayed()
	{
		return _isDecayed;
	}
	
	public void setDecayed(boolean decayed)
	{
		_isDecayed = decayed;
	}
	
	public void endDecayTask()
	{
		if (!isDecayed())
		{
			DecayTaskManager.getInstance().cancel(this);
			onDecay();
		}
	}
	
	public boolean isMob() // rather delete this check
	{
		return false; // This means we use MAX_NPC_ANIMATION instead of MAX_MONSTER_ANIMATION
	}
	
	// Two functions to change the appearance of the equipped weapons on the NPC
	// This is only useful for a few NPCs and is most likely going to be called from AI
	public void setLHandId(int newWeaponId)
	{
		_currentLHandId = newWeaponId;
		updateAbnormalEffect();
	}
	
	public void setRHandId(int newWeaponId)
	{
		_currentRHandId = newWeaponId;
		updateAbnormalEffect();
	}
	
	public void setLRHandId(int newLWeaponId, int newRWeaponId)
	{
		_currentRHandId = newRWeaponId;
		_currentLHandId = newLWeaponId;
		updateAbnormalEffect();
	}
	
	public void setEnchant(int newEnchantValue)
	{
		_currentEnchant = newEnchantValue;
		updateAbnormalEffect();
	}
	
	public boolean isShowName()
	{
		return getTemplate().isShowName();
	}
	
	@Override
	public boolean isTargetable()
	{
		return getTemplate().isTargetable();
	}
	
	public void setCollisionHeight(double height)
	{
		_currentCollisionHeight = height;
	}
	
	public void setCollisionRadius(double radius)
	{
		_currentCollisionRadius = radius;
	}
	
	public double getCollisionHeight()
	{
		return _currentCollisionHeight;
	}
	
	public double getCollisionRadius()
	{
		return _currentCollisionRadius;
	}
	
	@Override
	public void sendInfo(L2PcInstance activeChar)
	{
		if (isVisibleFor(activeChar))
		{
			if (Config.CHECK_KNOWN && activeChar.isGM())
			{
				activeChar.sendMessage("Added NPC: " + getName());
			}
			
			if (getRunSpeed() == 0)
			{
				activeChar.sendPacket(new ServerObjectInfo(this, activeChar));
			}
			else
			{
				activeChar.sendPacket(new AbstractNpcInfo.NpcInfo(this, activeChar));
			}
		}
	}
	
	public void showNoTeachHtml(L2PcInstance player)
	{
		int npcId = getId();
		String html = "";
		
		if (this instanceof L2WarehouseInstance)
		{
			html = HtmCache.getInstance().getHtm(player, player.getHtmlPrefix(), "data/html/warehouse/" + npcId + "-noteach.htm");
		}
		else if (this instanceof L2TrainerInstance)
		{
			html = HtmCache.getInstance().getHtm(player, player.getHtmlPrefix(), "data/html/trainer/" + npcId + "-noteach.htm");
			// Trainer Healer?
			if (html == null)
			{
				html = HtmCache.getInstance().getHtm(player, player.getHtmlPrefix(), "data/scripts/ai/npc/Trainers/HealerTrainer/" + npcId + "-noteach.html");
			}
		}
		
		final NpcHtmlMessage noTeachMsg = new NpcHtmlMessage(getObjectId());
		if (html == null)
		{
			_log.warn("Npc " + npcId + " missing noTeach html!");
			noTeachMsg.setHtml("<html><body>I cannot teach you any skills.<br>You must find your current class teachers.</body></html>");
		}
		else
		{
			noTeachMsg.setHtml(html);
			noTeachMsg.replace("%objectId%", String.valueOf(getObjectId()));
		}
		player.sendPacket(noTeachMsg);
	}
	
	public L2Npc scheduleDespawn(long delay)
	{
		ThreadPoolManager.getInstance().scheduleGeneral(() ->
		{
			if (!isDecayed())
			{
				deleteMe();
			}
		} , delay);
		return this;
	}
	
	@Override
	protected final void notifyQuestEventSkillFinished(L2Skill skill, L2Object target)
	{
		if ((target != null) && target.isPlayable())
		{
			EventDispatcher.getInstance().notifyEventAsync(new OnNpcSkillFinished(this, target.getActingPlayer(), skill), this);
		}
	}
	
	@Override
	public boolean isMovementDisabled()
	{
		return super.isMovementDisabled() || (getCanMove() == 0) || getAiType().equals(AIType.CORPSE);
	}
	
	public AIType getAiType()
	{
		return getTemplate().getAIType();
	}
	
	public void setDisplayEffect(int val)
	{
		if (val != _displayEffect)
		{
			_displayEffect = val;
			broadcastPacket(new ExChangeNpcState(getObjectId(), val));
		}
	}
	
	public int getDisplayEffect()
	{
		return _displayEffect;
	}
	
	public int getColorEffect()
	{
		return 0;
	}
	
	@Override
	public int getMinShopDistanceNPC()
	{
		return CustomServerConfigs.SHOP_MIN_RANGE_FROM_NPC;
	}
	
	@Override
	public int getMinShopDistancePlayer()
	{
		return CustomServerConfigs.SHOP_MIN_RANGE_FROM_PLAYER;
	}
	
	public boolean isTempInvis()
	{
		return _tempInvis;
	}
	
	public void setTempInvis(boolean tempInvis)
	{
		_tempInvis = tempInvis;
	}
	
	public void broadcastNpcSay(String text)
	{
		broadcastNpcSay(0, text);
	}
	
	public void broadcastNpcSay(int messageType, String text)
	{
		broadcastPacket(new NpcSay(getObjectId(), messageType, getId(), text));
	}
	
	/**
	 * @return the character that summoned this NPC.
	 */
	public L2Character getSummoner()
	{
		return _summoner;
	}
	
	/**
	 * @param summoner the summoner of this NPC.
	 */
	public void setSummoner(L2Character summoner)
	{
		_summoner = summoner;
	}
	
	@Override
	public boolean isNpc()
	{
		return true;
	}
	
	@Override
	public void setTeam(Team team)
	{
		super.setTeam(team);
		broadcastInfo();
	}
	
	/**
	 * @return {@code true} if this L2Npc is registered in WalkingManager
	 */
	@Override
	public boolean isWalker()
	{
		return WalkingManager.getInstance().isRegistered(this);
	}
	
	@Override
	public boolean isRunner()
	{
		return _isRunner;
	}
	
	public void setIsRunner(boolean status)
	{
		_isRunner = status;
	}
	
	@Override
	public boolean isChargedShot(ShotType type)
	{
		return (_shotsMask & type.getMask()) == type.getMask();
	}
	
	@Override
	public void setChargedShot(ShotType type, boolean charged)
	{
		if (charged)
		{
			_shotsMask |= type.getMask();
		}
		else
		{
			_shotsMask &= ~type.getMask();
		}
	}
	
	@Override
	public void rechargeShots(boolean physical, boolean magic)
	{
		if ((_soulshotamount > 0) || (_spiritshotamount > 0))
		{
			if (physical)
			{
				if ((_soulshotamount == 0) || (Rnd.get(100) > getSoulShotChance()))
				{
					return;
				}
				_soulshotamount--;
				Broadcast.toSelfAndKnownPlayersInRadius(this, new MagicSkillUse(this, this, 2154, 1, 0, 0), 600);
				setChargedShot(ShotType.SOULSHOTS, true);
			}
			if (magic)
			{
				if ((_spiritshotamount == 0) || (Rnd.get(100) > getSpiritShotChance()))
				{
					return;
				}
				_spiritshotamount--;
				Broadcast.toSelfAndKnownPlayersInRadius(this, new MagicSkillUse(this, this, 2039, 1, 0, 0), 600);
				setChargedShot(ShotType.SPIRITSHOTS, true);
			}
		}
	}
	
	/**
	 * Short wrapper for backward compatibility
	 * @return stored script value
	 */
	public int getScriptValue()
	{
		return getVariables().getInt("SCRIPT_VAL");
	}
	
	/**
	 * Short wrapper for backward compatibility. Stores script value
	 * @param val value to store
	 */
	public void setScriptValue(int val)
	{
		getVariables().set("SCRIPT_VAL", val);
	}
	
	/**
	 * Short wrapper for backward compatibility.
	 * @param val value to store
	 * @return {@code true} if stored script value equals given value, {@code false} otherwise
	 */
	public boolean isScriptValue(int val)
	{
		return getVariables().getInt("SCRIPT_VAL") == val;
	}
	
	/**
	 * @param paramName the parameter name to check
	 * @return given AI parameter value
	 */
	public int getAIValue(final String paramName)
	{
		return hasAIValue(paramName) ? NpcPersonalAIData.getInstance().getAIValue(getSpawn().getName(), paramName) : -1;
	}
	
	/**
	 * @param paramName the parameter name to check
	 * @return {@code true} if given parameter is set for NPC, {@code false} otherwise
	 */
	public boolean hasAIValue(final String paramName)
	{
		return (getSpawn() != null) && (getSpawn().getName() != null) && NpcPersonalAIData.getInstance().hasAIValue(getSpawn().getName(), paramName);
	}
	
	/**
	 * @param npc NPC to check
	 * @return {@code true} if both given NPC and this NPC is in the same spawn group, {@code false} otherwise
	 */
	public boolean isInMySpawnGroup(L2Npc npc)
	{
		return ((getSpawn() != null) && (npc.getSpawn() != null) && (getSpawn().getName() != null) && (getSpawn().getName().equals(npc.getSpawn().getName())));
	}
	
	/**
	 * @return {@code true} if NPC currently located in own spawn point, {@code false} otherwise
	 */
	public boolean staysInSpawnLoc()
	{
		return ((getSpawn() != null) && (getSpawn().getX(this) == getX()) && (getSpawn().getY(this) == getY()));
	}
	
	/**
	 * @return {@code true} if {@link NpcVariables} instance is attached to current player's scripts, {@code false} otherwise.
	 */
	public boolean hasVariables()
	{
		return getScript(NpcVariables.class) != null;
	}
	
	/**
	 * @return {@link NpcVariables} instance containing parameters regarding NPC.
	 */
	public NpcVariables getVariables()
	{
		final NpcVariables vars = getScript(NpcVariables.class);
		return vars != null ? vars : addScript(new NpcVariables());
	}
	
	/**
	 * Send an "event" to all NPC's within given radius
	 * @param eventName - name of event
	 * @param radius - radius to send event
	 * @param reference - L2Object to pass, if needed
	 */
	public void broadcastEvent(String eventName, int radius, L2Object reference)
	{
		for (L2Object obj : L2World.getInstance().getVisibleObjects(this, radius))
		{
			if (obj.isNpc() && obj.hasListener(EventType.ON_NPC_EVENT_RECEIVED))
			{
				EventDispatcher.getInstance().notifyEventAsync(new OnNpcEventReceived(eventName, this, (L2Npc) obj, reference), obj);
			}
		}
	}
	
	/**
	 * Sends an event to a given object.
	 * @param eventName the event name
	 * @param receiver the receiver
	 * @param reference the reference
	 */
	public void sendScriptEvent(String eventName, L2Object receiver, L2Object reference)
	{
		EventDispatcher.getInstance().notifyEventAsync(new OnNpcEventReceived(eventName, this, (L2Npc) receiver, reference), receiver);
	}
	
	/**
	 * Gets point in range between radiusMin and radiusMax from this NPC
	 * @param radiusMin miminal range from NPC (not closer than)
	 * @param radiusMax maximal range from NPC (not further than)
	 * @return Location in given range from this NPC
	 */
	public Location getPointInRange(int radiusMin, int radiusMax)
	{
		if ((radiusMax == 0) || (radiusMax < radiusMin))
		{
			return new Location(getX(), getY(), getZ());
		}
		
		final int radius = Rnd.get(radiusMin, radiusMax);
		final double angle = Rnd.nextDouble() * 2 * Math.PI;
		
		return new Location((int) (getX() + (radius * Math.cos(angle))), (int) (getY() + (radius * Math.sin(angle))), getZ());
	}
	
	/**
	 * Drops an item.
	 * @param player the last attacker or main damage dealer
	 * @param itemId the item ID
	 * @param itemCount the item count
	 * @return the dropped item
	 */
	public L2ItemInstance dropItem(L2PcInstance player, int itemId, long itemCount)
	{
		return dropItem(player, itemId, itemCount, false);
	}
	
	/**
	 * Drops an item.
	 * @param player the last attacker or main damage dealer
	 * @param itemId the item ID
	 * @param itemCount the item count
	 * @param fromMob
	 * @return the dropped item
	 */
	public L2ItemInstance dropItem(L2PcInstance player, int itemId, long itemCount, boolean fromMob)
	{
		L2ItemInstance item = null;
		for (int i = 0; i < itemCount; i++)
		{
			// Randomize drop position.
			final int newX = (getX() + Rnd.get((RANDOM_ITEM_DROP_LIMIT * 2) + 1)) - RANDOM_ITEM_DROP_LIMIT;
			final int newY = (getY() + Rnd.get((RANDOM_ITEM_DROP_LIMIT * 2) + 1)) - RANDOM_ITEM_DROP_LIMIT;
			final int newZ = getZ() + 20;
			
			if (ItemData.getInstance().getTemplate(itemId) == null)
			{
				_log.error("Item doesn't exist so cannot be dropped. Item ID: " + itemId + " Quest: " + getName());
				return null;
			}
			
			item = ItemData.getInstance().createItem("Loot", itemId, itemCount, player, this);
			if (item == null)
			{
				return null;
			}
			
			if (player != null)
			{
				item.getDropProtection().protect(player);
			}
			
			item.fromMob = fromMob;
			item.dropMe(this, newX, newY, newZ);
			
			// Add drop to auto destroy item task.
			if (!Config.LIST_PROTECTED_ITEMS.contains(itemId))
			{
				if (((Config.AUTODESTROY_ITEM_AFTER > 0) && !item.getItem().hasExImmediateEffect()) || ((Config.HERB_AUTO_DESTROY_TIME > 0) && item.getItem().hasExImmediateEffect()))
				{
					ItemsAutoDestroy.getInstance().addItem(item);
				}
			}
			item.setProtected(false);
			
			// If stackable, end loop as entire count is included in 1 instance of item.
			if (item.isStackable() || !Config.MULTIPLE_ITEM_DROP)
			{
				break;
			}
		}
		return item;
	}
	
	/**
	 * Method overload for {@link L2Attackable#dropItem(L2PcInstance, int, long)}
	 * @param player the last attacker or main damage dealer
	 * @param item the item holder
	 * @return the dropped item
	 */
	public L2ItemInstance dropItem(L2PcInstance player, ItemHolder item)
	{
		return dropItem(player, item.getId(), item.getCount(), item.isFromMob());
	}
	
	@Override
	public boolean isVisibleFor(L2PcInstance player)
	{
		if (hasListener(EventType.ON_NPC_CAN_BE_SEEN))
		{
			final TerminateReturn term = EventDispatcher.getInstance().notifyEvent(new OnNpcCanBeSeen(this, player), this, TerminateReturn.class);
			if (term != null)
			{
				return term.terminate();
			}
		}
		return super.isVisibleFor(player);
	}
	
	/**
	 * Sets if the players can talk with this npc or not
	 * @param val {@code true} if the players can talk, {@code false} otherwise
	 */
	public void setTalking(boolean val)
	{
		_isTalking = val;
	}
	
	/**
	 * Checks if the players can talk to this npc.
	 * @return {@code true} if the players can talk, {@code false} otherwise.
	 */
	public boolean isTalking()
	{
		return _isTalking;
	}
	
	/**
	 * Sets the weapon id with which this npc was killed.
	 * @param weaponId
	 */
	public void setKillingBlowWeapon(int weaponId)
	{
		_killingBlowWeaponId = weaponId;
	}
	
	/**
	 * @return the id of the weapon with which player killed this npc.
	 */
	public int getKillingBlowWeapon()
	{
		return _killingBlowWeaponId;
	}
	
	/**
	 * Adds a summoned NPC.
	 * @param npc the summoned NPC
	 */
	public final void addSummonedNpc(L2Npc npc)
	{
		if (_summonedNpcs == null)
		{
			synchronized (this)
			{
				if (_summonedNpcs == null)
				{
					_summonedNpcs = new ConcurrentHashMap<>();
				}
			}
		}
		
		_summonedNpcs.put(npc.getObjectId(), npc);
		
		npc.setSummoner(this);
	}
	
	/**
	 * Removes a summoned NPC by object ID.
	 * @param objectId the summoned NPC object ID
	 */
	public final void removeSummonedNpc(int objectId)
	{
		if (_summonedNpcs != null)
		{
			_summonedNpcs.remove(objectId);
		}
	}
	
	/**
	 * Gets the summoned NPCs.
	 * @return the summoned NPCs
	 */
	public final Collection<L2Npc> getSummonedNpcs()
	{
		return _summonedNpcs != null ? _summonedNpcs.values() : Collections.<L2Npc> emptyList();
	}
	
	/**
	 * Gets the summoned NPC by object ID.
	 * @param objectId the summoned NPC object ID
	 * @return the summoned NPC
	 */
	public final L2Npc getSummonedNpc(int objectId)
	{
		if (_summonedNpcs != null)
		{
			return _summonedNpcs.get(objectId);
		}
		return null;
	}
	
	/**
	 * Gets the summoned NPC count.
	 * @return the summoned NPC count
	 */
	public final int getSummonedNpcCount()
	{
		return _summonedNpcs != null ? _summonedNpcs.size() : 0;
	}
	
	/**
	 * Resets the summoned NPCs list.
	 */
	public final void resetSummonedNpcs()
	{
		if (_summonedNpcs != null)
		{
			_summonedNpcs.clear();
		}
	}
	
	private boolean _blocked;
	
	public void block()
	{
		_blocked = true;
	}
	
	public void unblock()
	{
		_blocked = false;
	}
	
	public boolean isBlocked()
	{
		return _blocked;
	}
}
