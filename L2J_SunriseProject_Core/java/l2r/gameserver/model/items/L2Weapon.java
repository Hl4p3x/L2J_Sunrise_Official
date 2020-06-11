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
package l2r.gameserver.model.items;

import java.util.Objects;

import l2r.gameserver.handler.ISkillHandler;
import l2r.gameserver.handler.SkillHandler;
import l2r.gameserver.model.StatsSet;
import l2r.gameserver.model.actor.L2Character;
import l2r.gameserver.model.actor.L2Npc;
import l2r.gameserver.model.actor.instance.L2PcInstance;
import l2r.gameserver.model.conditions.Condition;
import l2r.gameserver.model.conditions.ConditionGameChance;
import l2r.gameserver.model.events.EventDispatcher;
import l2r.gameserver.model.events.impl.character.npc.OnNpcSkillSee;
import l2r.gameserver.model.holders.SkillHolder;
import l2r.gameserver.model.items.type.WeaponType;
import l2r.gameserver.model.skills.L2Skill;
import l2r.gameserver.model.stats.Env;
import l2r.gameserver.model.stats.Formulas;
import l2r.gameserver.util.Util;
import l2r.util.StringUtil;

/**
 * This class is dedicated to the management of weapons.
 */
public final class L2Weapon extends L2Item
{
	private final WeaponType _type;
	private final boolean _isMagicWeapon;
	private final int _rndDam;
	private final int _soulShotCount;
	private final int _spiritShotCount;
	private final int _mpConsume;
	private final int _baseAttackRange;
	private final int _changeWeaponId;
	
	// Attached skills for Special Abilities
	private SkillHolder _skillsOnMagic;
	private Condition _skillsOnMagicCondition = null;
	private SkillHolder _skillsOnCrit;
	private Condition _skillsOnCritCondition = null;
	
	private final int _reducedSoulshot;
	private final int _reducedSoulshotChance;
	
	private final int _reducedMpConsume;
	private final int _reducedMpConsumeChance;
	
	private final boolean _isForceEquip;
	private final boolean _isAttackWeapon;
	private final boolean _useWeaponSkillsOnly;
	
	private int[] _damage_range;
	
	/**
	 * Constructor for Weapon.
	 * @param set the StatsSet designating the set of couples (key,value) characterizing the weapon.
	 */
	public L2Weapon(StatsSet set)
	{
		super(set);
		_type = WeaponType.valueOf(set.getString("weapon_type", "none").toUpperCase());
		_type1 = L2Item.TYPE1_WEAPON_RING_EARRING_NECKLACE;
		_type2 = L2Item.TYPE2_WEAPON;
		_isMagicWeapon = set.getBoolean("is_magic_weapon", false);
		_soulShotCount = set.getInt("soulshots", 0);
		_spiritShotCount = set.getInt("spiritshots", 0);
		_rndDam = set.getInt("random_damage", 0);
		_mpConsume = set.getInt("mp_consume", 0);
		_baseAttackRange = set.getInt("attack_range", 40);
		
		_damage_range = null;
		
		String[] damgeRange = set.getString("damage_range", "").split(";"); // 0?;0?;fan sector;base attack angle
		if ((damgeRange.length > 1) && Util.isDigit(damgeRange[3]))
		{
			_damage_range = new int[4];
			_damage_range[0] = Integer.parseInt(damgeRange[0]);
			_damage_range[1] = Integer.parseInt(damgeRange[1]); // direction
			_damage_range[2] = Integer.parseInt(damgeRange[2]); // length
			_damage_range[3] = Integer.parseInt(damgeRange[3]); // angle or width
		}
		
		String[] reduced_soulshots = set.getString("reduced_soulshot", "").split(",");
		_reducedSoulshotChance = (reduced_soulshots.length == 2) ? Integer.parseInt(reduced_soulshots[0]) : 0;
		_reducedSoulshot = (reduced_soulshots.length == 2) ? Integer.parseInt(reduced_soulshots[1]) : 0;
		
		String[] reduced_mpconsume = set.getString("reduced_mp_consume", "").split(",");
		_reducedMpConsumeChance = (reduced_mpconsume.length == 2) ? Integer.parseInt(reduced_mpconsume[0]) : 0;
		_reducedMpConsume = (reduced_mpconsume.length == 2) ? Integer.parseInt(reduced_mpconsume[1]) : 0;
		
		String skill = set.getString("onmagic_skill", null);
		if (skill != null)
		{
			String[] info = skill.split("-");
			final int chance = set.getInt("onmagic_chance", 100);
			if ((info != null) && (info.length == 2))
			{
				int id = 0;
				int level = 0;
				try
				{
					id = Integer.parseInt(info[0]);
					level = Integer.parseInt(info[1]);
				}
				catch (Exception nfe)
				{
					// Incorrect syntax, don't add new skill
					_log.info(StringUtil.concat("> Couldnt parse ", skill, " in weapon onmagic skills! item ", toString()));
				}
				if ((id > 0) && (level > 0) && (chance > 0))
				{
					_skillsOnMagic = new SkillHolder(id, level);
					_skillsOnMagicCondition = new ConditionGameChance(chance);
				}
			}
		}
		
		skill = set.getString("oncrit_skill", null);
		if (skill != null)
		{
			String[] info = skill.split("-");
			final int chance = set.getInt("oncrit_chance", 100);
			if ((info != null) && (info.length == 2))
			{
				int id = 0;
				int level = 0;
				try
				{
					id = Integer.parseInt(info[0]);
					level = Integer.parseInt(info[1]);
				}
				catch (Exception nfe)
				{
					// Incorrect syntax, don't add new skill
					_log.info(StringUtil.concat("> Couldnt parse ", skill, " in weapon oncrit skills! item ", toString()));
				}
				if ((id > 0) && (level > 0) && (chance > 0))
				{
					_skillsOnCrit = new SkillHolder(id, level);
					_skillsOnCritCondition = new ConditionGameChance(chance);
				}
			}
		}
		
		_changeWeaponId = set.getInt("change_weaponId", 0);
		_isForceEquip = set.getBoolean("isForceEquip", false);
		_isAttackWeapon = set.getBoolean("isAttackWeapon", true);
		_useWeaponSkillsOnly = set.getBoolean("useWeaponSkillsOnly", false);
	}
	
	/**
	 * @return the type of Weapon
	 */
	@Override
	public WeaponType getItemType()
	{
		return _type;
	}
	
	/**
	 * @return the ID of the Etc item after applying the mask.
	 */
	@Override
	public int getItemMask()
	{
		return getItemType().mask();
	}
	
	/**
	 * @return {@code true} if the weapon is magic, {@code false} otherwise.
	 */
	@Override
	public boolean isMagicWeapon()
	{
		return _isMagicWeapon;
	}
	
	/**
	 * @return the quantity of SoulShot used.
	 */
	public int getSoulShotCount()
	{
		return _soulShotCount;
	}
	
	/**
	 * @return the quantity of SpiritShot used.
	 */
	public int getSpiritShotCount()
	{
		return _spiritShotCount;
	}
	
	/**
	 * @return the reduced quantity of SoultShot used.
	 */
	public int getReducedSoulShot()
	{
		return _reducedSoulshot;
	}
	
	/**
	 * @return the chance to use Reduced SoultShot.
	 */
	public int getReducedSoulShotChance()
	{
		return _reducedSoulshotChance;
	}
	
	/**
	 * @return the random damage inflicted by the weapon.
	 */
	public int getRandomDamage()
	{
		return _rndDam;
	}
	
	/**
	 * @return the MP consumption with the weapon.
	 */
	public int getMpConsume()
	{
		return _mpConsume;
	}
	
	public int getBaseAttackRange()
	{
		return _baseAttackRange;
	}
	
	/**
	 * @return the reduced MP consumption with the weapon.
	 */
	public int getReducedMpConsume()
	{
		return _reducedMpConsume;
	}
	
	/**
	 * @return the chance to use getReducedMpConsume()
	 */
	public int getReducedMpConsumeChance()
	{
		return _reducedMpConsumeChance;
	}
	
	/**
	 * @return the Id in which weapon this weapon can be changed.
	 */
	public int getChangeWeaponId()
	{
		return _changeWeaponId;
	}
	
	/**
	 * @return {@code true} if the weapon is force equip, {@code false} otherwise.
	 */
	public boolean isForceEquip()
	{
		return _isForceEquip;
	}
	
	/**
	 * @return {@code true} if the weapon is attack weapon, {@code false} otherwise.
	 */
	public boolean isAttackWeapon()
	{
		return _isAttackWeapon;
	}
	
	/**
	 * @return {@code true} if the weapon is skills only, {@code false} otherwise.
	 */
	public boolean useWeaponSkillsOnly()
	{
		return _useWeaponSkillsOnly;
	}
	
	/**
	 * @param caster the L2Character pointing out the caster
	 * @param target the L2Character pointing out the target
	 */
	public void castOnCriticalSkill(L2Character caster, L2Character target)
	{
		if (_skillsOnCrit == null)
		{
			return;
		}
		
		final L2Skill onCritSkill = _skillsOnCrit.getSkill();
		
		if (_skillsOnCritCondition != null)
		{
			Env env = new Env();
			env.setCharacter(caster);
			env.setTarget(target);
			env.setSkill(onCritSkill);
			if (!_skillsOnCritCondition.test(env))
			{
				// Chance not met
				return;
			}
		}
		
		if (!onCritSkill.checkCondition(caster, target, false))
		{
			// Skill condition not met
			return;
		}
		
		final byte shld = Formulas.calcShldUse(caster, target, onCritSkill);
		if (!Formulas.calcSkillSuccess(caster, target, onCritSkill, shld, false, false, false))
		{
			// These skills should not work on RaidBoss
			return;
		}
		
		L2Character[] targets =
		{
			target
		};
		
		// Launch the magic skill and calculate its effects
		// Get the skill handler corresponding to the skill type
		final ISkillHandler handler = SkillHandler.getInstance().getHandler(onCritSkill.getSkillType());
		if (handler != null)
		{
			handler.useSkill(caster, onCritSkill, targets);
		}
		else
		{
			onCritSkill.useSkill(caster, targets);
		}
	}
	
	/**
	 * @param caster the L2Character pointing out the caster
	 * @param target the L2Character pointing out the target
	 * @param trigger the L2Skill pointing out the skill triggering this action
	 */
	public void castOnMagicSkill(L2Character caster, L2Character target, L2Skill trigger)
	{
		if (_skillsOnMagic == null)
		{
			return;
		}
		
		final L2Skill onMagicSkill = _skillsOnMagic.getSkill();
		
		// Trigger only if both are good or bad magic.
		if (trigger.isOffensive() != onMagicSkill.isOffensive())
		{
			return;
		}
		
		// No Trigger if not Magic Skill
		if (trigger.isMagic() != onMagicSkill.isMagic())
		{
			return;
		}
		
		if (trigger.isToggle())
		{
			return;
		}
		
		if (caster.getAI().getCastTarget() != target)
		{
			return;
		}
		
		if (_skillsOnMagicCondition != null)
		{
			Env env = new Env();
			env.setCharacter(caster);
			env.setTarget(target);
			env.setSkill(onMagicSkill);
			if (!_skillsOnMagicCondition.test(env))
			{
				// Chance not met
				return;
			}
		}
		
		if (!onMagicSkill.checkCondition(caster, target, false))
		{
			// Skill condition not met
			return;
		}
		
		if (onMagicSkill.isOffensive() && (Formulas.calcShldUse(caster, target, onMagicSkill) == Formulas.SHIELD_DEFENSE_PERFECT_BLOCK))
		{
			return;
		}
		
		L2Character[] targets =
		{
			target
		};
		
		// Launch the magic skill and calculate its effects
		// Get the skill handler corresponding to the skill type
		final ISkillHandler handler = SkillHandler.getInstance().getHandler(onMagicSkill.getSkillType());
		if (handler != null)
		{
			handler.useSkill(caster, onMagicSkill, targets);
		}
		else
		{
			onMagicSkill.useSkill(caster, targets);
		}
		
		// notify quests of a skill use
		if (caster instanceof L2PcInstance)
		{
			//@formatter:off
			caster.getKnownList().getKnownObjects().values().stream()
				.filter(Objects::nonNull)
				.filter(npc -> npc.isNpc())
				.filter(npc -> Util.checkIfInRange(1000, npc, caster, false))
				.forEach(npc -> 
				{
					EventDispatcher.getInstance().notifyEventAsync(new OnNpcSkillSee((L2Npc) npc, caster.getActingPlayer(), onMagicSkill, targets, false), npc);
				});
			//@formatter:on
		}
	}
	
	/**
	 * damageRange is used to calculate damage splash. Refer to skill scope FAN for more info
	 * @return
	 */
	public int[] getDamageRange()
	{
		return _damage_range;
	}
}
