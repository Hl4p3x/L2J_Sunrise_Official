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
package l2r.gameserver.model.actor.instance;

import static l2r.gameserver.enums.CtrlIntention.AI_INTENTION_IDLE;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Future;

import l2r.gameserver.ThreadPoolManager;
import l2r.gameserver.data.sql.NpcTable;
import l2r.gameserver.data.xml.impl.SkillData;
import l2r.gameserver.enums.CtrlIntention;
import l2r.gameserver.enums.InstanceType;
import l2r.gameserver.model.L2Object;
import l2r.gameserver.model.Location;
import l2r.gameserver.model.actor.L2Character;
import l2r.gameserver.model.effects.L2EffectType;
import l2r.gameserver.model.items.instance.L2ItemInstance;
import l2r.gameserver.model.skills.L2Skill;
import l2r.gameserver.model.skills.L2SkillType;
import l2r.gameserver.network.serverpackets.ActionFailed;
import l2r.gameserver.network.serverpackets.SocialAction;
import l2r.util.Rnd;

// While a tamed beast behaves a lot like a pet (ingame) and does have
// an owner, in all other aspects, it acts like a mob.
// In addition, it can be fed in order to increase its duration.
// This class handles the running tasks, AI, and feed of the mob.
// The (mostly optional) AI on feeding the spawn is handled by the datapack ai script
public final class L2TamedBeastInstance extends L2FeedableBeastInstance
{
	private int _foodSkillId;
	private static final int MAX_DISTANCE_FROM_HOME = 30000;
	private static final int MAX_DISTANCE_FROM_OWNER = 2000;
	private static final int MAX_DURATION = 1200000; // 20 minutes
	private static final int DURATION_CHECK_INTERVAL = 60000; // 1 minute
	private static final int DURATION_INCREASE_INTERVAL = 20000; // 20 secs (gained upon feeding)
	private static final int BUFF_INTERVAL = 5000; // 5 seconds
	private int _remainingTime = MAX_DURATION;
	private int _homeX, _homeY, _homeZ;
	protected L2PcInstance _owner;
	private Future<?> _buffTask = null;
	private Future<?> _durationCheckTask = null;
	protected boolean _isFreyaBeast;
	private List<L2Skill> _beastSkills = null;
	
	public L2TamedBeastInstance(int npcTemplateId)
	{
		super(NpcTable.getInstance().getTemplate(npcTemplateId));
		setInstanceType(InstanceType.L2TamedBeastInstance);
		setHome(this);
	}
	
	public L2TamedBeastInstance(int npcTemplateId, L2PcInstance owner, int foodSkillId, int x, int y, int z)
	{
		super(NpcTable.getInstance().getTemplate(npcTemplateId));
		_isFreyaBeast = false;
		setInstanceType(InstanceType.L2TamedBeastInstance);
		setCurrentHp(getMaxHp());
		setCurrentMp(getMaxMp());
		setOwner(owner);
		setFoodType(foodSkillId);
		setHome(x, y, z);
		spawnMe(x, y, z);
	}
	
	public L2TamedBeastInstance(int npcTemplateId, L2PcInstance owner, int food, int x, int y, int z, boolean isFreyaBeast)
	{
		super(NpcTable.getInstance().getTemplate(npcTemplateId));
		_isFreyaBeast = isFreyaBeast;
		setInstanceType(InstanceType.L2TamedBeastInstance);
		setCurrentHp(getMaxHp());
		setCurrentMp(getMaxMp());
		setFoodType(food);
		setHome(x, y, z);
		spawnMe(x, y, z);
		setOwner(owner);
		if (isFreyaBeast)
		{
			getAI().setIntention(CtrlIntention.AI_INTENTION_FOLLOW, _owner);
		}
	}
	
	public void onReceiveFood()
	{
		// Eating food extends the duration by 20secs, to a max of 20minutes
		_remainingTime = _remainingTime + DURATION_INCREASE_INTERVAL;
		if (_remainingTime > MAX_DURATION)
		{
			_remainingTime = MAX_DURATION;
		}
	}
	
	public Location getHome()
	{
		return new Location(_homeX, _homeY, _homeZ);
	}
	
	public void setHome(int x, int y, int z)
	{
		_homeX = x;
		_homeY = y;
		_homeZ = z;
	}
	
	public void setHome(L2Character c)
	{
		setHome(c.getX(), c.getY(), c.getZ());
	}
	
	public int getRemainingTime()
	{
		return _remainingTime;
	}
	
	public void setRemainingTime(int duration)
	{
		_remainingTime = duration;
	}
	
	public int getFoodType()
	{
		return _foodSkillId;
	}
	
	public void setFoodType(int foodItemId)
	{
		if (foodItemId > 0)
		{
			_foodSkillId = foodItemId;
			
			// start the duration checks
			// start the buff tasks
			if (_durationCheckTask != null)
			{
				_durationCheckTask.cancel(true);
			}
			_durationCheckTask = ThreadPoolManager.getInstance().scheduleGeneralAtFixedRate(new CheckDuration(this), DURATION_CHECK_INTERVAL, DURATION_CHECK_INTERVAL);
		}
	}
	
	@Override
	public boolean doDie(L2Character killer)
	{
		if (!super.doDie(killer))
		{
			return false;
		}
		
		getAI().stopFollow();
		if (_buffTask != null)
		{
			_buffTask.cancel(true);
		}
		if (_durationCheckTask != null)
		{
			_durationCheckTask.cancel(true);
		}
		
		// clean up variables
		if ((_owner != null) && (_owner.getTrainedBeasts() != null))
		{
			_owner.getTrainedBeasts().remove(this);
		}
		_buffTask = null;
		_durationCheckTask = null;
		_owner = null;
		_foodSkillId = 0;
		_remainingTime = 0;
		return true;
	}
	
	@Override
	public boolean isAutoAttackable(L2Character attacker)
	{
		return !_isFreyaBeast;
	}
	
	public boolean isFreyaBeast()
	{
		return _isFreyaBeast;
	}
	
	public void addBeastSkill(L2Skill skill)
	{
		if (_beastSkills == null)
		{
			_beastSkills = new CopyOnWriteArrayList<>();
		}
		_beastSkills.add(skill);
	}
	
	public void castBeastSkills()
	{
		if ((_owner == null) || (_beastSkills == null))
		{
			return;
		}
		int delay = 100;
		for (L2Skill skill : _beastSkills)
		{
			ThreadPoolManager.getInstance().scheduleGeneral(new buffCast(skill), delay);
			delay += (100 + skill.getHitTime());
		}
		ThreadPoolManager.getInstance().scheduleGeneral(new buffCast(null), delay);
	}
	
	private class buffCast implements Runnable
	{
		private final L2Skill _skill;
		
		public buffCast(L2Skill skill)
		{
			_skill = skill;
		}
		
		@Override
		public void run()
		{
			if (_skill == null)
			{
				getAI().setIntention(CtrlIntention.AI_INTENTION_FOLLOW, _owner);
			}
			else
			{
				sitCastAndFollow(_skill, _owner);
			}
		}
	}
	
	public L2PcInstance getOwner()
	{
		return _owner;
	}
	
	public void setOwner(L2PcInstance owner)
	{
		if (owner != null)
		{
			_owner = owner;
			setTitle(owner.getName());
			// broadcast the new title
			setShowSummonAnimation(true);
			sendInfo(owner);
			
			owner.addTrainedBeast(this);
			
			// always and automatically follow the owner.
			getAI().startFollow(_owner, 100);
			
			if (!_isFreyaBeast)
			{
				// instead of calculating this value each time, let's get this now and pass it on
				int totalBuffsAvailable = 0;
				for (L2Skill skill : getTemplate().getSkills().values())
				{
					// if the skill is a buff, check if the owner has it already [ owner.getEffect(L2Skill skill) ]
					if (skill.getSkillType() == L2SkillType.BUFF)
					{
						totalBuffsAvailable++;
					}
				}
				
				// start the buff tasks
				if (_buffTask != null)
				{
					_buffTask.cancel(true);
				}
				_buffTask = ThreadPoolManager.getInstance().scheduleGeneralAtFixedRate(new CheckOwnerBuffs(this, totalBuffsAvailable), BUFF_INTERVAL, BUFF_INTERVAL);
			}
		}
		else
		{
			deleteMe(); // despawn if no owner
		}
	}
	
	public boolean isTooFarFromHome()
	{
		return !isInsideRadius(_homeX, _homeY, _homeZ, MAX_DISTANCE_FROM_HOME, true, true);
	}
	
	@Override
	public void deleteMe()
	{
		if (_buffTask != null)
		{
			_buffTask.cancel(true);
		}
		_durationCheckTask.cancel(true);
		stopHpMpRegeneration();
		
		// clean up variables
		if ((_owner != null) && (_owner.getTrainedBeasts() != null))
		{
			_owner.getTrainedBeasts().remove(this);
		}
		setTarget(null);
		_buffTask = null;
		_durationCheckTask = null;
		_owner = null;
		_foodSkillId = 0;
		_remainingTime = 0;
		
		// remove the spawn
		super.deleteMe();
	}
	
	// notification triggered by the owner when the owner is attacked.
	// tamed mobs will heal/recharge or debuff the enemy according to their skills
	public void onOwnerGotAttacked(L2Character attacker)
	{
		// check if the owner is no longer around...if so, despawn
		if ((_owner == null) || !_owner.isOnline())
		{
			deleteMe();
			return;
		}
		// if the owner is too far away, stop anything else and immediately run towards the owner.
		if (!_owner.isInsideRadius(this, MAX_DISTANCE_FROM_OWNER, true, true))
		{
			getAI().startFollow(_owner);
			return;
		}
		// if the owner is dead, do nothing...
		if (_owner.isDead() || _isFreyaBeast)
		{
			return;
		}
		
		// if the tamed beast is currently in the middle of casting, let it complete its skill...
		if (isCastingNow())
		{
			return;
		}
		
		float HPRatio = ((float) _owner.getCurrentHp()) / _owner.getMaxHp();
		
		// if the owner has a lot of HP, then debuff the enemy with a random debuff among the available skills
		// use of more than one debuff at this moment is acceptable
		if (HPRatio >= 0.8)
		{
			for (L2Skill skill : getTemplate().getSkills().values())
			{
				// if the skill is a debuff, check if the attacker has it already [ attacker.getEffect(L2Skill skill) ]
				if ((skill.getSkillType() == L2SkillType.DEBUFF) && (Rnd.get(3) < 1) && ((attacker != null) && (attacker.getFirstEffect(skill) != null)))
				{
					sitCastAndFollow(skill, attacker);
				}
			}
		}
		// for HP levels between 80% and 50%, do not react to attack events (so that MP can regenerate a bit)
		// for lower HP ranges, heal or recharge the owner with 1 skill use per attack.
		else if (HPRatio < 0.5)
		{
			int chance = 1;
			if (HPRatio < 0.25)
			{
				chance = 2;
			}
			
			// if the owner has a lot of HP, then debuff the enemy with a random debuff among the available skills
			for (L2Skill skill : getTemplate().getSkills().values())
			{
				// if the skill is a buff, check if the owner has it already [ owner.getEffect(L2Skill skill) ]
				if ((Rnd.get(5) < chance) && skill.hasEffectType(L2EffectType.CPHEAL, L2EffectType.HEAL, L2EffectType.HEAL_PERCENT, L2EffectType.MANAHEAL_BY_LEVEL, L2EffectType.MANAHEAL_PERCENT))
				{
					sitCastAndFollow(skill, _owner);
				}
			}
		}
	}
	
	/**
	 * Prepare and cast a skill:<br>
	 * First smoothly prepare the beast for casting, by abandoning other actions.<br>
	 * Next, call super.doCast(skill) in order to actually cast the spell.<br>
	 * Finally, return to auto-following the owner.
	 * @param skill
	 * @param target
	 */
	protected void sitCastAndFollow(L2Skill skill, L2Character target)
	{
		stopMove();
		getAI().setIntention(AI_INTENTION_IDLE);
		
		setTarget(target);
		doCast(skill);
		getAI().setIntention(CtrlIntention.AI_INTENTION_FOLLOW, _owner);
	}
	
	private static class CheckDuration implements Runnable
	{
		private final L2TamedBeastInstance _tamedBeast;
		
		CheckDuration(L2TamedBeastInstance tamedBeast)
		{
			_tamedBeast = tamedBeast;
		}
		
		@Override
		public void run()
		{
			int foodTypeSkillId = _tamedBeast.getFoodType();
			L2PcInstance owner = _tamedBeast.getOwner();
			
			L2ItemInstance item = null;
			if (_tamedBeast._isFreyaBeast)
			{
				item = owner.getInventory().getItemByItemId(foodTypeSkillId);
				if ((item != null) && (item.getCount() >= 1))
				{
					owner.destroyItem("BeastMob", item, 1, _tamedBeast, true);
					_tamedBeast.broadcastPacket(new SocialAction(_tamedBeast.getObjectId(), 3));
				}
				else
				{
					_tamedBeast.deleteMe();
				}
			}
			else
			{
				_tamedBeast.setRemainingTime(_tamedBeast.getRemainingTime() - DURATION_CHECK_INTERVAL);
				// I tried to avoid this as much as possible...but it seems I can't avoid hardcoding
				// ids further, except by carrying an additional variable just for these two lines...
				// Find which food item needs to be consumed.
				if (foodTypeSkillId == 2188)
				{
					item = owner.getInventory().getItemByItemId(6643);
				}
				else if (foodTypeSkillId == 2189)
				{
					item = owner.getInventory().getItemByItemId(6644);
				}
				
				// if the owner has enough food, call the item handler (use the food and triffer all necessary actions)
				if ((item != null) && (item.getCount() >= 1))
				{
					L2Object oldTarget = owner.getTarget();
					owner.setTarget(_tamedBeast);
					L2Object[] targets =
					{
						_tamedBeast
					};
					
					// emulate a call to the owner using food, but bypass all checks for range, etc
					// this also causes a call to the AI tasks handling feeding, which may call onReceiveFood as required.
					owner.callSkill(SkillData.getInstance().getInfo(foodTypeSkillId, 1), targets);
					owner.setTarget(oldTarget);
				}
				else
				{
					// if the owner has no food, the beast immediately despawns, except when it was only
					// newly spawned. Newly spawned beasts can last up to 5 minutes
					if (_tamedBeast.getRemainingTime() < (MAX_DURATION - 300000))
					{
						_tamedBeast.setRemainingTime(-1);
					}
				}
				// There are too many conflicting reports about whether distance from home should be taken into consideration. Disabled for now.
				// if (_tamedBeast.isTooFarFromHome())
				// _tamedBeast.setRemainingTime(-1);
				
				if (_tamedBeast.getRemainingTime() <= 0)
				{
					_tamedBeast.deleteMe();
				}
			}
		}
	}
	
	private class CheckOwnerBuffs implements Runnable
	{
		private final L2TamedBeastInstance _tamedBeast;
		private final int _numBuffs;
		
		CheckOwnerBuffs(L2TamedBeastInstance tamedBeast, int numBuffs)
		{
			_tamedBeast = tamedBeast;
			_numBuffs = numBuffs;
		}
		
		@Override
		public void run()
		{
			L2PcInstance owner = _tamedBeast.getOwner();
			
			// check if the owner is no longer around...if so, despawn
			if ((owner == null) || !owner.isOnline())
			{
				deleteMe();
				return;
			}
			// if the owner is too far away, stop anything else and immediately run towards the owner.
			if (!isInsideRadius(owner, MAX_DISTANCE_FROM_OWNER, true, true))
			{
				getAI().startFollow(owner);
				return;
			}
			// if the owner is dead, do nothing...
			if (owner.isDead())
			{
				return;
			}
			// if the tamed beast is currently casting a spell, do not interfere (do not attempt to cast anything new yet).
			if (isCastingNow())
			{
				return;
			}
			
			int totalBuffsOnOwner = 0;
			int i = 0;
			int rand = Rnd.get(_numBuffs);
			L2Skill buffToGive = null;
			
			// get this npc's skills: getSkills()
			for (L2Skill skill : _tamedBeast.getTemplate().getSkills().values())
			{
				// if the skill is a buff, check if the owner has it already [ owner.getEffect(L2Skill skill) ]
				if (skill.getSkillType() == L2SkillType.BUFF)
				{
					if (i++ == rand)
					{
						buffToGive = skill;
					}
					if (owner.getFirstEffect(skill) != null)
					{
						totalBuffsOnOwner++;
					}
				}
			}
			// if the owner has less than 60% of this beast's available buff, cast a random buff
			if (((_numBuffs * 2) / 3) > totalBuffsOnOwner)
			{
				_tamedBeast.sitCastAndFollow(buffToGive, owner);
			}
			getAI().setIntention(CtrlIntention.AI_INTENTION_FOLLOW, _tamedBeast.getOwner());
		}
	}
	
	@Override
	public void onAction(L2PcInstance player, boolean interact)
	{
		if ((player == null) || !canTarget(player))
		{
			return;
		}
		
		// Check if the L2PcInstance already target the L2NpcInstance
		if (this != player.getTarget())
		{
			// Set the target of the L2PcInstance player
			player.setTarget(this);
		}
		else if (interact)
		{
			if (isAutoAttackable(player) && (Math.abs(player.getZ() - getZ()) < 100))
			{
				player.getAI().setIntention(CtrlIntention.AI_INTENTION_ATTACK, this);
			}
			else
			{
				// Send a Server->Client ActionFailed to the L2PcInstance in order to avoid that the client wait another packet
				player.sendPacket(ActionFailed.STATIC_PACKET);
			}
		}
	}
}
