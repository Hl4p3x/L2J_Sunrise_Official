/*
 * Copyright (C) L2J Sunrise
 * This file is part of L2J Sunrise.
 */
package l2r.gameserver.model.actor;

import static l2r.gameserver.enums.CtrlIntention.AI_INTENTION_ACTIVE;
import static l2r.gameserver.enums.CtrlIntention.AI_INTENTION_ATTACK;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import l2r.Config;
import l2r.gameserver.GameTimeController;
import l2r.gameserver.GeoData;
import l2r.gameserver.ThreadPoolManager;
import l2r.gameserver.ai.L2AttackableAI;
import l2r.gameserver.ai.L2CharacterAI;
import l2r.gameserver.data.xml.impl.CategoryData;
import l2r.gameserver.data.xml.impl.DoorData;
import l2r.gameserver.data.xml.impl.ItemData;
import l2r.gameserver.data.xml.impl.SkillData;
import l2r.gameserver.enums.BroadcastStatusType;
import l2r.gameserver.enums.CategoryType;
import l2r.gameserver.enums.CtrlEvent;
import l2r.gameserver.enums.CtrlIntention;
import l2r.gameserver.enums.InstanceType;
import l2r.gameserver.enums.PcCondOverride;
import l2r.gameserver.enums.ShotType;
import l2r.gameserver.enums.Team;
import l2r.gameserver.enums.TeleportWhereType;
import l2r.gameserver.enums.ZoneIdType;
import l2r.gameserver.handler.ISkillHandler;
import l2r.gameserver.handler.SkillHandler;
import l2r.gameserver.idfactory.IdFactory;
import l2r.gameserver.instancemanager.CHSiegeManager;
import l2r.gameserver.instancemanager.ClanHallManager;
import l2r.gameserver.instancemanager.InstanceManager;
import l2r.gameserver.instancemanager.MapRegionManager;
import l2r.gameserver.instancemanager.TerritoryWarManager;
import l2r.gameserver.model.ChanceSkillList;
import l2r.gameserver.model.CharEffectList;
import l2r.gameserver.model.FusionSkill;
import l2r.gameserver.model.L2AccessLevel;
import l2r.gameserver.model.L2Object;
import l2r.gameserver.model.L2Party;
import l2r.gameserver.model.L2World;
import l2r.gameserver.model.L2WorldRegion;
import l2r.gameserver.model.Location;
import l2r.gameserver.model.TimeStamp;
import l2r.gameserver.model.actor.instance.L2EventMapGuardInstance;
import l2r.gameserver.model.actor.instance.L2PcInstance;
import l2r.gameserver.model.actor.instance.L2PetInstance;
import l2r.gameserver.model.actor.instance.L2RaidBossInstance;
import l2r.gameserver.model.actor.instance.L2RiftInvaderInstance;
import l2r.gameserver.model.actor.knownlist.CharKnownList;
import l2r.gameserver.model.actor.stat.CharStat;
import l2r.gameserver.model.actor.status.CharStatus;
import l2r.gameserver.model.actor.tasks.character.HitTask;
import l2r.gameserver.model.actor.tasks.character.MagicUseTask;
import l2r.gameserver.model.actor.tasks.character.NotifyAITask;
import l2r.gameserver.model.actor.tasks.character.PacketSenderTask;
import l2r.gameserver.model.actor.tasks.character.QueuedMagicUseTask;
import l2r.gameserver.model.actor.templates.L2CharTemplate;
import l2r.gameserver.model.actor.templates.L2NpcTemplate;
import l2r.gameserver.model.actor.transform.Transform;
import l2r.gameserver.model.actor.transform.TransformTemplate;
import l2r.gameserver.model.effects.AbnormalEffect;
import l2r.gameserver.model.effects.EffectFlag;
import l2r.gameserver.model.effects.L2Effect;
import l2r.gameserver.model.effects.L2EffectType;
import l2r.gameserver.model.entity.ClanHall;
import l2r.gameserver.model.entity.Instance;
import l2r.gameserver.model.entity.clanhall.SiegableHall;
import l2r.gameserver.model.events.Containers;
import l2r.gameserver.model.events.EventDispatcher;
import l2r.gameserver.model.events.EventType;
import l2r.gameserver.model.events.impl.character.OnCreatureAttack;
import l2r.gameserver.model.events.impl.character.OnCreatureAttackAvoid;
import l2r.gameserver.model.events.impl.character.OnCreatureAttacked;
import l2r.gameserver.model.events.impl.character.OnCreatureDamageDealt;
import l2r.gameserver.model.events.impl.character.OnCreatureDamageReceived;
import l2r.gameserver.model.events.impl.character.OnCreatureKill;
import l2r.gameserver.model.events.impl.character.OnCreatureSkillUse;
import l2r.gameserver.model.events.impl.character.OnCreatureTeleported;
import l2r.gameserver.model.events.impl.character.npc.OnNpcSkillSee;
import l2r.gameserver.model.events.listeners.AbstractEventListener;
import l2r.gameserver.model.events.returns.TerminateReturn;
import l2r.gameserver.model.holders.InvulSkillHolder;
import l2r.gameserver.model.holders.SkillHolder;
import l2r.gameserver.model.holders.SkillUseHolder;
import l2r.gameserver.model.interfaces.IChanceSkillTrigger;
import l2r.gameserver.model.interfaces.ILocational;
import l2r.gameserver.model.interfaces.ISkillsHolder;
import l2r.gameserver.model.itemcontainer.Inventory;
import l2r.gameserver.model.items.L2Item;
import l2r.gameserver.model.items.L2Weapon;
import l2r.gameserver.model.items.instance.L2ItemInstance;
import l2r.gameserver.model.items.type.WeaponType;
import l2r.gameserver.model.options.OptionsSkillHolder;
import l2r.gameserver.model.options.OptionsSkillType;
import l2r.gameserver.model.skills.CommonSkill;
import l2r.gameserver.model.skills.L2Skill;
import l2r.gameserver.model.skills.L2SkillType;
import l2r.gameserver.model.skills.l2skills.L2SkillSummon;
import l2r.gameserver.model.skills.targets.L2TargetType;
import l2r.gameserver.model.stats.Calculator;
import l2r.gameserver.model.stats.Formulas;
import l2r.gameserver.model.stats.Stats;
import l2r.gameserver.model.stats.functions.AbstractFunction;
import l2r.gameserver.network.NpcStringId;
import l2r.gameserver.network.SystemMessageId;
import l2r.gameserver.network.clientpackets.Say2;
import l2r.gameserver.network.serverpackets.ActionFailed;
import l2r.gameserver.network.serverpackets.Attack;
import l2r.gameserver.network.serverpackets.ChangeMoveType;
import l2r.gameserver.network.serverpackets.ChangeWaitType;
import l2r.gameserver.network.serverpackets.ExRotation;
import l2r.gameserver.network.serverpackets.FlyToLocation;
import l2r.gameserver.network.serverpackets.FlyToLocation.FlyType;
import l2r.gameserver.network.serverpackets.L2GameServerPacket;
import l2r.gameserver.network.serverpackets.MagicSkillCanceld;
import l2r.gameserver.network.serverpackets.MagicSkillLaunched;
import l2r.gameserver.network.serverpackets.MagicSkillUse;
import l2r.gameserver.network.serverpackets.MoveToLocation;
import l2r.gameserver.network.serverpackets.MoveToPawn;
import l2r.gameserver.network.serverpackets.NpcSay;
import l2r.gameserver.network.serverpackets.Revive;
import l2r.gameserver.network.serverpackets.ServerObjectInfo;
import l2r.gameserver.network.serverpackets.SetupGauge;
import l2r.gameserver.network.serverpackets.SocialAction;
import l2r.gameserver.network.serverpackets.StatusUpdate;
import l2r.gameserver.network.serverpackets.StopMove;
import l2r.gameserver.network.serverpackets.SystemMessage;
import l2r.gameserver.network.serverpackets.TeleportToLocation;
import l2r.gameserver.network.serverpackets.ValidateLocation;
import l2r.gameserver.pathfinding.AbstractNodeLoc;
import l2r.gameserver.pathfinding.PathFinding;
import l2r.gameserver.taskmanager.AttackStanceTaskManager;
import l2r.gameserver.util.Util;
import l2r.util.EmptyQueue;
import l2r.util.Rnd;

import gr.sr.interf.SunriseEvents;
import gr.sr.raidEngine.manager.RaidManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Mother class of all character objects of the world (PC, NPC...)<br>
 * L2Character:<br>
 * <ul>
 * <li>L2DoorInstance</li>
 * <li>L2Playable</li>
 * <li>L2Npc</li>
 * <li>L2StaticObjectInstance_attackend</li>
 * <li>L2Trap</li>
 * <li>L2Vehicle</li>
 * </ul>
 * <br>
 * <b>Concept of L2CharTemplate:</b><br>
 * Each L2Character owns generic and static properties (ex : all Keltir have the same number of HP...).<br>
 * All of those properties are stored in a different template for each type of L2Character.<br>
 * Each template is loaded once in the server cache memory (reduce memory use).<br>
 * When a new instance of L2Character is spawned, server just create a link between the instance and the template.<br>
 * This link is stored in {@link #_template}
 * @version $Revision: 1.53.2.45.2.34 $ $Date: 2005/04/11 10:06:08 $
 */
public abstract class L2Character extends L2Object implements ISkillsHolder
{
	public static final Logger _log = LoggerFactory.getLogger(L2Character.class);
	
	private volatile Set<L2Character> _attackByList;
	private volatile boolean _isCastingNow = false;
	private volatile boolean _isCastingSimultaneouslyNow = false;
	private L2Skill _lastSkillCast;
	private L2Skill _lastSimultaneousSkillCast;
	
	private boolean _isDead = false;
	private boolean _isImmobilized = false;
	private boolean _isOverloaded = false; // the char is carrying too much
	private boolean _isParalyzed = false;
	private boolean _isPendingRevive = false;
	private boolean _isRunning = false;
	private boolean _isNoRndWalk = false; // Is no random walk
	protected boolean _showSummonAnimation = false;
	protected boolean _isTeleporting = false;
	private boolean _isInvul = false;
	private boolean _isMortal = true; // Char will die when HP decreased to 0
	private boolean _isFlying = false;
	
	private CharStat _stat;
	private CharStatus _status;
	private L2CharTemplate _template; // The link on the L2CharTemplate object containing generic and static properties of this L2Character type (ex : Max HP, Speed...)
	private String _title;
	
	public static final double MAX_HP_BAR_PX = 352.0;
	
	private double _hpUpdateIncCheck = .0;
	private double _hpUpdateDecCheck = .0;
	private double _hpUpdateInterval = .0;
	
	/** Table of Calculators containing all used calculator */
	private Calculator[] _calculators;
	/** Map containing all skills of this character. */
	private final Map<Integer, L2Skill> _skills = new ConcurrentHashMap<>();
	/** Map containing the active chance skills on this character */
	private volatile ChanceSkillList _chanceSkills;
	/** Map containing the skill reuse time stamps. */
	private volatile Map<Integer, TimeStamp> _reuseTimeStampsSkills = new ConcurrentHashMap<>();
	/** Map containing the item reuse time stamps. */
	private volatile Map<Integer, TimeStamp> _reuseTimeStampsItems = new ConcurrentHashMap<>();
	/** Map containing all the disabled skills. */
	private volatile Map<Integer, Long> _disabledSkills = null;
	private boolean _allSkillsDisabled;
	/** Current force buff this caster is casting to a target */
	protected FusionSkill _fusionSkill;
	
	private final byte[] _zones = new byte[ZoneIdType.getZoneCount()];
	protected byte _zoneValidateCounter = 4;
	
	private L2Character _debugger = null;
	
	private final ReentrantLock _teleportLock = new ReentrantLock();
	
	private Team _team = Team.NONE;
	
	protected long _exceptions = 0L;
	
	private boolean _lethalable = true;
	
	private volatile Map<Integer, OptionsSkillHolder> _triggerSkills;
	
	private volatile Map<Integer, InvulSkillHolder> _invulAgainst;
	
	/** Movement data of this L2Character */
	protected MoveData _move;
	
	/** This creature's target. */
	private L2Object _target;
	
	/** Represents the time where the attack should end, in nanoseconds. */
	private volatile long _attackEndTime;
	private long _disableBowAttackEndTime;
	
	private int _castInterruptTime;
	
	/** Table of calculators containing all standard NPC calculator (ex : ACCURACY_COMBAT, EVASION_RATE) */
	private static final Calculator[] NPC_STD_CALCULATOR = Formulas.getStdNPCCalculators();
	
	protected L2CharacterAI _ai;
	
	/** Future Skill Cast */
	protected Future<?> _skillCast;
	protected Future<?> _skillCast2;
	
	/**
	 * @return True if debugging is enabled for this L2Character
	 */
	public boolean isDebug()
	{
		return _debugger != null;
	}
	
	/**
	 * Sets L2Character instance, to which debug packets will be send
	 * @param d
	 */
	public void setDebug(L2Character d)
	{
		_debugger = d;
	}
	
	/**
	 * Send debug packet.
	 * @param pkt
	 */
	public void sendDebugPacket(L2GameServerPacket pkt)
	{
		if (_debugger != null)
		{
			_debugger.sendPacket(pkt);
		}
	}
	
	/**
	 * Send debug text string
	 * @param msg
	 */
	public void sendDebugMessage(String msg)
	{
		if (_debugger != null)
		{
			_debugger.sendMessage(msg);
		}
	}
	
	/**
	 * @return character inventory, default null, overridden in L2Playable types and in L2NPcInstance
	 */
	public Inventory getInventory()
	{
		return null;
	}
	
	public boolean destroyItemByItemId(String process, int itemId, long count, L2Object reference, boolean sendMessage)
	{
		// Default: NPCs consume virtual items for their skills
		// TODO: should be logged if even happens.. should be false
		return true;
	}
	
	public boolean destroyItem(String process, int objectId, long count, L2Object reference, boolean sendMessage)
	{
		// Default: NPCs consume virtual items for their skills
		// TODO: should be logged if even happens.. should be false
		return true;
	}
	
	/**
	 * Check if the character is in the given zone Id.
	 * @param zone the zone Id to check
	 * @return {code true} if the character is in that zone
	 */
	@Override
	public final boolean isInsideZone(ZoneIdType zone)
	{
		Instance instance = InstanceManager.getInstance().getInstance(getInstanceId());
		switch (zone)
		{
			case PVP:
				if ((instance != null) && instance.isPvPInstance())
				{
					return true;
				}
				return (_zones[ZoneIdType.PVP.ordinal()] > 0) && (_zones[ZoneIdType.PEACE.ordinal()] == 0);
			case PEACE:
				if ((instance != null) && instance.isPvPInstance())
				{
					return false;
				}
		}
		return _zones[zone.ordinal()] > 0;
	}
	
	/**
	 * @param zone
	 * @param state
	 */
	public final void setInsideZone(ZoneIdType zone, final boolean state)
	{
		if (state)
		{
			_zones[zone.ordinal()]++;
		}
		else if (_zones[zone.ordinal()] > 0)
		{
			_zones[zone.ordinal()]--;
		}
		
		// vGodFather broadcast new status
		broadcastInfo();
	}
	
	/**
	 * This will return true if the player is transformed,<br>
	 * but if the player is not transformed it will return false.
	 * @return transformation status
	 */
	public boolean isTransformed()
	{
		return false;
	}
	
	public Transform getTransformation()
	{
		return null;
	}
	
	/**
	 * This will untransform a player if they are an instance of L2Pcinstance and if they are transformed.
	 */
	public void untransform()
	{
		// Just a place holder
	}
	
	/**
	 * This will return true if the player is GM,<br>
	 * but if the player is not GM it will return false.
	 * @return GM status
	 */
	public boolean isGM()
	{
		return false;
	}
	
	/**
	 * Overridden in L2PcInstance.
	 * @return the access level.
	 */
	public L2AccessLevel getAccessLevel()
	{
		return null;
	}
	
	/**
	 * Creates a creature.
	 * @param template the creature template
	 */
	public L2Character(L2CharTemplate template)
	{
		this(IdFactory.getInstance().getNextId(), template);
	}
	
	/**
	 * Constructor of L2Character.<br>
	 * <B><U>Concept</U>:</B><br>
	 * Each L2Character owns generic and static properties (ex : all Keltir have the same number of HP...).<br>
	 * All of those properties are stored in a different template for each type of L2Character. Each template is loaded once in the server cache memory (reduce memory use).<br>
	 * When a new instance of L2Character is spawned, server just create a link between the instance and the template This link is stored in <B>_template</B><br>
	 * <B><U> Actions</U>:</B>
	 * <ul>
	 * <li>Set the _template of the L2Character</li>
	 * <li>Set _overloaded to false (the character can take more items)</li>
	 * <li>If L2Character is a L2NPCInstance, copy skills from template to object</li>
	 * <li>If L2Character is a L2NPCInstance, link _calculators to NPC_STD_CALCULATOR</li>
	 * <li>If L2Character is NOT a L2NPCInstance, create an empty _skills slot</li>
	 * <li>If L2Character is a L2PcInstance or L2Summon, copy basic Calculator set to object</li>
	 * </ul>
	 * @param objectId Identifier of the object to initialized
	 * @param template The L2CharTemplate to apply to the object
	 */
	public L2Character(int objectId, L2CharTemplate template)
	{
		super(objectId);
		if (template == null)
		{
			throw new NullPointerException("Template is null!");
		}
		
		setInstanceType(InstanceType.L2Character);
		initCharStat();
		initCharStatus();
		
		// Set its template to the new L2Character
		_template = template;
		
		if (isDoor())
		{
			_calculators = Formulas.getStdDoorCalculators();
		}
		else if (isNpc())
		{
			// Copy the Standard Calculators of the L2NPCInstance in _calculators
			_calculators = NPC_STD_CALCULATOR;
			
			// Copy the skills of the L2NPCInstance from its template to the L2Character Instance
			// The skills list can be affected by spell effects so it's necessary to make a copy
			// to avoid that a spell affecting a L2NPCInstance, affects others L2NPCInstance of the same type too.
			for (L2Skill skill : template.getSkills().values())
			{
				addSkill(skill);
			}
		}
		else
		{
			// If L2Character is a L2PcInstance or a L2Summon, create the basic calculator set
			_calculators = new Calculator[Stats.NUM_STATS];
			
			if (isSummon())
			{
				// Copy the skills of the L2Summon from its template to the L2Character Instance
				// The skills list can be affected by spell effects so it's necessary to make a copy
				// to avoid that a spell affecting a L2Summon, affects others L2Summon of the same type too.
				for (L2Skill skill : template.getSkills().values())
				{
					addSkill(skill);
				}
			}
			
			Formulas.addFuncsToNewCharacter(this);
		}
		
		setIsInvul(true);
	}
	
	protected void initCharStatusUpdateValues()
	{
		_hpUpdateIncCheck = getMaxHp();
		_hpUpdateInterval = _hpUpdateIncCheck / MAX_HP_BAR_PX;
		_hpUpdateDecCheck = _hpUpdateIncCheck - _hpUpdateInterval;
	}
	
	/**
	 * Remove the L2Character from the world when the decay task is launched.<br>
	 * <FONT COLOR=#FF0000><B> <U>Caution</U> : This method DOESN'T REMOVE the object from _allObjects of L2World </B></FONT><BR>
	 * <FONT COLOR=#FF0000><B> <U>Caution</U> : This method DOESN'T SEND Server->Client packets to players</B></FONT>
	 */
	public void onDecay()
	{
		decayMe();
	}
	
	@Override
	public void onSpawn()
	{
		super.onSpawn();
		revalidateZone(true);
	}
	
	public void onTeleported()
	{
		if (!_teleportLock.tryLock())
		{
			return;
		}
		try
		{
			if (!isTeleporting())
			{
				return;
			}
			spawnMe(getX(), getY(), getZ());
			setIsTeleporting(false);
			EventDispatcher.getInstance().notifyEventAsync(new OnCreatureTeleported(this), this);
		}
		finally
		{
			_teleportLock.unlock();
		}
	}
	
	/**
	 * Add L2Character instance that is attacking to the attacker list.
	 * @param player The L2Character that attacks this one
	 */
	public void addAttackerToAttackByList(L2Character player)
	{
		// DS: moved to L2Attackable
	}
	
	/**
	 * Send a packet to the L2Character AND to all L2PcInstance in the _KnownPlayers of the L2Character.<br>
	 * <B><U>Concept</U>:</B><br>
	 * L2PcInstance in the detection area of the L2Character are identified in <B>_knownPlayers</B>.<br>
	 * In order to inform other players of state modification on the L2Character, server just need to go through _knownPlayers to send Server->Client Packet
	 * @param mov
	 */
	public void broadcastPacket(L2GameServerPacket mov)
	{
		mov.setInvisible(isInvisible());
		Collection<L2PcInstance> plrs = getKnownList().getKnownPlayers().values();
		for (L2PcInstance player : plrs)
		{
			if (player != null)
			{
				player.sendPacket(mov);
			}
		}
	}
	
	/**
	 * Send a packet to the L2Character AND to all L2PcInstance in the radius (max knownlist radius) from the L2Character.<br>
	 * <B><U>Concept</U>:</B><br>
	 * L2PcInstance in the detection area of the L2Character are identified in <B>_knownPlayers</B>.<br>
	 * In order to inform other players of state modification on the L2Character, server just need to go through _knownPlayers to send Server->Client Packet
	 * @param mov
	 * @param radiusInKnownlist
	 */
	public void broadcastPacket(L2GameServerPacket mov, int radiusInKnownlist)
	{
		mov.setInvisible(isInvisible());
		Collection<L2PcInstance> plrs = getKnownList().getKnownPlayers().values();
		for (L2PcInstance player : plrs)
		{
			if ((player != null) && isInsideRadius(player, radiusInKnownlist, false, false))
			{
				player.sendPacket(mov);
			}
		}
	}
	
	/**
	 * @return true if hp update should be done, false if not
	 */
	public boolean needHpUpdate()
	{
		double currentHp = getCurrentHp();
		double maxHp = getMaxHp();
		
		if ((currentHp <= 1.0) || (maxHp < MAX_HP_BAR_PX))
		{
			return true;
		}
		
		if ((currentHp < _hpUpdateDecCheck) || (Math.abs(currentHp - _hpUpdateDecCheck) <= 1e-6) || (currentHp > _hpUpdateIncCheck) || (Math.abs(currentHp - _hpUpdateIncCheck) <= 1e-6))
		{
			if (Math.abs(currentHp - maxHp) <= 1e-6)
			{
				_hpUpdateIncCheck = currentHp + 1;
				_hpUpdateDecCheck = currentHp - _hpUpdateInterval;
			}
			else
			{
				double doubleMulti = currentHp / _hpUpdateInterval;
				int intMulti = (int) doubleMulti;
				
				_hpUpdateDecCheck = _hpUpdateInterval * (doubleMulti < intMulti ? intMulti-- : intMulti);
				_hpUpdateIncCheck = _hpUpdateDecCheck + _hpUpdateInterval;
			}
			
			return true;
		}
		
		return false;
	}
	
	// vGodFather: addon
	public StatusUpdate makeStatusUpdate(int... fields)
	{
		StatusUpdate su = new StatusUpdate(getObjectId());
		for (int field : fields)
		{
			switch (field)
			{
				case StatusUpdate.CUR_HP:
					su.addAttribute(field, (int) getCurrentHp());
					break;
				case StatusUpdate.MAX_HP:
					su.addAttribute(field, getMaxHp());
					break;
				case StatusUpdate.CUR_MP:
					su.addAttribute(field, (int) getCurrentMp());
					break;
				case StatusUpdate.MAX_MP:
					su.addAttribute(field, getMaxMp());
					break;
				case StatusUpdate.CUR_LOAD:
					su.addAttribute(field, getCurrentLoad());
					break;
				case StatusUpdate.KARMA:
					su.addAttribute(field, getKarma());
					break;
				case StatusUpdate.CUR_CP:
					su.addAttribute(field, (int) getCurrentCp());
					break;
				case StatusUpdate.MAX_CP:
					su.addAttribute(field, getMaxCp());
					break;
				case StatusUpdate.PVP_FLAG:
					su.addAttribute(field, getPvpFlag());
					break;
				case StatusUpdate.P_ATK:
					su.addAttribute(field, (int) getPAtk(null));
					break;
				case StatusUpdate.P_DEF:
					su.addAttribute(field, (int) getPDef(null));
					break;
				case StatusUpdate.ACCURACY:
					su.addAttribute(field, getAccuracy());
					break;
				case StatusUpdate.CRITICAL:
					su.addAttribute(field, getCriticalHit(null, null));
					break;
			}
		}
		return su;
	}
	
	// vGodFather: addon
	private final Lock statusListenersLock = new ReentrantLock();
	
	public void broadcastToStatusListeners(L2GameServerPacket... packets)
	{
		if (!isVisible() || (packets.length == 0))
		{
			return;
		}
		
		statusListenersLock.lock();
		try
		{
			for (int i = 0; i < getStatus().getStatusListener().size(); i++)
			{
				getStatus().getStatusListener().get(i).sendPacket(packets);
			}
		}
		finally
		{
			statusListenersLock.unlock();
		}
	}
	
	public void broadcastStatusUpdate()
	{
		if (getStatus().getStatusListener().isEmpty() || !needHpUpdate())
		{
			return;
		}
		
		// Create the Server->Client packet StatusUpdate with current HP
		StatusUpdate su = makeStatusUpdate(StatusUpdate.MAX_HP, StatusUpdate.MAX_MP, StatusUpdate.CUR_HP, StatusUpdate.CUR_MP);
		broadcastToStatusListeners(su);
	}
	
	/**
	 * @param text
	 */
	public void sendMessage(String text)
	{
		// default implementation
	}
	
	/**
	 * Teleport a L2Character and its pet if necessary.<br>
	 * <B><U>Actions</U>:</B>
	 * <ul>
	 * <li>Stop the movement of the L2Character</li>
	 * <li>Set the x,y,z position of the L2Object and if necessary modify its _worldRegion</li>
	 * <li>Send a Server->Client packet TeleportToLocationt to the L2Character AND to all L2PcInstance in its _KnownPlayers</li>
	 * <li>Modify the position of the pet if necessary</li>
	 * </ul>
	 * @param x
	 * @param y
	 * @param z
	 * @param heading
	 * @param instanceId
	 * @param randomOffset
	 */
	public void teleToLocation(int x, int y, int z, int heading, int instanceId, int randomOffset)
	{
		setInstanceId(instanceId);
		
		if (_isPendingRevive)
		{
			doRevive();
		}
		stopMove(null, false);
		abortAttack();
		abortCast();
		
		setIsTeleporting(true);
		setTarget(null);
		
		getAI().setIntention(CtrlIntention.AI_INTENTION_ACTIVE);
		
		// vGodFather addon
		int originalX = x;
		int originalY = y;
		
		if (Config.OFFSET_ON_TELEPORT_ENABLED && (randomOffset > 0))
		{
			x += Rnd.get(-randomOffset, randomOffset);
			y += Rnd.get(-randomOffset, randomOffset);
		}
		
		z += 5;
		
		// vGodFather addon
		Location fixedLoc = GeoData.getInstance().moveCheck(originalX, originalY, z, x, y, z, instanceId);
		x = fixedLoc.getX();
		y = fixedLoc.getY();
		z = !isFlying() ? fixedLoc.getZ() : z; // correct z only for non flying chars
		
		// Send a Server->Client packet TeleportToLocationt to the L2Character AND to all L2PcInstance in the _KnownPlayers of the L2Character
		broadcastPacket(new TeleportToLocation(this, x, y, z));
		
		// remove the object from its old location
		decayMe();
		
		// Set the x,y,z position of the L2Object and if necessary modify its _worldRegion
		setXYZ(x, y, z);
		
		// allow recall of the detached characters
		if (!isPlayer() || ((getActingPlayer().getClient() != null) && getActingPlayer().getClient().isDetached()))
		{
			onTeleported();
		}
		
		revalidateZone(true);
	}
	
	public void teleToLocation(int x, int y, int z, int heading, int instanceId, boolean randomOffset)
	{
		teleToLocation(x, y, z, heading, instanceId, (randomOffset) ? Config.MAX_OFFSET_ON_TELEPORT : 0);
	}
	
	public void teleToLocation(int x, int y, int z, int heading, int instanceId)
	{
		teleToLocation(x, y, z, heading, instanceId, 0);
	}
	
	public void teleToLocation(int x, int y, int z, int heading, boolean randomOffset)
	{
		teleToLocation(x, y, z, heading, -1, (randomOffset) ? Config.MAX_OFFSET_ON_TELEPORT : 0);
	}
	
	public void teleToLocation(int x, int y, int z, int heading)
	{
		teleToLocation(x, y, z, heading, -1, 0);
	}
	
	public void teleToLocation(int x, int y, int z, boolean randomOffset)
	{
		teleToLocation(x, y, z, 0, -1, (randomOffset) ? Config.MAX_OFFSET_ON_TELEPORT : 0);
	}
	
	public void teleToLocation(int x, int y, int z)
	{
		teleToLocation(x, y, z, 0, -1, 0);
	}
	
	public void teleToLocation(ILocational loc, int randomOffset)
	{
		teleToLocation(loc.getX(), loc.getY(), loc.getZ(), loc.getHeading(), loc.getInstanceId(), randomOffset);
	}
	
	public void teleToLocation(ILocational loc, int instanceId, int randomOffset)
	{
		teleToLocation(loc.getX(), loc.getY(), loc.getZ(), loc.getHeading(), instanceId, randomOffset);
	}
	
	public void teleToLocation(ILocational loc, boolean randomOffset)
	{
		teleToLocation(loc.getX(), loc.getY(), loc.getZ(), loc.getHeading(), loc.getInstanceId(), (randomOffset) ? Config.MAX_OFFSET_ON_TELEPORT : 0);
	}
	
	public void teleToLocation(ILocational loc)
	{
		teleToLocation(loc.getX(), loc.getY(), loc.getZ(), loc.getHeading(), loc.getInstanceId(), 0);
	}
	
	public void teleToLocation(TeleportWhereType teleportWhere)
	{
		teleToLocation(MapRegionManager.getInstance().getTeleToLocation(this, teleportWhere), true);
	}
	
	private boolean canUseRangeWeapon()
	{
		if (isTransformed())
		{
			return true;
		}
		L2Weapon weaponItem = getActiveWeaponItem();
		
		if ((weaponItem == null) || !weaponItem.getItemType().isRanged())
		{
			return false;
		}
		// Check for arrows and MP
		if (isPlayer())
		{
			// Equip arrows needed in left hand and send a Server->Client packet ItemList to the L2PcInstance then return True
			if (!checkAndEquipArrows())
			{
				// Cancel the action because the L2PcInstance have no arrow
				getAI().setIntention(CtrlIntention.AI_INTENTION_IDLE);
				sendPacket(ActionFailed.STATIC_PACKET);
				sendPacket(weaponItem.getItemType().isBow() ? SystemMessageId.NOT_ENOUGH_ARROWS : SystemMessageId.NOT_ENOUGH_BOLTS);
				return false;
			}
			
			// Verify if the bow can be use
			final long timeToNextBowCrossBowAttack = _disableBowAttackEndTime - System.currentTimeMillis();
			if (timeToNextBowCrossBowAttack > 0)
			{
				// Cancel the action because the bow can't be re-use at this moment
				ThreadPoolManager.getInstance().scheduleAi(new NotifyAITask(this, CtrlEvent.EVT_READY_TO_ACT), timeToNextBowCrossBowAttack);
				sendPacket(ActionFailed.STATIC_PACKET);
				return false;
			}
			
			// Verify if L2PcInstance owns enough MP
			int mpConsume = weaponItem.getMpConsume();
			if ((weaponItem.getReducedMpConsume() > 0) && (Rnd.get(100) < weaponItem.getReducedMpConsumeChance()))
			{
				mpConsume = weaponItem.getReducedMpConsume();
			}
			mpConsume = (int) calcStat(Stats.BOW_MP_CONSUME_RATE, mpConsume, null, null);
			
			if (getCurrentMp() < mpConsume)
			{
				// If L2PcInstance doesn't have enough MP, stop the attack
				ThreadPoolManager.getInstance().scheduleAi(new NotifyAITask(this, CtrlEvent.EVT_READY_TO_ACT), 100);
				sendPacket(SystemMessageId.NOT_ENOUGH_MP);
				sendPacket(ActionFailed.STATIC_PACKET);
				return false;
			}
			
			// If L2PcInstance have enough MP, the bow consumes it
			if (mpConsume > 0)
			{
				getStatus().reduceMp(mpConsume);
			}
		}
		else if (isNpc())
		{
			if (_disableBowAttackEndTime > System.currentTimeMillis())
			{
				return false;
			}
		}
		return true;
	}
	
	/**
	 * Launch a physical attack against a target (Simple, Bow, Pole or Dual).<br>
	 * <B><U>Actions</U>:</B>
	 * <ul>
	 * <li>Get the active weapon (always equipped in the right hand)</li>
	 * <li>If weapon is a bow, check for arrows, MP and bow re-use delay (if necessary, equip the L2PcInstance with arrows in left hand)</li>
	 * <li>If weapon is a bow, consume MP and set the new period of bow non re-use</li>
	 * <li>Get the Attack Speed of the L2Character (delay (in milliseconds) before next attack)</li>
	 * <li>Select the type of attack to start (Simple, Bow, Pole or Dual) and verify if SoulShot are charged then start calculation</li>
	 * <li>If the Server->Client packet Attack contains at least 1 hit, send the Server->Client packet Attack to the L2Character AND to all L2PcInstance in the _KnownPlayers of the L2Character</li>
	 * <li>Notify AI with EVT_READY_TO_ACT</li>
	 * </ul>
	 * @param target The L2Character targeted
	 */
	public void doAttack(L2Character target)
	{
		if ((target == null) || isAttackingDisabled())
		{
			return;
		}
		
		// Notify to scripts
		final TerminateReturn attackReturn = EventDispatcher.getInstance().notifyEvent(new OnCreatureAttack(this, target), this, TerminateReturn.class);
		if ((attackReturn != null) && attackReturn.terminate())
		{
			getAI().setIntention(CtrlIntention.AI_INTENTION_ACTIVE);
			sendPacket(ActionFailed.STATIC_PACKET);
			return;
		}
		
		// Notify to scripts
		final TerminateReturn attackedReturn = EventDispatcher.getInstance().notifyEvent(new OnCreatureAttacked(this, target), target, TerminateReturn.class);
		if ((attackedReturn != null) && attackedReturn.terminate())
		{
			getAI().setIntention(CtrlIntention.AI_INTENTION_ACTIVE);
			sendPacket(ActionFailed.STATIC_PACKET);
			return;
		}
		
		if (getActingPlayer() != null)
		{
			L2PcInstance owner = getActingPlayer();
			
			if (SunriseEvents.isInEvent(owner))
			{
				if (!SunriseEvents.canAttack(owner, target))
				{
					sendPacket(ActionFailed.STATIC_PACKET);
					return;
				}
			}
		}
		
		if (!isAlikeDead())
		{
			if ((isNpc() && target.isAlikeDead()) || !getKnownList().knowsObject(target))
			{
				getAI().setIntention(CtrlIntention.AI_INTENTION_ACTIVE);
				sendPacket(ActionFailed.STATIC_PACKET);
				return;
			}
			else if (isPlayer())
			{
				if (target.isDead())
				{
					getAI().setIntention(CtrlIntention.AI_INTENTION_ACTIVE);
					sendPacket(ActionFailed.STATIC_PACKET);
					return;
				}
				
				final L2PcInstance actor = getActingPlayer();
				if (actor.isTransformed() && !actor.getTransformation().canAttack())
				{
					sendPacket(ActionFailed.STATIC_PACKET);
					return;
				}
			}
		}
		
		// Check if attacker's weapon can attack
		if (getActiveWeaponItem() != null)
		{
			L2Weapon wpn = getActiveWeaponItem();
			if ((wpn != null) && !wpn.isAttackWeapon() && !isGM())
			{
				if (wpn.getItemType() == WeaponType.FISHINGROD)
				{
					sendPacket(SystemMessageId.CANNOT_ATTACK_WITH_FISHING_POLE);
				}
				else
				{
					sendPacket(SystemMessageId.THAT_WEAPON_CANT_ATTACK);
				}
				sendPacket(ActionFailed.STATIC_PACKET);
				return;
			}
		}
		
		if (getActingPlayer() != null)
		{
			if (getActingPlayer().inObserverMode())
			{
				sendPacket(SystemMessageId.OBSERVERS_CANNOT_PARTICIPATE);
				sendPacket(ActionFailed.STATIC_PACKET);
				return;
			}
			
			if (!canAttackSiegier(getActingPlayer(), target))
			{
				sendPacket(ActionFailed.STATIC_PACKET);
				return;
			}
			
			// Checking if target has moved to peace zone
			else if (target.isInsidePeaceZone(getActingPlayer()))
			{
				getAI().setIntention(CtrlIntention.AI_INTENTION_ACTIVE);
				sendPacket(ActionFailed.STATIC_PACKET);
				return;
			}
		}
		else if (isInsidePeaceZone(this, target))
		{
			getAI().setIntention(CtrlIntention.AI_INTENTION_ACTIVE);
			sendPacket(ActionFailed.STATIC_PACKET);
			return;
		}
		
		stopEffectsOnAction();
		
		if (!GeoData.getInstance().canSeeTarget(this, target))
		{
			sendPacket(SystemMessageId.CANT_SEE_TARGET);
			getAI().setIntention(CtrlIntention.AI_INTENTION_ACTIVE);
			sendPacket(ActionFailed.STATIC_PACKET);
			return;
		}
		
		if (SunriseEvents.isInEvent(this) && SunriseEvents.isInEvent(target))
		{
			if (!SunriseEvents.onAttack(this, target))
			{
				getAI().setIntention(CtrlIntention.AI_INTENTION_IDLE);
				sendPacket(ActionFailed.STATIC_PACKET);
				return;
			}
		}
		
		if (target.isPorting())
		{
			getAI().setIntention(CtrlIntention.AI_INTENTION_IDLE);
			sendPacket(ActionFailed.STATIC_PACKET);
			return;
		}
		
		// vGodFather: this will fix broken attack animations from mobs
		if (isAttackable())
		{
			stopMove(getLocation());
		}
		
		// Add the L2PcInstance to _knownObjects and _knownPlayer of the target
		target.getKnownList().addKnownObject(this);
		
		L2Weapon weaponItem = getActiveWeaponItem();
		final int timeAtk = calculateTimeBetweenAttacks();
		final int timeToHit = timeAtk / 2;
		
		Attack attack = new Attack(this, target, isChargedShot(ShotType.SOULSHOTS), (weaponItem != null) ? weaponItem.getItemGradeSPlus().getId() : 0);
		setHeading(Util.calculateHeadingFrom(this, target));
		int reuse = calculateReuseTime(weaponItem);
		
		// vGodFather: Just in case npc attack end time is not set
		if (isNpc() || isSummon())
		{
			_attackEndTime = System.nanoTime() + TimeUnit.NANOSECONDS.convert(timeAtk, TimeUnit.MILLISECONDS);
		}
		
		boolean hitted = false;
		switch (getAttackType())
		{
			case BOW:
			{
				if (!canUseRangeWeapon())
				{
					return;
				}
				_attackEndTime = System.nanoTime() + TimeUnit.NANOSECONDS.convert(timeAtk, TimeUnit.MILLISECONDS);
				hitted = doAttackHitByBow(attack, target, timeAtk, reuse);
				break;
			}
			case CROSSBOW:
			{
				if (!canUseRangeWeapon())
				{
					return;
				}
				_attackEndTime = System.nanoTime() + TimeUnit.NANOSECONDS.convert(timeAtk, TimeUnit.MILLISECONDS);
				hitted = doAttackHitByCrossBow(attack, target, timeAtk, reuse);
				break;
			}
			case POLE:
			{
				_attackEndTime = System.nanoTime() + TimeUnit.NANOSECONDS.convert(timeAtk, TimeUnit.MILLISECONDS);
				hitted = doAttackHitByPole(attack, target, timeToHit);
				break;
			}
			case FIST:
			{
				if (!isPlayer())
				{
					_attackEndTime = System.nanoTime() + TimeUnit.NANOSECONDS.convert(timeAtk, TimeUnit.MILLISECONDS);
					hitted = doAttackHitSimple(attack, target, timeToHit);
					break;
				}
			}
			case DUAL:
			case DUALFIST:
			case DUALDAGGER:
			{
				_attackEndTime = System.nanoTime() + TimeUnit.NANOSECONDS.convert(timeAtk, TimeUnit.MILLISECONDS);
				hitted = doAttackHitByDual(attack, target, timeToHit);
				break;
			}
			default:
			{
				_attackEndTime = System.nanoTime() + TimeUnit.NANOSECONDS.convert(timeAtk, TimeUnit.MILLISECONDS);
				hitted = doAttackHitSimple(attack, target, timeToHit);
				break;
			}
		}
		
		// Flag the attacker if it's a L2PcInstance outside a PvP area
		final L2PcInstance player = getActingPlayer();
		if (player != null)
		{
			AttackStanceTaskManager.getInstance().addAttackStanceTask(player);
			if (player.getSummon() != target)
			{
				player.updatePvPStatus(target);
			}
		}
		// Check if hit isn't missed
		if (!hitted)
		{
			abortAttack(); // Abort the attack of the L2Character and send Server->Client ActionFailed packet
		}
		else
		{
			if (isPlayer() && (target instanceof L2RaidBossInstance) && ((L2RaidBossInstance) target).isEventRaid())
			{
				RaidManager.getInstance().checkRaidAttack(getActingPlayer(), (L2RaidBossInstance) target);
			}
			
			// If we didn't miss the hit, discharge the shoulshots, if any
			setChargedShot(ShotType.SOULSHOTS, false);
			
			if (player != null)
			{
				if (player.isCursedWeaponEquipped())
				{
					// If hit by a cursed weapon, CP is reduced to 0
					if (!target.isInvul())
					{
						target.setCurrentCp(0);
					}
				}
				else if (player.isHero())
				{
					// If a cursed weapon is hit by a Hero, CP is reduced to 0
					if (target.isPlayer() && target.getActingPlayer().isCursedWeaponEquipped())
					{
						target.setCurrentCp(0);
					}
				}
			}
		}
		
		// If the Server->Client packet Attack contains at least 1 hit, send the Server->Client packet Attack
		// to the L2Character AND to all L2PcInstance in the _KnownPlayers of the L2Character
		if (attack.hasHits())
		{
			broadcastPacket(attack);
		}
		
		// Notify AI with EVT_READY_TO_ACT
		ThreadPoolManager.getInstance().scheduleAi(new NotifyAITask(this, CtrlEvent.EVT_READY_TO_ACT), timeAtk);
	}
	
	/**
	 * Launch a Bow attack.<br>
	 * <B><U>Actions</U>:</B>
	 * <ul>
	 * <li>Calculate if hit is missed or not</li>
	 * <li>Consume arrows</li>
	 * <li>If hit isn't missed, calculate if shield defense is efficient</li>
	 * <li>If hit isn't missed, calculate if hit is critical</li>
	 * <li>If hit isn't missed, calculate physical damages</li>
	 * <li>If the L2Character is a L2PcInstance, Send a Server->Client packet SetupGauge</li>
	 * <li>Create a new hit task with Medium priority</li>
	 * <li>Calculate and set the disable delay of the bow in function of the Attack Speed</li>
	 * <li>Add this hit to the Server-Client packet Attack</li>
	 * </ul>
	 * @param attack Server->Client packet Attack in which the hit will be added
	 * @param target The L2Character targeted
	 * @param sAtk The Attack Speed of the attacker
	 * @param reuse
	 * @return True if the hit isn't missed
	 */
	private boolean doAttackHitByBow(Attack attack, L2Character target, int sAtk, int reuse)
	{
		int damage1 = 0;
		byte shld1 = 0;
		boolean crit1 = false;
		
		// Calculate if hit is missed or not
		boolean miss1 = Formulas.calcHitMiss(this, target);
		
		// Consume arrows
		reduceArrowCount(false);
		
		_move = null;
		
		// Check if hit isn't missed
		if (!miss1)
		{
			// Calculate if shield defense is efficient
			shld1 = Formulas.calcShldUse(this, target);
			
			// Calculate if hit is critical
			crit1 = Formulas.calcCrit(this, target);
			
			// Calculate physical damages
			damage1 = (int) Formulas.calcPhysDam(this, target, null, shld1, crit1, attack.hasSoulshot());
			
			// Bows Ranged Damage Formula (Damage gradually decreases when 60% or lower than full hit range, and increases when 60% or higher).
			// full hit range is 500 which is the base bow range, and the 60% of this is 800.
			damage1 *= (calculateDistance(target, true, false) / 4000) + 0.8;
		}
		
		// Check if the L2Character is a L2PcInstance
		if (isPlayer())
		{
			sendPacket(new SetupGauge(SetupGauge.RED, sAtk + reuse));
		}
		
		// Create a new hit task with Medium priority
		ThreadPoolManager.getInstance().scheduleAi(new HitTask(this, target, damage1, crit1, miss1, attack.hasSoulshot(), shld1), sAtk);
		
		// Calculate and set the disable delay of the bow in function of the Attack Speed
		_disableBowAttackEndTime = System.currentTimeMillis() + (sAtk + reuse);
		
		// Add this hit to the Server-Client packet Attack
		attack.addHit(target, damage1, miss1, crit1, shld1);
		
		// Return true if hit isn't missed
		return !miss1;
	}
	
	/**
	 * Launch a CrossBow attack.<br>
	 * <B><U>Actions</U>:</B>
	 * <ul>
	 * <li>Calculate if hit is missed or not</li>
	 * <li>Consume bolts</li>
	 * <li>If hit isn't missed, calculate if shield defense is efficient</li>
	 * <li>If hit isn't missed, calculate if hit is critical</li>
	 * <li>If hit isn't missed, calculate physical damages</li>
	 * <li>If the L2Character is a L2PcInstance, Send a Server->Client packet SetupGauge</li>
	 * <li>Create a new hit task with Medium priority</li>
	 * <li>Calculate and set the disable delay of the crossbow in function of the Attack Speed</li>
	 * <li>Add this hit to the Server-Client packet Attack</li>
	 * @param attack Server->Client packet Attack in which the hit will be added
	 * @param target The L2Character targeted
	 * @param sAtk The Attack Speed of the attacker
	 * @param reuse
	 * @return True if the hit isn't missed
	 */
	private boolean doAttackHitByCrossBow(Attack attack, L2Character target, int sAtk, int reuse)
	{
		int damage1 = 0;
		byte shld1 = 0;
		boolean crit1 = false;
		
		// Calculate if hit is missed or not
		boolean miss1 = Formulas.calcHitMiss(this, target);
		
		// Consume bolts
		reduceArrowCount(true);
		
		_move = null;
		
		// Check if hit isn't missed
		if (!miss1)
		{
			// Calculate if shield defense is efficient
			shld1 = Formulas.calcShldUse(this, target);
			
			// Calculate if hit is critical
			crit1 = Formulas.calcCrit(this, target);
			
			// Calculate physical damages
			damage1 = (int) Formulas.calcPhysDam(this, target, null, shld1, crit1, attack.hasSoulshot());
		}
		
		// Check if the L2Character is a L2PcInstance
		if (isPlayer())
		{
			// Send a system message
			sendPacket(SystemMessageId.CROSSBOW_PREPARING_TO_FIRE);
			
			// Send a Server->Client packet SetupGauge
			SetupGauge sg = new SetupGauge(SetupGauge.RED, sAtk + reuse);
			sendPacket(sg);
		}
		
		// Create a new hit task with Medium priority
		ThreadPoolManager.getInstance().scheduleAi(new HitTask(this, target, damage1, crit1, miss1, attack.hasSoulshot(), shld1), sAtk);
		
		// Calculate and set the disable delay of the bow in function of the Attack Speed
		_disableBowAttackEndTime = System.currentTimeMillis() + (sAtk + reuse);
		
		// Add this hit to the Server-Client packet Attack
		attack.addHit(target, damage1, miss1, crit1, shld1);
		
		// Return true if hit isn't missed
		return !miss1;
	}
	
	/**
	 * Launch a Dual attack.<br>
	 * <B><U>Actions</U>:</B>
	 * <ul>
	 * <li>Calculate if hits are missed or not</li>
	 * <li>If hits aren't missed, calculate if shield defense is efficient</li>
	 * <li>If hits aren't missed, calculate if hit is critical</li>
	 * <li>If hits aren't missed, calculate physical damages</li>
	 * <li>Create 2 new hit tasks with Medium priority</li>
	 * <li>Add those hits to the Server-Client packet Attack</li>
	 * </ul>
	 * @param attack Server->Client packet Attack in which the hit will be added
	 * @param target The L2Character targeted
	 * @param sAtk
	 * @return True if hit 1 or hit 2 isn't missed
	 */
	private boolean doAttackHitByDual(Attack attack, L2Character target, int sAtk)
	{
		int damage1 = 0;
		int damage2 = 0;
		byte shld1 = 0;
		byte shld2 = 0;
		boolean crit1 = false;
		boolean crit2 = false;
		
		// Calculate if hits are missed or not
		boolean miss1 = Formulas.calcHitMiss(this, target);
		boolean miss2 = Formulas.calcHitMiss(this, target);
		
		// Check if hit 1 isn't missed
		if (!miss1)
		{
			// Calculate if shield defense is efficient against hit 1
			shld1 = Formulas.calcShldUse(this, target);
			
			// Calculate if hit 1 is critical
			crit1 = Formulas.calcCrit(this, target);
			
			// Calculate physical damages of hit 1
			damage1 = (int) Formulas.calcPhysDam(this, target, null, shld1, crit1, attack.hasSoulshot());
			damage1 /= 2;
		}
		
		// Check if hit 2 isn't missed
		if (!miss2)
		{
			// Calculate if shield defense is efficient against hit 2
			shld2 = Formulas.calcShldUse(this, target);
			
			// Calculate if hit 2 is critical
			crit2 = Formulas.calcCrit(this, target);
			
			// Calculate physical damages of hit 2
			damage2 = (int) Formulas.calcPhysDam(this, target, null, shld2, crit2, attack.hasSoulshot());
			damage2 /= 2;
		}
		
		// Create a new hit task with Medium priority for hit 1
		ThreadPoolManager.getInstance().scheduleAi(new HitTask(this, target, damage1, crit1, miss1, attack.hasSoulshot(), shld1), sAtk / 2);
		
		// Create a new hit task with Medium priority for hit 2 with a higher delay
		ThreadPoolManager.getInstance().scheduleAi(new HitTask(this, target, damage2, crit2, miss2, attack.hasSoulshot(), shld2), sAtk);
		
		// Add those hits to the Server-Client packet Attack
		attack.addHit(target, damage1, miss1, crit1, shld1);
		attack.addHit(target, damage2, miss2, crit2, shld2);
		
		// Return true if hit 1 or hit 2 isn't missed
		return (!miss1 || !miss2);
	}
	
	/**
	 * Launch a Pole attack.<br>
	 * <B><U>Actions</U>:</B>
	 * <ul>
	 * <li>Get all visible objects in a spherical area near the L2Character to obtain possible targets</li>
	 * <li>If possible target is the L2Character targeted, launch a simple attack against it</li>
	 * <li>If possible target isn't the L2Character targeted but is attackable, launch a simple attack against it</li>
	 * </ul>
	 * @param attack Server->Client packet Attack in which the hit will be added
	 * @param target
	 * @param sAtk
	 * @return True if one hit isn't missed
	 */
	private boolean doAttackHitByPole(Attack attack, L2Character target, int sAtk)
	{
		boolean hitted = doAttackHitSimple(attack, target, 100, sAtk);
		
		if (isAffected(EffectFlag.SINGLE_TARGET))
		{
			return hitted;
		}
		
		// H5 Changes: without Polearm Mastery (skill 216) max simultaneous attacks is 3 (1 by default + 2 in skill 3599).
		int attackRandomCountMax = (int) getStat().calcStat(Stats.ATTACK_COUNT_MAX, 1, null, null);
		int attackcount = 0;
		
		// if (angleChar <= 0) angleChar += 360;
		double attackpercent = 85;
		
		int[] _fanRange = null;
		if ((getActiveWeaponItem() == null) || (getActiveWeaponItem().getDamageRange() == null))
		{
			_fanRange = new int[4];
			_fanRange[0] = 0;
			_fanRange[1] = isMonster() ? ((L2NpcTemplate) getTemplate()).getDistance() : 80; // direction
			_fanRange[2] = isMonster() ? ((L2NpcTemplate) getTemplate()).getRange() : getStat().getPhysicalAttackRange(); // length
			_fanRange[3] = isMonster() ? ((L2NpcTemplate) getTemplate()).getWidth() : 120; // angle or width
		}
		else
		{
			_fanRange = getActiveWeaponItem().getDamageRange();
		}
		
		if (_fanRange != null)
		{
			final Collection<L2Character> objs = getKnownList().getKnownCharactersInRadius(_fanRange[2]);
			for (L2Character obj : objs)
			{
				if (attackcount >= attackRandomCountMax)
				{
					break;
				}
				
				if ((obj == null) || (obj == target))
				{
					continue; // do not hit twice
				}
				
				if (obj.isPet() && isPlayer() && (((L2PetInstance) obj).getOwner() == getActingPlayer()))
				{
					continue;
				}
				
				// otherwise hit too high/low. 650 because mob z coord
				// sometimes wrong on hills
				if (Math.abs(obj.getZ() - getZ()) > 650)
				{
					continue;
				}
				
				if (!Util.isOnAngle(this, obj, _fanRange[1], _fanRange[3]))
				{
					continue;
				}
				
				if (isAttackable() && obj.isPlayer() && (getTarget() != null) && getTarget().isAttackable())
				{
					continue;
				}
				
				if (isAttackable() && obj.isAttackable() && (((L2Attackable) this).getEnemyClan() == null) && (((L2Attackable) this).isChaos() == 0))
				{
					continue;
				}
				
				if (isAttackable() && obj.isAttackable() && !((L2Attackable) this).getEnemyClan().equals(((L2Attackable) obj).getClan()) && (((L2Attackable) this).isChaos() == 0))
				{
					continue;
				}
				
				// Launch a simple attack against the L2Character targeted
				if (!obj.isAlikeDead())
				{
					if ((obj == getAI().getAttackTarget()) || obj.isAutoAttackable(this))
					{
						hitted |= doAttackHitSimple(attack, obj, attackpercent, sAtk);
						attackpercent /= 1.15;
						
						attackcount++;
					}
				}
			}
		}
		
		// Return true if one hit isn't missed
		return hitted;
	}
	
	/**
	 * Launch a simple attack.<br>
	 * <B><U>Actions</U>:</B>
	 * <ul>
	 * <li>Calculate if hit is missed or not</li>
	 * <li>If hit isn't missed, calculate if shield defense is efficient</li>
	 * <li>If hit isn't missed, calculate if hit is critical</li>
	 * <li>If hit isn't missed, calculate physical damages</li>
	 * <li>Create a new hit task with Medium priority</li>
	 * <li>Add this hit to the Server-Client packet Attack</li>
	 * </ul>
	 * @param attack Server->Client packet Attack in which the hit will be added
	 * @param target The L2Character targeted
	 * @param sAtk
	 * @return True if the hit isn't missed
	 */
	private boolean doAttackHitSimple(Attack attack, L2Character target, int sAtk)
	{
		return doAttackHitSimple(attack, target, 100, sAtk);
	}
	
	private boolean doAttackHitSimple(Attack attack, L2Character target, double attackpercent, int sAtk)
	{
		int damage1 = 0;
		byte shld1 = 0;
		boolean crit1 = false;
		
		// Calculate if hit is missed or not
		boolean miss1 = Formulas.calcHitMiss(this, target);
		
		// Check if hit isn't missed
		if (!miss1)
		{
			// Calculate if shield defense is efficient
			shld1 = Formulas.calcShldUse(this, target);
			
			// Calculate if hit is critical
			crit1 = Formulas.calcCrit(this, target);
			
			// Calculate physical damages
			damage1 = (int) Formulas.calcPhysDam(this, target, null, shld1, crit1, attack.hasSoulshot());
			
			if (attackpercent != 100)
			{
				damage1 = (int) ((damage1 * attackpercent) / 100);
			}
		}
		
		// Create a new hit task with Medium priority
		ThreadPoolManager.getInstance().scheduleAi(new HitTask(this, target, damage1, crit1, miss1, attack.hasSoulshot(), shld1), sAtk);
		
		// Add this hit to the Server-Client packet Attack
		attack.addHit(target, damage1, miss1, crit1, shld1);
		
		// Return true if hit isn't missed
		return !miss1;
	}
	
	/**
	 * Manage the casting task (casting and interrupt time, re-use delay...) and display the casting bar and animation on client.<br>
	 * <B><U>Actions</U>:</B>
	 * <ul>
	 * <li>Verify the possibility of the the cast : skill is a spell, caster isn't muted...</li>
	 * <li>Get the list of all targets (ex : area effects) and define the L2Charcater targeted (its stats will be used in calculation)</li>
	 * <li>Calculate the casting time (base + modifier of MAtkSpd), interrupt time and re-use delay</li>
	 * <li>Send a Server->Client packet MagicSkillUser (to display casting animation), a packet SetupGauge (to display casting bar) and a system message</li>
	 * <li>Disable all skills during the casting time (create a task EnableAllSkills)</li>
	 * <li>Disable the skill during the re-use delay (create a task EnableSkill)</li>
	 * <li>Create a task MagicUseTask (that will call method onMagicUseTimer) to launch the Magic Skill at the end of the casting time</li>
	 * </ul>
	 * @param skill The L2Skill to use
	 */
	public void doCast(L2Skill skill)
	{
		beginCast(skill, false);
	}
	
	/**
	 * SkillHolder version of {@link #doCast(L2Skill)} method.
	 * @param skill The L2Skill to use
	 */
	public void doCast(SkillHolder skill)
	{
		beginCast(skill.getSkill(), false);
	}
	
	public void doSimultaneousCast(L2Skill skill)
	{
		beginCast(skill, true);
	}
	
	/**
	 * SkillHolder version of {@link #doSimultaneousCast(L2Skill)} method.
	 * @param skill The L2Skill to use
	 */
	public void doSimultaneousCast(SkillHolder skill)
	{
		beginCast(skill.getSkill(), true);
	}
	
	public void doCast(L2Skill skill, L2Character target, L2Object[] targets)
	{
		if (!checkDoCastConditions(skill))
		{
			setIsCastingNow(false);
			return;
		}
		
		// Override casting type
		if (skill.isSimultaneousCast())
		{
			doSimultaneousCast(skill, target, targets);
			return;
		}
		
		stopEffectsOnAction();
		
		beginCast(skill, false, target, targets);
	}
	
	public void doSimultaneousCast(L2Skill skill, L2Character target, L2Object[] targets)
	{
		if (!checkDoCastConditions(skill))
		{
			setIsCastingSimultaneouslyNow(false);
			return;
		}
		stopEffectsOnAction();
		
		// Recharge AutoSoulShot
		// this method should not used with L2Playable
		
		beginCast(skill, true, target, targets);
	}
	
	private void beginCast(L2Skill skill, boolean simultaneously)
	{
		boolean abort = false;
		
		if (getActingPlayer() != null)
		{
			L2PcInstance owner = getActingPlayer();
			
			if (SunriseEvents.isInEvent(owner) && (getTarget() instanceof L2Character) && (skill.getTargetType() != L2TargetType.SELF))
			{
				if (!SunriseEvents.isSkillNeutral(owner, skill))
				{
					if (owner.hasSummon() && (getTarget() == owner.getSummon()))
					{
						abort = false;
					}
					else if (SunriseEvents.isSkillOffensive(owner, skill) && !SunriseEvents.canAttack(owner, (L2Character) getTarget()) && (owner != getTarget()))
					{
						abort = true;
					}
					else if (!SunriseEvents.isSkillOffensive(owner, skill) && !SunriseEvents.canSupport(owner, (L2Character) getTarget()))
					{
						abort = true;
					}
				}
			}
		}
		
		if (!checkDoCastConditions(skill) || abort)
		{
			if (simultaneously)
			{
				setIsCastingSimultaneouslyNow(false);
			}
			else
			{
				setIsCastingNow(false);
			}
			return;
		}
		
		// Override casting type
		if (skill.isSimultaneousCast() && !simultaneously)
		{
			simultaneously = true;
		}
		
		// vGodFather: herb effects should not remove skills on action example: removedOnAnyActionExceptMove
		if (!skill.isHerb())
		{
			stopEffectsOnAction();
		}
		
		// Set the target of the skill in function of Skill Type and Target Type
		final L2Character target;
		// Get all possible targets of the skill in a table in function of the skill target type
		L2Object[] targets = skill.getTargetList(this);
		
		boolean doit = false;
		
		// AURA skills should always be using caster as target
		switch (skill.getTargetType())
		{
			case AREA_SUMMON: // We need it to correct facing
				target = getSummon();
				break;
			case AURA:
			case AURA_CORPSE_MOB:
			case FRONT_AURA:
			case BEHIND_AURA:
			case GROUND:
			case AURA_FRIENDLY:
			case AURA_UNDEAD_ENEMY:
				target = this;
				break;
			case SELF:
			case PET:
			case SERVITOR:
			case SUMMON:
			case OWNER_PET:
			case PARTY:
			case PARTY_NOTME:
			case CLAN:
			case PARTY_CLAN:
			case PARTY_TARGET:
			case COMMAND_CHANNEL:
				doit = true;
			default:
				if (targets.length == 0)
				{
					if (simultaneously)
					{
						setIsCastingSimultaneouslyNow(false);
					}
					else
					{
						setIsCastingNow(false);
					}
					// Send a Server->Client packet ActionFailed to the L2PcInstance
					if (isPlayer())
					{
						sendPacket(ActionFailed.STATIC_PACKET);
						getAI().setIntention(AI_INTENTION_ACTIVE);
					}
					// vGodFather setting back previous intention
					else if (isSummon())
					{
						getAI().notifyEvent(CtrlEvent.EVT_FINISH_CASTING);
					}
					return;
				}
				
				switch (skill.getSkillType())
				{
					case BUFF:
						doit = true;
						break;
					case DUMMY:
						if (skill.hasEffectType(L2EffectType.CPHEAL, L2EffectType.HEAL))
						{
							doit = true;
						}
						break;
				}
				
				target = (doit) ? (L2Character) targets[0] : (L2Character) getTarget();
		}
		
		if (isPlayer() && (getActingPlayer().getCurrentSkill() != null))
		{
			if (!getActingPlayer().checkUseMagicConditions(skill, getActingPlayer().getCurrentSkill().isCtrlPressed(), getActingPlayer().getCurrentSkill().isShiftPressed()))
			{
				setIsCastingNow(false);
				ThreadPoolManager.getInstance().executeAi(() -> checkNextActionAttack(getActingPlayer().getCurrentSkill(), target));
				return;
			}
		}
		
		beginCast(skill, simultaneously, target, targets);
	}
	
	private void checkNextActionAttack(SkillUseHolder skillUseHolder, L2Object target)
	{
		if ((target == null) || !target.isCharacter() || (skillUseHolder == null))
		{
			return;
		}
		
		// Attack target after skill use only if control is not pressed
		final boolean isCtrlPressed = skillUseHolder.isCtrlPressed();
		final boolean hasQueuedSkill = getActingPlayer().getQueuedSkill() != null;
		if (!isCtrlPressed && !hasQueuedSkill)
		{
			if ((skillUseHolder.getSkill().nextActionIsAttack() && (target != this) && target.isAutoAttackable(this)))
			{
				if ((getAI().getNextIntention() == null) || (getAI().getNextIntention().getCtrlIntention() != CtrlIntention.AI_INTENTION_MOVE_TO))
				{
					getAI().setIntention(CtrlIntention.AI_INTENTION_ATTACK, target);
				}
			}
		}
	}
	
	private void beginCast(L2Skill skill, boolean simultaneously, L2Character target, L2Object[] targets)
	{
		if (target == null)
		{
			if (simultaneously)
			{
				setIsCastingSimultaneouslyNow(false);
			}
			else
			{
				setIsCastingNow(false);
			}
			if (isPlayer())
			{
				sendPacket(ActionFailed.STATIC_PACKET);
				getAI().setIntention(AI_INTENTION_ACTIVE);
			}
			return;
		}
		
		// vGodFather: shadow mana consumption must be checked before skill cast start
		if (isPlayable())
		{
			// reduce talisman mana on skill use
			if ((skill.getReferenceItemId() > 0) && (ItemData.getInstance().getTemplate(skill.getReferenceItemId()).getBodyPart() == L2Item.SLOT_DECO))
			{
				for (L2ItemInstance item : getInventory().getItemsByItemId(skill.getReferenceItemId()))
				{
					if (item.isEquipped())
					{
						if (item.getMana() < item.useSkillDisTime())
						{
							sendPacket(SystemMessageId.THERE_ARE_NOT_ENOUGH_NECESSARY_ITEMS_TO_USE_THE_SKILL);
							abortCast();
							return;
						}
						
						item.decreaseMana(false, item.useSkillDisTime());
						break;
					}
				}
			}
		}
		
		final TerminateReturn term = EventDispatcher.getInstance().notifyEvent(new OnCreatureSkillUse(this, skill, simultaneously, target, targets), this, TerminateReturn.class);
		if ((term != null) && term.terminate())
		{
			if (simultaneously)
			{
				setIsCastingSimultaneouslyNow(false);
			}
			else
			{
				setIsCastingNow(false);
			}
			if (isPlayer())
			{
				sendPacket(ActionFailed.STATIC_PACKET);
				getAI().setIntention(AI_INTENTION_ACTIVE);
			}
			return;
		}
		
		if (skill.hasEffectType(L2EffectType.RESURRECTION))
		{
			if (isResurrectionBlocked() || target.isResurrectionBlocked())
			{
				sendPacket(SystemMessageId.REJECT_RESURRECTION); // Reject resurrection
				target.sendPacket(SystemMessageId.REJECT_RESURRECTION); // Reject resurrection
				
				if (simultaneously)
				{
					setIsCastingSimultaneouslyNow(false);
				}
				else
				{
					setIsCastingNow(false);
				}
				
				if (isPlayer())
				{
					getAI().setIntention(AI_INTENTION_ACTIVE);
					sendPacket(ActionFailed.STATIC_PACKET);
				}
				return;
			}
		}
		
		if ((skill.getHitTime() > 100) && !skill.isSimultaneousCast())
		{
			getAI().clientStopMoving(null);
		}
		
		// Get the Identifier of the skill
		int magicId = skill.getId();
		
		// Get the casting time of the skill (base)
		int hitTime = skill.getHitTime();
		int coolTime = skill.getCoolTime();
		
		boolean effectWhileCasting = (skill.getSkillType() == L2SkillType.FUSION) || (skill.getSkillType() == L2SkillType.SIGNET_CASTTIME);
		// Don't modify the skill time for FORCE_BUFF skills. The skill time for those skills represent the buff time.
		if (!effectWhileCasting)
		{
			hitTime = Formulas.calcAtkSpd(this, skill, hitTime);
			if (coolTime > 0)
			{
				coolTime = Formulas.calcAtkSpd(this, skill, coolTime);
			}
		}
		
		// Calculate altered Cast Speed due to BSpS/SpS
		if (skill.isMagic() && !effectWhileCasting)
		{
			// Only takes 70% of the time to cast a BSpS/SpS cast
			if (isChargedShot(ShotType.SPIRITSHOTS) || isChargedShot(ShotType.BLESSED_SPIRITSHOTS))
			{
				hitTime = (int) (0.70 * hitTime);
				coolTime = (int) (0.70 * coolTime);
			}
		}
		
		// Don't modify the skill time for FORCE_BUFF skills. The skill time for those skills represent the buff time.
		if (effectWhileCasting || skill.isStatic())
		{
			hitTime = skill.getHitTime();
			coolTime = skill.getCoolTime();
		}
		// Client can't handle less than 550ms Casting Animation in Magic Skills with more than 550ms base.
		// else if (skill.isMagic() && ((skill.getHitTime() + skill.getCoolTime()) > 550) && (hitTime < 550))
		// {
		// hitTime = 550;
		// }
		// if basic hitTime is higher than 500 than the min hitTime is 500
		else if ((skill.getHitTime() >= 500) && (hitTime < 500))
		{
			hitTime = 500;
		}
		
		if ((!skill.isStatic() && !effectWhileCasting && !skill.isSimultaneousCast() && !skill.isHealingPotionSkill()) && (skill.getHitTime() < 500) && (skill.getHitTime() > 0) && (hitTime < 500))
		{
			hitTime = 500;
		}
		
		hitTime = (skill.getFlyType() != null) && (skill.getFlyType() == FlyType.DUMMY) ? skill.getHitTime() : hitTime;
		
		// queue herbs and potions
		if (isCastingSimultaneouslyNow() && simultaneously)
		{
			ThreadPoolManager.getInstance().scheduleAi(() -> beginCast(skill, simultaneously, target, targets), 100);
			return;
		}
		
		if (simultaneously)
		{
			setIsCastingSimultaneouslyNow(true);
			setLastSimultaneousSkillCast(skill);
		}
		else
		{
			setIsCastingNow(true);
			_castInterruptTime = -2 + GameTimeController.getInstance().getGameTicks() + (hitTime / GameTimeController.MILLIS_IN_TICK);
			setLastSkillCast(skill);
		}
		
		// Calculate the Reuse Time of the Skill
		int reuseDelay;
		if (skill.isStaticReuse() || skill.isStatic())
		{
			reuseDelay = (skill.getReuseDelay());
		}
		else if (skill.isMagic())
		{
			reuseDelay = (int) (skill.getReuseDelay() * calcStat(Stats.MAGIC_REUSE_RATE, 1, null, null));
		}
		else
		{
			reuseDelay = (int) (skill.getReuseDelay() * calcStat(Stats.P_REUSE, 1, null, null));
		}
		
		boolean skillMastery = Formulas.calcSkillMastery(this, skill);
		
		// Skill reuse check
		if ((reuseDelay > 30000) && !skillMastery)
		{
			addTimeStamp(skill, reuseDelay);
		}
		
		// Check if this skill consume mp on start casting
		int initmpcons = getStat().getMpInitialConsume(skill);
		if (initmpcons > 0)
		{
			getStatus().reduceMp(initmpcons);
		}
		
		if (skillMastery)
		{
			reuseDelay = 0;
			
			if (getActingPlayer() != null)
			{
				getActingPlayer().sendPacket(SystemMessageId.SKILL_READY_TO_USE_AGAIN);
			}
		}
		
		// Disable the skill during the re-use delay and create a task EnableSkill with Medium priority to enable it at the end of the re-use delay
		if (reuseDelay > 10)
		{
			disableSkill(skill, Config.RETAIL_SKILL_REUSE ? Math.max((reuseDelay - 1000), 10) : reuseDelay);
		}
		
		// Make sure that char is facing selected target
		if (target != this)
		{
			setHeading(Util.calculateHeadingFrom(this, target));
			broadcastPacket(new ExRotation(getObjectId(), getHeading()));
		}
		
		if (isPlayer())
		{
			// Consume Charges
			if (skill.getChargeConsume() > 0)
			{
				getActingPlayer().decreaseCharges(skill.getChargeConsume());
			}
			
			if (SunriseEvents.isInEvent((L2PcInstance) this))
			{
				SunriseEvents.onUseSkill((L2PcInstance) this, skill);
			}
		}
		
		// For force buff skills, start the effect as long as the player is casting.
		if (effectWhileCasting)
		{
			// Consume Items if necessary and Send the Server->Client packet InventoryUpdate with Item modification to all the L2Character
			if (skill.getItemConsumeId() > 0)
			{
				if (!destroyItemByItemId("Consume", skill.getItemConsumeId(), skill.getItemConsumeCount(), null, true))
				{
					sendPacket(SystemMessageId.NOT_ENOUGH_ITEMS);
					if (simultaneously)
					{
						setIsCastingSimultaneouslyNow(false);
					}
					else
					{
						setIsCastingNow(false);
					}
					
					if (isPlayer())
					{
						getAI().setIntention(AI_INTENTION_ACTIVE);
					}
					return;
				}
			}
			
			// Consume Souls if necessary
			if (skill.getMaxSoulConsumeCount() > 0)
			{
				if (isPlayer())
				{
					if (!getActingPlayer().decreaseSouls(skill.getMaxSoulConsumeCount(), skill))
					{
						if (simultaneously)
						{
							setIsCastingSimultaneouslyNow(false);
						}
						else
						{
							setIsCastingNow(false);
						}
						return;
					}
				}
			}
			
			if (skill.getSkillType() == L2SkillType.FUSION)
			{
				startFusionSkill(target, skill);
			}
			else
			{
				callSkill(skill, targets);
			}
		}
		
		// vGodFather
		// Toggle skills should not broadcast MagicSkillUse packet.
		if (!skill.isToggle())
		{
			// Send a Server->Client packet MagicSkillUser with target, displayId, level, skillTime, reuseDelay
			// to the L2Character AND to all L2PcInstance in the _KnownPlayers of the L2Character
			broadcastPacket(new MagicSkillUse(this, target, skill.getDisplayId(), skill.getDisplayLevel(), hitTime, reuseDelay));
			
			_flyLoc = null;
			if (skill.getFlyType() != null)
			{
				switch (skill.getFlyType())
				{
					case DUMMY:
					case CHARGE:
					{
						final Location flyLoc = getFlyLocation(target, skill);
						if (flyLoc != null)
						{
							_flyLoc = flyLoc;
							
							// vGodFather: only if target is monster
							if (target.isMonster() && target.hasAI())
							{
								target.getAI().clientStopMoving(flyLoc);
							}
							
							ThreadPoolManager.getInstance().scheduleEffect(() -> broadcastPacket(new FlyToLocation(this, flyLoc, skill.getFlyType())), 5);
						}
						else
						{
							sendPacket(SystemMessageId.CANT_SEE_TARGET);
							return;
						}
					}
				}
			}
		}
		
		// Send a system message USE_S1 to the L2Character
		if (isPlayer() && !skill.isAbnormalInstant())
		{
			SystemMessage sm = null;
			switch (magicId)
			{
				case 1312: // Fishing
				{
					// Done in L2PcInstance.startFishing()
					break;
				}
				case 2046: // Wolf Collar
				{
					sm = SystemMessage.getSystemMessage(SystemMessageId.SUMMON_A_PET);
					break;
				}
				default:
				{
					sm = SystemMessage.getSystemMessage(SystemMessageId.USE_S1);
					sm.addSkillName(skill);
					break;
				}
			}
			
			sendPacket(sm);
		}
		
		if (isPlayable())
		{
			if (!effectWhileCasting && (skill.getItemConsumeId() > 0))
			{
				if (!destroyItemByItemId("Consume", skill.getItemConsumeId(), skill.getItemConsumeCount(), null, true))
				{
					getActingPlayer().sendPacket(SystemMessageId.NOT_ENOUGH_ITEMS);
					abortCast();
					return;
				}
			}
		}
		
		MagicUseTask mut = new MagicUseTask(this, targets, skill, hitTime, coolTime, simultaneously);
		
		// vGodFather
		// toggle skills should not enter here
		// launch the magic in skillTime milliseconds
		if ((hitTime > 410) && !skill.isToggle())
		{
			// Send a Server->Client packet SetupGauge with the color of the gauge and the casting time
			if (isPlayer() && !effectWhileCasting)
			{
				sendPacket(new SetupGauge(SetupGauge.BLUE, hitTime));
			}
			
			if (skill.getHitCounts() > 0)
			{
				hitTime = (hitTime * skill.getHitTimings()[0]) / 100;
			}
			
			if (effectWhileCasting)
			{
				mut.setPhase(2);
			}
			
			if (simultaneously)
			{
				Future<?> future = _skillCast2;
				if (future != null)
				{
					future.cancel(true);
					_skillCast2 = null;
				}
				
				// Create a task MagicUseTask to launch the MagicSkill at the end of the casting time (skillTime)
				// For client animation reasons (party buffs especially) 400 ms before!
				_skillCast2 = ThreadPoolManager.getInstance().scheduleEffect(mut, hitTime - 400);
			}
			else
			{
				Future<?> future = _skillCast;
				if (future != null)
				{
					future.cancel(true);
					_skillCast = null;
				}
				
				// Create a task MagicUseTask to launch the MagicSkill at the end of the casting time (skillTime)
				// For client animation reasons (party buffs especially) 400 ms before!
				_skillCast = ThreadPoolManager.getInstance().scheduleEffect(mut, hitTime - 400);
			}
		}
		else
		{
			mut.setHitTime(0);
			onMagicLaunchedTimer(mut);
		}
	}
	
	/**
	 * Check if casting of skill is possible
	 * @param skill
	 * @return True if casting is possible
	 */
	public boolean checkDoCastConditions(L2Skill skill)
	{
		if ((skill == null) || isSkillDisabled(skill) || ((skill.getFlyType() == FlyType.CHARGE) && isMovementDisabled()))
		{
			// Send a Server->Client packet ActionFailed to the L2PcInstance
			sendPacket(ActionFailed.STATIC_PACKET);
			return false;
		}
		
		// Check if the caster has enough MP
		if (getCurrentMp() < (getStat().getMpConsume(skill) + getStat().getMpInitialConsume(skill)))
		{
			// Send a System Message to the caster
			sendPacket(SystemMessageId.NOT_ENOUGH_MP);
			
			// Send a Server->Client packet ActionFailed to the L2PcInstance
			sendPacket(ActionFailed.STATIC_PACKET);
			return false;
		}
		
		// Check if the caster has enough HP
		if (getCurrentHp() <= skill.getHpConsume())
		{
			// Send a System Message to the caster
			sendPacket(SystemMessageId.NOT_ENOUGH_HP);
			
			// Send a Server->Client packet ActionFailed to the L2PcInstance
			sendPacket(ActionFailed.STATIC_PACKET);
			return false;
		}
		
		// Skill mute checks.
		if (!skill.isStatic())
		{
			// Check if the skill is a magic spell and if the L2Character is not muted
			if (skill.isMagic())
			{
				if (isMuted())
				{
					// Send a Server->Client packet ActionFailed to the L2PcInstance
					sendPacket(ActionFailed.STATIC_PACKET);
					return false;
				}
			}
			else
			{
				// Check if the skill is physical and if the L2Character is not physical_muted
				if (isPhysicalMuted())
				{
					// Send a Server->Client packet ActionFailed to the L2PcInstance
					sendPacket(ActionFailed.STATIC_PACKET);
					return false;
				}
			}
		}
		
		// prevent casting signets to peace zone
		if ((skill.getSkillType() == L2SkillType.SIGNET) || (skill.getSkillType() == L2SkillType.SIGNET_CASTTIME))
		{
			L2WorldRegion region = getWorldRegion();
			if (region == null)
			{
				return false;
			}
			boolean canCast = true;
			if ((skill.getTargetType() == L2TargetType.GROUND) && isPlayer())
			{
				Location wp = getActingPlayer().getCurrentSkillWorldPosition();
				if (!region.checkEffectRangeInsidePeaceZone(skill, wp.getX(), wp.getY(), wp.getZ()))
				{
					canCast = false;
				}
			}
			else if (!region.checkEffectRangeInsidePeaceZone(skill, getX(), getY(), getZ()))
			{
				canCast = false;
			}
			if (!canCast)
			{
				SystemMessage sm = SystemMessage.getSystemMessage(SystemMessageId.S1_CANNOT_BE_USED);
				sm.addSkillName(skill);
				sendPacket(sm);
				return false;
			}
		}
		
		// Check if the caster's weapon is limited to use only its own skills
		if (getActiveWeaponItem() != null)
		{
			L2Weapon wep = getActiveWeaponItem();
			if (wep.useWeaponSkillsOnly() && !isGM() && wep.hasSkills())
			{
				boolean found = false;
				for (SkillHolder sh : wep.getSkills())
				{
					if (sh.getSkillId() == skill.getId())
					{
						found = true;
					}
				}
				
				if (!found)
				{
					if (getActingPlayer() != null)
					{
						sendPacket(SystemMessageId.WEAPON_CAN_USE_ONLY_WEAPON_SKILL);
					}
					return false;
				}
			}
		}
		
		// Check if the spell consumes an Item
		// TODO: combine check and consume
		if ((skill.getItemConsumeId() > 0) && (getInventory() != null))
		{
			// Get the L2ItemInstance consumed by the spell
			L2ItemInstance requiredItems = getInventory().getItemByItemId(skill.getItemConsumeId());
			
			// Check if the caster owns enough consumed Item to cast
			if ((requiredItems == null) || (requiredItems.getCount() < skill.getItemConsumeCount()))
			{
				// Checked: when a summon skill failed, server show required consume item count
				if (skill.getSkillType() == L2SkillType.SUMMON)
				{
					SystemMessage sm = SystemMessage.getSystemMessage(SystemMessageId.SUMMONING_SERVITOR_COSTS_S2_S1);
					sm.addItemName(skill.getItemConsumeId());
					sm.addInt(skill.getItemConsumeCount());
					sendPacket(sm);
				}
				else
				{
					// Send a System Message to the caster
					sendPacket(SystemMessageId.THERE_ARE_NOT_ENOUGH_NECESSARY_ITEMS_TO_USE_THE_SKILL);
					getAI().setIntention(CtrlIntention.AI_INTENTION_IDLE);
				}
				return false;
			}
		}
		return true;
	}
	
	/**
	 * Gets the item reuse time stamps map.
	 * @return the item reuse time stamps map
	 */
	public final Map<Integer, TimeStamp> getItemReuseTimeStamps()
	{
		return _reuseTimeStampsItems;
	}
	
	/**
	 * Adds a item reuse time stamp.
	 * @param item the item
	 * @param reuse the reuse
	 */
	public final void addTimeStampItem(L2ItemInstance item, long reuse)
	{
		addTimeStampItem(item, reuse, -1);
	}
	
	/**
	 * Adds a item reuse time stamp.<br>
	 * Used for restoring purposes.
	 * @param item the item
	 * @param reuse the reuse
	 * @param systime the system time
	 */
	public final void addTimeStampItem(L2ItemInstance item, long reuse, long systime)
	{
		_reuseTimeStampsItems.put(item.getObjectId(), new TimeStamp(item, reuse, systime));
	}
	
	/**
	 * Gets the item remaining reuse time for a given item object ID.
	 * @param itemObjId the item object ID
	 * @return if the item has a reuse time stamp, the remaining time, otherwise -1
	 */
	public final long getItemRemainingReuseTime(int itemObjId)
	{
		if ((_reuseTimeStampsItems == null) || !_reuseTimeStampsItems.containsKey(itemObjId))
		{
			return -1;
		}
		return _reuseTimeStampsItems.get(itemObjId).getRemaining();
	}
	
	/**
	 * Gets the item remaining reuse time for a given shared reuse item group.
	 * @param group the shared reuse item group
	 * @return if the shared reuse item group has a reuse time stamp, the remaining time, otherwise -1
	 */
	public final long getReuseDelayOnGroup(int group)
	{
		if (group > 0)
		{
			try
			{
				for (TimeStamp ts : _reuseTimeStampsItems.values())
				{
					if ((ts.getSharedReuseGroup() == group) && ts.hasNotPassed())
					{
						return ts.getRemaining();
					}
				}
			}
			catch (Exception e)
			{
				return -1;
			}
		}
		return -1;
	}
	
	/**
	 * Gets the skill reuse time stamps map.
	 * @return the skill reuse time stamps map
	 */
	public final Map<Integer, TimeStamp> getSkillReuseTimeStamps()
	{
		return _reuseTimeStampsSkills;
	}
	
	/**
	 * Adds the skill reuse time stamp.
	 * @param skill the skill
	 * @param reuse the delay
	 */
	public final void addTimeStamp(L2Skill skill, long reuse)
	{
		addTimeStamp(skill, reuse, -1);
	}
	
	/**
	 * Adds the skill reuse time stamp.<br>
	 * Used for restoring purposes.
	 * @param skill the skill
	 * @param reuse the reuse
	 * @param systime the system time
	 */
	public final void addTimeStamp(L2Skill skill, long reuse, long systime)
	{
		_reuseTimeStampsSkills.put(skill.getReuseHashCode(), new TimeStamp(skill, reuse, systime));
	}
	
	/**
	 * Removes a skill reuse time stamp.
	 * @param skill the skill to remove
	 */
	public final void removeTimeStamp(L2Skill skill)
	{
		_reuseTimeStampsSkills.remove(skill.getReuseHashCode());
	}
	
	/**
	 * Removes all skill reuse time stamps.
	 */
	public final void resetTimeStamps()
	{
		if (_reuseTimeStampsSkills != null)
		{
			_reuseTimeStampsSkills.clear();
		}
	}
	
	/**
	 * Gets the skill remaining reuse time for a given skill hash code.
	 * @param hashCode the skill hash code
	 * @return if the skill has a reuse time stamp, the remaining time, otherwise -1
	 */
	public final long getSkillRemainingReuseTime(int hashCode)
	{
		if ((_reuseTimeStampsSkills == null) || !_reuseTimeStampsSkills.containsKey(hashCode))
		{
			return -1;
		}
		return _reuseTimeStampsSkills.get(hashCode).getRemaining();
	}
	
	/**
	 * Verifies if the skill is under reuse time.
	 * @param hashCode the skill hash code
	 * @return {@code true} if the skill is under reuse time, {@code false} otherwise
	 */
	public final boolean hasSkillReuse(int hashCode)
	{
		if ((_reuseTimeStampsSkills == null) || !_reuseTimeStampsSkills.containsKey(hashCode))
		{
			return false;
		}
		return _reuseTimeStampsSkills.get(hashCode).hasNotPassed();
	}
	
	/**
	 * Gets the skill reuse time stamp.
	 * @param hashCode the skill hash code
	 * @return if the skill has a reuse time stamp, the skill reuse time stamp, otherwise {@code null}
	 */
	public synchronized final TimeStamp getSkillReuseTimeStamp(int hashCode)
	{
		return _reuseTimeStampsSkills != null ? _reuseTimeStampsSkills.get(hashCode) : null;
	}
	
	public void startFusionSkill(L2Character target, L2Skill skill)
	{
		if (skill.getSkillType() != L2SkillType.FUSION)
		{
			return;
		}
		
		if (_fusionSkill == null)
		{
			_fusionSkill = new FusionSkill(this, target, skill);
		}
	}
	
	/**
	 * Kill the L2Character.<br>
	 * <B><U>Actions</U>:</B>
	 * <ul>
	 * <li>Set target to null and cancel Attack or Cast</li>
	 * <li>Stop movement</li>
	 * <li>Stop HP/MP/CP Regeneration task</li>
	 * <li>Stop all active skills effects in progress on the L2Character</li>
	 * <li>Send the Server->Client packet StatusUpdate with current HP and MP to all other L2PcInstance to inform</li>
	 * <li>Notify L2Character AI</li>
	 * </ul>
	 * @param killer The L2Character who killed it
	 * @return false if the player is already dead.
	 */
	public boolean doDie(L2Character killer)
	{
		final TerminateReturn returnBack = EventDispatcher.getInstance().notifyEvent(new OnCreatureKill(killer, this), this, TerminateReturn.class);
		if ((returnBack != null) && returnBack.terminate())
		{
			return false;
		}
		
		// killing is only possible one time
		synchronized (this)
		{
			if (isDead())
			{
				return false;
			}
			// now reset currentHp to zero
			setCurrentHp(0);
			setIsDead(true);
		}
		
		// Set target to null and cancel Attack or Cast
		setTarget(null);
		
		// Stop movement
		stopMove(null);
		
		// Stop HP/MP/CP Regeneration task
		getStatus().stopHpMpRegeneration();
		
		stopAllEffectsExceptThoseThatLastThroughDeath();
		
		calculateRewards(killer);
		
		// Send the Server->Client packet StatusUpdate with current HP and MP to all other L2PcInstance to inform
		broadcastStatusUpdate();
		
		// Notify L2Character AI
		if (hasAI())
		{
			getAI().notifyEvent(CtrlEvent.EVT_DEAD);
		}
		
		if (getWorldRegion() != null)
		{
			getWorldRegion().onDeath(this);
		}
		
		getAttackByList().clear();
		
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
		return true;
	}
	
	public void deleteMe()
	{
		setDebug(null);
		
		if (hasAI())
		{
			getAI().stopAITask();
		}
	}
	
	public void detachAI()
	{
		if (isWalker())
		{
			return;
		}
		setAI(null);
	}
	
	protected void calculateRewards(L2Character killer)
	{
	}
	
	/** Sets HP, MP and CP and revives the L2Character. */
	public void doRevive()
	{
		if (!isDead())
		{
			return;
		}
		
		if (!isTeleporting())
		{
			setIsPendingRevive(false);
			setIsDead(false);
			
			if ((Config.RESPAWN_RESTORE_CP > 0) && (getCurrentCp() < (getMaxCp() * Config.RESPAWN_RESTORE_CP)))
			{
				_status.setCurrentCp(getMaxCp() * Config.RESPAWN_RESTORE_CP);
			}
			if ((Config.RESPAWN_RESTORE_HP > 0) && (getCurrentHp() < (getMaxHp() * Config.RESPAWN_RESTORE_HP)))
			{
				_status.setCurrentHp(getMaxHp() * Config.RESPAWN_RESTORE_HP);
			}
			if ((Config.RESPAWN_RESTORE_MP > 0) && (getCurrentMp() < (getMaxMp() * Config.RESPAWN_RESTORE_MP)))
			{
				_status.setCurrentMp(getMaxMp() * Config.RESPAWN_RESTORE_MP);
			}
			
			// Start broadcast status
			broadcastPacket(new Revive(this));
			if (getWorldRegion() != null)
			{
				getWorldRegion().onRevive(this);
			}
		}
		else
		{
			setIsPendingRevive(true);
		}
	}
	
	/**
	 * Revives the L2Character using skill.
	 * @param revivePower
	 */
	public void doRevive(double revivePower)
	{
		doRevive();
	}
	
	/**
	 * Gets this creature's AI.
	 * @return the AI
	 */
	public final L2CharacterAI getAI()
	{
		if (_ai == null)
		{
			synchronized (this)
			{
				if (_ai == null)
				{
					// Return the new AI within the synchronized block
					// to avoid being nulled by other threads
					return _ai = initAI();
				}
			}
		}
		return _ai;
	}
	
	/**
	 * Initialize this creature's AI.<br>
	 * OOP approach to be overridden in child classes.
	 * @return the new AI
	 */
	protected L2CharacterAI initAI()
	{
		return new L2CharacterAI(this);
	}
	
	public void setAI(L2CharacterAI newAI)
	{
		final L2CharacterAI oldAI = _ai;
		if ((oldAI != null) && (oldAI != newAI) && (oldAI instanceof L2AttackableAI))
		{
			((L2AttackableAI) oldAI).stopAITask();
		}
		_ai = newAI;
	}
	
	/**
	 * Verifies if this creature has an AI,
	 * @return {@code true} if this creature has an AI, {@code false} otherwise
	 */
	public boolean hasAI()
	{
		return _ai != null;
	}
	
	/**
	 * @return True if the L2Character is RaidBoss or his minion.
	 */
	public boolean isRaid()
	{
		return false;
	}
	
	/**
	 * @return True if the L2Character is minion.
	 */
	public boolean isMinion()
	{
		return false;
	}
	
	/**
	 * @return True if the L2Character is minion of RaidBoss.
	 */
	public boolean isRaidMinion()
	{
		return false;
	}
	
	/**
	 * @return a list of L2Character that attacked.
	 */
	public final Set<L2Character> getAttackByList()
	{
		if (_attackByList == null)
		{
			synchronized (this)
			{
				if (_attackByList == null)
				{
					_attackByList = ConcurrentHashMap.newKeySet();
				}
			}
		}
		return _attackByList;
	}
	
	public final L2Skill getLastSimultaneousSkillCast()
	{
		return _lastSimultaneousSkillCast;
	}
	
	public void setLastSimultaneousSkillCast(L2Skill skill)
	{
		_lastSimultaneousSkillCast = skill;
	}
	
	public final L2Skill getLastSkillCast()
	{
		return _lastSkillCast;
	}
	
	public void setLastSkillCast(L2Skill skill)
	{
		_lastSkillCast = skill;
	}
	
	public final boolean isNoRndWalk()
	{
		return _isNoRndWalk;
	}
	
	public final void setIsNoRndWalk(boolean value)
	{
		_isNoRndWalk = value;
	}
	
	public final boolean isAfraid()
	{
		return isAffected(EffectFlag.FEAR);
	}
	
	/**
	 * @return True if the L2Character can't use its skills (ex : stun, sleep...).
	 */
	public final boolean isAllSkillsDisabled()
	{
		return _allSkillsDisabled || isStunned() || isSleeping() || isParalyzed();
	}
	
	/**
	 * @return True if the L2Character can't attack (stun, sleep, attackEndTime, fakeDeath, paralyze, attackMute).
	 */
	public boolean isAttackingDisabled()
	{
		return isFlying() || isStunned() || isSleeping() || isAttackingNow() || isAlikeDead() || isParalyzed() || isPhysicalAttackMuted() || isCoreAIDisabled();
	}
	
	public boolean canFinishAttack()
	{
		return !isStunned() && !isSleeping() && !isAlikeDead() && !isParalyzed() && !isPhysicalAttackMuted();
	}
	
	public final Calculator[] getCalculators()
	{
		return _calculators;
	}
	
	public final boolean isConfused()
	{
		return isAffected(EffectFlag.CONFUSED);
	}
	
	/**
	 * @return True if the L2Character is dead or use fake death.
	 */
	public boolean isAlikeDead()
	{
		return _isDead;
	}
	
	/**
	 * @return True if the L2Character is dead.
	 */
	public final boolean isDead()
	{
		return _isDead;
	}
	
	public final void setIsDead(boolean value)
	{
		_isDead = value;
	}
	
	public boolean isImmobilized()
	{
		return _isImmobilized;
	}
	
	public void setIsImmobilized(boolean value)
	{
		_isImmobilized = value;
	}
	
	public final boolean isMuted()
	{
		return isAffected(EffectFlag.MUTED);
	}
	
	public final boolean isPhysicalMuted()
	{
		return isAffected(EffectFlag.PSYCHICAL_MUTED);
	}
	
	public final boolean isPhysicalAttackMuted()
	{
		return isAffected(EffectFlag.PSYCHICAL_ATTACK_MUTED);
	}
	
	/**
	 * @return True if the L2Character can't move (stun, root, sleep, overload, paralyzed).
	 */
	public boolean isMovementDisabled()
	{
		// check for isTeleporting to prevent teleport cheating (if appear packet not received)
		return isStunned() || isRooted() || isSleeping() || isOverloaded() || isParalyzed() || isImmobilized() || isAlikeDead() || isTeleporting();
	}
	
	/**
	 * @return True if the L2Character can not be controlled by the player (confused, afraid).
	 */
	public final boolean isOutOfControl()
	{
		return isConfused() || isAfraid();
	}
	
	public final boolean isOverloaded()
	{
		return _isOverloaded;
	}
	
	/**
	 * Set the overloaded status of the L2Character is overloaded (if True, the L2PcInstance can't take more item).
	 * @param value
	 */
	public final void setIsOverloaded(boolean value)
	{
		_isOverloaded = value;
	}
	
	public final boolean isParalyzed()
	{
		return _isParalyzed || isAffected(EffectFlag.PARALYZED);
	}
	
	public final void setIsParalyzed(boolean value)
	{
		_isParalyzed = value;
	}
	
	public final boolean isPendingRevive()
	{
		return isDead() && _isPendingRevive;
	}
	
	public final void setIsPendingRevive(boolean value)
	{
		_isPendingRevive = value;
	}
	
	public final boolean isDisarmed()
	{
		return isAffected(EffectFlag.DISARMED);
	}
	
	/**
	 * @return the summon
	 */
	public L2Summon getSummon()
	{
		return null;
	}
	
	/**
	 * @return {@code true} if the character has a summon, {@code false} otherwise
	 */
	public final boolean hasSummon()
	{
		return getSummon() != null;
	}
	
	/**
	 * @return {@code true} if the character has a pet, {@code false} otherwise
	 */
	public final boolean hasPet()
	{
		return hasSummon() && getSummon().isPet();
	}
	
	/**
	 * @return {@code true} if the character has a servitor, {@code false} otherwise
	 */
	public final boolean hasServitor()
	{
		return hasSummon() && getSummon().isServitor();
	}
	
	public final boolean isRooted()
	{
		return isAffected(EffectFlag.ROOTED);
	}
	
	/**
	 * @return True if the L2Character is running.
	 */
	public boolean isRunning()
	{
		return _isRunning;
	}
	
	public final void setIsRunning(boolean value)
	{
		if (_isRunning == value)
		{
			return;
		}
		
		_isRunning = value;
		if (getRunSpeed() != 0)
		{
			broadcastPacket(new ChangeMoveType(this));
		}
		if (isPlayer())
		{
			getActingPlayer().broadcastUserInfo();
		}
		else if (isSummon())
		{
			broadcastStatusUpdate();
		}
		else if (isNpc())
		{
			Collection<L2PcInstance> plrs = getKnownList().getKnownPlayers().values();
			for (L2PcInstance player : plrs)
			{
				if ((player == null) || !isVisibleFor(player))
				{
					continue;
				}
				else if (getRunSpeed() == 0)
				{
					player.sendPacket(new ServerObjectInfo((L2Npc) this, player));
				}
				else
				{
					((L2Npc) this).sendInfo(player);
				}
			}
		}
	}
	
	/** Set the L2Character movement type to run and send Server->Client packet ChangeMoveType to all others L2PcInstance. */
	public final void setRunning()
	{
		if (!isRunning())
		{
			setIsRunning(true);
		}
	}
	
	public final boolean isSleeping()
	{
		return isAffected(EffectFlag.SLEEP);
	}
	
	public final boolean isStunned()
	{
		return isAffected(EffectFlag.STUNNED);
	}
	
	public final boolean isBetrayed()
	{
		return isAffected(EffectFlag.BETRAYED);
	}
	
	public final boolean isTeleporting()
	{
		return _isTeleporting;
	}
	
	public void setIsTeleporting(boolean value)
	{
		_isTeleporting = value;
	}
	
	public void setIsInvul(boolean b)
	{
		_isInvul = b;
	}
	
	public boolean isInvul()
	{
		return _isInvul || _isTeleporting || isAffected(EffectFlag.INVUL) || isHpBlocked();
	}
	
	public boolean isUndying()
	{
		return false;
	}
	
	public boolean isHpBlocked()
	{
		return isAffected(EffectFlag.BLOCK_HP);
	}
	
	public boolean isMpBlocked()
	{
		return isAffected(EffectFlag.BLOCK_MP);
	}
	
	public boolean isBuffBlocked()
	{
		return isAffected(EffectFlag.BLOCK_BUFF);
	}
	
	public boolean isDebuffBlocked()
	{
		return isAffected(EffectFlag.BLOCK_DEBUFF);
	}
	
	public void setIsMortal(boolean b)
	{
		_isMortal = b;
	}
	
	public boolean isMortal()
	{
		return _isMortal;
	}
	
	public boolean isUndead()
	{
		return false;
	}
	
	public boolean isResurrectionBlocked()
	{
		return isAffected(EffectFlag.BLOCK_RESURRECTION);
	}
	
	public final boolean isFlying()
	{
		return _isFlying;
	}
	
	public final void setIsFlying(boolean mode)
	{
		_isFlying = mode;
	}
	
	@Override
	public CharKnownList getKnownList()
	{
		return ((CharKnownList) super.getKnownList());
	}
	
	@Override
	public void initKnownList()
	{
		setKnownList(new CharKnownList(this));
	}
	
	public CharStat getStat()
	{
		return _stat;
	}
	
	/**
	 * Initializes the CharStat class of the L2Object, is overwritten in classes that require a different CharStat Type.<br>
	 * Removes the need for instanceof checks.
	 */
	public void initCharStat()
	{
		_stat = new CharStat(this);
	}
	
	public final void setStat(CharStat value)
	{
		_stat = value;
	}
	
	public CharStatus getStatus()
	{
		return _status;
	}
	
	/**
	 * Initializes the CharStatus class of the L2Object, is overwritten in classes that require a different CharStatus Type.<br>
	 * Removes the need for instanceof checks.
	 */
	public void initCharStatus()
	{
		_status = new CharStatus(this);
	}
	
	public final void setStatus(CharStatus value)
	{
		_status = value;
	}
	
	public L2CharTemplate getTemplate()
	{
		return _template;
	}
	
	/**
	 * Set the template of the L2Character.<br>
	 * <B><U>Concept</U>:</B><br>
	 * Each L2Character owns generic and static properties (ex : all Keltir have the same number of HP...).<br>
	 * All of those properties are stored in a different template for each type of L2Character.<br>
	 * Each template is loaded once in the server cache memory (reduce memory use).<br>
	 * When a new instance of L2Character is spawned, server just create a link between the instance and the template This link is stored in <B>_template</B>.
	 * @param template
	 */
	protected final void setTemplate(L2CharTemplate template)
	{
		_template = template;
	}
	
	/**
	 * @return the Title of the L2Character.
	 */
	public final String getTitle()
	{
		return _title == null ? "" : _title;
	}
	
	/**
	 * Set the Title of the L2Character.
	 * @param value
	 */
	public final void setTitle(String value)
	{
		if (value == null)
		{
			_title = "";
		}
		else
		{
			_title = value.length() > 21 ? value.substring(0, 20) : value;
		}
	}
	
	/**
	 * Set the L2Character movement type to walk and send Server->Client packet ChangeMoveType to all others L2PcInstance.
	 */
	public final void setWalking()
	{
		if (isRunning())
		{
			setIsRunning(false);
		}
	}
	
	// Abnormal Effect - NEED TO REMOVE ONCE L2CHARABNORMALEFFECT IS COMPLETE
	/** Map 32 bits (0x0000) containing all abnormal effect in progress */
	private int _AbnormalEffects;
	
	protected CharEffectList _effects = new CharEffectList(this);
	
	public boolean isAffectedBySkill(int skillId)
	{
		return _effects.isAffectedBySkill(skillId);
	}
	
	public final CharEffectList getEffectList()
	{
		return _effects;
	}
	
	private int _SpecialEffects;
	
	/**
	 * Launch and add L2Effect (including Stack Group management) to L2Character and update client magic icon.<br>
	 * <B><U>Concept</U>:</B><br>
	 * All active skills effects in progress on the L2Character are identified in ConcurrentHashMap(Integer,L2Effect) <B>_effects</B>.<br>
	 * The Integer key of _effects is the L2Skill Identifier that has created the L2Effect.<br>
	 * Several same effect can't be used on a L2Character at the same time.<br>
	 * Indeed, effects are not stackable and the last cast will replace the previous in progress.<br>
	 * More, some effects belong to the same Stack Group (ex WindWald and Haste Potion).<br>
	 * If 2 effects of a same group are used at the same time on a L2Character, only the more efficient (identified by its priority order) will be preserve.<br>
	 * <B><U>Actions</U>:</B>
	 * <ul>
	 * <li>Add the L2Effect to the L2Character _effects</li>
	 * <li>If this effect doesn't belong to a Stack Group, add its Funcs to the Calculator set of the L2Character (remove the old one if necessary)</li>
	 * <li>If this effect has higher priority in its Stack Group, add its Funcs to the Calculator set of the L2Character (remove previous stacked effect Funcs if necessary)</li>
	 * <li>If this effect has NOT higher priority in its Stack Group, set the effect to Not In Use</li>
	 * <li>Update active skills in progress icons on player client</li>
	 * </ul>
	 * @param newEffect
	 */
	public void addEffect(L2Effect newEffect)
	{
		_effects.queueEffect(newEffect, false);
	}
	
	/**
	 * Stop and remove L2Effect (including Stack Group management) from L2Character and update client magic icon.<br>
	 * <B><U>Concept</U>:</B><br>
	 * All active skills effects in progress on the L2Character are identified in ConcurrentHashMap(Integer,L2Effect) <B>_effects</B>.<br>
	 * The Integer key of _effects is the L2Skill Identifier that has created the L2Effect.<br>
	 * Several same effect can't be used on a L2Character at the same time.<br>
	 * Indeed, effects are not stackable and the last cast will replace the previous in progress.<br>
	 * More, some effects belong to the same Stack Group (ex Wind Walk and Haste Potion).<br>
	 * If 2 effects of a same group are used at the same time on a L2Character, only the more efficient (identified by its priority order) will be preserve.<br>
	 * <B><U>Actions</U>:</B>
	 * <ul>
	 * <li>Remove Func added by this effect from the L2Character Calculator (Stop L2Effect)</li>
	 * <li>If the L2Effect belongs to a not empty Stack Group, replace theses Funcs by next stacked effect Funcs</li>
	 * <li>Remove the L2Effect from _effects of the L2Character</li>
	 * <li>Update active skills in progress icons on player client</li>
	 * </ul>
	 * @param effect
	 */
	public final void removeEffect(L2Effect effect)
	{
		_effects.queueEffect(effect, true);
	}
	
	/**
	 * Active abnormal effects flags in the binary mask and send Server->Client UserInfo/CharInfo packet.
	 * @param mask
	 */
	public final void startAbnormalEffect(AbnormalEffect mask)
	{
		startAbnormalEffect(true, mask);
	}
	
	/**
	 * Active abnormal effects flags in the binary mask and send Server->Client UserInfo/CharInfo packet.
	 * @param update
	 * @param mask
	 */
	public final void startAbnormalEffect(boolean update, AbnormalEffect mask)
	{
		_AbnormalEffects |= mask.getMask();
		if (update)
		{
			updateAbnormalEffect();
		}
	}
	
	/**
	 * Active special effects flags in the binary mask and send Server->Client UserInfo/CharInfo packet.
	 * @param mask
	 */
	public final void startSpecialEffect(AbnormalEffect[] mask)
	{
		for (AbnormalEffect special : mask)
		{
			_SpecialEffects |= special.getMask();
		}
		updateAbnormalEffect();
	}
	
	public final void startAbnormalEffect(int mask)
	{
		_AbnormalEffects |= mask;
		updateAbnormalEffect();
	}
	
	public final void startSpecialEffect(int mask)
	{
		_SpecialEffects |= mask;
		updateAbnormalEffect();
	}
	
	/**
	 * Active the abnormal effect Confused flag, notify the L2Character AI and send Server->Client UserInfo/CharInfo packet.
	 */
	public final void startConfused()
	{
		getAI().notifyEvent(CtrlEvent.EVT_CONFUSED);
		updateAbnormalEffect();
	}
	
	/**
	 * Active the abnormal effect Fake Death flag, notify the L2Character AI and send Server->Client UserInfo/CharInfo packet.
	 */
	public final void startFakeDeath()
	{
		if (!isPlayer())
		{
			return;
		}
		
		getActingPlayer().setIsFakeDeath(true);
		// Aborts any attacks/casts if fake dead
		abortAttack();
		abortCast();
		stopMove(null);
		getAI().notifyEvent(CtrlEvent.EVT_FAKE_DEATH);
		broadcastPacket(new ChangeWaitType(this, ChangeWaitType.WT_START_FAKEDEATH));
	}
	
	/**
	 * Launch a Stun Abnormal Effect on the L2Character.<br>
	 * <B><U>Actions</U>:</B>
	 * <ul>
	 * <li>Calculate the success rate of the Stun Abnormal Effect on this L2Character</li>
	 * <li>If Stun succeed, active the abnormal effect Stun flag, notify the L2Character AI and send Server->Client UserInfo/CharInfo packet</li>
	 * <li>If Stun NOT succeed, send a system message Failed to the L2PcInstance attacker</li>
	 * </ul>
	 */
	public final void startStunning()
	{
		// Aborts any attacks/casts if stunned
		abortAttack();
		abortCast();
		stopMove(null);
		getAI().notifyEvent(CtrlEvent.EVT_STUNNED);
		if (!isSummon())
		{
			getAI().setIntention(CtrlIntention.AI_INTENTION_IDLE);
		}
		updateAbnormalEffect();
	}
	
	public final void startParalyze()
	{
		// Aborts any attacks/casts if paralyzed
		abortAttack();
		abortCast();
		stopMove(null);
		getAI().notifyEvent(CtrlEvent.EVT_PARALYZED);
		updateAbnormalEffect();
	}
	
	/**
	 * Modify the abnormal effect map according to the mask.
	 * @param mask
	 */
	public final void stopAbnormalEffect(AbnormalEffect mask)
	{
		stopAbnormalEffect(true, mask);
	}
	
	/**
	 * Modify the abnormal effect map according to the mask.
	 * @param update
	 * @param mask
	 */
	public final void stopAbnormalEffect(boolean update, AbnormalEffect mask)
	{
		_AbnormalEffects &= ~mask.getMask();
		if (update)
		{
			updateAbnormalEffect();
		}
	}
	
	/**
	 * Modify the special effect map according to the mask.
	 * @param mask
	 */
	public final void stopSpecialEffect(AbnormalEffect[] mask)
	{
		for (AbnormalEffect special : mask)
		{
			_SpecialEffects &= ~special.getMask();
		}
		updateAbnormalEffect();
	}
	
	public final void stopAbnormalEffect(int mask)
	{
		_AbnormalEffects &= ~mask;
		updateAbnormalEffect();
	}
	
	public final void stopSpecialEffect(int mask)
	{
		_SpecialEffects &= ~mask;
		updateAbnormalEffect();
	}
	
	/**
	 * Stop all active skills effects in progress on the L2Character.
	 */
	public void stopAllEffects()
	{
		_effects.stopAllEffects();
	}
	
	public void stopAllEffectsExceptThoseThatLastThroughDeath()
	{
		_effects.stopAllEffectsExceptThoseThatLastThroughDeath();
	}
	
	/**
	 * Stop and remove the L2Effects corresponding to the L2Skill Identifier and update client magic icon.<br>
	 * <B><U>Concept</U>:</B><br>
	 * All active skills effects in progress on the L2Character are identified in ConcurrentHashMap(Integer,L2Effect) <B>_effects</B>.<br>
	 * The Integer key of _effects is the L2Skill Identifier that has created the L2Effect.
	 * @param skillId the L2Skill Identifier of the L2Effect to remove from _effects
	 */
	public void stopSkillEffects(int skillId)
	{
		_effects.stopSkillEffects(skillId);
	}
	
	/**
	 * Stop and remove all L2Effect of the selected type (ex : BUFF, DMG_OVER_TIME...) from the L2Character and update client magic icon.<br>
	 * <B><U>Concept</U>:</B><br>
	 * All active skills effects in progress on the L2Character are identified in ConcurrentHashMap(Integer,L2Effect) <B>_effects</B>.<br>
	 * The Integer key of _effects is the L2Skill Identifier that has created the L2Effect.<br>
	 * <B><U>Actions</U>:</B>
	 * <ul>
	 * <li>Remove Func added by this effect from the L2Character Calculator (Stop L2Effect)</li>
	 * <li>Remove the L2Effect from _effects of the L2Character</li>
	 * <li>Update active skills in progress icons on player client</li>
	 * </ul>
	 * @param type The type of effect to stop ((ex : BUFF, DMG_OVER_TIME...)
	 */
	public final void stopEffects(L2EffectType type)
	{
		_effects.stopEffects(type);
	}
	
	/**
	 * Exits all buffs effects of the skills with "removedOnAnyAction" set.<br>
	 * Called on any action except movement (attack, cast).
	 */
	public final void stopEffectsOnAction()
	{
		_effects.stopEffectsOnAction();
	}
	
	/**
	 * Exits all buffs effects of the skills with "removedOnDamage" set.<br>
	 * Called on decreasing HP and mana burn.
	 * @param awake
	 */
	public final void stopEffectsOnDamage(boolean awake)
	{
		_effects.stopEffectsOnDamage(awake);
	}
	
	/**
	 * Stop a specified/all Fake Death abnormal L2Effect.<br>
	 * <B><U>Actions</U>:</B>
	 * <ul>
	 * <li>Delete a specified/all (if effect=null) Fake Death abnormal L2Effect from L2Character and update client magic icon</li>
	 * <li>Set the abnormal effect flag _fake_death to False</li>
	 * <li>Notify the L2Character AI</li>
	 * </ul>
	 * @param removeEffects
	 */
	public final void stopFakeDeath(boolean removeEffects)
	{
		if (removeEffects)
		{
			stopEffects(L2EffectType.FAKE_DEATH);
		}
		
		// if this is a player instance, start the grace period for this character (grace from mobs only)!
		if (isPlayer())
		{
			getActingPlayer().setIsFakeDeath(false);
			getActingPlayer().setRecentFakeDeath(true);
		}
		
		broadcastPacket(new ChangeWaitType(this, ChangeWaitType.WT_STOP_FAKEDEATH));
		broadcastPacket(new Revive(this));
	}
	
	/**
	 * Stop a specified/all Stun abnormal L2Effect.<br>
	 * <B><U>Actions</U>:</B>
	 * <ul>
	 * <li>Delete a specified/all (if effect=null) Stun abnormal L2Effect from L2Character and update client magic icon</li>
	 * <li>Set the abnormal effect flag _stuned to False</li>
	 * <li>Notify the L2Character AI</li>
	 * <li>Send Server->Client UserInfo/CharInfo packet</li>
	 * </ul>
	 * @param removeEffects
	 */
	public final void stopStunning(boolean removeEffects)
	{
		if (removeEffects)
		{
			stopEffects(L2EffectType.STUN);
		}
		
		if (!isPlayer())
		{
			getAI().notifyEvent(CtrlEvent.EVT_THINK);
		}
		updateAbnormalEffect();
	}
	
	/**
	 * Stop L2Effect: Transformation.<br>
	 * <B><U>Actions</U>:</B>
	 * <ul>
	 * <li>Remove Transformation Effect</li>
	 * <li>Notify the L2Character AI</li>
	 * <li>Send Server->Client UserInfo/CharInfo packet</li>
	 * </ul>
	 * @param removeEffects
	 */
	public final void stopTransformation(boolean removeEffects)
	{
		if (removeEffects)
		{
			stopEffects(L2EffectType.TRANSFORMATION);
		}
		
		// if this is a player instance, then untransform, also set the transform_id column equal to 0 if not cursed.
		if (isPlayer())
		{
			if (getActingPlayer().getTransformation() != null)
			{
				getActingPlayer().untransform();
				getActingPlayer().stopAllToggles();
			}
		}
		
		if (!isPlayer())
		{
			getAI().notifyEvent(CtrlEvent.EVT_THINK);
		}
		updateAbnormalEffect();
	}
	
	public abstract void updateAbnormalEffect();
	
	/**
	 * Update active skills in progress (In Use and Not In Use because stacked) icons on client.<br>
	 * <B><U>Concept</U>:</B><br>
	 * All active skills effects in progress (In Use and Not In Use because stacked) are represented by an icon on the client.<br>
	 * <FONT COLOR=#FF0000><B> <U>Caution</U> : This method ONLY UPDATE the client of the player and not clients of all players in the party.</B></FONT>
	 */
	public final void updateEffectIcons()
	{
		updateEffectIcons(false);
	}
	
	/**
	 * Updates Effect Icons for this character(player/summon) and his party if any.
	 * @param partyOnly
	 */
	public void updateEffectIcons(boolean partyOnly)
	{
		// overridden
	}
	
	/**
	 * <B><U>Concept</U>:</B><br>
	 * In Server->Client packet, each effect is represented by 1 bit of the map (ex : BLEEDING = 0x0001 (bit 1), SLEEP = 0x0080 (bit 8)...). The map is calculated by applying a BINARY OR operation on each effect.<br>
	 * <B><U>Example of use </U>:</B>
	 * <ul>
	 * <li>Server Packet : CharInfo, NpcInfo, NpcInfoPoly, UserInfo...</li>
	 * </ul>
	 * @return a map of 16 bits (0x0000) containing all abnormal effect in progress for this L2Character.
	 */
	public int getAbnormalEffect()
	{
		int ae = _AbnormalEffects;
		if (!isFlying() && isStunned())
		{
			ae |= AbnormalEffect.STUN.getMask();
		}
		if (!isFlying() && isRooted())
		{
			ae |= AbnormalEffect.ROOT.getMask();
		}
		if (isSleeping())
		{
			ae |= AbnormalEffect.SLEEP.getMask();
		}
		if (isConfused())
		{
			ae |= AbnormalEffect.FEAR.getMask();
		}
		if (isMuted())
		{
			ae |= AbnormalEffect.MUTED.getMask();
		}
		if (isPhysicalMuted())
		{
			ae |= AbnormalEffect.MUTED.getMask();
		}
		if (isAfraid())
		{
			ae |= AbnormalEffect.SKULL_FEAR.getMask();
		}
		return ae;
	}
	
	/**
	 * <B><U>Concept</U>:</B><br>
	 * In Server->Client packet, each effect is represented by 1 bit of the map (ex : INVULNERABLE = 0x0001 (bit 1), PINK_AFFRO = 0x0020 (bit 6)...). The map is calculated by applying a BINARY OR operation on each effect.<br>
	 * <B><U>Example of use </U>:</B>
	 * <ul>
	 * <li>Server Packet : CharInfo, UserInfo...</li>
	 * </ul>
	 * @return a map of 32 bits (0x00000000) containing all special effect in progress for this L2Character.
	 */
	public int getSpecialEffect()
	{
		int se = _SpecialEffects;
		if (isFlying() && isStunned())
		{
			se |= AbnormalEffect.S_AIR_STUN.getMask();
		}
		if (isFlying() && isRooted())
		{
			se |= AbnormalEffect.S_AIR_ROOT.getMask();
		}
		return se;
	}
	
	/**
	 * Return all active skills effects in progress on the L2Character.<br>
	 * <B><U>Concept</U>:</B><br>
	 * All active skills effects in progress on the L2Character are identified in <B>_effects</B>.<br>
	 * The Integer key of _effects is the L2Skill Identifier that has created the effect.
	 * @return A table containing all active skills effect in progress on the L2Character
	 */
	public final L2Effect[] getAllEffects()
	{
		return _effects.getAllEffects();
	}
	
	/**
	 * Return L2Effect in progress on the L2Character corresponding to the L2Skill Identifier.<br>
	 * <B><U>Concept</U>:</B><br>
	 * All active skills effects in progress on the L2Character are identified in <B>_effects</B>.
	 * @param skillId The L2Skill Identifier of the L2Effect to return from the _effects
	 * @return The L2Effect corresponding to the L2Skill Identifier
	 */
	public final L2Effect getFirstEffect(int skillId)
	{
		return _effects.getFirstEffect(skillId);
	}
	
	/**
	 * Return the first L2Effect in progress on the L2Character created by the L2Skill.<br>
	 * <B><U>Concept</U>:</B><br>
	 * All active skills effects in progress on the L2Character are identified in <B>_effects</B>.
	 * @param skill The L2Skill whose effect must be returned
	 * @return The first L2Effect created by the L2Skill
	 */
	public final L2Effect getFirstEffect(L2Skill skill)
	{
		return _effects.getFirstEffect(skill);
	}
	
	/**
	 * Return the first L2Effect in progress on the L2Character corresponding to the Effect Type (ex : BUFF, STUN, ROOT...).<br>
	 * <B><U>Concept</U>:</B><br>
	 * All active skills effects in progress on the L2Character are identified in <B>_effects</B>.
	 * @param tp The Effect Type of skills whose effect must be returned
	 * @return The first L2Effect corresponding to the Effect Type
	 */
	public final L2Effect getFirstEffect(L2EffectType tp)
	{
		return _effects.getFirstEffect(tp);
	}
	
	/**
	 * This class group all movement data.<br>
	 * <B><U> Data</U> :</B>
	 * <ul>
	 * <li>_moveTimestamp : Last time position update</li>
	 * <li>_xDestination, _yDestination, _zDestination : Position of the destination</li>
	 * <li>_xMoveFrom, _yMoveFrom, _zMoveFrom : Position of the origin</li>
	 * <li>_moveStartTime : Start time of the movement</li>
	 * <li>_ticksToMove : Nb of ticks between the start and the destination</li>
	 * <li>_xSpeedTicks, _ySpeedTicks : Speed in unit/ticks</li>
	 * </ul>
	 */
	public static class MoveData
	{
		// when we retrieve x/y/z we use GameTimeControl.getGameTicks()
		// if we are moving, but move timestamp==gameticks, we don't need
		// to recalculate position
		public int _moveStartTime;
		public int _moveTimestamp; // last update
		public int _xDestination;
		public int _yDestination;
		public int _zDestination;
		public double _xAccurate; // otherwise there would be rounding errors
		public double _yAccurate;
		public double _zAccurate;
		public int _heading;
		
		public boolean disregardingGeodata;
		public int onGeodataPathIndex;
		public List<AbstractNodeLoc> geoPath;
		public int geoPathAccurateTx;
		public int geoPathAccurateTy;
		public int geoPathGtx;
		public int geoPathGty;
	}
	
	/**
	 * Add a Func to the Calculator set of the L2Character. <B><U> Concept</U> :</B> A L2Character owns a table of Calculators called <B>_calculators</B>. Each Calculator (a calculator per state) own a table of Func object. A Func object is a mathematic function that permit to calculate the modifier
	 * of a state (ex : REGENERATE_HP_RATE...). To reduce cache memory use, L2NPCInstances who don't have skills share the same Calculator set called <B>NPC_STD_CALCULATOR</B>. That's why, if a L2NPCInstance is under a skill/spell effect that modify one of its state, a copy of the NPC_STD_CALCULATOR
	 * must be create in its _calculators before addind new Func object. <B><U> Actions</U> :</B>
	 * <li>If _calculators is linked to NPC_STD_CALCULATOR, create a copy of NPC_STD_CALCULATOR in _calculators</li>
	 * <li>Add the Func object to _calculators</li>
	 * @param f The Func object to add to the Calculator corresponding to the state affected
	 */
	public final void addStatFunc(AbstractFunction f)
	{
		if (f == null)
		{
			return;
		}
		
		synchronized (this)
		{
			// Check if Calculator set is linked to the standard Calculator set of NPC
			if (_calculators == NPC_STD_CALCULATOR)
			{
				// Create a copy of the standard NPC Calculator set
				_calculators = new Calculator[Stats.NUM_STATS];
				
				for (int i = 0; i < Stats.NUM_STATS; i++)
				{
					if (NPC_STD_CALCULATOR[i] != null)
					{
						_calculators[i] = new Calculator(NPC_STD_CALCULATOR[i]);
					}
				}
			}
			
			// Select the Calculator of the affected state in the Calculator set
			int stat = f.getStat().ordinal();
			
			if (_calculators[stat] == null)
			{
				_calculators[stat] = new Calculator();
			}
			
			// Add the Func to the calculator corresponding to the state
			_calculators[stat].addFunc(f);
		}
	}
	
	/**
	 * Add a list of Funcs to the Calculator set of the L2Character.<br>
	 * <B><U>Concept</U>:</B><br>
	 * A L2Character owns a table of Calculators called <B>_calculators</B>.<br>
	 * Each Calculator (a calculator per state) own a table of Func object.<br>
	 * A Func object is a mathematic function that permit to calculate the modifier of a state (ex : REGENERATE_HP_RATE...).<br>
	 * <FONT COLOR=#FF0000><B> <U>Caution</U> : This method is ONLY for L2PcInstance</B></FONT><br>
	 * <B><U>Example of use</U>:</B>
	 * <ul>
	 * <li>Equip an item from inventory</li>
	 * <li>Learn a new passive skill</li>
	 * <li>Use an active skill</li>
	 * </ul>
	 * @param funcs The list of Func objects to add to the Calculator corresponding to the state affected
	 */
	public final void addStatFuncs(AbstractFunction[] funcs)
	{
		if (!isPlayer() && getKnownList().getKnownPlayers().isEmpty())
		{
			for (AbstractFunction f : funcs)
			{
				addStatFunc(f);
			}
		}
		else
		{
			final List<Stats> modifiedStats = new ArrayList<>();
			for (AbstractFunction f : funcs)
			{
				modifiedStats.add(f.getStat());
				addStatFunc(f);
			}
			broadcastModifiedStats(modifiedStats);
		}
	}
	
	/**
	 * Remove a Func from the Calculator set of the L2Character.<br>
	 * <B><U>Concept</U>:</B><br>
	 * A L2Character owns a table of Calculators called <B>_calculators</B>.<br>
	 * Each Calculator (a calculator per state) own a table of Func object.<br>
	 * A Func object is a mathematic function that permit to calculate the modifier of a state (ex : REGENERATE_HP_RATE...).<br>
	 * To reduce cache memory use, L2NPCInstances who don't have skills share the same Calculator set called <B>NPC_STD_CALCULATOR</B>.<br>
	 * That's why, if a L2NPCInstance is under a skill/spell effect that modify one of its state, a copy of the NPC_STD_CALCULATOR must be create in its _calculators before addind new Func object.<br>
	 * <B><U>Actions</U>:</B>
	 * <ul>
	 * <li>Remove the Func object from _calculators</li>
	 * <li>If L2Character is a L2NPCInstance and _calculators is equal to NPC_STD_CALCULATOR, free cache memory and just create a link on NPC_STD_CALCULATOR in _calculators</li>
	 * </ul>
	 * @param f The Func object to remove from the Calculator corresponding to the state affected
	 */
	public final void removeStatFunc(AbstractFunction f)
	{
		if (f == null)
		{
			return;
		}
		
		// Select the Calculator of the affected state in the Calculator set
		int stat = f.getStat().ordinal();
		
		synchronized (this)
		{
			if (_calculators[stat] == null)
			{
				return;
			}
			
			// Remove the Func object from the Calculator
			_calculators[stat].removeFunc(f);
			
			if (_calculators[stat].size() == 0)
			{
				_calculators[stat] = null;
			}
			
			// If possible, free the memory and just create a link on NPC_STD_CALCULATOR
			if (isNpc())
			{
				int i = 0;
				for (; i < Stats.NUM_STATS; i++)
				{
					if (!Calculator.equalsCals(_calculators[i], NPC_STD_CALCULATOR[i]))
					{
						break;
					}
				}
				
				if (i >= Stats.NUM_STATS)
				{
					_calculators = NPC_STD_CALCULATOR;
				}
			}
		}
	}
	
	/**
	 * Remove a list of Funcs from the Calculator set of the L2PcInstance.<br>
	 * <B><U>Concept</U>:</B><br>
	 * A L2Character owns a table of Calculators called <B>_calculators</B>.<br>
	 * Each Calculator (a calculator per state) own a table of Func object.<br>
	 * A Func object is a mathematic function that permit to calculate the modifier of a state (ex : REGENERATE_HP_RATE...).<br>
	 * <FONT COLOR=#FF0000><B> <U>Caution</U> : This method is ONLY for L2PcInstance</B></FONT><br>
	 * <B><U>Example of use</U>:</B>
	 * <ul>
	 * <li>Unequip an item from inventory</li>
	 * <li>Stop an active skill</li>
	 * </ul>
	 * @param funcs The list of Func objects to add to the Calculator corresponding to the state affected
	 */
	public final void removeStatFuncs(AbstractFunction[] funcs)
	{
		if (!isPlayer() && getKnownList().getKnownPlayers().isEmpty())
		{
			for (AbstractFunction f : funcs)
			{
				removeStatFunc(f);
			}
		}
		else
		{
			final List<Stats> modifiedStats = new ArrayList<>();
			for (AbstractFunction f : funcs)
			{
				modifiedStats.add(f.getStat());
				removeStatFunc(f);
			}
			
			broadcastModifiedStats(modifiedStats);
		}
	}
	
	/**
	 * Remove all Func objects with the selected owner from the Calculator set of the L2Character.<br>
	 * <B><U>Concept</U>:</B><br>
	 * A L2Character owns a table of Calculators called <B>_calculators</B>.<br>
	 * Each Calculator (a calculator per state) own a table of Func object.<br>
	 * A Func object is a mathematic function that permit to calculate the modifier of a state (ex : REGENERATE_HP_RATE...).<br>
	 * To reduce cache memory use, L2NPCInstances who don't have skills share the same Calculator set called <B>NPC_STD_CALCULATOR</B>.<br>
	 * That's why, if a L2NPCInstance is under a skill/spell effect that modify one of its state, a copy of the NPC_STD_CALCULATOR must be create in its _calculators before addind new Func object.<br>
	 * <B><U>Actions</U>:</B>
	 * <ul>
	 * <li>Remove all Func objects of the selected owner from _calculators</li>
	 * <li>If L2Character is a L2NPCInstance and _calculators is equal to NPC_STD_CALCULATOR, free cache memory and just create a link on NPC_STD_CALCULATOR in _calculators</li>
	 * </ul>
	 * <B><U>Example of use</U>:</B>
	 * <ul>
	 * <li>Unequip an item from inventory</li>
	 * <li>Stop an active skill</li>
	 * </ul>
	 * @param owner The Object(Skill, Item...) that has created the effect
	 */
	public final void removeStatsOwner(Object owner)
	{
		List<Stats> modifiedStats = null;
		int i = 0;
		// Go through the Calculator set
		synchronized (_calculators)
		{
			for (Calculator calc : _calculators)
			{
				if (calc != null)
				{
					// Delete all Func objects of the selected owner
					if (modifiedStats != null)
					{
						modifiedStats.addAll(calc.removeOwner(owner));
					}
					else
					{
						modifiedStats = calc.removeOwner(owner);
					}
					
					if (calc.size() == 0)
					{
						_calculators[i] = null;
					}
				}
				i++;
			}
			
			// If possible, free the memory and just create a link on NPC_STD_CALCULATOR
			if (isNpc())
			{
				i = 0;
				for (; i < Stats.NUM_STATS; i++)
				{
					if (!Calculator.equalsCals(_calculators[i], NPC_STD_CALCULATOR[i]))
					{
						break;
					}
				}
				
				if (i >= Stats.NUM_STATS)
				{
					_calculators = NPC_STD_CALCULATOR;
				}
			}
			
			if (owner instanceof L2Effect)
			{
				if (!((L2Effect) owner).preventExitUpdate)
				{
					broadcastModifiedStats(modifiedStats);
				}
			}
			else
			{
				broadcastModifiedStats(modifiedStats);
			}
		}
	}
	
	protected void broadcastModifiedStats(List<Stats> stats)
	{
		if ((stats == null) || stats.isEmpty())
		{
			return;
		}
		
		if (isSummon())
		{
			L2Summon summon = (L2Summon) this;
			if (summon.getOwner() != null)
			{
				summon.updateAndBroadcastStatus(BroadcastStatusType.DEFAULT);
			}
		}
		else
		{
			boolean broadcastFull = false;
			StatusUpdate su = new StatusUpdate(this);
			
			for (Stats stat : stats)
			{
				if (stat == Stats.POWER_ATTACK_SPEED)
				{
					su.addAttribute(StatusUpdate.ATK_SPD, (int) getPAtkSpd());
				}
				else if (stat == Stats.MAGIC_ATTACK_SPEED)
				{
					su.addAttribute(StatusUpdate.CAST_SPD, getMAtkSpd());
				}
				else if (stat == Stats.MOVE_SPEED)
				{
					broadcastFull = true;
				}
			}
			
			if (isPlayer())
			{
				startUpdate(su, broadcastFull);
				if ((getSummon() != null) && isAffected(EffectFlag.SERVITOR_SHARE))
				{
					getSummon().broadcastStatusUpdate();
				}
			}
			else if (isNpc())
			{
				if (broadcastFull)
				{
					Collection<L2PcInstance> plrs = getKnownList().getKnownPlayers().values();
					for (L2PcInstance player : plrs)
					{
						if ((player == null) || !isVisibleFor(player))
						{
							continue;
						}
						if (getRunSpeed() == 0)
						{
							player.sendPacket(new ServerObjectInfo((L2Npc) this, player));
						}
						else
						{
							((L2Npc) this).sendInfo(player);
						}
					}
				}
				else if (su.hasAttributes())
				{
					broadcastPacket(su);
				}
			}
			else if (su.hasAttributes())
			{
				broadcastPacket(su);
			}
		}
	}
	
	public final int getXdestination()
	{
		MoveData m = _move;
		
		if (m != null)
		{
			return m._xDestination;
		}
		
		return getX();
	}
	
	/**
	 * @return the Y destination of the L2Character or the Y position if not in movement.
	 */
	public final int getYdestination()
	{
		MoveData m = _move;
		
		if (m != null)
		{
			return m._yDestination;
		}
		
		return getY();
	}
	
	/**
	 * @return the Z destination of the L2Character or the Z position if not in movement.
	 */
	public final int getZdestination()
	{
		MoveData m = _move;
		
		if (m != null)
		{
			return m._zDestination;
		}
		
		return getZ();
	}
	
	/**
	 * @return True if the L2Character is in combat.
	 */
	public boolean isInCombat()
	{
		return hasAI() && ((getAI().getAttackTarget() != null) || getAI().isAutoAttacking());
	}
	
	/**
	 * @return True if the L2Character is moving.
	 */
	public final boolean isMoving()
	{
		return _move != null;
	}
	
	/**
	 * @return True if the L2Character is travelling a calculated path.
	 */
	public final boolean isOnGeodataPath()
	{
		MoveData m = _move;
		if (m == null)
		{
			return false;
		}
		if (m.onGeodataPathIndex == -1)
		{
			return false;
		}
		if (m.onGeodataPathIndex == (m.geoPath.size() - 1))
		{
			return false;
		}
		return true;
	}
	
	/**
	 * @return True if the L2Character is casting.
	 */
	public final boolean isCastingNow()
	{
		return _isCastingNow;
	}
	
	public void setIsCastingNow(boolean value)
	{
		_isCastingNow = value;
	}
	
	public final boolean isCastingSimultaneouslyNow()
	{
		return _isCastingSimultaneouslyNow;
	}
	
	public void setIsCastingSimultaneouslyNow(boolean value)
	{
		_isCastingSimultaneouslyNow = value;
	}
	
	/**
	 * @return True if the cast of the L2Character can be aborted.
	 */
	public final boolean canAbortCast()
	{
		return _castInterruptTime > GameTimeController.getInstance().getGameTicks();
	}
	
	public int getCastInterruptTime()
	{
		return _castInterruptTime;
	}
	
	/**
	 * Verifies if the creature is attacking now.
	 * @return {@code true} if the creature is attacking now, {@code false} otherwise
	 */
	public final boolean isAttackingNow()
	{
		return _attackEndTime > System.nanoTime();
	}
	
	/**
	 * Abort the attack of the L2Character and send Server->Client ActionFailed packet.
	 */
	public final void abortAttack()
	{
		if (isAttackingNow())
		{
			sendPacket(ActionFailed.STATIC_PACKET);
		}
	}
	
	/**
	 * Abort the cast of the L2Character and send Server->Client MagicSkillCanceld/ActionFailed packet.
	 */
	public final void abortCast()
	{
		if (isCastingNow() || isCastingSimultaneouslyNow())
		{
			Future<?> future = _skillCast;
			// cancels the skill hit scheduled task
			if (future != null)
			{
				future.cancel(true);
				_skillCast = null;
			}
			future = _skillCast2;
			if (future != null)
			{
				future.cancel(true);
				_skillCast2 = null;
			}
			
			if (getFusionSkill() != null)
			{
				getFusionSkill().onCastAbort();
			}
			
			L2Effect mog = getFirstEffect(L2EffectType.SIGNET_GROUND);
			if (mog != null)
			{
				mog.exit();
			}
			
			if (_allSkillsDisabled)
			{
				enableAllSkills(); // this remains for forced skill use, e.g. scroll of escape
			}
			setIsCastingNow(false);
			setIsCastingSimultaneouslyNow(false);
			// safeguard for cannot be interrupt any more
			_castInterruptTime = 0;
			finishFly();
			
			if (isPlayer())
			{
				getAI().notifyEvent(CtrlEvent.EVT_FINISH_CASTING); // setting back previous intention
			}
			broadcastPacket(new MagicSkillCanceld(getObjectId())); // broadcast packet to stop animations client-side
			sendPacket(ActionFailed.STATIC_PACKET); // send an "action failed" packet to the caster
		}
	}
	
	/**
	 * Update the position of the L2Character during a movement and return True if the movement is finished.<br>
	 * <B><U>Concept</U>:</B><br>
	 * At the beginning of the move action, all properties of the movement are stored in the MoveData object called <B>_move</B> of the L2Character.<br>
	 * The position of the start point and of the destination permit to estimated in function of the movement speed the time to achieve the destination.<br>
	 * When the movement is started (ex : by MovetoLocation), this method will be called each 0.1 sec to estimate and update the L2Character position on the server.<br>
	 * Note, that the current server position can differe from the current client position even if each movement is straight foward.<br>
	 * That's why, client send regularly a Client->Server ValidatePosition packet to eventually correct the gap on the server.<br>
	 * But, it's always the server position that is used in range calculation. At the end of the estimated movement time,<br>
	 * the L2Character position is automatically set to the destination position even if the movement is not finished.<br>
	 * <FONT COLOR=#FF0000><B><U>Caution</U>: The current Z position is obtained FROM THE CLIENT by the Client->Server ValidatePosition Packet.<br>
	 * But x and y positions must be calculated to avoid that players try to modify their movement speed.</B></FONT>
	 * @return True if the movement is finished
	 */
	public boolean updatePosition()
	{
		// Get movement data
		MoveData m = _move;
		
		if (m == null)
		{
			return true;
		}
		
		if (!isVisible())
		{
			_move = null;
			return true;
		}
		
		// Check if this is the first update
		if (m._moveTimestamp == 0)
		{
			m._moveTimestamp = m._moveStartTime;
			m._xAccurate = getX();
			m._yAccurate = getY();
		}
		
		int gameTicks = GameTimeController.getInstance().getGameTicks();
		
		// Check if the position has already been calculated
		if (m._moveTimestamp == gameTicks)
		{
			return false;
		}
		
		int xPrev = getX();
		int yPrev = getY();
		int zPrev = getZ(); // the z coordinate may be modified by coordinate synchronizations
		
		double dx, dy, dz;
		if (Config.COORD_SYNCHRONIZE == 1)
		// the only method that can modify x,y while moving (otherwise _move would/should be set null)
		{
			dx = m._xDestination - xPrev;
			dy = m._yDestination - yPrev;
		}
		else
		// otherwise we need saved temporary values to avoid rounding errors
		{
			dx = m._xDestination - m._xAccurate;
			dy = m._yDestination - m._yAccurate;
		}
		
		final boolean isFloating = isFlying() || isInsideZone(ZoneIdType.WATER);
		
		// Z coordinate will follow geodata or client values
		if ((Config.COORD_SYNCHRONIZE == 2) && !isFloating && !m.disregardingGeodata && ((GameTimeController.getInstance().getGameTicks() % 10) == 0 // once a second to reduce possible cpu load
		) && GeoData.getInstance().hasGeo(xPrev, yPrev))
		{
			int geoHeight = GeoData.getInstance().getSpawnHeight(xPrev, yPrev, zPrev);
			dz = m._zDestination - geoHeight;
			// quite a big difference, compare to validatePosition packet
			if (isPlayer() && (Math.abs(getActingPlayer().getClientZ() - geoHeight) > 200) && (Math.abs(getActingPlayer().getClientZ() - geoHeight) < 1500))
			{
				dz = m._zDestination - zPrev; // allow diff
			}
			else if (isInCombat() && (Math.abs(dz) > 200) && (((dx * dx) + (dy * dy)) < 40000)) // allow mob to climb up to pcinstance
			{
				dz = m._zDestination - zPrev; // climbing
			}
			zPrev = geoHeight;
		}
		else
		{
			dz = m._zDestination - zPrev;
		}
		
		double delta = (dx * dx) + (dy * dy);
		if ((delta < 10000) && ((dz * dz) > 2500) // close enough, allows error between client and server geodata if it cannot be avoided
		&& !isFloating)
		{
			delta = Math.sqrt(delta);
		}
		else
		{
			delta = Math.sqrt(delta + (dz * dz));
		}
		
		double distFraction = Double.MAX_VALUE;
		if (delta > 1)
		{
			final double distPassed = (getMoveSpeed() * (gameTicks - m._moveTimestamp)) / GameTimeController.TICKS_PER_SECOND;
			distFraction = distPassed / delta;
		}
		
		// if (Config.DEVELOPER) _log.warn("Move Ticks:" + (gameTicks - m._moveTimestamp) + ", distPassed:" + distPassed + ", distFraction:" + distFraction);
		
		if (distFraction > 1)
		{
			// Set the position of the L2Character to the destination
			super.setXYZ(m._xDestination, m._yDestination, m._zDestination);
		}
		else
		{
			m._xAccurate += dx * distFraction;
			m._yAccurate += dy * distFraction;
			
			// Set the position of the L2Character to estimated after parcial move
			super.setXYZ((int) (m._xAccurate), (int) (m._yAccurate), zPrev + (int) ((dz * distFraction) + 0.5));
		}
		revalidateZone(false);
		
		// Set the timer of last position update to now
		m._moveTimestamp = gameTicks;
		
		if (distFraction > 1)
		{
			ThreadPoolManager.getInstance().executeAi(() ->
			{
				if (Config.MOVE_BASED_KNOWNLIST)
				{
					getKnownList().findObjects();
				}
				
				getAI().notifyEvent(CtrlEvent.EVT_ARRIVED);
				
				// vGodFather: movement tweaks
				if (isOnGeodataPath() && isPlayer())
				{
					broadcastPacket(new ValidateLocation(this));
				}
			});
			
			return true;
		}
		
		return false;
	}
	
	public void revalidateZone(boolean force)
	{
		if (getWorldRegion() == null)
		{
			return;
		}
		
		// This function is called too often from movement code
		if (force)
		{
			_zoneValidateCounter = 4;
		}
		else
		{
			_zoneValidateCounter--;
			if (_zoneValidateCounter < 0)
			{
				_zoneValidateCounter = 4;
			}
			else
			{
				return;
			}
		}
		
		getWorldRegion().revalidateZones(this);
	}
	
	/**
	 * Stop movement of the L2Character (Called by AI Accessor only).<br>
	 * <B><U>Actions</U>:</B>
	 * <ul>
	 * <li>Delete movement data of the L2Character</li>
	 * <li>Set the current position (x,y,z), its current L2WorldRegion if necessary and its heading</li>
	 * <li>Remove the L2Object object from _gmList of GmListTable</li>
	 * <li>Remove object from _knownObjects and _knownPlayer of all surrounding L2WorldRegion L2Characters</li>
	 * </ul>
	 * <FONT COLOR=#FF0000><B><U>Caution</U>: This method DOESN'T send Server->Client packet StopMove/StopRotation</B></FONT>
	 * @param loc
	 */
	public void stopMove(Location loc)
	{
		stopMove(loc, false);
	}
	
	public void stopMove(Location loc, boolean updateKnownObjects)
	{
		// Delete movement data of the L2Character
		_move = null;
		
		// if (getAI() != null)
		// getAI().setIntention(CtrlIntention.AI_INTENTION_IDLE);
		
		// Set the current position (x,y,z), its current L2WorldRegion if necessary and its heading
		// All data are contained in a Location object
		if (loc != null)
		{
			setXYZ(loc.getX(), loc.getY(), loc.getZ());
			setHeading(loc.getHeading());
			revalidateZone(true);
		}
		broadcastPacket(new StopMove(this));
		if (Config.MOVE_BASED_KNOWNLIST && updateKnownObjects)
		{
			getKnownList().findObjects();
		}
	}
	
	/**
	 * @return Returns the showSummonAnimation.
	 */
	public boolean isShowSummonAnimation()
	{
		return _showSummonAnimation;
	}
	
	/**
	 * @param showSummonAnimation The showSummonAnimation to set.
	 */
	public void setShowSummonAnimation(boolean showSummonAnimation)
	{
		_showSummonAnimation = showSummonAnimation;
	}
	
	/**
	 * Target a L2Object (add the target to the L2Character _target, _knownObject and L2Character to _KnownObject of the L2Object).<br>
	 * <B><U>Concept</U>:</B><br>
	 * The L2Object (including L2Character) targeted is identified in <B>_target</B> of the L2Character.<br>
	 * <B><U>Actions</U>:</B>
	 * <ul>
	 * <li>Set the _target of L2Character to L2Object</li>
	 * <li>If necessary, add L2Object to _knownObject of the L2Character</li>
	 * <li>If necessary, add L2Character to _KnownObject of the L2Object</li>
	 * <li>If object==null, cancel Attak or Cast</li>
	 * </ul>
	 * @param object L2object to target
	 */
	public void setTarget(L2Object object)
	{
		if ((object != null) && !object.isVisible())
		{
			object = null;
		}
		
		if ((object != null) && (object != _target))
		{
			getKnownList().addKnownObject(object);
			object.getKnownList().addKnownObject(this);
		}
		
		_target = object;
	}
	
	/**
	 * @return the identifier of the L2Object targeted or -1.
	 */
	public final int getTargetId()
	{
		if (_target != null)
		{
			return _target.getObjectId();
		}
		return -1;
	}
	
	/**
	 * @return the L2Object targeted or null.
	 */
	public final L2Object getTarget()
	{
		return _target;
	}
	
	/**
	 * Calculate movement data for a move to location action and add the L2Character to movingObjects of GameTimeController (only called by AI Accessor).<br>
	 * <B><U>Concept</U>:</B><br>
	 * At the beginning of the move action, all properties of the movement are stored in the MoveData object called <B>_move</B> of the L2Character.<br>
	 * The position of the start point and of the destination permit to estimated in function of the movement speed the time to achieve the destination.<br>
	 * All L2Character in movement are identified in <B>movingObjects</B> of GameTimeController that will call the updatePosition method of those L2Character each 0.1s.<br>
	 * <B><U>Actions</U>:</B>
	 * <ul>
	 * <li>Get current position of the L2Character</li>
	 * <li>Calculate distance (dx,dy) between current position and destination including offset</li>
	 * <li>Create and Init a MoveData object</li>
	 * <li>Set the L2Character _move object to MoveData object</li>
	 * <li>Add the L2Character to movingObjects of the GameTimeController</li>
	 * <li>Create a task to notify the AI that L2Character arrives at a check point of the movement</li>
	 * </ul>
	 * <FONT COLOR=#FF0000><B><U>Caution</U>: This method DOESN'T send Server->Client packet MoveToPawn/CharMoveToLocation.</B></FONT><br>
	 * <B><U>Example of use</U>:</B>
	 * <ul>
	 * <li>AI : onIntentionMoveTo(L2CharPosition), onIntentionPickUp(L2Object), onIntentionInteract(L2Object)</li>
	 * <li>FollowTask</li>
	 * </ul>
	 * @param x The X position of the destination
	 * @param y The Y position of the destination
	 * @param z The Y position of the destination
	 * @param offset The size of the interaction area of the L2Character targeted
	 */
	public void moveToLocation(int x, int y, int z, int offset)
	{
		// Get the Move Speed of the L2Charcater
		double speed = getMoveSpeed();
		if ((speed <= 0) || isMovementDisabled())
		{
			return;
		}
		
		// Get current position of the L2Character
		final int curX = super.getX();
		final int curY = super.getY();
		final int curZ = super.getZ();
		
		// Calculate distance (dx,dy) between current position and destination
		// TODO: improve Z axis move/follow support when dx,dy are small compared to dz
		double dx = (x - curX);
		double dy = (y - curY);
		double dz = (z - curZ);
		double distance = Math.sqrt((dx * dx) + (dy * dy));
		
		final boolean verticalMovementOnly = isFlying() && (distance == 0) && (dz != 0);
		if (verticalMovementOnly)
		{
			distance = Math.abs(dz);
		}
		
		// make water move short and use no geodata checks for swimming chars
		// distance in a click can easily be over 3000
		if (isInsideZone(ZoneIdType.WATER) && (distance > 700))
		{
			double divider = 700 / distance;
			x = curX + (int) (divider * dx);
			y = curY + (int) (divider * dy);
			z = curZ + (int) (divider * dz);
			dx = (x - curX);
			dy = (y - curY);
			dz = (z - curZ);
			distance = Math.sqrt((dx * dx) + (dy * dy));
		}
		
		// @formatter:off
		// Define movement angles needed
		// ^
		// |    X (x,y)
		// |   /
		// |  / distance
		// | /
		// |/ angle
		// X ---------->
		// (curx,cury)
		// @formatter:on
		
		double cos;
		double sin;
		
		// Check if a movement offset is defined or no distance to go through
		if ((offset > 0) || (distance < 1))
		{
			// If no distance to go through, the movement is canceled
			if ((distance < 1) || ((distance - offset) <= 0))
			{
				// Notify the AI that the L2Character is arrived at destination
				getAI().notifyEvent(CtrlEvent.EVT_ARRIVED);
				return;
			}
			
			// Calculate movement angles needed
			sin = dy / distance;
			cos = dx / distance;
			
			distance -= (offset - 5); // due to rounding error, we have to move a bit closer to be in range
			
			// Calculate the new destination with offset included
			x = curX + (int) (distance * cos);
			y = curY + (int) (distance * sin);
		}
		else
		{
			// Calculate movement angles needed
			sin = dy / distance;
			cos = dx / distance;
		}
		
		// Create and Init a MoveData object
		MoveData m = new MoveData();
		
		// GEODATA MOVEMENT CHECKS AND PATHFINDING
		m.onGeodataPathIndex = -1; // Initialize not on geodata path
		m.disregardingGeodata = false;
		
		if (!isFlying() // flying chars not checked - even canSeeTarget doesn't work yet
		&& (!isInsideZone(ZoneIdType.WATER) || (isInsideZone(ZoneIdType.SIEGE) || isInsideZone(ZoneIdType.CASTLE)))) // swimming also not checked unless in siege zone - but distance is limited
		{
			final boolean isInVehicle = isPlayer() && (getActingPlayer().getVehicle() != null);
			if (isInVehicle)
			{
				m.disregardingGeodata = true;
			}
			
			double originalDistance = distance;
			int originalX = x;
			int originalY = y;
			int originalZ = z;
			int gtx = (originalX - L2World.MAP_MIN_X) >> 4;
			int gty = (originalY - L2World.MAP_MIN_Y) >> 4;
			
			// Movement checks:
			// when PATHFINDING > 0, for all characters except mobs returning home (could be changed later to teleport if pathfinding fails)
			if (((Config.PATHFINDING > 0) && (!(isAttackable() && ((L2Attackable) this).isReturningToSpawnPoint()))) //
			|| (isPlayer() && !(isInVehicle && (distance > 1500))) //
			|| (this instanceof L2RiftInvaderInstance))
			{
				if (isOnGeodataPath())
				{
					try
					{
						if ((gtx == _move.geoPathGtx) && (gty == _move.geoPathGty))
						{
							return;
						}
						_move.onGeodataPathIndex = -1; // Set not on geodata path
					}
					catch (NullPointerException e)
					{
						// nothing
					}
				}
				
				if ((curX < L2World.MAP_MIN_X) || (curX > L2World.MAP_MAX_X) || (curY < L2World.MAP_MIN_Y) || (curY > L2World.MAP_MAX_Y))
				{
					if (Config.DEBUG)
					{
						_log.warn("Character " + getName() + " outside world area, in coordinates x:" + curX + " y:" + curY);
					}
					
					getAI().setIntention(CtrlIntention.AI_INTENTION_IDLE);
					if (isPlayer())
					{
						getActingPlayer().logout();
					}
					else if (isSummon())
					{
						return; // preventation when summon get out of world coords, player will not loose him, unsummon handled from pcinstance
					}
					else if (isAttackable())
					{
						getAttackable().returnHome(true);
					}
					else
					{
						onDecay();
					}
					return;
				}
				Location destiny = GeoData.getInstance().moveCheck(curX, curY, curZ, x, y, z, getInstanceId());
				// location different if destination wasn't reached (or just z coord is different)
				x = destiny.getX();
				y = destiny.getY();
				z = destiny.getZ();
				dx = x - curX;
				dy = y - curY;
				dz = z - curZ;
				distance = verticalMovementOnly ? Math.abs(dz * dz) : Math.sqrt((dx * dx) + (dy * dy));
			}
			// Pathfinding checks. Only when geodata setting is 2, the LoS check gives shorter result
			// than the original movement was and the LoS gives a shorter distance than 2000
			// This way of detecting need for pathfinding could be changed.
			if ((Config.PATHFINDING > 0) && ((originalDistance - distance) > 30) && (distance < 2000))
			{
				// Path calculation
				// Overrides previous movement check
				if ((isPlayable() && !isInVehicle) || isMinion() || isInCombat() || isWalker())
				{
					// These intentions should not trigger find path
					if (isPlayer() && ((getAI().getIntention() == CtrlIntention.AI_INTENTION_CAST) || (getAI().getIntention() == CtrlIntention.AI_INTENTION_ATTACK) || (getAI().getIntention() == CtrlIntention.AI_INTENTION_INTERACT)))
					{
						m.geoPath = null;
					}
					else
					{
						m.geoPath = PathFinding.getInstance().findPath(curX, curY, curZ, originalX, originalY, originalZ, getInstanceId(), isPlayable());
					}
					
					if ((m.geoPath == null) || (m.geoPath.size() < 2)) // No path found
					{
						if (!GeoData.getInstance().canMove(curX, curY, curZ, x, y, z, getInstanceId()))
						{
							Location destiny = GeoData.getInstance().moveCheck(curX, curY, curZ, x, y, z, getInstanceId());
							x = destiny.getX();
							y = destiny.getY();
							z = destiny.getZ();
						}
					}
					else
					{
						m.onGeodataPathIndex = 0; // on first segment
						m.geoPathGtx = gtx;
						m.geoPathGty = gty;
						m.geoPathAccurateTx = originalX;
						m.geoPathAccurateTy = originalY;
						
						x = m.geoPath.get(m.onGeodataPathIndex).getX();
						y = m.geoPath.get(m.onGeodataPathIndex).getY();
						z = m.geoPath.get(m.onGeodataPathIndex).getZ();
						
						// check for doors in the route
						if (DoorData.getInstance().checkIfDoorsBetween(curX, curY, curZ, x, y, z, getInstanceId(), false))
						{
							m.geoPath = null;
							getAI().setIntention(CtrlIntention.AI_INTENTION_IDLE);
							return;
						}
						for (int i = 0; i < (m.geoPath.size() - 1); i++)
						{
							if (DoorData.getInstance().checkIfDoorsBetween(m.geoPath.get(i), m.geoPath.get(i + 1), getInstanceId()))
							{
								m.geoPath = null;
								getAI().setIntention(CtrlIntention.AI_INTENTION_IDLE);
								return;
							}
						}
						
						dx = x - curX;
						dy = y - curY;
						dz = z - curZ;
						distance = verticalMovementOnly ? Math.abs(dz * dz) : Math.sqrt((dx * dx) + (dy * dy));
						sin = dy / distance;
						cos = dx / distance;
					}
				}
			}
			// If no distance to go through, the movement is canceled
			if ((distance < 1) && ((Config.PATHFINDING > 0) || isPlayable() || (this instanceof L2RiftInvaderInstance) || isAfraid()))
			{
				getAI().notifyEvent(CtrlEvent.EVT_ARRIVED);
				return;
			}
		}
		
		// Apply Z distance for flying or swimming for correct timing calculations
		if ((isFlying() || isInsideZone(ZoneIdType.WATER)) && !verticalMovementOnly)
		{
			distance = Math.sqrt((distance * distance) + (dz * dz));
		}
		
		// Caclulate the Nb of ticks between the current position and the destination
		// One tick added for rounding reasons
		int ticksToMove = 1 + (int) ((GameTimeController.TICKS_PER_SECOND * distance) / speed);
		m._xDestination = x;
		m._yDestination = y;
		m._zDestination = z; // this is what was requested from client
		
		// Calculate and set the heading of the L2Character
		m._heading = 0; // initial value for coordinate sync
		// Does not broke heading on vertical movements
		if (!verticalMovementOnly)
		{
			setHeading(Util.calculateHeadingFrom(cos, sin));
		}
		
		m._moveStartTime = GameTimeController.getInstance().getGameTicks();
		
		// Set the L2Character _move object to MoveData object
		_move = m;
		
		// Add the L2Character to movingObjects of the GameTimeController
		// The GameTimeController manage objects movement
		GameTimeController.getInstance().registerMovingObject(this);
		
		// Create a task to notify the AI that L2Character arrives at a check point of the movement
		if ((ticksToMove * GameTimeController.MILLIS_IN_TICK) > 3000)
		{
			ThreadPoolManager.getInstance().scheduleAi(new NotifyAITask(this, CtrlEvent.EVT_ARRIVED_REVALIDATE), 2000);
		}
		
		// the CtrlEvent.EVT_ARRIVED will be sent when the character will actually arrive
		// to destination by GameTimeController
	}
	
	public boolean moveToNextRoutePoint()
	{
		if (!isOnGeodataPath())
		{
			// Cancel the move action
			_move = null;
			return false;
		}
		
		// Get the Move Speed of the L2Charcater
		double speed = getMoveSpeed();
		if ((speed <= 0) || isMovementDisabled())
		{
			// Cancel the move action
			_move = null;
			return false;
		}
		
		MoveData md = _move;
		if ((md == null) || (md.geoPath == null))
		{
			return false;
		}
		
		// Create and Init a MoveData object
		MoveData m = new MoveData();
		
		// Update MoveData object
		m.onGeodataPathIndex = md.onGeodataPathIndex + 1; // next segment
		m.geoPath = md.geoPath;
		m.geoPathGtx = md.geoPathGtx;
		m.geoPathGty = md.geoPathGty;
		m.geoPathAccurateTx = md.geoPathAccurateTx;
		m.geoPathAccurateTy = md.geoPathAccurateTy;
		
		if (md.onGeodataPathIndex == (md.geoPath.size() - 2))
		{
			m._xDestination = md.geoPathAccurateTx;
			m._yDestination = md.geoPathAccurateTy;
			m._zDestination = md.geoPath.get(m.onGeodataPathIndex).getZ();
		}
		else
		{
			m._xDestination = md.geoPath.get(m.onGeodataPathIndex).getX();
			m._yDestination = md.geoPath.get(m.onGeodataPathIndex).getY();
			m._zDestination = md.geoPath.get(m.onGeodataPathIndex).getZ();
		}
		double dx = (m._xDestination - super.getX());
		double dy = (m._yDestination - super.getY());
		double distance = Math.sqrt((dx * dx) + (dy * dy));
		// Calculate and set the heading of the L2Character
		if (distance != 0)
		{
			setHeading(Util.calculateHeadingFrom(getX(), getY(), m._xDestination, m._yDestination));
		}
		
		// Caclulate the Nb of ticks between the current position and the destination
		// One tick added for rounding reasons
		int ticksToMove = 1 + (int) ((GameTimeController.TICKS_PER_SECOND * distance) / speed);
		
		m._heading = 0; // initial value for coordinate sync
		
		m._moveStartTime = GameTimeController.getInstance().getGameTicks();
		
		// Set the L2Character _move object to MoveData object
		_move = m;
		
		// Add the L2Character to movingObjects of the GameTimeController
		// The GameTimeController manage objects movement
		GameTimeController.getInstance().registerMovingObject(this);
		
		// Create a task to notify the AI that L2Character arrives at a check point of the movement
		if ((ticksToMove * GameTimeController.MILLIS_IN_TICK) > 3000)
		{
			ThreadPoolManager.getInstance().scheduleAi(new NotifyAITask(this, CtrlEvent.EVT_ARRIVED_REVALIDATE), 2000);
		}
		
		// the CtrlEvent.EVT_ARRIVED will be sent when the character will actually arrive
		// to destination by GameTimeController
		
		// Send a Server->Client packet CharMoveToLocation to the actor and all L2PcInstance in its _knownPlayers
		broadcastPacket(new MoveToLocation(this));
		
		if (isNpc())
		{
			broadcastPacket(new ValidateLocation(this));
		}
		
		return true;
	}
	
	public boolean validateMovementHeading(int heading)
	{
		MoveData m = _move;
		
		if (m == null)
		{
			return true;
		}
		
		boolean result = true;
		if (m._heading != heading)
		{
			result = (m._heading == 0); // initial value or false
			m._heading = heading;
		}
		
		return result;
	}
	
	/**
	 * Return the distance between the current position of the L2Character and the target (x,y).
	 * @param x X position of the target
	 * @param y Y position of the target
	 * @return the plan distance
	 * @deprecated use getPlanDistanceSq(int x, int y, int z)
	 */
	@Deprecated
	public final double getDistance(int x, int y)
	{
		double dx = x - getX();
		double dy = y - getY();
		
		return Math.sqrt((dx * dx) + (dy * dy));
	}
	
	/**
	 * Return the distance between the current position of the L2Character and the target (x,y).
	 * @param x X position of the target
	 * @param y Y position of the target
	 * @param z
	 * @return the plan distance
	 * @deprecated use getPlanDistanceSq(int x, int y, int z)
	 */
	@Deprecated
	public final double getDistance(int x, int y, int z)
	{
		double dx = x - getX();
		double dy = y - getY();
		double dz = z - getZ();
		
		return Math.sqrt((dx * dx) + (dy * dy) + (dz * dz));
	}
	
	/**
	 * Return the squared distance between the current position of the L2Character and the given object.
	 * @param object L2Object
	 * @return the squared distance
	 */
	public final double getDistanceSq(L2Object object)
	{
		return getDistanceSq(object.getX(), object.getY(), object.getZ());
	}
	
	/**
	 * Return the squared distance between the current position of the L2Character and the given x, y, z.
	 * @param x X position of the target
	 * @param y Y position of the target
	 * @param z Z position of the target
	 * @return the squared distance
	 */
	public final double getDistanceSq(int x, int y, int z)
	{
		double dx = x - getX();
		double dy = y - getY();
		double dz = z - getZ();
		
		return ((dx * dx) + (dy * dy) + (dz * dz));
	}
	
	/**
	 * Return the squared plan distance between the current position of the L2Character and the given object.<BR>
	 * (check only x and y, not z)
	 * @param object L2Object
	 * @return the squared plan distance
	 */
	public final double getPlanDistanceSq(L2Object object)
	{
		return getPlanDistanceSq(object.getX(), object.getY());
	}
	
	/**
	 * Return the squared plan distance between the current position of the L2Character and the given x, y, z.<BR>
	 * (check only x and y, not z)
	 * @param x X position of the target
	 * @param y Y position of the target
	 * @return the squared plan distance
	 */
	public final double getPlanDistanceSq(int x, int y)
	{
		double dx = x - getX();
		double dy = y - getY();
		
		return ((dx * dx) + (dy * dy));
	}
	
	/**
	 * Check if this object is inside the given radius around the given point.
	 * @param loc Location of the target
	 * @param radius the radius around the target
	 * @param checkZAxis should we check Z axis also
	 * @param strictCheck true if (distance < radius), false if (distance <= radius)
	 * @return true if the L2Character is inside the radius.
	 */
	public final boolean isInsideRadius(ILocational loc, int radius, boolean checkZAxis, boolean strictCheck)
	{
		return isInsideRadius(loc.getX(), loc.getY(), loc.getZ(), radius, checkZAxis, strictCheck);
	}
	
	/**
	 * Check if this object is inside the given radius around the given point.
	 * @param x X position of the target
	 * @param y Y position of the target
	 * @param z Z position of the target
	 * @param radius the radius around the target
	 * @param checkZAxis should we check Z axis also
	 * @param strictCheck true if (distance < radius), false if (distance <= radius)
	 * @return true if the L2Character is inside the radius.
	 */
	public final boolean isInsideRadius(int x, int y, int z, int radius, boolean checkZAxis, boolean strictCheck)
	{
		final double distance = calculateDistance(x, y, z, checkZAxis, true);
		return (strictCheck) ? (distance < (radius * radius)) : (distance <= (radius * radius));
	}
	
	/**
	 * <B><U> Overridden in </U> :</B>
	 * <li>L2PcInstance</li>
	 * @return True if arrows are available.
	 */
	protected boolean checkAndEquipArrows()
	{
		return true;
	}
	
	/**
	 * <B><U> Overridden in </U> :</B>
	 * <li>L2PcInstance</li>
	 * @return True if bolts are available.
	 */
	protected boolean checkAndEquipBolts()
	{
		return true;
	}
	
	/**
	 * Add Exp and Sp to the L2Character.<br>
	 * <B><U> Overridden in </U> :</B>
	 * <li>L2PcInstance</li>
	 * <li>L2PetInstance</li>
	 * @param addToExp
	 * @param addToSp
	 */
	public void addExpAndSp(long addToExp, int addToSp)
	{
		// Dummy method (overridden by players and pets)
	}
	
	/**
	 * <B><U> Overridden in </U> :</B>
	 * <li>L2PcInstance</li>
	 * @return the active weapon instance (always equiped in the right hand).
	 */
	public abstract L2ItemInstance getActiveWeaponInstance();
	
	/**
	 * <B><U> Overridden in </U> :</B>
	 * <li>L2PcInstance</li>
	 * @return the active weapon item (always equiped in the right hand).
	 */
	public abstract L2Weapon getActiveWeaponItem();
	
	/**
	 * <B><U> Overridden in </U> :</B>
	 * <li>L2PcInstance</li>
	 * @return the secondary weapon instance (always equiped in the left hand).
	 */
	public abstract L2ItemInstance getSecondaryWeaponInstance();
	
	/**
	 * <B><U> Overridden in </U> :</B>
	 * <li>L2PcInstance</li>
	 * @return the secondary {@link L2Item} item (always equiped in the left hand).
	 */
	public abstract L2Item getSecondaryWeaponItem();
	
	/**
	 * Manage hit process (called by Hit Task).<br>
	 * <B><U>Actions</U>:</B>
	 * <ul>
	 * <li>If the attacker/target is dead or use fake death, notify the AI with EVT_CANCEL and send a Server->Client packet ActionFailed (if attacker is a L2PcInstance)</li>
	 * <li>If attack isn't aborted, send a message system (critical hit, missed...) to attacker/target if they are L2PcInstance</li>
	 * <li>If attack isn't aborted and hit isn't missed, reduce HP of the target and calculate reflection damage to reduce HP of attacker if necessary</li>
	 * <li>if attack isn't aborted and hit isn't missed, manage attack or cast break of the target (calculating rate, sending message...)</li>
	 * </ul>
	 * @param target The L2Character targeted
	 * @param damage Nb of HP to reduce
	 * @param crit True if hit is critical
	 * @param miss True if hit is missed
	 * @param soulshot True if SoulShot are charged
	 * @param shld True if shield is efficient
	 */
	public void onHitTimer(L2Character target, int damage, boolean crit, boolean miss, boolean soulshot, byte shld)
	{
		// vGodFather: Deny the whole process if actor is casting or is attacking disabled.
		if (isCastingNow() || !canFinishAttack())
		{
			return;
		}
		
		// If the attacker/target is dead or use fake death, notify the AI with EVT_CANCEL
		// and send a Server->Client packet ActionFailed (if attacker is a L2PcInstance)
		if ((target == null) || target.isAlikeDead())
		{
			getAI().notifyEvent(CtrlEvent.EVT_CANCEL);
			return;
		}
		
		if ((isNpc() && target.isAlikeDead()) || target.isDead() || (!getKnownList().knowsObject(target) && !isDoor()))
		{
			// getAI().setIntention(CtrlIntention.AI_INTENTION_ACTIVE, null);
			// Some times attack is processed but target die before the hit
			// So we need to recharge shot for next attack
			rechargeShots(true, false);
			getAI().notifyEvent(CtrlEvent.EVT_CANCEL);
			sendPacket(ActionFailed.STATIC_PACKET);
			return;
		}
		
		if (miss)
		{
			// Notify target AI
			if (target.hasAI())
			{
				target.getAI().notifyEvent(CtrlEvent.EVT_EVADED, this);
			}
			
			notifyAttackAvoid(target, false);
			
			// ON_EVADED_HIT
			if (target.getChanceSkills() != null)
			{
				target.getChanceSkills().onEvadedHit(this);
			}
		}
		
		// Send message about damage/crit or miss
		sendDamageMessage(target, damage, false, crit, miss);
		
		// Check Raidboss attack
		// Character will be petrified if attacking a raid that's more
		// than 8 levels lower
		if (target.isRaid() && target.giveRaidCurse() && !Config.RAID_DISABLE_CURSE)
		{
			if (getLevel() > (target.getLevel() + 8))
			{
				L2Skill skill = CommonSkill.RAID_CURSE2.getSkill();
				
				if (skill != null)
				{
					abortAttack();
					abortCast();
					getAI().setIntention(CtrlIntention.AI_INTENTION_IDLE);
					skill.getEffects(target, this);
				}
				else
				{
					_log.warn("Skill 4515 at level 1 is missing in DP.");
				}
				
				damage = 0; // prevents messing up drop calculation
			}
		}
		
		// If L2Character target is a L2PcInstance, send a system message
		// vGodFather: auto attack must start only if hit didn't miss
		if (target.isPlayer() && !miss)
		{
			L2PcInstance enemy = target.getActingPlayer();
			enemy.getAI().clientStartAutoAttack();
		}
		
		if (!miss && (damage > 0))
		{
			L2Weapon weapon = getActiveWeaponItem();
			boolean isBow = ((weapon != null) && weapon.getItemType().isRanged());
			int reflectedDamage = 0;
			
			if (!isBow && !target.isInvul()) // Do not reflect if weapon is of type bow or target is invulnerable
			{
				// quick fix for no drop from raid if boss attack high-level char with damage reflection
				if (!target.isRaid() || (getActingPlayer() == null) || (getActingPlayer().getLevel() <= (target.getLevel() + 8)))
				{
					// vGodFather this will fix max reflect to 100%
					double reflectPercent = Math.min(target.getStat().calcStat(Stats.REFLECT_DAMAGE_PERCENT, 0, null, null), 100);
					
					if (reflectPercent > 0)
					{
						reflectedDamage = (int) ((reflectPercent / 100.) * damage);
						
						if (reflectedDamage > target.getMaxHp())
						{
							reflectedDamage = target.getMaxHp();
						}
					}
				}
			}
			
			// reduce targets HP
			target.reduceCurrentHp(damage, this, null);
			target.notifyDamageReceived(damage, this, null, crit, false, false);
			
			if (reflectedDamage > 0)
			{
				reduceCurrentHp(reflectedDamage, target, true, false, null);
				
				notifyDamageReceived(reflectedDamage, target, null, crit, false, true);
			}
			
			if (!isBow && !target.isInvul()) // Do not absorb if weapon is of type bow or target is invul
			{
				// Absorb HP from the damage inflicted
				double absorbPercent = getStat().calcStat(Stats.ABSORB_DAMAGE_PERCENT, 0, null, null);
				
				if (absorbPercent > 0)
				{
					int maxCanAbsorb = (int) (getMaxRecoverableHp() - getCurrentHp());
					int absorbDamage = (int) ((absorbPercent / 100.) * damage);
					
					if (absorbDamage > maxCanAbsorb)
					{
						absorbDamage = maxCanAbsorb; // Can't absord more than max hp
					}
					
					if (absorbDamage > 0)
					{
						setCurrentHp(getCurrentHp() + absorbDamage);
					}
				}
				
				// Absorb MP from the damage inflicted
				absorbPercent = getStat().calcStat(Stats.ABSORB_MANA_DAMAGE_PERCENT, 0, null, null);
				
				if (absorbPercent > 0)
				{
					int maxCanAbsorb = (int) (getMaxRecoverableMp() - getCurrentMp());
					int absorbDamage = (int) ((absorbPercent / 100.) * damage);
					
					if (absorbDamage > maxCanAbsorb)
					{
						absorbDamage = maxCanAbsorb; // Can't absord more than max hp
					}
					
					if (absorbDamage > 0)
					{
						setCurrentMp(getCurrentMp() + absorbDamage);
					}
				}
			}
			
			// Notify AI with EVT_ATTACKED
			if (target.hasAI())
			{
				target.getAI().notifyEvent(CtrlEvent.EVT_ATTACKED, this);
			}
			getAI().clientStartAutoAttack();
			if (isSummon())
			{
				L2PcInstance owner = ((L2Summon) this).getOwner();
				if (owner != null)
				{
					owner.getAI().clientStartAutoAttack();
				}
			}
			
			// Manage attack or cast break of the target (calculating rate, sending message...)
			if (!target.isRaid() && Formulas.calcAtkBreak(target, damage))
			{
				target.breakAttack();
				target.breakCast();
			}
			
			// Maybe launch chance skills on us
			if (_chanceSkills != null)
			{
				_chanceSkills.onHit(target, damage, false, crit);
				// Reflect triggers onHit
				if (reflectedDamage > 0)
				{
					// TODO Test vGodFather fix: this will fix wrong skills triggers from reflect damage
					// _chanceSkills.onHit(target, reflectedDamage, false, false); // test fix
					_chanceSkills.onHit(target, reflectedDamage, true, false); // original
				}
			}
			
			if (_triggerSkills != null)
			{
				for (OptionsSkillHolder holder : _triggerSkills.values())
				{
					if ((!crit && (holder.getSkillType() == OptionsSkillType.ATTACK)) || ((holder.getSkillType() == OptionsSkillType.CRITICAL) && crit))
					{
						if (Rnd.get(100) < holder.getChance())
						{
							makeTriggerCast(holder.getSkill(), target);
						}
					}
				}
			}
			
			// Launch weapon onCritical Special ability effect if available
			if (crit && (weapon != null))
			{
				weapon.castOnCriticalSkill(this, target);
			}
			
			// Maybe launch chance skills on target
			if (target.getChanceSkills() != null)
			{
				target.getChanceSkills().onHit(this, damage, true, crit);
			}
		}
		
		// vGodFather SunriseEvents
		if ((this instanceof L2EventMapGuardInstance) && (target instanceof L2PcInstance))
		{
			target.doDie(this);
		}
		
		// Recharge any active auto-soulshot tasks for current creature.
		rechargeShots(true, false);
	}
	
	/**
	 * Break an attack and send Server->Client ActionFailed packet and a System Message to the L2Character.
	 */
	public void breakAttack()
	{
		if (isAttackingNow())
		{
			// Abort the attack of the L2Character and send Server->Client ActionFailed packet
			abortAttack();
			if (isPlayer())
			{
				// Send a system message
				sendPacket(SystemMessageId.ATTACK_FAILED);
			}
		}
	}
	
	/**
	 * Break a cast and send Server->Client ActionFailed packet and a System Message to the L2Character.
	 */
	public void breakCast()
	{
		// damage can only cancel magical & static skills
		if (isCastingNow() && canAbortCast() && (getLastSkillCast() != null) && (getLastSkillCast().isMagic() || getLastSkillCast().isStatic()))
		{
			// Abort the cast of the L2Character and send Server->Client MagicSkillCanceld/ActionFailed packet.
			abortCast();
			
			if (isPlayer())
			{
				// Send a system message
				sendPacket(SystemMessageId.CASTING_INTERRUPTED);
			}
		}
	}
	
	/**
	 * Reduce the arrow number of the L2Character.<br>
	 * <B><U> Overridden in </U> :</B>
	 * <li>L2PcInstance</li>
	 * @param bolts
	 */
	protected void reduceArrowCount(boolean bolts)
	{
		// default is to do nothing
	}
	
	/**
	 * Manage Forced attack (shift + select target).<br>
	 * <B><U>Actions</U>:</B>
	 * <ul>
	 * <li>If L2Character or target is in a town area, send a system message TARGET_IN_PEACEZONE a Server->Client packet ActionFailed</li>
	 * <li>If target is confused, send a Server->Client packet ActionFailed</li>
	 * <li>If L2Character is a L2ArtefactInstance, send a Server->Client packet ActionFailed</li>
	 * <li>Send a Server->Client packet MyTargetSelected to start attack and Notify AI with AI_INTENTION_ATTACK</li>
	 * </ul>
	 * @param player The L2PcInstance to attack
	 */
	@Override
	public void onForcedAttack(L2PcInstance player)
	{
		if (isInsidePeaceZone(player))
		{
			// If L2Character or target is in a peace zone, send a system message TARGET_IN_PEACEZONE a Server->Client packet ActionFailed
			player.sendPacket(SystemMessageId.TARGET_IN_PEACEZONE);
			player.sendPacket(ActionFailed.STATIC_PACKET);
			return;
		}
		
		if (player.isInOlympiadMode() && (player.getTarget() != null) && player.getTarget().isPlayable())
		{
			L2PcInstance target = null;
			L2Object object = player.getTarget();
			if ((object != null) && object.isPlayable())
			{
				target = object.getActingPlayer();
			}
			
			if ((target == null) || (target.isInOlympiadMode() && (!player.isOlympiadStart() || (player.getOlympiadGameId() != target.getOlympiadGameId()))))
			{
				// if L2PcInstance is in Olympia and the match isn't already start, send a Server->Client packet ActionFailed
				player.sendPacket(ActionFailed.STATIC_PACKET);
				return;
			}
		}
		
		if ((player.getTarget() != null) && !player.getTarget().canBeAttacked() && !player.getAccessLevel().allowPeaceAttack())
		{
			// If target is not attackable, send a Server->Client packet ActionFailed
			player.sendPacket(ActionFailed.STATIC_PACKET);
			return;
		}
		
		if (player.isConfused())
		{
			// If target is confused, send a Server->Client packet ActionFailed
			player.sendPacket(ActionFailed.STATIC_PACKET);
			return;
		}
		
		// vGodFather in retail when trying to attack or force attack and cant see target
		// player is not looking for path just goes at wall
		if (!GeoData.getInstance().canSeeTarget(player, this))
		{
			final Location destination = GeoData.getInstance().moveCheck(player, this);
			player.getAI().setIntention(CtrlIntention.AI_INTENTION_MOVE_TO, destination);
			return;
		}
		
		if (player.getBlockCheckerArena() != -1)
		{
			player.sendPacket(ActionFailed.STATIC_PACKET);
			return;
		}
		
		// Notify AI with AI_INTENTION_ATTACK
		player.getAI().setIntention(CtrlIntention.AI_INTENTION_ATTACK, this);
	}
	
	/**
	 * @param attacker
	 * @return True if inside peace zone.
	 */
	public boolean isInsidePeaceZone(L2PcInstance attacker)
	{
		return isInsidePeaceZone(attacker, this);
	}
	
	public boolean isInsidePeaceZone(L2PcInstance attacker, L2Object target)
	{
		return (!attacker.getAccessLevel().allowPeaceAttack() && isInsidePeaceZone((L2Object) attacker, target));
	}
	
	public boolean isInsidePeaceZone(L2Object attacker, L2Object target)
	{
		if (target == null)
		{
			return false;
		}
		if (!(target.isPlayable() && attacker.isPlayable()))
		{
			return false;
		}
		if (InstanceManager.getInstance().getInstance(getInstanceId()).isPvPInstance())
		{
			return false;
		}
		
		if (TerritoryWarManager.PLAYER_WITH_WARD_CAN_BE_KILLED_IN_PEACEZONE && TerritoryWarManager.getInstance().isTWInProgress())
		{
			if (target.isPlayer() && target.getActingPlayer().isCombatFlagEquipped())
			{
				return false;
			}
		}
		
		if (Config.ALT_GAME_KARMA_PLAYER_CAN_BE_KILLED_IN_PEACEZONE)
		{
			// allows red to be attacked and red to attack flagged players
			if ((target.getActingPlayer() != null) && (target.getActingPlayer().getKarma() > 0))
			{
				return false;
			}
			if ((attacker.getActingPlayer() != null) && (attacker.getActingPlayer().getKarma() > 0) && (target.getActingPlayer() != null) && (target.getActingPlayer().getPvpFlag() > 0))
			{
				return false;
			}
		}
		
		return (target.isInsideZone(ZoneIdType.PEACE) || attacker.isInsideZone(ZoneIdType.PEACE));
	}
	
	/**
	 * @return true if this character is inside an active grid.
	 */
	public boolean isInActiveRegion()
	{
		L2WorldRegion region = getWorldRegion();
		return ((region != null) && (region.isActive()));
	}
	
	/**
	 * @return True if the L2Character has a Party in progress.
	 */
	public boolean isInParty()
	{
		return false;
	}
	
	/**
	 * @return the L2Party object of the L2Character.
	 */
	public L2Party getParty()
	{
		return null;
	}
	
	/**
	 * @return the Attack Speed of the L2Character (delay (in milliseconds) before next attack).
	 */
	public int calculateTimeBetweenAttacks()
	{
		return (int) (500000 / getPAtkSpd());
	}
	
	/**
	 * @param weapon
	 * @return the Reuse Time of Attack (used for bow delay)
	 */
	public int calculateReuseTime(final L2Weapon weapon)
	{
		if (isTransformed())
		{
			switch (getAttackType())
			{
				case BOW:
					return (int) ((1500 * 333 * getStat().getWeaponReuseModifier(null)) / getStat().getPAtkSpd());
				case CROSSBOW:
					return (int) ((1200 * 333 * getStat().getWeaponReuseModifier(null)) / getStat().getPAtkSpd());
			}
		}
		
		if ((weapon == null) || (weapon.getReuseDelay() == 0))
		{
			return 0;
		}
		
		return (int) ((weapon.getReuseDelay() * 333) / getPAtkSpd());
	}
	
	/**
	 * @return True if the L2Character use a dual weapon.
	 */
	public boolean isUsingDualWeapon()
	{
		return false;
	}
	
	/**
	 * Add a skill to the L2Character _skills and its Func objects to the calculator set of the L2Character.<br>
	 * <B><U>Concept</U>:</B><br>
	 * All skills own by a L2Character are identified in <B>_skills</B><br>
	 * <B><U>Actions</U>:</B>
	 * <ul>
	 * <li>Replace oldSkill by newSkill or Add the newSkill</li>
	 * <li>If an old skill has been replaced, remove all its Func objects of L2Character calculator set</li>
	 * <li>Add Func objects of newSkill to the calculator set of the L2Character</li>
	 * </ul>
	 * <B><U>Overridden in</U>:</B>
	 * <ul>
	 * <li>L2PcInstance : Save update in the character_skills table of the database</li>
	 * </ul>
	 * @param newSkill The L2Skill to add to the L2Character
	 * @return The L2Skill replaced or null if just added a new L2Skill
	 */
	@Override
	public L2Skill addSkill(L2Skill newSkill)
	{
		L2Skill oldSkill = null;
		if (newSkill != null)
		{
			// Replace oldSkill by newSkill or Add the newSkill
			oldSkill = _skills.put(newSkill.getId(), newSkill);
			// If an old skill has been replaced, remove all its Func objects
			if (oldSkill != null)
			{
				// if skill came with another one, we should delete the other one too.
				if ((oldSkill.triggerAnotherSkill()))
				{
					removeSkill(oldSkill.getTriggeredId(), true);
				}
				removeStatsOwner(oldSkill);
			}
			// Add Func objects of newSkill to the calculator set of the L2Character
			addStatFuncs(newSkill.getStatFuncs(null, this));
			
			if ((oldSkill != null) && (_chanceSkills != null))
			{
				removeChanceSkill(oldSkill.getId());
			}
			if (newSkill.isChance())
			{
				addChanceTrigger(newSkill);
			}
			
			// Add passive effects if there are any.
			newSkill.getEffectsPassive(this);
		}
		return oldSkill;
	}
	
	public L2Skill removeSkill(L2Skill skill, boolean cancelEffect)
	{
		return (skill != null) ? removeSkill(skill.getId(), cancelEffect) : null;
	}
	
	public L2Skill removeSkill(int skillId)
	{
		return removeSkill(skillId, true);
	}
	
	public L2Skill removeSkill(int skillId, boolean cancelEffect)
	{
		// Remove the skill from the L2Character _skills
		L2Skill oldSkill = _skills.remove(skillId);
		// Remove all its Func objects from the L2Character calculator set
		if (oldSkill != null)
		{
			// this is just a fail-safe againts buggers and gm dummies...
			if ((oldSkill.triggerAnotherSkill()) && (oldSkill.getTriggeredId() > 0))
			{
				removeSkill(oldSkill.getTriggeredId(), true);
			}
			
			// Stop casting if this skill is used right now
			if ((getLastSkillCast() != null) && isCastingNow())
			{
				if (oldSkill.getId() == getLastSkillCast().getId())
				{
					abortCast();
				}
			}
			if ((getLastSimultaneousSkillCast() != null) && isCastingSimultaneouslyNow())
			{
				if (oldSkill.getId() == getLastSimultaneousSkillCast().getId())
				{
					abortCast();
				}
			}
			
			// Remove passive effects.
			_effects.removePassiveEffects(skillId);
			
			if (cancelEffect || oldSkill.isToggle() || oldSkill.isPassive())
			{
				// for now, to support transformations, we have to let their
				// effects stay when skill is removed
				L2Effect e = getFirstEffect(oldSkill);
				if ((e == null) || (e.getEffectType() != L2EffectType.TRANSFORMATION))
				{
					removeStatsOwner(oldSkill);
					stopSkillEffects(oldSkill.getId());
				}
			}
			if (isPlayer())
			{
				// TODO: Unhardcode it!
				if ((oldSkill instanceof L2SkillSummon) && (oldSkill.getId() == 710) && hasSummon() && (getSummon().getId() == 14870))
				{
					getActingPlayer().getSummon().unSummon(getActingPlayer());
				}
			}
			
			if (oldSkill.isChance() && (_chanceSkills != null))
			{
				removeChanceSkill(oldSkill.getId());
			}
		}
		
		return oldSkill;
	}
	
	public void removeChanceSkill(int id)
	{
		if (_chanceSkills == null)
		{
			return;
		}
		synchronized (_chanceSkills)
		{
			for (IChanceSkillTrigger trigger : _chanceSkills.keySet())
			{
				if (!(trigger instanceof L2Skill))
				{
					continue;
				}
				if (((L2Skill) trigger).getId() == id)
				{
					_chanceSkills.remove(trigger);
				}
			}
		}
	}
	
	public void addChanceTrigger(IChanceSkillTrigger trigger)
	{
		if (_chanceSkills == null)
		{
			synchronized (this)
			{
				if (_chanceSkills == null)
				{
					_chanceSkills = new ChanceSkillList(this);
				}
			}
		}
		_chanceSkills.put(trigger, trigger.getTriggeredChanceCondition());
	}
	
	public void removeChanceEffect(IChanceSkillTrigger effect)
	{
		if (_chanceSkills == null)
		{
			return;
		}
		_chanceSkills.remove(effect);
	}
	
	public void onStartChanceEffect(byte element)
	{
		if (_chanceSkills == null)
		{
			return;
		}
		
		_chanceSkills.onStart(element);
	}
	
	public void onActionTimeChanceEffect(byte element)
	{
		if (_chanceSkills == null)
		{
			return;
		}
		
		_chanceSkills.onActionTime(element);
	}
	
	public void onExitChanceEffect(byte element)
	{
		if (_chanceSkills == null)
		{
			return;
		}
		
		_chanceSkills.onExit(element);
	}
	
	/**
	 * <B><U>Concept</U>:</B><br>
	 * All skills own by a L2Character are identified in <B>_skills</B> the L2Character
	 * @return all skills own by the L2Character in a table of L2Skill.
	 */
	public final Collection<L2Skill> getAllSkills()
	{
		return _skills.values();
	}
	
	/**
	 * @return the map containing this character skills.
	 */
	@Override
	public Map<Integer, L2Skill> getSkills()
	{
		return _skills;
	}
	
	public ChanceSkillList getChanceSkills()
	{
		return _chanceSkills;
	}
	
	/**
	 * Return the level of a skill owned by the L2Character.
	 * @param skillId The identifier of the L2Skill whose level must be returned
	 * @return The level of the L2Skill identified by skillId
	 */
	@Override
	public int getSkillLevel(int skillId)
	{
		final L2Skill skill = getKnownSkill(skillId);
		return (skill == null) ? -1 : skill.getLevel();
	}
	
	/**
	 * @param skillId The identifier of the L2Skill to check the knowledge
	 * @return the skill from the known skill.
	 */
	@Override
	public final L2Skill getKnownSkill(int skillId)
	{
		return _skills.get(skillId);
	}
	
	/**
	 * Return the number of buffs affecting this L2Character.
	 * @return The number of Buffs affecting this L2Character
	 */
	public int getBuffCount()
	{
		return _effects.getBuffCount();
	}
	
	public int getDanceCount()
	{
		return _effects.getDanceCount();
	}
	
	/**
	 * Manage the magic skill launching task (MP, HP, Item consummation...) and display the magic skill animation on client.<br>
	 * <B><U>Actions</U>:</B>
	 * <ul>
	 * <li>Send a Server->Client packet MagicSkillLaunched (to display magic skill animation) to all L2PcInstance of L2Charcater _knownPlayers</li>
	 * <li>Consumme MP, HP and Item if necessary</li>
	 * <li>Send a Server->Client packet StatusUpdate with MP modification to the L2PcInstance</li>
	 * <li>Launch the magic skill in order to calculate its effects</li>
	 * <li>If the skill type is PDAM, notify the AI of the target with AI_INTENTION_ATTACK</li>
	 * <li>Notify the AI of the L2Character with EVT_FINISH_CASTING</li>
	 * </ul>
	 * <FONT COLOR=#FF0000><B><U>Caution</U>: A magic skill casting MUST BE in progress</B></FONT>
	 * @param mut
	 */
	public void onMagicLaunchedTimer(MagicUseTask mut)
	{
		final L2Skill skill = mut.getSkill();
		if ((skill == null) || (mut.getTargets() == null))
		{
			abortCast();
			return;
		}
		
		// vGodFather: this will fix target checks when skill finish cast
		L2Object[] targets = isPlayer() ? skill.isAura() ? skill.getTargetList(this) : skill.isArea() ? skill.getTargetList(this, false, getAI().getCastTarget()) : mut.getTargets() : mut.getTargets();
		mut.setTargets(targets);
		
		// only AURA-type skills can be cast without target
		if ((targets.length == 0) && !skill.isAura())
		{
			abortCast();
			return;
		}
		
		// Escaping from under skill's radius and peace zone check
		int escapeRange = 0;
		if (skill.getEffectRange() > escapeRange)
		{
			escapeRange = skill.getEffectRange();
		}
		else if ((skill.getCastRange() < 0) && (skill.getAffectRange() > 80))
		{
			escapeRange = skill.getAffectRange();
		}
		
		if ((targets.length > 0) && (escapeRange > 0))
		{
			int skipRange = 0;
			int skipLOS = 0;
			int skipPeaceZone = 0;
			List<L2Character> targetList = new ArrayList<>();
			for (L2Object target : targets)
			{
				if (target.isCharacter())
				{
					if (!isInsideRadius(target.getX(), target.getY(), target.getZ(), escapeRange + getTemplate().getCollisionRadius(), true, false))
					{
						skipRange++;
						continue;
					}
					
					// Healing party members should ignore LOS.
					if (((skill.getTargetType() != L2TargetType.PARTY) || !skill.hasEffectType(L2EffectType.HEAL)) //
					&& (mut.getHitTime() > 550) && !GeoData.getInstance().canSeeTarget(this, target))
					{
						skipLOS++;
						continue;
					}
					
					if (skill.isOffensive())
					{
						if (isPlayer())
						{
							if (((L2Character) target).isInsidePeaceZone(getActingPlayer()))
							{
								skipPeaceZone++;
								continue;
							}
						}
						else
						{
							if (((L2Character) target).isInsidePeaceZone(this, target))
							{
								skipPeaceZone++;
								continue;
							}
						}
					}
					targetList.add((L2Character) target);
				}
			}
			
			if (targetList.isEmpty() && !skill.isAura())
			{
				if (isPlayer())
				{
					if (skipRange > 0)
					{
						sendPacket(SystemMessageId.DIST_TOO_FAR_CASTING_STOPPED);
					}
					else if (skipLOS > 0)
					{
						sendPacket(SystemMessageId.CANT_SEE_TARGET);
					}
					else if (skipPeaceZone > 0)
					{
						sendPacket(SystemMessageId.A_MALICIOUS_SKILL_CANNOT_BE_USED_IN_PEACE_ZONE);
					}
				}
				abortCast();
				return;
			}
			mut.setTargets(targetList.toArray(new L2Character[targetList.size()]));
			targets = mut.getTargets();
		}
		
		// Ensure that a cast is in progress
		// Check if player is using fake death.
		// Static skills can be used while faking death.
		if ((mut.isSimultaneous() && !isCastingSimultaneouslyNow()) || (!mut.isSimultaneous() && !isCastingNow()) || (isAlikeDead() && !skill.isStatic()))
		{
			// now cancels both, simultaneous and normal
			getAI().notifyEvent(CtrlEvent.EVT_CANCEL);
			return;
		}
		
		// Send a Server->Client packet MagicSkillLaunched to the L2Character AND to all L2PcInstance in the _KnownPlayers of the L2Character
		if (!skill.isStatic())
		{
			broadcastPacket(new MagicSkillLaunched(this, skill.getDisplayId(), skill.getDisplayLevel(), targets));
		}
		
		mut.setPhase(2);
		if (mut.getHitTime() == 0)
		{
			onMagicHitTimer(mut);
		}
		else
		{
			_skillCast = ThreadPoolManager.getInstance().scheduleEffect(mut, 400);
		}
	}
	
	// Runs in the end of skill casting
	public void onMagicHitTimer(MagicUseTask mut)
	{
		final L2Skill skill = mut.getSkill();
		final L2Object[] targets = mut.getTargets();
		
		if ((skill == null) || (targets == null))
		{
			abortCast();
			return;
		}
		
		if (getFusionSkill() != null)
		{
			if (mut.isSimultaneous())
			{
				_skillCast2 = null;
				setIsCastingSimultaneouslyNow(false);
			}
			else
			{
				_skillCast = null;
				setIsCastingNow(false);
			}
			getFusionSkill().onCastAbort();
			notifyQuestEventSkillFinished(skill, targets[0]);
			return;
		}
		
		L2Effect mog = getFirstEffect(L2EffectType.SIGNET_GROUND);
		if (mog != null)
		{
			if (mut.isSimultaneous())
			{
				_skillCast2 = null;
				setIsCastingSimultaneouslyNow(false);
			}
			else
			{
				_skillCast = null;
				setIsCastingNow(false);
			}
			mog.exit();
			notifyQuestEventSkillFinished(skill, targets[0]);
			return;
		}
		
		// Go through targets table
		for (L2Object tgt : targets)
		{
			if (tgt.isPlayable())
			{
				L2Character target = (L2Character) tgt;
				
				if (skill.getSkillType() == L2SkillType.BUFF)
				{
					SystemMessage smsg = SystemMessage.getSystemMessage(SystemMessageId.YOU_FEEL_S1_EFFECT);
					smsg.addSkillName(skill);
					target.sendPacket(smsg);
				}
				
				if (isPlayer() && target.isSummon())
				{
					((L2Summon) target).updateAndBroadcastStatus(BroadcastStatusType.DEFAULT);
				}
			}
		}
		
		rechargeShots(skill.useSoulShot(), skill.useSpiritShot());
		
		// Consume MP of the L2Character and Send the Server->Client packet StatusUpdate with current HP and MP to all other L2PcInstance to inform
		double mpConsume = getStat().getMpConsume(skill);
		
		if (mpConsume > 0)
		{
			if (mpConsume > getCurrentMp())
			{
				sendPacket(SystemMessageId.NOT_ENOUGH_MP);
				abortCast();
				return;
			}
			
			getStatus().reduceMp(mpConsume);
		}
		
		// Consume HP if necessary and Send the Server->Client packet StatusUpdate with current HP and MP to all other L2PcInstance to inform
		if (skill.getHpConsume() > 0)
		{
			double consumeHp = skill.getHpConsume();
			
			if (consumeHp >= getCurrentHp())
			{
				sendPacket(SystemMessageId.NOT_ENOUGH_HP);
				abortCast();
				return;
			}
			
			getStatus().reduceHp(consumeHp, this, true);
		}
		
		// Consume CP if necessary and Send the Server->Client packet StatusUpdate with current CP/HP and MP to all other L2PcInstance to inform
		if (skill.getCpConsume() > 0)
		{
			double consumeCp;
			
			consumeCp = skill.getCpConsume();
			if ((consumeCp + 1) >= getCurrentHp())
			{
				consumeCp = getCurrentHp() - 1.0;
			}
			
			getStatus().reduceCp((int) consumeCp);
		}
		
		if (skill.getFlyType() != null)
		{
			switch (skill.getFlyType())
			{
				case THROW_UP:
				case THROW_HORIZONTAL:
					Location flyLoc;
					for (L2Object trg : targets)
					{
						if (trg.isCharacter())
						{
							L2Character target = (L2Character) trg;
							// target.setHeading(this, false);
							flyLoc = getFlyLocation(null, skill);
							target.setXYZ(flyLoc);
							broadcastPacket(new FlyToLocation(target, flyLoc, skill.getFlyType()));
						}
					}
					break;
			}
		}
		
		// Launch the magic skill in order to calculate its effects
		callSkill(mut.getSkill(), mut.getTargets());
		
		if (mut.getHitTime() > 0)
		{
			mut.setCount(mut.getCount() + 1);
			if (mut.getCount() < skill.getHitCounts())
			{
				int skillTime = (mut.getHitTime() * skill.getHitTimings()[mut.getCount()]) / 100;
				if (mut.isSimultaneous())
				{
					_skillCast2 = ThreadPoolManager.getInstance().scheduleEffect(mut, skillTime);
				}
				else
				{
					_skillCast = ThreadPoolManager.getInstance().scheduleEffect(mut, skillTime);
				}
				return;
			}
		}
		
		mut.setPhase(3);
		if ((mut.getHitTime() == 0) || (mut.getCoolTime() == 0))
		{
			onMagicFinalizer(mut);
		}
		else
		{
			if (mut.isSimultaneous())
			{
				_skillCast2 = ThreadPoolManager.getInstance().scheduleEffect(mut, mut.getCoolTime());
			}
			else
			{
				_skillCast = ThreadPoolManager.getInstance().scheduleEffect(mut, mut.getCoolTime());
			}
		}
	}
	
	// Runs after skillTime
	public void onMagicFinalizer(MagicUseTask mut)
	{
		if (mut.isSimultaneous())
		{
			_skillCast2 = null;
			setIsCastingSimultaneouslyNow(false);
			return;
		}
		
		// Cleanup
		_skillCast = null;
		_castInterruptTime = 0;
		
		// Stop casting
		setIsCastingNow(false);
		setIsCastingSimultaneouslyNow(false);
		
		finishFly();
		
		final L2Skill skill = mut.getSkill();
		final L2Object target = mut.getTargets().length > 0 ? mut.getTargets()[0] : null;
		
		if (isPlayer() && (skill.getMaxSoulConsumeCount() > 0))
		{
			if (!getActingPlayer().decreaseSouls(skill.getMaxSoulConsumeCount(), skill))
			{
				abortCast();
				return;
			}
		}
		
		// On each repeat recharge shots before cast.
		// we will force npc to recharge shots no matter what
		if (isNpc())
		{
			rechargeShots(mut.getSkill().useSoulShot(), mut.getSkill().useSpiritShot());
		}
		else if (mut.getCount() > 0)
		{
			rechargeShots(mut.getSkill().useSoulShot(), mut.getSkill().useSpiritShot());
		}
		
		if (isPlayer() && (getActingPlayer().getCurrentSkill() != null))
		{
			checkNextActionAttack(getActingPlayer().getCurrentSkill(), target);
		}
		
		if (skill.isOffensive() && !(skill.getSkillType() == L2SkillType.UNLOCK) && (skill.getTargetType() != L2TargetType.AURA))
		{
			getAI().clientStartAutoAttack();
		}
		
		if ((skill.getTargetType() == L2TargetType.AURA) && (target != null))
		{
			getAI().clientStartAutoAttack();
		}
		
		// Notify the AI of the L2Character with EVT_FINISH_CASTING
		getAI().notifyEvent(CtrlEvent.EVT_FINISH_CASTING);
		
		notifyQuestEventSkillFinished(skill, target);
		
		// If character is a player, then wipe their current cast state and check if a skill is queued.
		// If there is a queued skill, launch it and wipe the queue.
		if (isPlayer())
		{
			L2PcInstance currPlayer = getActingPlayer();
			SkillUseHolder queuedSkill = currPlayer.getQueuedSkill();
			
			currPlayer.setCurrentSkill(null, false, false);
			
			if (queuedSkill != null)
			{
				currPlayer.setQueuedSkill(null, false, false);
				
				// DON'T USE : Recursive call to useMagic() method
				// currPlayer.useMagic(queuedSkill.getSkill(), queuedSkill.isCtrlPressed(), queuedSkill.isShiftPressed());
				ThreadPoolManager.getInstance().executeGeneral(new QueuedMagicUseTask(currPlayer, queuedSkill.getSkill(), queuedSkill.isCtrlPressed(), queuedSkill.isShiftPressed()));
			}
		}
	}
	
	// Quest event ON_SPELL_FNISHED
	protected void notifyQuestEventSkillFinished(L2Skill skill, L2Object target)
	{
	
	}
	
	/**
	 * Gets the disabled skills map.
	 * @return the disabled skills map
	 */
	public Map<Integer, Long> getDisabledSkills()
	{
		return _disabledSkills;
	}
	
	/**
	 * Enables a skill.
	 * @param skill the skill to enable
	 */
	public void enableSkill(L2Skill skill)
	{
		if ((skill == null) || (_disabledSkills == null))
		{
			return;
		}
		
		_disabledSkills.remove(skill.getReuseHashCode());
	}
	
	/**
	 * Disables a skill for a given time.<br>
	 * If delay is lesser or equal than zero, skill will be disabled "forever".
	 * @param skill the skill to disable
	 * @param delay delay in milliseconds
	 */
	public void disableSkill(L2Skill skill, long delay)
	{
		if (skill == null)
		{
			return;
		}
		
		if (_disabledSkills == null)
		{
			_disabledSkills = new ConcurrentHashMap<>();
		}
		
		_disabledSkills.put(skill.getReuseHashCode(), delay >= 10 ? System.currentTimeMillis() + delay : Long.MAX_VALUE);
	}
	
	/**
	 * Removes all the disabled skills.
	 */
	public final void resetDisabledSkills()
	{
		if (_disabledSkills != null)
		{
			_disabledSkills.clear();
		}
	}
	
	/**
	 * <B><U>Concept</U>:</B><br>
	 * All skills disabled are identified by their reuse hashcodes in <B>_disabledSkills</B> of the L2Character
	 * @param skill The L2Skill to check
	 * @return true if a skill is disabled.
	 */
	public boolean isSkillDisabled(L2Skill skill)
	{
		if (skill == null)
		{
			return true;
		}
		
		return isSkillDisabled(skill.getReuseHashCode());
	}
	
	/**
	 * Verifies if the skill is disabled.
	 * @param hashCode the skill hash code
	 * @return {@code true} if the skill is disabled, {@code false} otherwise
	 */
	public boolean isSkillDisabled(int hashCode)
	{
		if (isAllSkillsDisabled())
		{
			return true;
		}
		
		if (_disabledSkills == null)
		{
			return false;
		}
		
		final Long stamp = _disabledSkills.get(hashCode);
		if (stamp == null)
		{
			return false;
		}
		
		if (stamp < System.currentTimeMillis())
		{
			_disabledSkills.remove(hashCode);
			return false;
		}
		return true;
	}
	
	/**
	 * Disables all skills.
	 */
	public void disableAllSkills()
	{
		_allSkillsDisabled = true;
	}
	
	/**
	 * Enables all skills, except those under reuse time or previously disabled.
	 */
	public void enableAllSkills()
	{
		_allSkillsDisabled = false;
	}
	
	/**
	 * Launch the magic skill and calculate its effects on each target contained in the targets table.
	 * @param skill The L2Skill to use
	 * @param targets The table of L2Object targets
	 */
	public void callSkill(L2Skill skill, L2Object[] targets)
	{
		try
		{
			L2Weapon activeWeapon = getActiveWeaponItem();
			
			// Check if the toggle skill effects are already in progress on the L2Character
			if (skill.isToggle() && (getFirstEffect(skill.getId()) != null))
			{
				return;
			}
			
			// Initial checks
			for (L2Object obj : targets)
			{
				if ((obj == null) || !obj.isCharacter())
				{
					continue;
				}
				
				final L2Character target = (L2Character) obj;
				// Check raid monster attack and check buffing characters who attack raid monsters.
				L2Character targetsAttackTarget = null;
				L2Character targetsCastTarget = null;
				if (target.hasAI())
				{
					targetsAttackTarget = target.getAI().getAttackTarget();
					targetsCastTarget = target.getAI().getCastTarget();
				}
				
				if (!Config.RAID_DISABLE_CURSE && ((target.isRaid() && target.giveRaidCurse() && (getLevel() > (target.getLevel() + 8))) || (!skill.isOffensive() && (targetsAttackTarget != null) && targetsAttackTarget.isRaid() && targetsAttackTarget.giveRaidCurse() && targetsAttackTarget.getAttackByList().contains(target) // has
																																																																																	// attacked
																																																																																	// raid
				&& (getLevel() > (targetsAttackTarget.getLevel() + 8))) || (!skill.isOffensive() && (targetsCastTarget != null) && targetsCastTarget.isRaid() && targetsCastTarget.giveRaidCurse() && targetsCastTarget.getAttackByList().contains(target) // has attacked raid
				&& (getLevel() > (targetsCastTarget.getLevel() + 8)))))
				{
					final CommonSkill curse = skill.isMagic() ? CommonSkill.RAID_CURSE : CommonSkill.RAID_CURSE2;
					L2Skill curseSkill = curse.getSkill();
					if (curseSkill != null)
					{
						abortAttack();
						abortCast();
						getAI().setIntention(CtrlIntention.AI_INTENTION_IDLE);
						curseSkill.getEffects(target, this);
					}
					else
					{
						_log.warn("Skill ID " + curse.getId() + " level " + curse.getLevel() + " is missing in DP!");
					}
					return;
				}
				
				// Check if over-hit is possible
				if (skill.isOverhit())
				{
					if (target.isAttackable())
					{
						((L2Attackable) target).overhitEnabled(true);
					}
				}
				
				if (!skill.isStatic())
				{
					if ((activeWeapon != null) && !target.isDead())
					{
						activeWeapon.castOnMagicSkill(this, target, skill);
					}
					
					if (_chanceSkills != null)
					{
						_chanceSkills.onSkillHit(target, skill, false, 0);
					}
					
					if (target.getChanceSkills() != null)
					{
						target.getChanceSkills().onSkillHit(this, skill, true, 0);
					}
					
					if (_triggerSkills != null)
					{
						for (OptionsSkillHolder holder : _triggerSkills.values())
						{
							if ((skill.isMagic() && (holder.getSkillType() == OptionsSkillType.MAGIC)) || (skill.isPhysical() && (holder.getSkillType() == OptionsSkillType.ATTACK)))
							{
								if (Rnd.get(100) < holder.getChance())
								{
									makeTriggerCast(holder.getSkill(), target);
								}
							}
						}
					}
				}
			}
			
			// Launch the magic skill and calculate its effects
			// Get the skill handler corresponding to the skill type (PDAM, MDAM, SWEEP...) started in gameserver
			ISkillHandler handler = SkillHandler.getInstance().getHandler(skill.getSkillType());
			if (handler != null)
			{
				handler.useSkill(this, skill, targets);
			}
			else
			{
				skill.useSkill(this, targets);
			}
			
			L2PcInstance player = getActingPlayer();
			if (player != null)
			{
				for (L2Object target : targets)
				{
					// EVT_ATTACKED and PvPStatus
					if (target instanceof L2Character)
					{
						if (skill.isOffensive())
						{
							if (target.isPlayer() || target.isSummon() || target.isTrap())
							{
								// Signets are a special case, casted on target_self but don't harm self
								if ((skill.getSkillType() != L2SkillType.SIGNET) && (skill.getSkillType() != L2SkillType.SIGNET_CASTTIME))
								{
									if (target.isPlayer())
									{
										target.getActingPlayer().getAI().clientStartAutoAttack();
									}
									else if (target.isSummon() && ((L2Character) target).hasAI())
									{
										L2PcInstance owner = ((L2Summon) target).getOwner();
										if (owner != null)
										{
											owner.getAI().clientStartAutoAttack();
										}
									}
									// attack of the own pet does not flag player
									// triggering trap not flag trap owner
									if ((player.getSummon() != target) && !isTrap())
									{
										player.updatePvPStatus((L2Character) target);
									}
								}
							}
							else if (target.isAttackable())
							{
								switch (skill.getId())
								{
									case 51: // Lure
									case 511: // Temptation
										break;
									default:
										// add attacker into list
										((L2Character) target).addAttackerToAttackByList(this);
								}
								
								if (isPlayer() && (target instanceof L2RaidBossInstance) && ((L2RaidBossInstance) target).isEventRaid())
								{
									RaidManager.getInstance().checkRaidAttack(getActingPlayer(), (L2RaidBossInstance) target);
								}
							}
							// notify target AI about the attack
							if (((L2Character) target).hasAI() && !skill.hasEffectType(L2EffectType.HATE) && !skill.hasEffectType(L2EffectType.PASSIVE) && !skill.hasEffectType(L2EffectType.REMOVE_TARGET))
							{
								((L2Character) target).getAI().notifyEvent(CtrlEvent.EVT_ATTACKED, this);
							}
						}
						else
						{
							if (target.isPlayer())
							{
								// Casting non offensive skill on player with pvp flag set or with karma
								if (!(target.equals(this) || target.equals(player)) && ((target.getActingPlayer().getPvpFlag() > 0) || (target.getActingPlayer().getKarma() > 0)))
								{
									player.updatePvPStatus();
								}
							}
							// vGodFather: Fix Casting non offensive skill on pet with pvp flag set or with karma
							else if (target.isSummon())
							{
								// Casting non offensive skill on pet with pvp flag set or with karma
								if (!(((L2Summon) target).getOwner().equals(this) || ((L2Summon) target).getOwner().equals(player)) && ((((L2Summon) target).getOwner().getPvpFlag() > 0) || (((L2Summon) target).getOwner().getKarma() > 0)))
								{
									player.updatePvPStatus();
								}
							}
							else if (target.isAttackable())
							{
								switch (skill.getSkillType())
								{
									case SUMMON:
									case UNLOCK:
									case UNLOCK_SPECIAL:
										break;
									default:
										player.updatePvPStatus();
								}
							}
						}
					}
				}
				
				// Mobs in range 1000 see spell
				Collection<L2Object> objs = player.getKnownList().getKnownObjects().values();
				for (L2Object spMob : objs)
				{
					if ((spMob != null) && spMob.isNpc())
					{
						final L2Npc npcMob = (L2Npc) spMob;
						if ((npcMob.isInsideRadius(player, 1000, true, true)))
						{
							EventDispatcher.getInstance().notifyEventAsync(new OnNpcSkillSee(npcMob, player, skill, targets, isSummon()), npcMob);
							
							// On Skill See logic
							if (npcMob.isAttackable())
							{
								final L2Attackable attackable = (L2Attackable) npcMob;
								
								int skillEffectPoint = skill.getAggroPoints();
								
								if (player.hasSummon())
								{
									if ((targets.length == 1) && Util.contains(targets, player.getSummon()))
									{
										skillEffectPoint = 0;
									}
									
									if (skillEffectPoint > 0)
									{
										if (attackable.hasAI() && (attackable.getAI().getIntention() == AI_INTENTION_ATTACK))
										{
											L2Object npcTarget = attackable.getTarget();
											for (L2Object skillTarget : targets)
											{
												if ((npcTarget == skillTarget) || (npcMob == skillTarget))
												{
													L2Character originalCaster = isSummon() ? getSummon() : player;
													attackable.addDamageHate(originalCaster, 0, (skillEffectPoint * 150) / (attackable.getLevel() + 7));
												}
											}
										}
									}
								}
							}
						}
					}
				}
			}
			
			// Notify AI
			if (skill.isOffensive() && !skill.hasEffectType(L2EffectType.HATE) && !skill.hasEffectType(L2EffectType.PASSIVE))
			{
				for (L2Object target : targets)
				{
					if (target.isCharacter())
					{
						final L2Character creature = (L2Character) target;
						
						// vGodFather
						if (target.isPlayer() && (target.getActingPlayer().getTarget() == null) && !skill.hasEffectType(L2EffectType.REMOVE_TARGET))
						{
							target.getActingPlayer().setTarget(this);
						}
						
						if (creature.hasAI() && !skill.hasEffectType(L2EffectType.REMOVE_TARGET))
						{
							// Notify target AI about the attack
							creature.getAI().notifyEvent(CtrlEvent.EVT_ATTACKED, this);
						}
					}
				}
			}
		}
		catch (Exception e)
		{
			_log.warn(getClass().getSimpleName() + ": callSkill() failed, Actor: " + getName() + "[" + getId() + "] Skill: " + skill.getId() + ".", e);
		}
	}
	
	/**
	 * @param target
	 * @return True if the L2Character is behind the target and can't be seen.
	 */
	public boolean isBehind(L2Object target)
	{
		double angleChar, angleTarget, angleDiff, maxAngleDiff = 60;
		
		if (target == null)
		{
			return false;
		}
		
		if (target instanceof L2Character)
		{
			L2Character target1 = (L2Character) target;
			angleChar = Util.calculateAngleFrom(this, target1);
			angleTarget = Util.convertHeadingToDegree(target1.getHeading());
			angleDiff = angleChar - angleTarget;
			if (angleDiff <= (-360 + maxAngleDiff))
			{
				angleDiff += 360;
			}
			if (angleDiff >= (360 - maxAngleDiff))
			{
				angleDiff -= 360;
			}
			if (Math.abs(angleDiff) <= maxAngleDiff)
			{
				return true;
			}
		}
		else
		{
			_log.info("isBehindTarget's target not an L2 Character.");
		}
		return false;
	}
	
	public boolean isBehindTarget()
	{
		return isBehind(getTarget());
	}
	
	/**
	 * @param target
	 * @return True if the target is facing the L2Character.
	 */
	public boolean isInFrontOf(L2Character target)
	{
		double angleChar, angleTarget, angleDiff, maxAngleDiff = 60;
		if (target == null)
		{
			return false;
		}
		
		angleTarget = Util.calculateAngleFrom(target, this);
		angleChar = Util.convertHeadingToDegree(target.getHeading());
		angleDiff = angleChar - angleTarget;
		if (angleDiff <= (-360 + maxAngleDiff))
		{
			angleDiff += 360;
		}
		if (angleDiff >= (360 - maxAngleDiff))
		{
			angleDiff -= 360;
		}
		return Math.abs(angleDiff) <= maxAngleDiff;
	}
	
	/**
	 * @param target
	 * @param maxAngle
	 * @return true if target is in front of L2Character (shield def etc)
	 */
	public boolean isFacing(L2Object target, int maxAngle)
	{
		double angleChar, angleTarget, angleDiff, maxAngleDiff;
		if (target == null)
		{
			return false;
		}
		maxAngleDiff = maxAngle / 2.;
		angleTarget = Util.calculateAngleFrom(this, target);
		angleChar = Util.convertHeadingToDegree(getHeading());
		angleDiff = angleChar - angleTarget;
		if (angleDiff <= (-360 + maxAngleDiff))
		{
			angleDiff += 360;
		}
		if (angleDiff >= (360 - maxAngleDiff))
		{
			angleDiff -= 360;
		}
		return Math.abs(angleDiff) <= maxAngleDiff;
	}
	
	public boolean isInFrontOfTarget()
	{
		L2Object target = getTarget();
		if (target instanceof L2Character)
		{
			return isInFrontOf((L2Character) target);
		}
		return false;
	}
	
	/**
	 * @return the Level Modifier ((level + 89) / 100).
	 */
	public double getLevelMod()
	{
		return ((getLevel() + 89) / 100d);
	}
	
	public final void setSkillCast(Future<?> newSkillCast)
	{
		_skillCast = newSkillCast;
	}
	
	/**
	 * Sets _isCastingNow to true and _castInterruptTime is calculated from end time (ticks)
	 * @param newSkillCastEndTick
	 */
	public final void forceIsCasting(int newSkillCastEndTick)
	{
		setIsCastingNow(true);
		// for interrupt -400 ms
		_castInterruptTime = newSkillCastEndTick - 4;
	}
	
	private boolean _AIdisabled = false;
	
	public void updatePvPFlag(int value)
	{
		// Overridden in L2PcInstance
	}
	
	/**
	 * @return a multiplier based on weapon random damage
	 */
	public final double getRandomDamageMultiplier()
	{
		L2Weapon activeWeapon = getActiveWeaponItem();
		int random;
		
		if (activeWeapon != null)
		{
			random = activeWeapon.getRandomDamage();
		}
		else
		{
			random = 5 + (int) Math.sqrt(getLevel());
		}
		
		return (1 + ((double) Rnd.get(0 - random, random) / 100));
	}
	
	public final long getAttackEndTime()
	{
		return _attackEndTime;
	}
	
	public long getBowAttackEndTime()
	{
		return _disableBowAttackEndTime;
	}
	
	/**
	 * Not Implemented.
	 * @return
	 */
	public abstract int getLevel();
	
	public final double calcStat(Stats stat, double init)
	{
		return getStat().calcStat(stat, init, null, null);
	}
	
	// Stat - NEED TO REMOVE ONCE L2CHARSTAT IS COMPLETE
	public final double calcStat(Stats stat, double init, L2Character target, L2Skill skill)
	{
		return getStat().calcStat(stat, init, target, skill);
	}
	
	public int getAccuracy()
	{
		return getStat().getAccuracy();
	}
	
	public final float getAttackSpeedMultiplier()
	{
		return getStat().getAttackSpeedMultiplier();
	}
	
	public final double getCriticalDmg(L2Character target, double init)
	{
		return getStat().getCriticalDmg(target, init);
	}
	
	public int getCriticalHit(L2Character target, L2Skill skill)
	{
		return getStat().getCriticalHit(target, skill);
	}
	
	public int getEvasionRate(L2Character target)
	{
		return getStat().getEvasionRate(target);
	}
	
	public final int getMagicalAttackRange(L2Skill skill)
	{
		return getStat().getMagicalAttackRange(skill);
	}
	
	public final int getMaxCp()
	{
		return getStat().getMaxCp();
	}
	
	public final int getMaxRecoverableCp()
	{
		return getStat().getMaxRecoverableCp();
	}
	
	public double getMAtk(L2Character target, L2Skill skill)
	{
		return getStat().getMAtk(target, skill);
	}
	
	public int getMAtkSpd()
	{
		return getStat().getMAtkSpd();
	}
	
	public int getMaxMp()
	{
		return getStat().getMaxMp();
	}
	
	public int getMaxRecoverableMp()
	{
		return getStat().getMaxRecoverableMp();
	}
	
	public int getMaxHp()
	{
		return getStat().getMaxHp();
	}
	
	public int getMaxRecoverableHp()
	{
		return getStat().getMaxRecoverableHp();
	}
	
	public final int getMCriticalHit(L2Character target, L2Skill skill)
	{
		return getStat().getMCriticalHit(target, skill);
	}
	
	public double getMDef(L2Character target, L2Skill skill)
	{
		return getStat().getMDef(target, skill);
	}
	
	public double getMReuseRate(L2Skill skill)
	{
		return getStat().getMReuseRate(skill);
	}
	
	public double getPAtk(L2Character target)
	{
		return getStat().getPAtk(target);
	}
	
	public double getPAtkSpd()
	{
		return getStat().getPAtkSpd();
	}
	
	public double getPDef(L2Character target)
	{
		return getStat().getPDef(target);
	}
	
	public final int getPhysicalAttackRange()
	{
		return getStat().getPhysicalAttackRange();
	}
	
	public double getMovementSpeedMultiplier()
	{
		return getStat().getMovementSpeedMultiplier();
	}
	
	public double getRunSpeed()
	{
		return getStat().getRunSpeed();
	}
	
	public double getWalkSpeed()
	{
		return getStat().getWalkSpeed();
	}
	
	public final double getSwimRunSpeed()
	{
		return getStat().getSwimRunSpeed();
	}
	
	public final double getSwimWalkSpeed()
	{
		return getStat().getSwimWalkSpeed();
	}
	
	public double getMoveSpeed()
	{
		return getStat().getMoveSpeed();
	}
	
	public final int getShldDef()
	{
		return getStat().getShldDef();
	}
	
	public int getSTR()
	{
		return getStat().getSTR();
	}
	
	public int getDEX()
	{
		return getStat().getDEX();
	}
	
	public int getCON()
	{
		return getStat().getCON();
	}
	
	public int getINT()
	{
		return getStat().getINT();
	}
	
	public int getWIT()
	{
		return getStat().getWIT();
	}
	
	public int getMEN()
	{
		return getStat().getMEN();
	}
	
	// Status - NEED TO REMOVE ONCE L2CHARTATUS IS COMPLETE
	public void addStatusListener(L2Character object)
	{
		statusListenersLock.lock();
		try
		{
			getStatus().addStatusListener(object);
		}
		finally
		{
			statusListenersLock.unlock();
		}
	}
	
	public void reduceCurrentHp(double i, L2Character attacker, L2Skill skill)
	{
		reduceCurrentHp(i, attacker, true, false, skill);
	}
	
	public void reduceCurrentHpByDOT(double i, L2Character attacker, L2Skill skill)
	{
		reduceCurrentHp(i, attacker, !skill.isToggle(), true, skill);
	}
	
	public void reduceCurrentHp(double i, L2Character attacker, boolean awake, boolean isDOT, L2Skill skill)
	{
		if (Config.L2JMOD_CHAMPION_ENABLE && isChampion() && (Config.L2JMOD_CHAMPION_HP != 0))
		{
			getStatus().reduceHp(i / Config.L2JMOD_CHAMPION_HP, attacker, awake, isDOT, false);
		}
		else
		{
			getStatus().reduceHp(i, attacker, awake, isDOT, false);
		}
	}
	
	public void reduceCurrentMp(double i)
	{
		getStatus().reduceMp(i);
	}
	
	@Override
	public void removeStatusListener(L2Character object)
	{
		statusListenersLock.lock();
		try
		{
			getStatus().removeStatusListener(object);
		}
		finally
		{
			statusListenersLock.unlock();
		}
	}
	
	protected void stopHpMpRegeneration()
	{
		getStatus().stopHpMpRegeneration();
	}
	
	public final double getCurrentCp()
	{
		return getStatus().getCurrentCp();
	}
	
	public final int getCurrentCpPercent()
	{
		return (int) ((getCurrentCp() * 100) / getMaxCp());
	}
	
	public final void setCurrentCp(Double newCp)
	{
		setCurrentCp((double) newCp);
	}
	
	public final void setCurrentCp(double newCp)
	{
		getStatus().setCurrentCp(newCp);
	}
	
	public final double getCurrentHp()
	{
		return getStatus().getCurrentHp();
	}
	
	public final int getCurrentHpPercent()
	{
		return (int) ((getCurrentHp() * 100) / getMaxHp());
	}
	
	public final void setCurrentHp(double newHp)
	{
		getStatus().setCurrentHp(newHp);
	}
	
	public final void setCurrentHpMp(double newHp, double newMp)
	{
		getStatus().setCurrentHpMp(newHp, newMp);
	}
	
	public final double getCurrentMp()
	{
		return getStatus().getCurrentMp();
	}
	
	public final int getCurrentMpPercent()
	{
		return (int) ((getCurrentMp() * 100) / getMaxMp());
	}
	
	public final void setCurrentMp(Double newMp)
	{
		setCurrentMp((double) newMp);
	}
	
	public final void setCurrentMp(double newMp)
	{
		getStatus().setCurrentMp(newMp);
	}
	
	/**
	 * @return the max weight that the L2Character can load.
	 */
	public int getMaxLoad()
	{
		return 0;
	}
	
	public int getBonusWeightPenalty()
	{
		return 0;
	}
	
	/**
	 * @return the current weight of the L2Character.
	 */
	public int getCurrentLoad()
	{
		return 0;
	}
	
	public boolean isChampion()
	{
		return false;
	}
	
	/**
	 * Send system message about damage.
	 * @param target
	 * @param damage
	 * @param mcrit
	 * @param pcrit
	 * @param miss
	 */
	public void sendDamageMessage(L2Character target, int damage, boolean mcrit, boolean pcrit, boolean miss)
	{
		if (miss && target.isPlayer())
		{
			SystemMessage sm = SystemMessage.getSystemMessage(SystemMessageId.C1_EVADED_C2_ATTACK);
			sm.addPcName(target.getActingPlayer());
			sm.addCharName(this);
			target.sendPacket(sm);
		}
	}
	
	public FusionSkill getFusionSkill()
	{
		return _fusionSkill;
	}
	
	public void setFusionSkill(FusionSkill fb)
	{
		_fusionSkill = fb;
	}
	
	public byte getAttackElement()
	{
		return getStat().getAttackElement();
	}
	
	public int getAttackElementValue(byte attackAttribute)
	{
		return getStat().getAttackElementValue(attackAttribute);
	}
	
	public int getDefenseElementValue(byte defenseAttribute)
	{
		return getStat().getDefenseElementValue(defenseAttribute);
	}
	
	public final void startPhysicalAttackMuted()
	{
		abortAttack();
	}
	
	public void disableCoreAI(boolean val)
	{
		_AIdisabled = val;
	}
	
	public boolean isCoreAIDisabled()
	{
		return _AIdisabled;
	}
	
	/**
	 * @return true
	 */
	public boolean giveRaidCurse()
	{
		return true;
	}
	
	/**
	 * Check if target is affected with special buff
	 * @see CharEffectList#isAffected(EffectFlag)
	 * @param flag int
	 * @return boolean
	 */
	public boolean isAffected(EffectFlag flag)
	{
		return _effects.isAffected(flag);
	}
	
	public void broadcastSocialAction(int id)
	{
		broadcastPacket(new SocialAction(getObjectId(), id));
	}
	
	public Team getTeam()
	{
		return _team;
	}
	
	public void setTeam(Team team)
	{
		_team = team;
	}
	
	public void addOverrideCond(PcCondOverride... excs)
	{
		for (PcCondOverride exc : excs)
		{
			_exceptions |= exc.getMask();
		}
	}
	
	public void removeOverridedCond(PcCondOverride... excs)
	{
		for (PcCondOverride exc : excs)
		{
			_exceptions &= ~exc.getMask();
		}
	}
	
	public boolean canOverrideCond(PcCondOverride excs)
	{
		return (_exceptions & excs.getMask()) == excs.getMask();
	}
	
	public void setOverrideCond(long masks)
	{
		_exceptions = masks;
	}
	
	public void setLethalable(boolean val)
	{
		_lethalable = val;
	}
	
	public boolean isLethalable()
	{
		return _lethalable;
	}
	
	public Map<Integer, OptionsSkillHolder> getTriggerSkills()
	{
		if (_triggerSkills == null)
		{
			synchronized (this)
			{
				if (_triggerSkills == null)
				{
					_triggerSkills = new ConcurrentHashMap<>();
				}
			}
		}
		return _triggerSkills;
	}
	
	public void addTriggerSkill(OptionsSkillHolder holder)
	{
		getTriggerSkills().put(holder.getSkillId(), holder);
	}
	
	public void removeTriggerSkill(OptionsSkillHolder holder)
	{
		getTriggerSkills().remove(holder.getSkillId());
	}
	
	public void makeTriggerCast(L2Skill skill, L2Character target)
	{
		try
		{
			// vGodFather if target is already casting or dead must return
			if ((skill == null) || target.isCastingNow() || target.isDead())
			{
				return;
			}
			
			if (skill.checkCondition(this, target, false))
			{
				if (skill.triggersChanceSkill()) // skill will trigger another skill, but only if its not chance skill
				{
					skill = SkillData.getInstance().getInfo(skill.getTriggeredChanceId(), skill.getTriggeredChanceLevel());
					if ((skill == null) || (skill.getSkillType() == L2SkillType.NOTDONE))
					{
						return;
					}
					// We change skill to new one, we should verify conditions for new one
					if (!skill.checkCondition(this, target, false))
					{
						return;
					}
				}
				
				if (isSkillDisabled(skill))
				{
					return;
				}
				
				if (skill.getReuseDelay() > 0)
				{
					disableSkill(skill, skill.getReuseDelay());
				}
				
				final L2Object[] targets = skill.getTargetList(this, false, target);
				
				if (targets.length == 0)
				{
					return;
				}
				
				final L2Character firstTarget = (L2Character) targets[0];
				
				if (Config.ALT_VALIDATE_TRIGGER_SKILLS && isPlayable() && (firstTarget != null) && firstTarget.isPlayable())
				{
					final L2PcInstance player = getActingPlayer();
					if (!player.checkPvpSkill(firstTarget, skill, isSummon()))
					{
						return;
					}
				}
				
				final ISkillHandler handler = SkillHandler.getInstance().getHandler(skill.getSkillType());
				
				broadcastPacket(new MagicSkillLaunched(this, skill.getDisplayId(), skill.getLevel(), targets));
				broadcastPacket(new MagicSkillUse(this, firstTarget, skill.getDisplayId(), skill.getLevel(), 0, 0));
				// Launch the magic skill and calculate its effects
				// TODO: once core will support all possible effects, use effects (not handler)
				if (handler != null)
				{
					handler.useSkill(this, skill, targets);
				}
				else
				{
					skill.useSkill(this, targets);
				}
			}
		}
		catch (Exception e)
		{
			_log.warn(String.valueOf(e));
		}
	}
	
	/**
	 * Dummy method overridden in {@link L2Attackable}
	 * @return {@code true} if there is a loot to sweep, {@code false} otherwise.
	 */
	public boolean isSweepActive()
	{
		return false;
	}
	
	/**
	 * Dummy method overridden in {@link L2PcInstance}
	 * @return {@code true} if player is on event, {@code false} otherwise.
	 */
	public boolean isOnEvent()
	{
		return false;
	}
	
	public int getClanId()
	{
		return 0;
	}
	
	/**
	 * Dummy method overridden in {@link L2PcInstance}
	 * @return {@code true} if player is in academy, {@code false} otherwise.
	 */
	public boolean isAcademyMember()
	{
		return false;
	}
	
	/**
	 * Dummy method overridden in {@link L2PcInstance}
	 * @return the pledge type of current character.
	 */
	public int getPledgeType()
	{
		return 0;
	}
	
	public int getAllyId()
	{
		return 0;
	}
	
	/**
	 * Notifies to listeners that current character received damage.
	 * @param damage
	 * @param attacker
	 * @param skill
	 * @param critical
	 * @param damageOverTime
	 * @param isReflect
	 */
	public void notifyDamageReceived(double damage, L2Character attacker, L2Skill skill, boolean critical, boolean damageOverTime, boolean isReflect)
	{
		EventDispatcher.getInstance().notifyEventAsync(new OnCreatureDamageReceived(attacker, this, damage, skill, critical, damageOverTime, isReflect), this);
		EventDispatcher.getInstance().notifyEventAsync(new OnCreatureDamageDealt(attacker, this, damage, skill, critical, damageOverTime, isReflect), attacker);
	}
	
	/**
	 * Notifies to listeners that current character avoid attack.
	 * @param target
	 * @param isDot
	 */
	public void notifyAttackAvoid(final L2Character target, final boolean isDot)
	{
		EventDispatcher.getInstance().notifyEventAsync(new OnCreatureAttackAvoid(this, target, isDot), target);
	}
	
	/**
	 * @return {@link WeaponType} of current character's weapon or basic weapon type.
	 */
	public final WeaponType getAttackType()
	{
		if (isTransformed())
		{
			final TransformTemplate template = getTransformation().getTemplate(getActingPlayer());
			if (template != null)
			{
				return template.getBaseAttackType();
			}
		}
		final L2Weapon weapon = getActiveWeaponItem();
		if (weapon != null)
		{
			return weapon.getItemType();
		}
		return getTemplate().getBaseAttackType();
	}
	
	public final boolean isInCategory(CategoryType type)
	{
		return CategoryData.getInstance().isInCategory(type, getId());
	}
	
	@Override
	public Queue<AbstractEventListener> getListeners(EventType type)
	{
		final Queue<AbstractEventListener> objectListenres = super.getListeners(type);
		final Queue<AbstractEventListener> templateListeners = getTemplate().getListeners(type);
		final Queue<AbstractEventListener> globalListeners = isNpc() && !isMonster() ? Containers.Npcs().getListeners(type) : isMonster() ? Containers.Monsters().getListeners(type) : isPlayer() ? Containers.Players().getListeners(type) : EmptyQueue.emptyQueue();
		
		// Attempt to do not create collection
		if (objectListenres.isEmpty() && templateListeners.isEmpty() && globalListeners.isEmpty())
		{
			return EmptyQueue.emptyQueue();
		}
		else if (!objectListenres.isEmpty() && templateListeners.isEmpty() && globalListeners.isEmpty())
		{
			return objectListenres;
		}
		else if (!templateListeners.isEmpty() && objectListenres.isEmpty() && globalListeners.isEmpty())
		{
			return templateListeners;
		}
		else if (!globalListeners.isEmpty() && objectListenres.isEmpty() && templateListeners.isEmpty())
		{
			return globalListeners;
		}
		
		final Queue<AbstractEventListener> both = new LinkedBlockingDeque<>(objectListenres.size() + templateListeners.size() + globalListeners.size());
		both.addAll(objectListenres);
		both.addAll(templateListeners);
		both.addAll(globalListeners);
		return both;
	}
	
	@Override
	public boolean isCharacter()
	{
		return true;
	}
	
	public boolean isInDuel()
	{
		return false;
	}
	
	public int getDuelId()
	{
		return 0;
	}
	
	public byte getSiegeState()
	{
		return 0;
	}
	
	public int getSiegeSide()
	{
		return 0;
	}
	
	/**
	 * Send a normal message to all L2PcInstance in the known list.<br>
	 * @param msg String with message
	 */
	public void say(String msg)
	{
		broadcastPacket(new NpcSay(getObjectId(), Say2.NPC_ALL, getId(), msg));
	}
	
	/**
	 * Send a client message to all L2PcInstance in the known list.<br>
	 * @param msg NpcString from client
	 */
	public void say(NpcStringId msg)
	{
		broadcastPacket(new NpcSay(getObjectId(), Say2.NPC_ALL, getId(), msg));
	}
	
	/**
	 * Send a shout message (orange chat) to all L2PcInstance in the known list.<br>
	 * @param msg String with message
	 */
	public void shout(String msg)
	{
		broadcastPacket(new NpcSay(getObjectId(), Say2.NPC_SHOUT, getId(), msg));
	}
	
	/**
	 * Send a shout message (orange chat) to all L2PcInstance in the known list.<br>
	 * @param msg NpcString from client
	 */
	public void shout(NpcStringId msg)
	{
		broadcastPacket(new NpcSay(getObjectId(), Say2.NPC_SHOUT, getId(), msg));
	}
	
	public void addInvulAgainst(SkillHolder holder)
	{
		final InvulSkillHolder invulHolder = getInvulAgainstSkills().get(holder.getSkillId());
		if (invulHolder != null)
		{
			invulHolder.increaseInstances();
			return;
		}
		getInvulAgainstSkills().put(holder.getSkillId(), new InvulSkillHolder(holder));
	}
	
	public void removeInvulAgainst(SkillHolder holder)
	{
		final InvulSkillHolder invulHolder = getInvulAgainstSkills().get(holder.getSkillId());
		if ((invulHolder != null) && (invulHolder.decreaseInstances() < 1))
		{
			getInvulAgainstSkills().remove(holder.getSkillId());
		}
	}
	
	public boolean isInvulAgainst(int skillId, int skillLvl)
	{
		if (_invulAgainst != null)
		{
			final SkillHolder holder = getInvulAgainstSkills().get(skillId);
			return ((holder != null) && ((holder.getSkillLvl() < 1) || (holder.getSkillLvl() == skillLvl)));
		}
		return false;
	}
	
	private Map<Integer, InvulSkillHolder> getInvulAgainstSkills()
	{
		if (_invulAgainst == null)
		{
			synchronized (this)
			{
				if (_invulAgainst == null)
				{
					return _invulAgainst = new ConcurrentHashMap<>();
				}
			}
		}
		return _invulAgainst;
	}
	
	public double getPAtkAnimals(L2Character target)
	{
		return getStat().getPAtkAnimals(target);
	}
	
	public double getPAtkDragons(L2Character target)
	{
		return getStat().getPAtkDragons(target);
	}
	
	public double getPAtkInsects(L2Character target)
	{
		return getStat().getPAtkInsects(target);
	}
	
	public double getPAtkMonsters(L2Character target)
	{
		return getStat().getPAtkMonsters(target);
	}
	
	public double getPAtkPlants(L2Character target)
	{
		return getStat().getPAtkPlants(target);
	}
	
	public double getPAtkGiants(L2Character target)
	{
		return getStat().getPAtkGiants(target);
	}
	
	public double getPAtkMagicCreatures(L2Character target)
	{
		return getStat().getPAtkMagicCreatures(target);
	}
	
	public double getPDefAnimals(L2Character target)
	{
		return getStat().getPDefAnimals(target);
	}
	
	public double getPDefDragons(L2Character target)
	{
		return getStat().getPDefDragons(target);
	}
	
	public double getPDefInsects(L2Character target)
	{
		return getStat().getPDefInsects(target);
	}
	
	public double getPDefMonsters(L2Character target)
	{
		return getStat().getPDefMonsters(target);
	}
	
	public double getPDefPlants(L2Character target)
	{
		return getStat().getPDefPlants(target);
	}
	
	public double getPDefGiants(L2Character target)
	{
		return getStat().getPDefGiants(target);
	}
	
	public double getPDefMagicCreatures(L2Character target)
	{
		return getStat().getPDefMagicCreatures(target);
	}
	
	public int getMinShopDistanceNPC()
	{
		return 0;
	}
	
	public int getMinShopDistancePlayer()
	{
		return 0;
	}
	
	/**
	 * Active the abnormal effect Fear flag, notify the L2Character AI and send Server->Client UserInfo/CharInfo packet.
	 */
	public final void startFear()
	{
		getAI().notifyEvent(CtrlEvent.EVT_AFRAID);
		updateAbnormalEffect();
	}
	
	/**
	 * Stop a specified/all Fear abnormal L2Effect.<br>
	 * <B><U>Actions</U>:</B>
	 * <ul>
	 * <li>Delete a specified/all (if effect=null) Fear abnormal L2Effect from L2Character and update client magic icon</li>
	 * <li>Set the abnormal effect flag _affraid to False</li>
	 * <li>Notify the L2Character AI</li>
	 * <li>Send Server->Client UserInfo/CharInfo packet</li>
	 * </ul>
	 * @param removeEffects
	 */
	public final void stopFear(boolean removeEffects)
	{
		if (removeEffects)
		{
			stopEffects(L2EffectType.FEAR);
		}
		updateAbnormalEffect();
	}
	
	protected boolean canAttackSiegier(L2Character activeChar, L2Object target)
	{
		// vGodFather: conditions for siegable halls
		//@formatter:off
		if (activeChar.isPlayable() && activeChar.isInsideZone(ZoneIdType.SIEGE))
		{
			final L2PcInstance player = activeChar.getActingPlayer();
			
			final ClanHall hall = ClanHallManager.getInstance().getClanHall(activeChar);
			if (hall != null)
			{
				final SiegableHall sHall = hall.isSiegableHall() ? CHSiegeManager.getInstance().getSiegableHall(hall.getId()) : null;
				if ((sHall != null) && sHall.isInSiege())
				{
					if (
						player.isInSameParty(target.getActingPlayer())
						|| player.isInSameChannel(target.getActingPlayer())
						|| player.isInSameClan(target.getActingPlayer())
						||player.isInSameAlly(target.getActingPlayer())
					)
					{
						return false;
					}
					
					return true;
				}
			}
		}
		//@formatter:on
		
		if ((target.getActingPlayer() != null) && (activeChar.getSiegeState() > 0) && activeChar.isInsideZone(ZoneIdType.SIEGE) && (target.getActingPlayer().getSiegeState() == activeChar.getSiegeState()) && (target.getActingPlayer() != activeChar) && (target.getActingPlayer().getSiegeSide() == activeChar.getSiegeSide()))
		{
			// vGodFather: no need this
			// if (getOwner().getActingPlayer().isInSameParty(target.getActingPlayer()) || getOwner().getActingPlayer().isInSameChannel(target.getActingPlayer()) || getOwner().getActingPlayer().isInSameClan(target.getActingPlayer()) ||
			// getOwner().getActingPlayer().isInSameAlly(target.getActingPlayer()))
			// {
			if (TerritoryWarManager.getInstance().isTWInProgress())
			{
				sendPacket(SystemMessageId.YOU_CANNOT_ATTACK_A_MEMBER_OF_THE_SAME_TERRITORY);
			}
			else
			{
				sendPacket(SystemMessageId.FORCED_ATTACK_IS_IMPOSSIBLE_AGAINST_SIEGE_SIDE_TEMPORARY_ALLIED_MEMBERS);
			}
			sendPacket(ActionFailed.STATIC_PACKET);
			return false;
			// }
		}
		return true;
	}
	
	private boolean _isPorting = false;
	
	public boolean isPorting()
	{
		return _isPorting;
	}
	
	public void setIsPorting(boolean porting)
	{
		_isPorting = porting;
	}
	
	public boolean entering = true;
	private Future<?> _updateAndBroadcastStatus;
	
	public void startUpdate(StatusUpdate su, boolean fullUpdate)
	{
		if ((_updateAndBroadcastStatus != null) && !_updateAndBroadcastStatus.isDone())
		{
			return;
		}
		
		_updateAndBroadcastStatus = ThreadPoolManager.getInstance().scheduleGeneral(() -> PacketSenderTask.updateAndBroadcastStatus(getActingPlayer(), fullUpdate), Config.stats_update_packetsDelay);
		
		if (su.hasAttributes())
		{
			broadcastPacket(su);
		}
	}
	
	// vGodFather addon some mobs must have higher watch-forget distance
	int _watchDistance = 0;
	
	public void setWatchDistance(int distance)
	{
		_watchDistance = distance;
	}
	
	@Override
	public int getWatchDistance()
	{
		return _watchDistance;
	}
	
	public int getKarma()
	{
		return 0;
	}
	
	public byte getPvpFlag()
	{
		return 0;
	}
	
	private Future<?> _moveToPawnTask;
	
	public void moveToPawn(L2Character _actor, L2Character _followTarget, int _clientMovingToPawnOffset)
	{
		if ((Config.moveToPawn_packetsDelay == 0) || _actor.isPlayer())
		{
			broadcastPacket(new MoveToPawn(_actor, _followTarget, _clientMovingToPawnOffset));
			return;
		}
		
		if ((_moveToPawnTask != null) && !_moveToPawnTask.isDone())
		{
			return;
		}
		
		_moveToPawnTask = ThreadPoolManager.getInstance().scheduleGeneral(() -> broadcastPacket(new MoveToPawn(_actor, _followTarget, _clientMovingToPawnOffset)), Config.moveToPawn_packetsDelay);
	}
	
	private Location _flyLoc;
	
	public final Location getFlyLocation(L2Character target, L2Skill skill)
	{
		if (target == null)
		{
			target = this;
		}
		
		final Location toLoc;
		switch (skill.getSkillType())
		{
			case DUMMY:// mostly used for instant teleport skills
			{
				final int sign = skill.isFlyToBack() ? -1 : 1;
				final int offset = target.isMoving() ? 5 : 40;
				final int heading = target == this ? getHeading() : target.getHeading();
				final double radian = Util.convertHeadingToRadian(heading);
				
				if (target != this)
				{
					toLoc = new Location(target.getX() + (int) (Math.sin(radian) * (offset * (-sign))), target.getY() + (int) (Math.cos(radian) * (offset * sign)), target.getZ(), heading);
					// _log.warn("OffsetX: " + (int) (Math.sin(radian) * (offset * (-sign))));
					// _log.warn("OffsetY: " + (int) (Math.cos(radian) * (offset * sign)));
					// _log.warn("=====================");
				}
				else
				{
					toLoc = new Location(target.getX() + (int) (Math.sin(radian) * (skill.getFlyRadius() * (-sign))), target.getY() + (int) (Math.cos(radian) * (skill.getFlyRadius() * sign)), target.getZ(), heading);
				}
				break;
			}
			default:
			{
				// Get current position of the L2Character
				final int curX = super.getX();
				final int curY = super.getY();
				// final int curZ = super.getZ();
				
				int x = target.getX();
				int y = target.getY();
				int z = target.getZ();
				
				double dx = (x - curX);
				double dy = (y - curY);
				// double dz = (z - curZ);
				double distance = Math.sqrt((dx * dx) + (dy * dy));
				
				// Calculate movement angles needed
				double sin = dy / distance;
				double cos = dx / distance;
				
				distance -= 40; // offset must be 40
				
				// Calculate the new destination with offset included
				x = curX + (int) (distance * cos);
				y = curY + (int) (distance * sin);
				
				final int heading = target == this ? getHeading() : target.getHeading();
				toLoc = new Location(x, y, z, heading);
				break;
			}
		}
		
		if (isFlying())
		{
			return toLoc;
		}
		
		return GeoData.getInstance().moveCheck(this, toLoc);
	}
	
	private void finishFly()
	{
		revalidateZone(true);
		
		final Location flyLoc = _flyLoc;
		_flyLoc = null;
		if (flyLoc != null)
		{
			setXYZ(flyLoc);
		}
	}
}
