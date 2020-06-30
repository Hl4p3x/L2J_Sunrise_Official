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
package l2r.gameserver.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import l2r.Config;
import l2r.gameserver.ThreadPoolManager;
import l2r.gameserver.model.actor.L2Character;
import l2r.gameserver.model.actor.L2Summon;
import l2r.gameserver.model.actor.instance.L2PcInstance;
import l2r.gameserver.model.effects.EffectFlag;
import l2r.gameserver.model.effects.L2Effect;
import l2r.gameserver.model.effects.L2EffectType;
import l2r.gameserver.model.entity.olympiad.OlympiadGameManager;
import l2r.gameserver.model.entity.olympiad.tasks.OlympiadGameTask;
import l2r.gameserver.model.skills.AbnormalType;
import l2r.gameserver.model.skills.L2Skill;
import l2r.gameserver.model.skills.L2SkillType;
import l2r.gameserver.network.SystemMessageId;
import l2r.gameserver.network.serverpackets.AbnormalStatusUpdate;
import l2r.gameserver.network.serverpackets.ExOlympiadSpelledInfo;
import l2r.gameserver.network.serverpackets.PartySpelled;
import l2r.gameserver.network.serverpackets.ShortBuffStatusUpdate;
import l2r.gameserver.network.serverpackets.SystemMessage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CharEffectList
{
	protected static final Logger _log = LoggerFactory.getLogger(CharEffectList.class);
	private static final L2Effect[] EMPTY_EFFECTS = new L2Effect[0];
	
	/** Map containing all effects from buffs for this effect list. */
	private List<L2Effect> _buffs;
	/** Map containing all debuffs for this effect list. */
	private List<L2Effect> _debuffs;
	/** They bypass most of the actions, they are not included in most operations. */
	private List<L2Effect> _passives;
	/** Map containing the all stacked effect in progress for each abnormal type. */
	private Map<String, List<L2Effect>> _stackedEffects;
	private volatile boolean _hasBuffsRemovedOnAnyAction = false;
	private volatile boolean _hasBuffsRemovedOnDamage = false;
	private volatile boolean _hasDebuffsRemovedOnDamage = false;
	
	private boolean _queuesInitialized = false;
	private LinkedBlockingQueue<L2Effect> _addQueue;
	private LinkedBlockingQueue<L2Effect> _removeQueue;
	private final AtomicBoolean queueLock = new AtomicBoolean();
	/** Effect flags. */
	private int _effectFlags;
	/** If {@code true} only party icons need to be updated. */
	private boolean _partyOnly = false;
	/** The owner of this effect list. */
	private final L2Character _owner;
	
	private L2Effect[] _effectCache;
	private volatile boolean _rebuildCache = true;
	private final Object _buildEffectLock = new Object();
	
	/** Set containing all abnormal types that shouldn't be added to this creature effect list. */
	private volatile Set<String> _blockedBuffSlots = null;
	/** Short buff skill ID. */
	private L2Effect _shortBuff = null;
	
	public CharEffectList(L2Character owner)
	{
		_owner = owner;
	}
	
	/**
	 * Returns all effects affecting stored in this CharEffectList
	 * @return
	 */
	public final L2Effect[] getAllEffects()
	{
		// If no effect is active, return EMPTY_EFFECTS
		if (isEmpty())
		{
			return EMPTY_EFFECTS;
		}
		
		synchronized (_buildEffectLock)
		{
			// If we dont need to rebuild the cache, just return the current one.
			if (!_rebuildCache)
			{
				return _effectCache;
			}
			
			_rebuildCache = false;
			
			// Create a copy of the effects
			List<L2Effect> temp = new ArrayList<>();
			
			// Add all buffs and all debuffs
			if (hasBuffs())
			{
				temp.addAll(getBuffs());
			}
			if (hasDebuffs())
			{
				temp.addAll(getDebuffs());
			}
			
			// Return all effects in an array
			L2Effect[] tempArray = new L2Effect[temp.size()];
			temp.toArray(tempArray);
			return (_effectCache = tempArray);
		}
	}
	
	/**
	 * Gets all the effects on this effect list.
	 * @return all the effects on this effect list
	 */
	public List<L2Effect> getEffects()
	{
		if (isEmpty())
		{
			return Collections.<L2Effect> emptyList();
		}
		
		final List<L2Effect> buffs = new ArrayList<>();
		if (hasBuffs())
		{
			buffs.addAll(getBuffs());
		}
		
		if (hasDebuffs())
		{
			buffs.addAll(getDebuffs());
		}
		return buffs;
	}
	
	/**
	 * Returns the first effect matching the given EffectType
	 * @param tp
	 * @return
	 */
	public final L2Effect getFirstEffect(L2EffectType tp)
	{
		L2Effect effectNotInUse = null;
		
		if (hasBuffs())
		{
			for (L2Effect e : getBuffs())
			{
				if (e == null)
				{
					continue;
				}
				
				if (e.getEffectType() == tp)
				{
					if (e.getInUse())
					{
						return e;
					}
					
					effectNotInUse = e;
				}
			}
		}
		if ((effectNotInUse == null) && hasDebuffs())
		{
			for (L2Effect e : getDebuffs())
			{
				if (e == null)
				{
					continue;
				}
				
				if (e.getEffectType() == tp)
				{
					if (e.getInUse())
					{
						return e;
					}
					
					effectNotInUse = e;
				}
			}
		}
		return effectNotInUse;
	}
	
	/**
	 * Returns the first effect matching the given L2Skill
	 * @param skill
	 * @return
	 */
	public final L2Effect getFirstEffect(L2Skill skill)
	{
		L2Effect effectNotInUse = null;
		
		if (skill.isDebuff())
		{
			if (hasDebuffs())
			{
				for (L2Effect e : getDebuffs())
				{
					if (e == null)
					{
						continue;
					}
					
					if (e.getSkill() == skill)
					{
						if (e.getInUse())
						{
							return e;
						}
						
						effectNotInUse = e;
					}
				}
			}
		}
		else
		{
			if (hasBuffs())
			{
				for (L2Effect e : getBuffs())
				{
					if (e == null)
					{
						continue;
					}
					
					if (e.getSkill() == skill)
					{
						if (e.getInUse())
						{
							return e;
						}
						
						effectNotInUse = e;
					}
				}
			}
		}
		return effectNotInUse;
	}
	
	/**
	 * Returns the first effect matching the given skillId
	 * @param skillId
	 * @return
	 */
	public final L2Effect getFirstEffect(int skillId)
	{
		L2Effect effectNotInUse = null;
		
		if (hasBuffs())
		{
			for (L2Effect e : getBuffs())
			{
				if (e == null)
				{
					continue;
				}
				
				if (e.getSkill().getId() == skillId)
				{
					if (e.getInUse())
					{
						return e;
					}
					
					effectNotInUse = e;
				}
			}
		}
		
		if ((effectNotInUse == null) && hasDebuffs())
		{
			for (L2Effect e : getDebuffs())
			{
				if (e == null)
				{
					continue;
				}
				if (e.getSkill().getId() == skillId)
				{
					if (e.getInUse())
					{
						return e;
					}
					
					effectNotInUse = e;
				}
			}
		}
		return effectNotInUse;
	}
	
	/**
	 * Checks if the given skill stacks with an existing one.
	 * @param checkSkill the skill to be checked
	 * @return Returns whether or not this skill will stack
	 */
	private boolean doesStack(L2Skill checkSkill)
	{
		if ((!hasBuffs()) || (checkSkill._effectTemplates == null) || (checkSkill._effectTemplates.length < 1) || (checkSkill._effectTemplates[0].abnormalType == null) || "none".equals(checkSkill._effectTemplates[0].abnormalType))
		{
			return false;
		}
		
		String stackType = checkSkill._effectTemplates[0].abnormalType;
		
		for (L2Effect e : getBuffs())
		{
			if ((e.getAbnormalType() != null) && e.getAbnormalType().equals(stackType))
			{
				return true;
			}
		}
		return false;
	}
	
	/**
	 * Return the number of buffs in this CharEffectList not counting Songs/Dances
	 * @return
	 */
	public int getBuffCount()
	{
		if (!hasBuffs())
		{
			return 0;
		}
		
		final List<Integer> buffIds = new LinkedList<>();
		for (L2Effect e : getBuffs())
		{
			if ((e != null) && e.getShowIcon() && !e.getSkill().isDance() && !e.getSkill().isTriggeredSkill() && !e.getSkill().is7Signs())
			{
				if (!e.getSkill().isPassive() && !e.getSkill().isToggle() && !e.getSkill().isDebuff() && !e.getSkill().isHealingPotionSkill())
				{
					int skillId = e.getSkill().getId();
					if (!buffIds.contains(skillId))
					{
						buffIds.add(skillId);
					}
				}
			}
		}
		
		int size = buffIds.size();
		return size;
	}
	
	/**
	 * Return the number of Songs/Dances in this CharEffectList
	 * @return
	 */
	public int getDanceCount()
	{
		if (!hasBuffs())
		{
			return 0;
		}
		
		final List<Integer> buffIds = new LinkedList<>();
		for (L2Effect e : getBuffs())
		{
			if ((e != null) && e.getSkill().isDance() && e.getInUse() && !e.isInstant())
			{
				int skillId = e.getSkill().getId();
				if (!buffIds.contains(skillId))
				{
					buffIds.add(skillId);
				}
			}
		}
		
		int size = buffIds.size();
		return size;
	}
	
	/**
	 * Return the number of Activation Buffs in this CharEffectList
	 * @return
	 */
	public int getTriggeredBuffCount()
	{
		if (!hasBuffs())
		{
			return 0;
		}
		
		final List<Integer> buffIds = new LinkedList<>();
		for (L2Effect e : getBuffs())
		{
			if ((e != null) && e.getSkill().isTriggeredSkill() && e.getInUse())
			{
				int skillId = e.getSkill().getId();
				if (!buffIds.contains(skillId))
				{
					buffIds.add(skillId);
				}
			}
		}
		
		int size = buffIds.size();
		return size;
	}
	
	/**
	 * Exits all effects in this CharEffectList
	 */
	public final void stopAllEffects()
	{
		stopAllEffects(true);
	}
	
	/**
	 * Exits all effects in this CharEffectList
	 * @param all will remove all effects even if they can't be dispelled
	 */
	public final void stopAllEffects(boolean all)
	{
		if (all)
		{
			getEffects().stream().filter(e -> e != null).forEach(e -> e.exit(true));
		}
		else
		{
			getEffects().stream().filter(e -> (e != null) && e.canBeStolen()).forEach(e -> e.exit(true));
		}
	}
	
	/**
	 * Exits all effects in this CharEffectList
	 */
	public final void stopAllEffectsExceptThoseThatLastThroughDeath()
	{
		// Get all active skills effects from this list and exit them
		getEffects().stream().filter(e -> (e != null) && !e.getSkill().isStayAfterDeath()).forEach(e -> e.exit(true));
	}
	
	/**
	 * Exit all toggle-type effects
	 */
	public void stopAllToggles()
	{
		getBuffs().stream().filter(e -> (e != null) && e.getSkill().isToggle()).forEach(e -> e.exit());
	}
	
	/**
	 * Exit all effects having a specified type
	 * @param type
	 */
	public final void stopEffects(L2EffectType type)
	{
		getBuffs().stream().filter(e -> (e != null) && (e.getEffectType() == type)).forEach(e -> stopSkillEffects(e.getSkill().getId()));
		getDebuffs().stream().filter(e -> (e != null) && (e.getEffectType() == type)).forEach(e -> stopSkillEffects(e.getSkill().getId()));
	}
	
	/**
	 * Exits all effects created by a specific skillId
	 * @param skillId
	 */
	public final void stopSkillEffects(int skillId)
	{
		getBuffs().stream().filter(e -> (e != null) && (e.getSkill().getId() == skillId)).forEach(e -> e.exit());
		getDebuffs().stream().filter(e -> (e != null) && (e.getSkill().getId() == skillId)).forEach(e -> e.exit());
	}
	
	/**
	 * Exits all buffs effects of the skills with "removedOnAnyAction" set. Called on any action except movement (attack, cast).
	 */
	public void stopEffectsOnAction()
	{
		if (_hasBuffsRemovedOnAnyAction)
		{
			getBuffs().stream().filter(e -> (e != null) && e.getSkill().isRemovedOnAnyActionExceptMove()).forEach(e -> e.exit(true));
		}
	}
	
	public void stopEffectsOnDamage(boolean awake)
	{
		if (_hasBuffsRemovedOnDamage)
		{
			if (hasBuffs())
			{
				for (L2Effect e : getBuffs())
				{
					if ((e != null) && e.getSkill().isRemovedOnDamage() && (awake || (e.getSkill().getSkillType() != L2SkillType.SLEEP)))
					{
						e.exit(true);
					}
				}
			}
		}
		if (_hasDebuffsRemovedOnDamage)
		{
			if (hasDebuffs())
			{
				for (L2Effect e : getDebuffs())
				{
					if ((e != null) && e.getSkill().isRemovedOnDamage() && (awake || (e.getSkill().getSkillType() != L2SkillType.SLEEP)))
					{
						e.exit(true);
					}
				}
			}
		}
	}
	
	public void updateEffectIcons(boolean partyOnly)
	{
		if (!hasBuffs() && !hasDebuffs())
		{
			return;
		}
		
		if (partyOnly)
		{
			_partyOnly = true;
		}
		
		queueRunner();
	}
	
	public void queueEffect(L2Effect effect, boolean remove)
	{
		if (effect == null)
		{
			return;
		}
		
		if (!_queuesInitialized)
		{
			init();
		}
		
		if (remove)
		{
			_removeQueue.offer(effect);
		}
		else
		{
			_addQueue.offer(effect);
		}
		
		queueRunner();
	}
	
	private synchronized void init()
	{
		if (_queuesInitialized)
		{
			return;
		}
		_addQueue = new LinkedBlockingQueue<>();
		_removeQueue = new LinkedBlockingQueue<>();
		_queuesInitialized = true;
	}
	
	private void queueRunner()
	{
		if (!queueLock.compareAndSet(false, true))
		{
			return;
		}
		
		try
		{
			L2Effect effect;
			do
			{
				// remove has more priority than add
				// so removing all effects from queue first
				while ((effect = _removeQueue.poll()) != null)
				{
					removeEffectFromQueue(effect);
					_partyOnly = false;
				}
				
				if ((effect = _addQueue.poll()) != null)
				{
					addEffectFromQueue(effect);
					_partyOnly = false;
				}
			}
			while (!_addQueue.isEmpty() || !_removeQueue.isEmpty());
			
			computeEffectFlags();
			updateEffectIcons();
		}
		finally
		{
			queueLock.set(false);
		}
	}
	
	protected void removeEffectFromQueue(L2Effect effect)
	{
		if (effect == null)
		{
			return;
		}
		
		if (effect.isPassiveEffect())
		{
			if (effect.setInUse(false))
			{
				// Remove Func added by this effect from the L2Character Calculator
				_owner.removeStatsOwner(effect);
				getPassives().remove(effect);
			}
		}
		
		List<L2Effect> effectList;
		
		// array modified, then rebuild on next request
		_rebuildCache = true;
		
		if (effect.getSkill().isDebuff() || effect.getSkill().isOffensive())
		{
			if (!hasDebuffs())
			{
				return;
			}
			effectList = getDebuffs();
		}
		else
		{
			if (!hasBuffs())
			{
				return;
			}
			effectList = getBuffs();
		}
		
		if ("none".equals(effect.getAbnormalType()))
		{
			// Remove Func added by this effect from the L2Character Calculator
			_owner.removeStatsOwner(effect);
		}
		else
		{
			if (_stackedEffects == null)
			{
				return;
			}
			
			// Get the list of all stacked effects corresponding to the stack type of the L2Effect to add
			List<L2Effect> stackQueue = _stackedEffects.get(effect.getAbnormalType());
			if ((stackQueue == null) || stackQueue.isEmpty())
			{
				return;
			}
			
			int index = stackQueue.indexOf(effect);
			
			// Remove the effect from the stack group
			if (index >= 0)
			{
				stackQueue.remove(effect);
				// Check if the first stacked effect was the effect to remove
				if (index == 0)
				{
					// Remove all its Func objects from the L2Character calculator set
					_owner.removeStatsOwner(effect);
					
					// Check if there's another effect in the Stack Group
					if (!stackQueue.isEmpty())
					{
						L2Effect newStackedEffect = listsContains(stackQueue.get(0));
						if (newStackedEffect != null)
						{
							// Set the effect to In Use
							if (newStackedEffect.setInUse(true))
							{
								// Add its list of Funcs to the Calculator set of the L2Character
								_owner.addStatFuncs(newStackedEffect.getStatFuncs());
							}
						}
					}
				}
				if (stackQueue.isEmpty())
				{
					_stackedEffects.remove(effect.getAbnormalType());
				}
				else
				{
					// Update the Stack Group table _stackedEffects of the L2Character
					_stackedEffects.put(effect.getAbnormalType(), stackQueue);
				}
			}
		}
		
		// Remove the active skill L2effect from _effects of the L2Character
		if (effectList.remove(effect) && _owner.isPlayer() && effect.getShowIcon() && !effect.isInstant())
		{
			if ((effect.getCount() == 0) && effect.getShowIcon() && !effect.isInstant())
			{
				SystemMessage smsg3 = SystemMessage.getSystemMessage(SystemMessageId.S1_HAS_WORN_OFF);
				smsg3.addSkillName(effect.getSkill());
				_owner.sendPacket(smsg3);
			}
			else
			{
				SystemMessage sm = effect.getSkill().isToggle() ? SystemMessage.getSystemMessage(SystemMessageId.S1_HAS_BEEN_ABORTED) : SystemMessage.getSystemMessage(SystemMessageId.EFFECT_S1_HAS_BEEN_REMOVED);
				sm.addSkillName(effect);
				_owner.sendPacket(sm);
			}
		}
		
		// Remove short buff.
		if (effect == _owner.getEffectList().getShortBuff())
		{
			_owner.getEffectList().shortBuffStatusUpdate(null);
		}
	}
	
	protected void addEffectFromQueue(L2Effect newEffect)
	{
		if (newEffect == null)
		{
			return;
		}
		
		// Support for blocked buff slots.
		L2Skill newSkill = newEffect.getSkill();
		if ((_blockedBuffSlots != null) && _blockedBuffSlots.contains(newEffect.getAbnormalType()))
		{
			return;
		}
		
		// Passive effects are treated specially
		if (newEffect.isPassiveEffect())
		{
			// Passive effects dont need stack type
			if ("none".equals(newEffect.getAbnormalType()))
			{
				// Set this L2Effect to In Use
				if (newEffect.setInUse(true))
				{
					for (L2Effect eff : getPassives())
					{
						if (eff == null)
						{
							continue;
						}
						
						// Check and remove if there is already such effect in order to prevent passive effects overstack.
						if (eff.getEffectTemplate().equals(newEffect.getEffectTemplate()))
						{
							eff.exit();
						}
					}
					
					// Add Funcs of this effect to the Calculator set of the L2Character
					_owner.addStatFuncs(newEffect.getStatFuncs());
					getPassives().add(newEffect);
				}
			}
			
			return;
		}
		
		// array modified, then rebuild on next request
		_rebuildCache = true;
		
		if (newSkill.isDebuff() || newSkill.isOffensive())
		{
			for (L2Effect e : getDebuffs())
			{
				if ((e != null) && (e.getSkill().getId() == newEffect.getSkill().getId()) && (e.getEffectType() == newEffect.getEffectType()) && (e.getAbnormalLvl() == newEffect.getAbnormalLvl()) && e.getAbnormalType().equals(newEffect.getAbnormalType()))
				{
					// Started scheduled timer needs to be canceled.
					newEffect.stopEffectTask();
					return;
				}
			}
			getDebuffs().add(newEffect);
		}
		else
		{
			for (L2Effect e : getBuffs())
			{
				if ((e != null) && (e.getSkill().getId() == newEffect.getSkill().getId()) && (e.getEffectType() == newEffect.getEffectType()) && (e.getAbnormalLvl() == newEffect.getAbnormalLvl()) && e.getAbnormalType().equals(newEffect.getAbnormalType()))
				{
					if ((e.getEffectType() != L2EffectType.BLOCK_DAMAGE) || (newEffect.getEffectType() != L2EffectType.BLOCK_DAMAGE))
					{
						stopSkillEffects(e.getSkill().getId());
					}
				}
				
				//@formatter:off
				if (
						((e != null) && !newEffect.getSkill().isHerb())
						&& !e.getAbnormalType().equals("none")
						&& (e.getSkill().getId() != newEffect.getSkill().getId())
						&& (e.getSkill().getSkillType() == L2SkillType.BUFF)
						&& e.getAbnormalType().equals(newEffect.getAbnormalType())
						&& (newEffect.getAbnormalLvl() >= e.getAbnormalLvl())
					)
				{
					stopSkillEffects(e.getSkill().getId());
				}
				//@formatter:on
			}
			
			// Remove first buff when buff list is full
			if (!doesStack(newSkill) && !newSkill.is7Signs())
			{
				int effectsToRemove;
				if (newSkill.isDance())
				{
					effectsToRemove = getDanceCount() - Config.DANCES_MAX_AMOUNT;
					if (effectsToRemove >= 0)
					{
						for (L2Effect e : getBuffs())
						{
							if ((e == null) || !e.getSkill().isDance())
							{
								continue;
							}
							
							// get first dance
							stopSkillEffects(e.getSkill().getId());
							effectsToRemove--;
							if (effectsToRemove < 0)
							{
								break;
							}
						}
					}
				}
				else if (newSkill.isTriggeredSkill())
				{
					effectsToRemove = getTriggeredBuffCount() - Config.TRIGGERED_BUFFS_MAX_AMOUNT;
					if (effectsToRemove >= 0)
					{
						for (L2Effect e : getBuffs())
						{
							if ((e == null) || !e.getSkill().isTriggeredSkill())
							{
								continue;
							}
							
							// get first trigger
							stopSkillEffects(e.getSkill().getId());
							effectsToRemove--;
							if (effectsToRemove < 0)
							{
								break;
							}
						}
					}
				}
				else if (!newSkill.isHealingPotionSkill() && !newEffect.isInstant())
				{
					effectsToRemove = getBuffCount() - _owner.getStat().getMaxBuffCount();
					if (effectsToRemove >= 0)
					{
						if (newSkill.getSkillType() == L2SkillType.BUFF)
						{
							for (L2Effect e : getBuffs())
							{
								if ((e == null) || e.getSkill().isDance() || e.getSkill().isTriggeredSkill())
								{
									continue;
								}
								
								if (e.getSkill().getAbnormalType() == AbnormalType.summon_condition)
								{
									continue;
								}
								
								if (e.getSkill().getSkillType() == L2SkillType.BUFF)
								{
									stopSkillEffects(e.getSkill().getId());
									effectsToRemove--;
								}
								else
								{
									continue; // continue for()
								}
								
								if (effectsToRemove < 0)
								{
									break; // break for()
								}
							}
						}
					}
				}
			}
			
			// Icons order: buffs, 7s, toggles, dances, activation buffs
			if (newSkill.isTriggeredSkill())
			{
				getBuffs().add(newEffect);
			}
			else
			{
				int pos = 0;
				if (newSkill.isToggle())
				{
					// toggle skill - before all dances
					for (L2Effect e : getBuffs())
					{
						if (e == null)
						{
							continue;
						}
						if (e.getSkill().isDance())
						{
							break;
						}
						pos++;
					}
				}
				else if (newSkill.isDance())
				{
					// dance skill - before all activation buffs
					for (L2Effect e : getBuffs())
					{
						if (e == null)
						{
							continue;
						}
						if (e.getSkill().isTriggeredSkill())
						{
							break;
						}
						pos++;
					}
				}
				else
				{
					// normal buff - before toggles and 7s and dances
					for (L2Effect e : getBuffs())
					{
						if (e == null)
						{
							continue;
						}
						if (e.getSkill().isToggle() || e.getSkill().is7Signs() || e.getSkill().isDance() || e.getSkill().isTriggeredSkill())
						{
							break;
						}
						pos++;
					}
				}
				getBuffs().add(pos, newEffect);
			}
		}
		
		// Check if a stack group is defined for this effect
		if ("none".equals(newEffect.getAbnormalType()))
		{
			// Set this L2Effect to In Use
			if (newEffect.setInUse(true))
			{
				// Add Funcs of this effect to the Calculator set of the L2Character
				_owner.addStatFuncs(newEffect.getStatFuncs());
			}
			
			return;
		}
		
		L2Effect effectToAdd = null;
		L2Effect effectToRemove = null;
		if (_stackedEffects == null)
		{
			_stackedEffects = new ConcurrentHashMap<>();
		}
		
		// Get the list of all stacked effects corresponding to the stack type of the L2Effect to add
		List<L2Effect> stackQueue = _stackedEffects.get(newEffect.getAbnormalType());
		if (stackQueue == null)
		{
			stackQueue = new ArrayList<>();
		}
		
		if (!stackQueue.isEmpty())
		{
			// Get the first stacked effect of the Stack group selected
			effectToRemove = listsContains(stackQueue.get(0));
			
			// Create an Iterator to go through the list of stacked effects in progress on the L2Character
			Iterator<L2Effect> queueIterator = stackQueue.iterator();
			
			int pos = 0;
			while (queueIterator.hasNext())
			{
				if ((newEffect.getAbnormalLvl() < queueIterator.next().getAbnormalLvl()) && !newEffect.getSkill().isStatic())
				{
					pos++;
				}
				else
				{
					break;
				}
			}
			// Add the new effect to the Stack list in function of its position in the Stack group
			stackQueue.add(pos, newEffect);
			
			// skill.exit() could be used, if the users don't wish to see "effect
			// removed" always when a timer goes off, even if the buff isn't active
			// any more (has been replaced). but then check e.g. npc hold and raid petrification.
			if (Config.EFFECT_CANCELING && !newEffect.getSkill().isStatic() && (stackQueue.size() > 1))
			{
				if (newSkill.isDebuff() || newSkill.isOffensive())
				{
					getDebuffs().remove(stackQueue.remove(1));
				}
				else
				{
					getBuffs().remove(stackQueue.remove(1));
				}
			}
		}
		else
		{
			stackQueue.add(0, newEffect);
		}
		
		// Update the Stack Group table _stackedEffects of the L2Character
		_stackedEffects.put(newEffect.getAbnormalType(), stackQueue);
		
		// Get the first stacked effect of the Stack group selected
		if (!stackQueue.isEmpty())
		{
			effectToAdd = listsContains(stackQueue.get(0));
		}
		
		if (effectToRemove != effectToAdd)
		{
			if (effectToRemove != null)
			{
				// Remove all Func objects corresponding to this stacked effect from the Calculator set of the L2Character
				_owner.removeStatsOwner(effectToRemove);
				
				// Set the L2Effect to Not In Use
				effectToRemove.setInUse(false);
			}
			if (effectToAdd != null)
			{
				// Set this L2Effect to In Use
				if (effectToAdd.setInUse(true))
				{
					// Add all Func objects corresponding to this stacked effect to the Calculator set of the L2Character
					_owner.addStatFuncs(effectToAdd.getStatFuncs());
				}
			}
		}
	}
	
	/**
	 * Remove all passive effects held by this <b>skillId</b>.
	 * @param skillId
	 */
	public void removePassiveEffects(int skillId)
	{
		if (!hasPassives())
		{
			return;
		}
		
		for (L2Effect eff : getPassives())
		{
			if (eff == null)
			{
				continue;
			}
			
			if (eff.getSkill().getId() == skillId)
			{
				eff.exit(false, true);
				getPassives().remove(eff);
			}
		}
	}
	
	private ScheduledFuture<?> _effectIconsUpdate;
	
	protected void updateEffectIcons()
	{
		if (_owner == null)
		{
			return;
		}
		
		// Check if the previous call hasn't finished, if so, don't send packets uselessly again.
		if ((_effectIconsUpdate != null) && !_effectIconsUpdate.isDone())
		{
			return;
		}
		
		// Schedule the icon update packets 500miliseconds ahead, so it can gather-up most of the changes.
		_effectIconsUpdate = ThreadPoolManager.getInstance().scheduleGeneral(() -> updateEffectIconsTask(), Config.effects_packetsDelay);
	}
	
	private void updateEffectIconsTask()
	{
		if (_owner == null)
		{
			return;
		}
		
		updateEffectFlags();
		
		if (!_owner.isPlayable())
		{
			return;
		}
		
		AbnormalStatusUpdate asu = null;
		PartySpelled ps = null;
		PartySpelled psSummon = null;
		ExOlympiadSpelledInfo os = null;
		boolean isSummon = false;
		
		if (_owner.isPlayer())
		{
			if (_partyOnly)
			{
				_partyOnly = false;
			}
			else
			{
				asu = new AbnormalStatusUpdate();
			}
			
			if (_owner.isInParty())
			{
				ps = new PartySpelled(_owner);
			}
			
			if (_owner.getActingPlayer().isInOlympiadMode() && _owner.getActingPlayer().isOlympiadStart())
			{
				os = new ExOlympiadSpelledInfo(_owner.getActingPlayer());
			}
		}
		else if (_owner.isSummon())
		{
			isSummon = true;
			ps = new PartySpelled(_owner);
			psSummon = new PartySpelled(_owner);
		}
		
		if (hasBuffs())
		{
			for (L2Effect e : getBuffs())
			{
				if ((e == null) || !e.getShowIcon() || e.isInstant() || (e.getEffectType() == L2EffectType.SIGNET_GROUND))
				{
					continue;
				}
				
				if (e.getInUse())
				{
					if (e.getSkill().isHealingPotionSkill())
					{
						shortBuffStatusUpdate(e);
					}
					else
					{
						addIcon(e, asu, ps, psSummon, os, isSummon);
					}
				}
			}
		}
		
		if (hasDebuffs())
		{
			for (L2Effect e : getDebuffs())
			{
				if ((e == null) || !e.getShowIcon() || e.isInstant() || (e.getEffectType() == L2EffectType.SIGNET_GROUND))
				{
					continue;
				}
				
				if (e.getInUse())
				{
					addIcon(e, asu, ps, psSummon, os, isSummon);
				}
			}
		}
		
		if (asu != null)
		{
			_owner.sendPacket(asu);
		}
		
		if (ps != null)
		{
			if (_owner.isSummon())
			{
				L2PcInstance summonOwner = ((L2Summon) _owner).getOwner();
				
				if (summonOwner != null)
				{
					if (summonOwner.isInParty())
					{
						summonOwner.getParty().broadcastToPartyMembers(summonOwner, psSummon); // send to all member except summonOwner
					}
					summonOwner.sendPacket(ps);
				}
			}
			else if (_owner.isPlayer() && _owner.isInParty())
			{
				_owner.getParty().broadcastPacket(ps);
			}
		}
		
		if (os != null)
		{
			final OlympiadGameTask game = OlympiadGameManager.getInstance().getOlympiadTask(_owner.getActingPlayer().getOlympiadGameId());
			if ((game != null) && game.isBattleStarted())
			{
				game.getZone().broadcastPacketToObservers(os);
			}
		}
	}
	
	protected void updateEffectFlags()
	{
		if (hasBuffs())
		{
			for (L2Effect info : getBuffs())
			{
				if (info == null)
				{
					continue;
				}
				
				if (info.getSkill().isRemovedOnAnyActionExceptMove())
				{
					_hasBuffsRemovedOnAnyAction = true;
				}
				
				if (info.getSkill().isRemovedOnDamage())
				{
					_hasBuffsRemovedOnDamage = true;
				}
			}
		}
		
		if (hasDebuffs())
		{
			for (L2Effect info : getDebuffs())
			{
				if ((info != null) && info.getSkill().isRemovedOnDamage())
				{
					_hasDebuffsRemovedOnDamage = true;
				}
			}
		}
	}
	
	/**
	 * Returns effect if contains in _buffs or _debuffs and null if not found
	 * @param effect
	 * @return
	 */
	private L2Effect listsContains(L2Effect effect)
	{
		if (hasBuffs() && getBuffs().contains(effect))
		{
			return effect;
		}
		if (hasDebuffs() && getDebuffs().contains(effect))
		{
			return effect;
		}
		return null;
	}
	
	/**
	 * Recalculate effect bits flag.<br>
	 * Please no concurrency access
	 */
	private final void computeEffectFlags()
	{
		int flags = 0;
		
		if (hasBuffs())
		{
			for (L2Effect e : getBuffs())
			{
				if (e == null)
				{
					continue;
				}
				flags |= e.getEffectFlags();
			}
		}
		
		if (hasDebuffs())
		{
			for (L2Effect e : getDebuffs())
			{
				if (e == null)
				{
					continue;
				}
				flags |= e.getEffectFlags();
			}
		}
		
		if (hasPassives())
		{
			for (L2Effect e : getPassives())
			{
				if (e == null)
				{
					continue;
				}
				flags |= e.getEffectFlags();
			}
		}
		
		_effectFlags = flags;
	}
	
	/**
	 * Verify if this effect list is empty.
	 * @return {@code true} if both {@link #_buffs} and {@link #_debuffs} are either {@code null} or empty
	 */
	public boolean isEmpty()
	{
		return ((_buffs == null) || _buffs.isEmpty()) && ((_debuffs == null) || _debuffs.isEmpty());
	}
	
	/**
	 * Verify if this effect list has buffs effects.
	 * @return {@code true} if {@link #_buffs} is not {@code null} and is not empty
	 */
	public boolean hasBuffs()
	{
		return (_buffs != null) && !_buffs.isEmpty();
	}
	
	/**
	 * Verify if this effect list has debuffs effects.
	 * @return {@code true} if {@link #_debuffs} is not {@code null} and is not empty
	 */
	public boolean hasDebuffs()
	{
		return (_debuffs != null) && !_debuffs.isEmpty();
	}
	
	/**
	 * Verify if this effect list has passive skills.<br>
	 * Prevents initialization.
	 * @return {@code true} if {@link #_passives} is not {@code null} and is not empty
	 */
	public boolean hasPassives()
	{
		return (_passives != null) && !_passives.isEmpty();
	}
	
	/**
	 * Gets buff skills.
	 * @return the buff skills
	 */
	public List<L2Effect> getBuffs()
	{
		if (_buffs == null)
		{
			_buffs = new CopyOnWriteArrayList<>();
		}
		return _buffs;
	}
	
	/**
	 * Gets debuff skills.
	 * @return the debuff skills
	 */
	public List<L2Effect> getDebuffs()
	{
		if (_debuffs == null)
		{
			_debuffs = new CopyOnWriteArrayList<>();
		}
		return _debuffs;
	}
	
	/**
	 * Gets the debuff skills count.<br>
	 * Prevents initialization.
	 * @return the number of debuff effects in this creature effect list
	 */
	public int getDebuffCount()
	{
		if (_debuffs == null)
		{
			_debuffs = new CopyOnWriteArrayList<>();
		}
		return _debuffs.size();
	}
	
	/**
	 * Gets passive skills.
	 * @return the passive skills
	 */
	public List<L2Effect> getPassives()
	{
		if (_passives == null)
		{
			_passives = new CopyOnWriteArrayList<>();
		}
		return _passives;
	}
	
	/**
	 * Verifies if this effect list contains the given skill ID.<br>
	 * Prevents initialization.
	 * @param skillId the skill ID to verify
	 * @return {@code true} if the skill ID is present in the effect list, {@code false} otherwise
	 */
	public boolean isAffectedBySkill(int skillId)
	{
		if (hasBuffs())
		{
			for (L2Effect e : getBuffs())
			{
				if (e == null)
				{
					continue;
				}
				
				if (e.getSkill().getId() == skillId)
				{
					return true;
				}
			}
		}
		
		if (hasDebuffs())
		{
			for (L2Effect e : getDebuffs())
			{
				if (e == null)
				{
					continue;
				}
				if (e.getSkill().getId() == skillId)
				{
					return true;
				}
			}
		}
		
		if (hasPassives())
		{
			for (L2Effect e : getPassives())
			{
				if (e == null)
				{
					continue;
				}
				if (e.getSkill().getId() == skillId)
				{
					return true;
				}
			}
		}
		
		return false;
	}
	
	/**
	 * Check if target is affected with special buff
	 * @param flag of special buff
	 * @return boolean true if affected
	 */
	public boolean isAffected(EffectFlag flag)
	{
		return (_effectFlags & flag.getMask()) != 0;
	}
	
	/**
	 * Clear and null all queues and lists Use only during delete character from the world.
	 */
	public void clear()
	{
		/*
		 * Removed .clear() since nodes/entries and its references (Effects) should be terminated by GC when Queue/Map/List object has no more reference. This way we will save a little more CPU [DrHouse]
		 */
		try
		{
			_addQueue = null;
			_removeQueue = null;
			_buffs = null;
			_debuffs = null;
			_stackedEffects = null;
			_queuesInitialized = false;
			_effectCache = null;
		}
		catch (Exception e)
		{
			_log.warn(String.valueOf(e));
		}
	}
	
	/**
	 * Adds abnormal types to the blocked buff slot set.
	 * @param blockedBuffSlots the blocked buff slot set to add
	 */
	public void addBlockedBuffSlots(Set<String> blockedBuffSlots)
	{
		if (_blockedBuffSlots == null)
		{
			synchronized (this)
			{
				if (_blockedBuffSlots == null)
				{
					_blockedBuffSlots = ConcurrentHashMap.newKeySet(blockedBuffSlots.size());
				}
			}
		}
		_blockedBuffSlots.addAll(blockedBuffSlots);
	}
	
	/**
	 * Removes abnormal types from the blocked buff slot set.
	 * @param blockedBuffSlots the blocked buff slot set to remove
	 * @return {@code true} if the blocked buff slots set has been modified, {@code false} otherwise
	 */
	public boolean removeBlockedBuffSlots(Set<String> blockedBuffSlots)
	{
		if (_blockedBuffSlots != null)
		{
			return _blockedBuffSlots.removeAll(blockedBuffSlots);
		}
		return false;
	}
	
	/**
	 * Gets all the blocked abnormal types for this creature effect list.
	 * @return the current blocked buff slots set
	 */
	public Set<String> getAllBlockedBuffSlots()
	{
		return _blockedBuffSlots;
	}
	
	/**
	 * Gets the Short Buff info.
	 * @return the short buff info
	 */
	public L2Effect getShortBuff()
	{
		return _shortBuff;
	}
	
	/**
	 * Sets the Short Buff data and sends an update if the effected is a player.
	 * @param info the buff info
	 */
	public void shortBuffStatusUpdate(L2Effect info)
	{
		if (_owner.isPlayer())
		{
			_shortBuff = info;
			if (info == null)
			{
				_owner.sendPacket(ShortBuffStatusUpdate.RESET_SHORT_BUFF);
			}
			else
			{
				_owner.sendPacket(new ShortBuffStatusUpdate(info.getSkill().getId(), info.getSkill().getLevel(), info.getRemainingTime()));
			}
		}
	}
	
	private void addIcon(L2Effect info, AbnormalStatusUpdate asu, PartySpelled ps, PartySpelled psSummon, ExOlympiadSpelledInfo os, boolean isSummon)
	{
		// Avoid null and not in use buffs.
		if ((info == null) || !info.getInUse())
		{
			return;
		}
		
		final L2Skill skill = info.getSkill();
		if (asu != null)
		{
			asu.addSkill(info);
		}
		
		if ((ps != null) && (isSummon || !skill.isToggle()))
		{
			ps.addSkill(info);
		}
		
		if ((psSummon != null) && !skill.isToggle())
		{
			psSummon.addSkill(info);
		}
		
		if (os != null)
		{
			os.addSkill(info);
		}
	}
}
