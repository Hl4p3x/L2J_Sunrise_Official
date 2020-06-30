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
package l2r.gameserver.model.stats;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import l2r.Config;
import l2r.gameserver.SevenSigns;
import l2r.gameserver.SevenSignsFestival;
import l2r.gameserver.data.xml.impl.HitConditionBonusData;
import l2r.gameserver.data.xml.impl.KarmaData;
import l2r.gameserver.enums.ShotType;
import l2r.gameserver.enums.ZoneIdType;
import l2r.gameserver.instancemanager.CastleManager;
import l2r.gameserver.instancemanager.ClanHallManager;
import l2r.gameserver.instancemanager.FortManager;
import l2r.gameserver.instancemanager.SiegeManager;
import l2r.gameserver.instancemanager.ZoneManager;
import l2r.gameserver.model.Elementals;
import l2r.gameserver.model.L2SiegeClan;
import l2r.gameserver.model.StatsSet;
import l2r.gameserver.model.actor.L2Attackable;
import l2r.gameserver.model.actor.L2Character;
import l2r.gameserver.model.actor.L2Npc;
import l2r.gameserver.model.actor.instance.L2PcInstance;
import l2r.gameserver.model.actor.instance.L2PetInstance;
import l2r.gameserver.model.cubic.CubicInstance;
import l2r.gameserver.model.effects.EffectTemplate;
import l2r.gameserver.model.effects.L2Effect;
import l2r.gameserver.model.effects.L2EffectType;
import l2r.gameserver.model.entity.Castle;
import l2r.gameserver.model.entity.ClanHall;
import l2r.gameserver.model.entity.Fort;
import l2r.gameserver.model.entity.Siege;
import l2r.gameserver.model.items.L2Armor;
import l2r.gameserver.model.items.L2Item;
import l2r.gameserver.model.items.L2Weapon;
import l2r.gameserver.model.items.type.ArmorType;
import l2r.gameserver.model.items.type.WeaponType;
import l2r.gameserver.model.skills.L2Skill;
import l2r.gameserver.model.skills.L2SkillType;
import l2r.gameserver.model.skills.L2TraitType;
import l2r.gameserver.model.stats.functions.formulas.FuncArmorSet;
import l2r.gameserver.model.stats.functions.formulas.FuncAtkAccuracy;
import l2r.gameserver.model.stats.functions.formulas.FuncAtkCritical;
import l2r.gameserver.model.stats.functions.formulas.FuncAtkEvasion;
import l2r.gameserver.model.stats.functions.formulas.FuncGatesMDefMod;
import l2r.gameserver.model.stats.functions.formulas.FuncGatesPDefMod;
import l2r.gameserver.model.stats.functions.formulas.FuncHenna;
import l2r.gameserver.model.stats.functions.formulas.FuncMAtkCritical;
import l2r.gameserver.model.stats.functions.formulas.FuncMAtkMod;
import l2r.gameserver.model.stats.functions.formulas.FuncMAtkSpeed;
import l2r.gameserver.model.stats.functions.formulas.FuncMDefMod;
import l2r.gameserver.model.stats.functions.formulas.FuncMaxCpMul;
import l2r.gameserver.model.stats.functions.formulas.FuncMaxHpMul;
import l2r.gameserver.model.stats.functions.formulas.FuncMaxMpMul;
import l2r.gameserver.model.stats.functions.formulas.FuncMoveSpeed;
import l2r.gameserver.model.stats.functions.formulas.FuncPAtkMod;
import l2r.gameserver.model.stats.functions.formulas.FuncPAtkSpeed;
import l2r.gameserver.model.stats.functions.formulas.FuncPDefMod;
import l2r.gameserver.model.zone.type.L2CastleZone;
import l2r.gameserver.model.zone.type.L2ClanHallZone;
import l2r.gameserver.model.zone.type.L2FortZone;
import l2r.gameserver.model.zone.type.L2MotherTreeZone;
import l2r.gameserver.network.Debug;
import l2r.gameserver.network.SystemMessageId;
import l2r.gameserver.network.serverpackets.SystemMessage;
import l2r.gameserver.util.Util;
import l2r.util.Rnd;

import gr.sr.balanceEngine.BalanceHandler;
import gr.sr.configsEngine.configs.impl.FormulasConfigs;
import gr.sr.configsEngine.configs.impl.PremiumServiceConfigs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Global calculations.
 */
public final class Formulas
{
	private static final Logger _log = LoggerFactory.getLogger(Formulas.class);
	
	/** Regen Task period. */
	private static final int HP_REGENERATE_PERIOD = 3000; // 3 secs
	
	public static final byte SHIELD_DEFENSE_FAILED = 0; // no shield defense
	public static final byte SHIELD_DEFENSE_SUCCEED = 1; // normal shield defense
	public static final byte SHIELD_DEFENSE_PERFECT_BLOCK = 2; // perfect block
	
	public static final byte SKILL_REFLECT_FAILED = 0; // no reflect
	public static final byte SKILL_REFLECT_SUCCEED = 1; // normal reflect, some damage reflected some other not
	public static final byte SKILL_REFLECT_VENGEANCE = 2; // 100% of the damage affect both
	
	private static final byte MELEE_ATTACK_RANGE = 80;
	
	/**
	 * Return the period between 2 regeneration task (3s for L2Character, 5 min for L2DoorInstance).
	 * @param cha
	 * @return
	 */
	public static int getRegeneratePeriod(L2Character cha)
	{
		return cha.isDoor() ? HP_REGENERATE_PERIOD * 100 : HP_REGENERATE_PERIOD;
	}
	
	/**
	 * Return the standard NPC Calculator set containing ACCURACY_COMBAT and EVASION_RATE.<br>
	 * <B><U>Concept</U>:</B><br>
	 * A calculator is created to manage and dynamically calculate the effect of a character property (ex : MAX_HP, REGENERATE_HP_RATE...). In fact, each calculator is a table of Func object in which each Func represents a mathematic function : <br>
	 * FuncAtkAccuracy -> Math.sqrt(_player.getDEX())*6+_player.getLevel()<br>
	 * To reduce cache memory use, L2NPCInstances who don't have skills share the same Calculator set called <B>NPC_STD_CALCULATOR</B>.<br>
	 * @return
	 */
	public static Calculator[] getStdNPCCalculators()
	{
		Calculator[] std = new Calculator[Stats.NUM_STATS];
		
		std[Stats.MAX_HP.ordinal()] = new Calculator();
		std[Stats.MAX_HP.ordinal()].addFunc(FuncMaxHpMul.getInstance());
		
		std[Stats.MAX_MP.ordinal()] = new Calculator();
		std[Stats.MAX_MP.ordinal()].addFunc(FuncMaxMpMul.getInstance());
		
		std[Stats.POWER_ATTACK.ordinal()] = new Calculator();
		std[Stats.POWER_ATTACK.ordinal()].addFunc(FuncPAtkMod.getInstance());
		
		std[Stats.MAGIC_ATTACK.ordinal()] = new Calculator();
		std[Stats.MAGIC_ATTACK.ordinal()].addFunc(FuncMAtkMod.getInstance());
		
		std[Stats.POWER_DEFENCE.ordinal()] = new Calculator();
		std[Stats.POWER_DEFENCE.ordinal()].addFunc(FuncPDefMod.getInstance());
		
		std[Stats.MAGIC_DEFENCE.ordinal()] = new Calculator();
		std[Stats.MAGIC_DEFENCE.ordinal()].addFunc(FuncMDefMod.getInstance());
		
		std[Stats.CRITICAL_RATE.ordinal()] = new Calculator();
		std[Stats.CRITICAL_RATE.ordinal()].addFunc(FuncAtkCritical.getInstance());
		
		std[Stats.MCRITICAL_RATE.ordinal()] = new Calculator();
		std[Stats.MCRITICAL_RATE.ordinal()].addFunc(FuncMAtkCritical.getInstance());
		
		std[Stats.ACCURACY_COMBAT.ordinal()] = new Calculator();
		std[Stats.ACCURACY_COMBAT.ordinal()].addFunc(FuncAtkAccuracy.getInstance());
		
		std[Stats.EVASION_RATE.ordinal()] = new Calculator();
		std[Stats.EVASION_RATE.ordinal()].addFunc(FuncAtkEvasion.getInstance());
		
		std[Stats.POWER_ATTACK_SPEED.ordinal()] = new Calculator();
		std[Stats.POWER_ATTACK_SPEED.ordinal()].addFunc(FuncPAtkSpeed.getInstance());
		
		std[Stats.MAGIC_ATTACK_SPEED.ordinal()] = new Calculator();
		std[Stats.MAGIC_ATTACK_SPEED.ordinal()].addFunc(FuncMAtkSpeed.getInstance());
		
		std[Stats.MOVE_SPEED.ordinal()] = new Calculator();
		std[Stats.MOVE_SPEED.ordinal()].addFunc(FuncMoveSpeed.getInstance());
		
		return std;
	}
	
	public static Calculator[] getStdDoorCalculators()
	{
		Calculator[] std = new Calculator[Stats.NUM_STATS];
		
		// Add the FuncAtkAccuracy to the Standard Calculator of ACCURACY_COMBAT
		std[Stats.ACCURACY_COMBAT.ordinal()] = new Calculator();
		std[Stats.ACCURACY_COMBAT.ordinal()].addFunc(FuncAtkAccuracy.getInstance());
		
		// Add the FuncAtkEvasion to the Standard Calculator of EVASION_RATE
		std[Stats.EVASION_RATE.ordinal()] = new Calculator();
		std[Stats.EVASION_RATE.ordinal()].addFunc(FuncAtkEvasion.getInstance());
		
		// SevenSigns PDEF Modifier
		std[Stats.POWER_DEFENCE.ordinal()] = new Calculator();
		std[Stats.POWER_DEFENCE.ordinal()].addFunc(FuncGatesPDefMod.getInstance());
		
		// SevenSigns MDEF Modifier
		std[Stats.MAGIC_DEFENCE.ordinal()] = new Calculator();
		std[Stats.MAGIC_DEFENCE.ordinal()].addFunc(FuncGatesMDefMod.getInstance());
		
		return std;
	}
	
	/**
	 * Add basics Func objects to L2PcInstance and L2Summon.<br>
	 * <B><U>Concept</U>:</B><br>
	 * A calculator is created to manage and dynamically calculate the effect of a character property (ex : MAX_HP, REGENERATE_HP_RATE...). In fact, each calculator is a table of Func object in which each Func represents a mathematic function : <br>
	 * FuncAtkAccuracy -> Math.sqrt(_player.getDEX())*6+_player.getLevel()<br>
	 * @param cha L2PcInstance or L2Summon that must obtain basic Func objects
	 */
	public static void addFuncsToNewCharacter(L2Character cha)
	{
		if (cha.isPlayer())
		{
			cha.addStatFunc(FuncMaxHpMul.getInstance());
			cha.addStatFunc(FuncMaxCpMul.getInstance());
			cha.addStatFunc(FuncMaxMpMul.getInstance());
			cha.addStatFunc(FuncPAtkMod.getInstance());
			cha.addStatFunc(FuncMAtkMod.getInstance());
			cha.addStatFunc(FuncPDefMod.getInstance());
			cha.addStatFunc(FuncMDefMod.getInstance());
			cha.addStatFunc(FuncAtkCritical.getInstance());
			cha.addStatFunc(FuncMAtkCritical.getInstance());
			cha.addStatFunc(FuncAtkAccuracy.getInstance());
			cha.addStatFunc(FuncAtkEvasion.getInstance());
			cha.addStatFunc(FuncPAtkSpeed.getInstance());
			cha.addStatFunc(FuncMAtkSpeed.getInstance());
			cha.addStatFunc(FuncMoveSpeed.getInstance());
			
			cha.addStatFunc(FuncHenna.getInstance(Stats.STAT_STR));
			cha.addStatFunc(FuncHenna.getInstance(Stats.STAT_DEX));
			cha.addStatFunc(FuncHenna.getInstance(Stats.STAT_INT));
			cha.addStatFunc(FuncHenna.getInstance(Stats.STAT_MEN));
			cha.addStatFunc(FuncHenna.getInstance(Stats.STAT_CON));
			cha.addStatFunc(FuncHenna.getInstance(Stats.STAT_WIT));
			
			cha.addStatFunc(FuncArmorSet.getInstance(Stats.STAT_STR));
			cha.addStatFunc(FuncArmorSet.getInstance(Stats.STAT_DEX));
			cha.addStatFunc(FuncArmorSet.getInstance(Stats.STAT_INT));
			cha.addStatFunc(FuncArmorSet.getInstance(Stats.STAT_MEN));
			cha.addStatFunc(FuncArmorSet.getInstance(Stats.STAT_CON));
			cha.addStatFunc(FuncArmorSet.getInstance(Stats.STAT_WIT));
		}
		else if (cha.isSummon())
		{
			cha.addStatFunc(FuncMaxHpMul.getInstance());
			cha.addStatFunc(FuncMaxMpMul.getInstance());
			cha.addStatFunc(FuncPAtkMod.getInstance());
			cha.addStatFunc(FuncMAtkMod.getInstance());
			cha.addStatFunc(FuncPDefMod.getInstance());
			cha.addStatFunc(FuncMDefMod.getInstance());
			cha.addStatFunc(FuncAtkCritical.getInstance());
			cha.addStatFunc(FuncMAtkCritical.getInstance());
			cha.addStatFunc(FuncAtkAccuracy.getInstance());
			cha.addStatFunc(FuncAtkEvasion.getInstance());
			cha.addStatFunc(FuncMoveSpeed.getInstance());
			cha.addStatFunc(FuncPAtkSpeed.getInstance());
			cha.addStatFunc(FuncMAtkSpeed.getInstance());
		}
	}
	
	/**
	 * Calculate the HP regen rate (base + modifiers).
	 * @param cha
	 * @return
	 */
	public static final double calcHpRegen(L2Character cha)
	{
		double init = cha.isPlayer() ? cha.getActingPlayer().getTemplate().getBaseHpRegen(cha.getLevel()) : cha.getTemplate().getBaseHpReg();
		double hpRegenMultiplier = cha.isRaid() ? Config.RAID_HP_REGEN_MULTIPLIER : Config.HP_REGEN_MULTIPLIER;
		double sHpPropHpRate = cha.calcStat(Stats.S_HP_PROP_HP_RATE, cha.getTemplate().getNpcPropHpRate());
		double hpRegenBonus = 0;
		
		hpRegenMultiplier *= sHpPropHpRate;
		
		if (Config.L2JMOD_CHAMPION_ENABLE && cha.isChampion())
		{
			hpRegenMultiplier *= Config.L2JMOD_CHAMPION_HP_REGEN;
		}
		
		if (cha.isPlayer())
		{
			L2PcInstance player = cha.getActingPlayer();
			
			// SevenSigns Festival modifier
			if (SevenSignsFestival.getInstance().isFestivalInProgress() && player.isFestivalParticipant())
			{
				hpRegenMultiplier *= calcFestivalRegenModifier(player);
			}
			else
			{
				double siegeModifier = calcSiegeRegenModifier(player);
				if (siegeModifier > 0)
				{
					hpRegenMultiplier *= siegeModifier;
				}
			}
			
			if (player.isInsideZone(ZoneIdType.CLAN_HALL) && (player.getClan() != null) && (player.getClan().getHideoutId() > 0))
			{
				L2ClanHallZone zone = ZoneManager.getInstance().getZone(player, L2ClanHallZone.class);
				int posChIndex = zone == null ? -1 : zone.getResidenceId();
				int clanHallIndex = player.getClan().getHideoutId();
				if ((clanHallIndex > 0) && (clanHallIndex == posChIndex))
				{
					ClanHall clansHall = ClanHallManager.getInstance().getClanHallById(clanHallIndex);
					if (clansHall != null)
					{
						if (clansHall.getFunction(ClanHall.FUNC_RESTORE_HP) != null)
						{
							hpRegenMultiplier *= 1 + ((double) clansHall.getFunction(ClanHall.FUNC_RESTORE_HP).getLvl() / 100);
						}
					}
				}
			}
			
			if (player.isInsideZone(ZoneIdType.CASTLE) && (player.getClan() != null) && (player.getClan().getCastleId() > 0))
			{
				L2CastleZone zone = ZoneManager.getInstance().getZone(player, L2CastleZone.class);
				int posCastleIndex = zone == null ? -1 : zone.getResidenceId();
				int castleIndex = player.getClan().getCastleId();
				if ((castleIndex > 0) && (castleIndex == posCastleIndex))
				{
					Castle castle = CastleManager.getInstance().getCastleById(castleIndex);
					if (castle != null)
					{
						if (castle.getFunction(Castle.FUNC_RESTORE_HP) != null)
						{
							hpRegenMultiplier *= 1 + ((double) castle.getFunction(Castle.FUNC_RESTORE_HP).getLvl() / 100);
						}
					}
				}
			}
			
			if (player.isInsideZone(ZoneIdType.FORT) && (player.getClan() != null) && (player.getClan().getFortId() > 0))
			{
				L2FortZone zone = ZoneManager.getInstance().getZone(player, L2FortZone.class);
				int posFortIndex = zone == null ? -1 : zone.getResidenceId();
				int fortIndex = player.getClan().getFortId();
				if ((fortIndex > 0) && (fortIndex == posFortIndex))
				{
					Fort fort = FortManager.getInstance().getFortById(fortIndex);
					if (fort != null)
					{
						if (fort.getFunction(Fort.FUNC_RESTORE_HP) != null)
						{
							hpRegenMultiplier *= 1 + ((double) fort.getFunction(Fort.FUNC_RESTORE_HP).getLvl() / 100);
						}
					}
				}
			}
			
			// Mother Tree effect is calculated at last
			if (player.isInsideZone(ZoneIdType.MOTHER_TREE))
			{
				L2MotherTreeZone zone = ZoneManager.getInstance().getZone(player, L2MotherTreeZone.class);
				int hpBonus = zone == null ? 0 : zone.getHpRegenBonus();
				hpRegenBonus += hpBonus;
			}
			
			// Calculate Movement bonus
			if (player.isSitting())
			{
				hpRegenMultiplier *= 1.5; // Sitting
			}
			else if (!player.isMoving())
			{
				hpRegenMultiplier *= 1.1; // Staying
			}
			else if (player.isRunning())
			{
				hpRegenMultiplier *= 0.7; // Running
			}
			
			// Add CON bonus
			init *= cha.getLevelMod() * BaseStats.CON.calcBonus(cha);
		}
		else if (cha.isPet())
		{
			init = ((L2PetInstance) cha).getPetLevelData().getPetRegenHP() * Config.PET_HP_REGEN_MULTIPLIER;
		}
		
		return (cha.calcStat(Stats.REGENERATE_HP_RATE, Math.max(1, init), null, null) * hpRegenMultiplier) + hpRegenBonus;
	}
	
	/**
	 * Calculate the MP regen rate (base + modifiers).
	 * @param cha
	 * @return
	 */
	public static final double calcMpRegen(L2Character cha)
	{
		double init = cha.isPlayer() ? cha.getActingPlayer().getTemplate().getBaseMpRegen(cha.getLevel()) : cha.getTemplate().getBaseMpReg();
		double mpRegenMultiplier = cha.isRaid() ? Config.RAID_MP_REGEN_MULTIPLIER : Config.MP_REGEN_MULTIPLIER;
		double mpRegenBonus = 0;
		
		if (cha.isPlayer())
		{
			L2PcInstance player = cha.getActingPlayer();
			
			// SevenSigns Festival modifier
			if (SevenSignsFestival.getInstance().isFestivalInProgress() && player.isFestivalParticipant())
			{
				mpRegenMultiplier *= calcFestivalRegenModifier(player);
			}
			
			// Mother Tree effect is calculated at last'
			if (player.isInsideZone(ZoneIdType.MOTHER_TREE))
			{
				L2MotherTreeZone zone = ZoneManager.getInstance().getZone(player, L2MotherTreeZone.class);
				int mpBonus = zone == null ? 0 : zone.getMpRegenBonus();
				mpRegenBonus += mpBonus;
			}
			
			if (player.isInsideZone(ZoneIdType.CLAN_HALL) && (player.getClan() != null) && (player.getClan().getHideoutId() > 0))
			{
				L2ClanHallZone zone = ZoneManager.getInstance().getZone(player, L2ClanHallZone.class);
				int posChIndex = zone == null ? -1 : zone.getResidenceId();
				int clanHallIndex = player.getClan().getHideoutId();
				if ((clanHallIndex > 0) && (clanHallIndex == posChIndex))
				{
					ClanHall clansHall = ClanHallManager.getInstance().getClanHallById(clanHallIndex);
					if (clansHall != null)
					{
						if (clansHall.getFunction(ClanHall.FUNC_RESTORE_MP) != null)
						{
							mpRegenMultiplier *= 1 + ((double) clansHall.getFunction(ClanHall.FUNC_RESTORE_MP).getLvl() / 100);
						}
					}
				}
			}
			
			if (player.isInsideZone(ZoneIdType.CASTLE) && (player.getClan() != null) && (player.getClan().getCastleId() > 0))
			{
				L2CastleZone zone = ZoneManager.getInstance().getZone(player, L2CastleZone.class);
				int posCastleIndex = zone == null ? -1 : zone.getResidenceId();
				int castleIndex = player.getClan().getCastleId();
				if ((castleIndex > 0) && (castleIndex == posCastleIndex))
				{
					Castle castle = CastleManager.getInstance().getCastleById(castleIndex);
					if (castle != null)
					{
						if (castle.getFunction(Castle.FUNC_RESTORE_MP) != null)
						{
							mpRegenMultiplier *= 1 + ((double) castle.getFunction(Castle.FUNC_RESTORE_MP).getLvl() / 100);
						}
					}
				}
			}
			
			if (player.isInsideZone(ZoneIdType.FORT) && (player.getClan() != null) && (player.getClan().getFortId() > 0))
			{
				L2FortZone zone = ZoneManager.getInstance().getZone(player, L2FortZone.class);
				int posFortIndex = zone == null ? -1 : zone.getResidenceId();
				int fortIndex = player.getClan().getFortId();
				if ((fortIndex > 0) && (fortIndex == posFortIndex))
				{
					Fort fort = FortManager.getInstance().getFortById(fortIndex);
					if (fort != null)
					{
						if (fort.getFunction(Fort.FUNC_RESTORE_MP) != null)
						{
							mpRegenMultiplier *= 1 + ((double) fort.getFunction(Fort.FUNC_RESTORE_MP).getLvl() / 100);
						}
					}
				}
			}
			
			// Calculate Movement bonus
			if (player.isSitting())
			{
				mpRegenMultiplier *= 1.5; // Sitting
			}
			else if (!player.isMoving())
			{
				mpRegenMultiplier *= 1.1; // Staying
			}
			else if (player.isRunning())
			{
				mpRegenMultiplier *= 0.7; // Running
			}
			
			// Add MEN bonus
			init *= cha.getLevelMod() * BaseStats.MEN.calcBonus(cha);
		}
		else if (cha.isPet())
		{
			init = ((L2PetInstance) cha).getPetLevelData().getPetRegenMP() * Config.PET_MP_REGEN_MULTIPLIER;
		}
		
		return (cha.calcStat(Stats.REGENERATE_MP_RATE, Math.max(1, init), null, null) * mpRegenMultiplier) + mpRegenBonus;
	}
	
	/**
	 * Calculates the CP regeneration rate (base + modifiers).
	 * @param player the player
	 * @return the CP regeneration rate
	 */
	public static final double calcCpRegen(L2PcInstance player)
	{
		// With CON bonus
		final double init = player.getActingPlayer().getTemplate().getBaseCpRegen(player.getLevel()) * player.getLevelMod() * BaseStats.CON.calcBonus(player);
		double cpRegenMultiplier = Config.CP_REGEN_MULTIPLIER;
		if (player.isSitting())
		{
			cpRegenMultiplier *= 1.5; // Sitting
		}
		else if (!player.isMoving())
		{
			cpRegenMultiplier *= 1.1; // Staying
		}
		else if (player.isRunning())
		{
			cpRegenMultiplier *= 0.7; // Running
		}
		return player.calcStat(Stats.REGENERATE_CP_RATE, Math.max(1, init), null, null) * cpRegenMultiplier;
	}
	
	@SuppressWarnings("deprecation")
	public static final double calcFestivalRegenModifier(L2PcInstance activeChar)
	{
		final int[] festivalInfo = SevenSignsFestival.getInstance().getFestivalForPlayer(activeChar);
		final int oracle = festivalInfo[0];
		final int festivalId = festivalInfo[1];
		int[] festivalCenter;
		
		// If the player isn't found in the festival, leave the regen rate as it is.
		if (festivalId < 0)
		{
			return 0;
		}
		
		// Retrieve the X and Y coords for the center of the festival arena the player is in.
		if (oracle == SevenSigns.CABAL_DAWN)
		{
			festivalCenter = SevenSignsFestival.FESTIVAL_DAWN_PLAYER_SPAWNS[festivalId];
		}
		else
		{
			festivalCenter = SevenSignsFestival.FESTIVAL_DUSK_PLAYER_SPAWNS[festivalId];
		}
		
		// Check the distance between the player and the player spawn point, in the center of the arena.
		double distToCenter = activeChar.getDistance(festivalCenter[0], festivalCenter[1]);
		
		if (Config.DEBUG)
		{
			_log.info("Distance: " + distToCenter + ", RegenMulti: " + ((distToCenter * 2.5) / 50));
		}
		
		return 1.0 - (distToCenter * 0.0005); // Maximum Decreased Regen of ~ -65%;
	}
	
	public static final double calcSiegeRegenModifier(L2PcInstance activeChar)
	{
		if ((activeChar == null) || (activeChar.getClan() == null))
		{
			return 0;
		}
		
		Siege siege = SiegeManager.getInstance().getSiege(activeChar.getX(), activeChar.getY(), activeChar.getZ());
		if ((siege == null) || !siege.isInProgress())
		{
			return 0;
		}
		
		L2SiegeClan siegeClan = siege.getAttackerClan(activeChar.getClan().getId());
		if ((siegeClan == null) || siegeClan.getFlag().isEmpty() || !Util.checkIfInRange(200, activeChar, siegeClan.getFlag().get(0), true))
		{
			return 0;
		}
		
		return 1.5; // If all is true, then modifier will be 50% more
	}
	
	public static double calcBlowDamage(L2Character attacker, L2Character target, L2Skill skill, byte shld, boolean ss)
	{
		double defence = target.getPDef(attacker);
		
		switch (shld)
		{
			case Formulas.SHIELD_DEFENSE_SUCCEED:
			{
				defence += target.getShldDef();
				break;
			}
			case Formulas.SHIELD_DEFENSE_PERFECT_BLOCK: // perfect block
			{
				return 1;
			}
		}
		
		final boolean isPvP = attacker.isPlayable() && target.isPlayable();
		final boolean isPvE = attacker.isPlayable() && target.isAttackable();
		double damage = 0;
		double proximityBonus = attacker.isBehindTarget() ? 1.2 : attacker.isInFrontOfTarget() ? 1 : 1.1;
		double ssboost = ss ? 1.458 : 1;
		double pvpBonus = 1;
		
		if (isPvP)
		{
			// Damage bonuses in PvP fight
			pvpBonus = attacker.calcStat(Stats.PVP_PHYS_SKILL_DMG, 1, null, null);
			// Defense bonuses in PvP fight
			defence *= target.calcStat(Stats.PVP_PHYS_SKILL_DEF, 1, null, null);
		}
		
		// Initial damage
		double baseMod = ((77 * (skill.getPower(isPvP, isPvE) + (attacker.getPAtk(target) * ssboost))) / defence);
		// Critical
		double criticalMod = (attacker.calcStat(Stats.CRITICAL_DAMAGE, 1, target, skill));
		double criticalModPos = (((attacker.calcStat(Stats.CRITICAL_DAMAGE_POS, 1, target, skill) - 1) / 2) + 1);
		double criticalVulnMod = (target.calcStat(Stats.DEFENCE_CRITICAL_DAMAGE, 1, target, skill));
		double criticalAddMod = ((attacker.getStat().calcStat(Stats.CRITICAL_DAMAGE_ADD, 0) * 6.1 * 77) / defence);
		double criticalAddVuln = target.calcStat(Stats.DEFENCE_CRITICAL_DAMAGE_ADD, 0, target, skill);
		// Trait, elements
		// FIXME: General Traits
		double weaponTraitMod = calcWeaponTraitBonus(attacker, target);
		double generalTraitMod = 1.0;// calcGeneralTraitBonus(attacker, target, skill.getTraitType(), false);
		double attributeMod = calcAttributeBonus(attacker, target, skill);
		double weaponMod = attacker.getRandomDamageMultiplier();
		
		double penaltyMod = 1;
		if ((target instanceof L2Attackable) && !target.isRaid() && !target.isRaidMinion() && (target.getLevel() >= Config.MIN_NPC_LVL_DMG_PENALTY) && (attacker.getActingPlayer() != null) && ((target.getLevel() - attacker.getActingPlayer().getLevel()) >= 2))
		{
			int lvlDiff = target.getLevel() - attacker.getActingPlayer().getLevel() - 1;
			if (lvlDiff >= Config.NPC_SKILL_DMG_PENALTY.size())
			{
				penaltyMod *= Config.NPC_SKILL_DMG_PENALTY.get(Config.NPC_SKILL_DMG_PENALTY.size() - 1);
			}
			else
			{
				penaltyMod *= Config.NPC_SKILL_DMG_PENALTY.get(lvlDiff);
			}
		}
		
		damage = (baseMod * criticalMod * criticalModPos * criticalVulnMod * proximityBonus * pvpBonus) + criticalAddMod + criticalAddVuln;
		damage *= weaponTraitMod;
		damage *= generalTraitMod;
		damage *= attributeMod;
		damage *= weaponMod;
		damage *= penaltyMod;
		
		if (attacker.isDebug())
		{
			final StatsSet set = new StatsSet();
			set.set("skillPower", skill.getPower(isPvP, isPvE));
			set.set("ssboost", ssboost);
			set.set("proximityBonus", proximityBonus);
			set.set("pvpBonus", pvpBonus);
			set.set("baseMod", baseMod);
			set.set("criticalMod", criticalMod);
			set.set("criticalModPos", criticalModPos);
			set.set("criticalVulnMod", criticalVulnMod);
			set.set("criticalAddMod", criticalAddMod);
			set.set("criticalAddVuln", criticalAddVuln);
			set.set("weaponTraitMod", weaponTraitMod);
			set.set("generalTraitMod", generalTraitMod);
			set.set("attributeMod", attributeMod);
			set.set("weaponMod", weaponMod);
			set.set("penaltyMod", penaltyMod);
			set.set("damage", (int) damage);
			Debug.sendSkillDebug(attacker, target, skill, set);
		}
		
		// Reunion balancer
		damage = BalanceHandler.getInstance().calc(attacker, target, skill, damage, false);
		// Reunion balancer - End
		
		return Math.max(damage, 1);
	}
	
	public static double calcBackstabDamage(L2Character attacker, L2Character target, L2Skill skill, byte shld, boolean ss)
	{
		double defence = target.getPDef(attacker);
		
		switch (shld)
		{
			case Formulas.SHIELD_DEFENSE_SUCCEED:
			{
				defence += target.getShldDef();
				break;
			}
			case Formulas.SHIELD_DEFENSE_PERFECT_BLOCK: // perfect block
			{
				return 1;
			}
		}
		
		boolean isPvP = attacker.isPlayable() && target.isPlayer();
		boolean isPvE = attacker.isPlayable() && target.isAttackable();
		double damage = 0;
		double proximityBonus = attacker.isBehindTarget() ? 1.2 : attacker.isInFrontOfTarget() ? 1 : 1.1; // Behind: +20% - Side: +10%
		double ssboost = ss ? 1.458 : 1;
		double pvpBonus = 1;
		
		if (isPvP)
		{
			// Damage bonuses in PvP fight
			pvpBonus = attacker.calcStat(Stats.PVP_PHYS_SKILL_DMG, 1, null, null);
			// Defense bonuses in PvP fight
			defence *= target.calcStat(Stats.PVP_PHYS_SKILL_DEF, 1, null, null);
		}
		
		// Initial damage
		double baseMod = ((77 * (skill.getPower(isPvP, isPvE) + attacker.getPAtk(target))) / defence) * ssboost;
		// Critical
		double criticalMod = (attacker.calcStat(Stats.CRITICAL_DAMAGE, 1, target, skill));
		double criticalModPos = (((attacker.calcStat(Stats.CRITICAL_DAMAGE_POS, 1, target, skill) - 1) / 2) + 1);
		double criticalVulnMod = (target.calcStat(Stats.DEFENCE_CRITICAL_DAMAGE, 1, target, skill));
		double criticalAddMod = ((attacker.calcStat(Stats.CRITICAL_DAMAGE_ADD, 0, target, skill) * 6.1 * 77) / defence);
		double criticalAddVuln = target.calcStat(Stats.DEFENCE_CRITICAL_DAMAGE_ADD, 0, target, skill);
		// Trait, elements
		// FIXME: General Traits
		double weaponTraitMod = calcWeaponTraitBonus(attacker, target);
		double generalTraitMod = 1.0; // calcGeneralTraitBonus(attacker, target, skill.getTraitType(), false);
		double attributeMod = calcAttributeBonus(attacker, target, skill);
		double weaponMod = attacker.getRandomDamageMultiplier();
		
		double penaltyMod = 1;
		if (target.isAttackable() && !target.isRaid() && !target.isRaidMinion() && (target.getLevel() >= Config.MIN_NPC_LVL_DMG_PENALTY) && (attacker.getActingPlayer() != null) && ((target.getLevel() - attacker.getActingPlayer().getLevel()) >= 2))
		{
			int lvlDiff = target.getLevel() - attacker.getActingPlayer().getLevel() - 1;
			if (lvlDiff >= Config.NPC_SKILL_DMG_PENALTY.size())
			{
				penaltyMod *= Config.NPC_SKILL_DMG_PENALTY.get(Config.NPC_SKILL_DMG_PENALTY.size() - 1);
			}
			else
			{
				penaltyMod *= Config.NPC_SKILL_DMG_PENALTY.get(lvlDiff);
			}
		}
		
		damage = (baseMod * criticalMod * criticalModPos * criticalVulnMod * proximityBonus * pvpBonus) + criticalAddMod + criticalAddVuln;
		damage *= weaponTraitMod;
		damage *= generalTraitMod;
		damage *= attributeMod;
		damage *= weaponMod;
		damage *= penaltyMod;
		
		if (attacker.isDebug())
		{
			final StatsSet set = new StatsSet();
			set.set("Formula", "Backstab damage");
			set.set("skillPower", skill.getPower(isPvP, isPvE));
			set.set("ssboost", ssboost);
			set.set("proximityBonus", proximityBonus);
			set.set("pvpBonus", pvpBonus);
			set.set("baseMod", baseMod);
			set.set("criticalMod", criticalMod);
			set.set("criticalModPos", criticalModPos);
			set.set("criticalVulnMod", criticalVulnMod);
			set.set("criticalAddMod", criticalAddMod);
			set.set("criticalAddVuln", criticalAddVuln);
			set.set("weaponTraitMod", weaponTraitMod);
			set.set("generalTraitMod", generalTraitMod);
			set.set("attributeMod", attributeMod);
			set.set("weaponMod", weaponMod);
			set.set("penaltyMod", penaltyMod);
			set.set("damage", (int) damage);
			Debug.sendSkillDebug(attacker, target, skill, set);
		}
		
		// Reunion balancer
		damage = BalanceHandler.getInstance().calc(attacker, target, skill, damage, false);
		// Reunion balancer - End
		
		return Math.max(damage, 1);
	}
	
	/**
	 * Calculated damage caused by ATTACK of attacker on target, called separately for each weapon, if dual-weapon is used.
	 * @param attacker player or NPC that makes ATTACK
	 * @param target player or NPC, target of ATTACK
	 * @param skill
	 * @param shld
	 * @param crit if the ATTACK have critical success
	 * @param ss if weapon item was charged by soulshot
	 * @return
	 */
	public static final double calcPhysDam(L2Character attacker, L2Character target, L2Skill skill, byte shld, boolean crit, boolean ss)
	{
		final boolean isPvP = attacker.isPlayable() && target.isPlayable();
		final boolean isPvE = attacker.isPlayable() && target.isAttackable();
		double proximityBonus = skill == null ? attacker.isBehindTarget() ? 1.2 : attacker.isInFrontOfTarget() ? 1 : 1.1 : 1; // Behind: +20% - Side: +10%
		double damage = attacker.getPAtk(target);
		double defence = target.getPDef(attacker);
		damage *= calcValakasTrait(attacker, target, skill);
		
		// Defense bonuses in PvP fight
		if (isPvP)
		{
			defence *= (skill == null) ? target.calcStat(Stats.PVP_PHYSICAL_DEF, 1, null, null) : target.calcStat(Stats.PVP_PHYS_SKILL_DEF, 1, null, null);
		}
		
		switch (shld)
		{
			case SHIELD_DEFENSE_SUCCEED:
				if (!Config.ALT_GAME_SHIELD_BLOCKS)
				{
					defence += target.getShldDef();
				}
				break;
			case SHIELD_DEFENSE_PERFECT_BLOCK: // perfect block
				return 1.;
		}
		
		// vGodFather correct wrong ss multiplier applied on skills
		// Add soulshot boost.
		int ssBoost = ss ? 2 : 1;
		damage = (skill != null) ? (damage + skill.getPower(attacker, target, isPvP, isPvE)) * ssBoost : damage * ssBoost;
		
		L2Weapon weapon = attacker.getActiveWeaponItem();
		boolean isBow = (weapon != null) && !attacker.isTransformed() && (weapon.getItemType().isRanged());
		
		if (crit)
		{
			// H5 Damage Formula
			damage = 2 * attacker.calcStat(Stats.CRITICAL_DAMAGE, 1, target, skill) * attacker.calcStat(Stats.CRITICAL_DAMAGE_POS, 1, target, skill) * target.calcStat(Stats.DEFENCE_CRITICAL_DAMAGE, 1, target, null) * ((76 * damage * proximityBonus) / defence);
			damage += ((attacker.calcStat(Stats.CRITICAL_DAMAGE_ADD, 0, target, skill) * 77) / defence);
			damage += target.calcStat(Stats.DEFENCE_CRITICAL_DAMAGE_ADD, 0, target, skill);
		}
		else
		{
			damage = (76 * damage * proximityBonus) / defence;
		}
		
		damage *= calcWeaponTraitBonus(attacker, target);
		// Weapon random damage
		damage *= attacker.getRandomDamageMultiplier();
		if ((shld > 0) && Config.ALT_GAME_SHIELD_BLOCKS)
		{
			damage -= target.getShldDef();
			if (damage < 0)
			{
				damage = 0;
			}
		}
		
		if (target.isNpc())
		{
			switch (((L2Npc) target).getTemplate().getRace())
			{
				case BEAST:
					damage *= attacker.getPAtkMonsters(target);
					break;
				case ANIMAL:
					damage *= attacker.getPAtkAnimals(target);
					break;
				case PLANT:
					damage *= attacker.getPAtkPlants(target);
					break;
				case DRAGON:
					damage *= attacker.getPAtkDragons(target);
					break;
				case BUG:
					damage *= attacker.getPAtkInsects(target);
					break;
				case GIANT:
					damage *= attacker.getPAtkGiants(target);
					break;
				case CONSTRUCT:
					damage *= attacker.getPAtkMagicCreatures(target);
					break;
				default:
					// nothing
					break;
			}
		}
		
		if ((damage > 0) && (damage < 1))
		{
			damage = 1;
		}
		else if (damage < 0)
		{
			damage = 0;
		}
		
		// Dmg bonuses in PvP fight
		if (isPvP)
		{
			if (skill == null)
			{
				damage *= attacker.calcStat(Stats.PVP_PHYSICAL_DMG, 1, null, null);
			}
			else
			{
				damage *= attacker.calcStat(Stats.PVP_PHYS_SKILL_DMG, 1, null, null);
			}
		}
		
		// Physical skill dmg boost
		if (skill != null)
		{
			damage = attacker.calcStat(Stats.PHYSICAL_SKILL_POWER, damage, null, null);
		}
		
		damage *= calcAttributeBonus(attacker, target, skill);
		if (target.isAttackable())
		{
			if (isBow)
			{
				if (skill != null)
				{
					damage *= attacker.calcStat(Stats.PVE_BOW_SKILL_DMG, 1, null, null);
				}
				else
				{
					damage *= attacker.calcStat(Stats.PVE_BOW_DMG, 1, null, null);
				}
			}
			else
			{
				damage *= attacker.calcStat(Stats.PVE_PHYSICAL_DMG, 1, null, null);
			}
			if (!target.isRaid() && !target.isRaidMinion() && (target.getLevel() >= Config.MIN_NPC_LVL_DMG_PENALTY) && (attacker.getActingPlayer() != null) && ((target.getLevel() - attacker.getActingPlayer().getLevel()) >= 2))
			{
				int lvlDiff = target.getLevel() - attacker.getActingPlayer().getLevel() - 1;
				if (skill != null)
				{
					if (lvlDiff >= Config.NPC_SKILL_DMG_PENALTY.size())
					{
						damage *= Config.NPC_SKILL_DMG_PENALTY.get(Config.NPC_SKILL_DMG_PENALTY.size() - 1);
					}
					else
					{
						damage *= Config.NPC_SKILL_DMG_PENALTY.get(lvlDiff);
					}
				}
				else if (crit)
				{
					if (lvlDiff >= Config.NPC_CRIT_DMG_PENALTY.size())
					{
						damage *= Config.NPC_CRIT_DMG_PENALTY.get(Config.NPC_CRIT_DMG_PENALTY.size() - 1);
					}
					else
					{
						damage *= Config.NPC_CRIT_DMG_PENALTY.get(lvlDiff);
					}
				}
				else
				{
					if (lvlDiff >= Config.NPC_DMG_PENALTY.size())
					{
						damage *= Config.NPC_DMG_PENALTY.get(Config.NPC_DMG_PENALTY.size() - 1);
					}
					else
					{
						damage *= Config.NPC_DMG_PENALTY.get(lvlDiff);
					}
				}
			}
		}
		
		// Reunion balancer
		damage = BalanceHandler.getInstance().calc(attacker, target, skill, damage, false);
		// Reunion balancer - End
		
		return damage;
	}
	
	public static final double calcMagicDam(L2Character attacker, L2Character target, L2Skill skill, byte shld, boolean sps, boolean bss, boolean mcrit)
	{
		double mDef = target.getMDef(attacker, skill);
		switch (shld)
		{
			case SHIELD_DEFENSE_SUCCEED:
				mDef += target.getShldDef(); // kamael
				break;
			case SHIELD_DEFENSE_PERFECT_BLOCK: // perfect block
				return 1;
		}
		
		double mAtk = attacker.getMAtk(target, skill);
		final boolean isPvP = attacker.isPlayable() && target.isPlayable();
		final boolean isPvE = attacker.isPlayable() && target.isAttackable();
		
		// PvP bonuses for defense
		if (isPvP)
		{
			if (skill.isMagic())
			{
				mDef *= target.calcStat(Stats.PVP_MAGICAL_DEF, 1, null, null);
			}
			else
			{
				mDef *= target.calcStat(Stats.PVP_PHYS_SKILL_DEF, 1, null, null);
			}
		}
		
		// Bonus Spirit shot
		mAtk *= bss ? 4 : sps ? 2 : 1;
		// MDAM Formula.
		double damage = ((91 * Math.sqrt(mAtk)) / mDef) * skill.getPower(attacker, target, isPvP, isPvE);
		
		// Failure calculation
		if (Config.ALT_GAME_MAGICFAILURES && !calcMagicSuccess(attacker, target, skill))
		{
			if (attacker.isPlayer())
			{
				if (calcMagicSuccess(attacker, target, skill) && ((target.getLevel() - attacker.getLevel()) <= 9))
				{
					if (skill.getSkillType() == L2SkillType.DRAIN)
					{
						attacker.sendPacket(SystemMessageId.DRAIN_HALF_SUCCESFUL);
					}
					else
					{
						attacker.sendPacket(SystemMessageId.ATTACK_FAILED);
					}
					
					damage /= 2;
				}
				else
				{
					SystemMessage sm = SystemMessage.getSystemMessage(SystemMessageId.C1_RESISTED_YOUR_S2);
					sm.addCharName(target);
					sm.addSkillName(skill);
					attacker.sendPacket(sm);
					
					damage = 1;
				}
			}
			
			if (target.isPlayer())
			{
				final SystemMessage sm = (skill.getSkillType() == L2SkillType.DRAIN) ? SystemMessage.getSystemMessage(SystemMessageId.RESISTED_C1_DRAIN) : SystemMessage.getSystemMessage(SystemMessageId.RESISTED_C1_MAGIC);
				sm.addCharName(attacker);
				target.sendPacket(sm);
			}
		}
		else if (mcrit)
		{
			damage *= attacker.isPlayer() && target.isPlayer() ? 2.5 : 3;
			damage *= attacker.calcStat(Stats.MAGIC_CRIT_DMG, 1, null, null);
		}
		
		// Weapon random damage
		damage *= attacker.getRandomDamageMultiplier();
		
		// PvP bonuses for damage
		if (isPvP)
		{
			Stats stat = skill.isMagic() ? Stats.PVP_MAGICAL_DMG : Stats.PVP_PHYS_SKILL_DMG;
			damage *= attacker.calcStat(stat, 1, null, null);
		}
		
		damage *= calcAttributeBonus(attacker, target, skill);
		
		if (target.isAttackable())
		{
			damage *= attacker.calcStat(Stats.PVE_MAGICAL_DMG, 1, null, null);
			if (!target.isRaid() && !target.isRaidMinion() && (target.getLevel() >= Config.MIN_NPC_LVL_DMG_PENALTY) && (attacker.getActingPlayer() != null) && ((target.getLevel() - attacker.getActingPlayer().getLevel()) >= 2))
			{
				int lvlDiff = target.getLevel() - attacker.getActingPlayer().getLevel() - 1;
				if (lvlDiff >= Config.NPC_SKILL_DMG_PENALTY.size())
				{
					damage *= Config.NPC_SKILL_DMG_PENALTY.get(Config.NPC_SKILL_DMG_PENALTY.size() - 1);
				}
				else
				{
					damage *= Config.NPC_SKILL_DMG_PENALTY.get(lvlDiff);
				}
			}
		}
		
		// Reunion balancer
		damage = BalanceHandler.getInstance().calc(attacker, target, skill, damage, true);
		// Reunion balancer - End
		
		return damage;
	}
	
	public static final double calcMagicDam(CubicInstance attacker, L2Character target, L2Skill skill, boolean mcrit, byte shld)
	{
		double mDef = target.getMDef(attacker.getOwner(), skill);
		switch (shld)
		{
			case SHIELD_DEFENSE_SUCCEED:
				mDef += target.getShldDef(); // kamael
				break;
			case SHIELD_DEFENSE_PERFECT_BLOCK: // perfect block
				return 1;
		}
		
		double mAtk = attacker.getTemplate().getPower();
		final boolean isPvP = target.isPlayable();
		final boolean isPvE = target.isAttackable();
		
		// Cubics MDAM Formula (similar to PDAM formula, but using 91 instead of 70, also resisted by mDef).
		double damage = 91 * ((mAtk + skill.getPower(isPvP, isPvE)) / mDef);
		
		// Failure calculation
		L2PcInstance owner = attacker.getOwner().getActingPlayer();
		if (Config.ALT_GAME_MAGICFAILURES && !calcMagicSuccess(owner, target, skill))
		{
			if (calcMagicSuccess(owner, target, skill) && ((target.getLevel() - skill.getMagicLevel()) <= 9))
			{
				if (skill.getSkillType() == L2SkillType.DRAIN)
				{
					owner.sendPacket(SystemMessageId.DRAIN_HALF_SUCCESFUL);
				}
				else
				{
					owner.sendPacket(SystemMessageId.ATTACK_FAILED);
				}
				damage /= 2;
			}
			else
			{
				SystemMessage sm = SystemMessage.getSystemMessage(SystemMessageId.C1_RESISTED_YOUR_S2);
				sm.addCharName(target);
				sm.addSkillName(skill);
				owner.sendPacket(sm);
				damage = 1;
			}
			
			if (target.isPlayer())
			{
				if (skill.getSkillType() == L2SkillType.DRAIN)
				{
					SystemMessage sm = SystemMessage.getSystemMessage(SystemMessageId.RESISTED_C1_DRAIN);
					sm.addCharName(owner);
					target.sendPacket(sm);
				}
				else
				{
					SystemMessage sm = SystemMessage.getSystemMessage(SystemMessageId.RESISTED_C1_MAGIC);
					sm.addCharName(owner);
					target.sendPacket(sm);
				}
			}
		}
		else if (mcrit)
		{
			damage *= 3;
		}
		
		damage *= calcAttributeBonus(owner, target, skill);
		
		if (target.isAttackable())
		{
			damage *= attacker.getOwner().calcStat(Stats.PVE_MAGICAL_DMG, 1, null, null);
			if (!target.isRaid() && !target.isRaidMinion() && (target.getLevel() >= Config.MIN_NPC_LVL_DMG_PENALTY) && (attacker.getOwner() != null) && ((target.getLevel() - attacker.getOwner().getLevel()) >= 2))
			{
				int lvlDiff = target.getLevel() - attacker.getOwner().getLevel() - 1;
				if (lvlDiff >= Config.NPC_SKILL_DMG_PENALTY.size())
				{
					damage *= Config.NPC_SKILL_DMG_PENALTY.get(Config.NPC_SKILL_DMG_PENALTY.size() - 1);
				}
				else
				{
					damage *= Config.NPC_SKILL_DMG_PENALTY.get(lvlDiff);
				}
			}
		}
		
		return damage;
	}
	
	public static final boolean calcCrit(L2Character attacker, L2Character target)
	{
		return calcCrit(attacker, target, null);
	}
	
	/**
	 * Returns true in case of critical hit
	 * @param attacker
	 * @param target
	 * @param skill
	 * @return
	 */
	public static final boolean calcCrit(L2Character attacker, L2Character target, L2Skill skill)
	{
		double rate = 0.d;
		if (skill != null)
		{
			rate = skill.getBaseCritRate() * 10 * BaseStats.STR.calcBonus(attacker);
		}
		else
		{
			rate = attacker.getStat().getCriticalHit(target, null);
			double ratepos = attacker.getStat().calcStat(Stats.CRITICAL_RATE_POS, 1, null, null);
			rate = ratepos != 1 ? rate + (rate * ratepos) : rate;
		}
		return (target.getStat().calcStat(Stats.DEFENCE_CRITICAL_RATE, rate, null, null) + target.getStat().calcStat(Stats.DEFENCE_CRITICAL_RATE_ADD, 0, null, null)) > Rnd.get(1000);
	}
	
	/**
	 * Calculates value of lethal chance depending on level difference
	 * @param activeChar
	 * @param target
	 * @param baseLethal
	 * @param magiclvl
	 * @return
	 */
	private static boolean calcLethalSuccess(L2Character activeChar, L2Character target, double baseLethal, int magiclvl)
	{
		if (baseLethal <= 0)
		{
			return false;
		}
		
		double chance = 0;
		if (magiclvl > 0)
		{
			int delta = ((magiclvl + activeChar.getLevel()) / 2) - 1 - target.getLevel();
			
			// delta [-3,infinite)
			if (delta >= -3)
			{
				chance = (baseLethal * ((double) activeChar.getLevel() / target.getLevel()));
			}
			// delta [-9, -3[
			else if ((delta < -3) && (delta >= -9))
			{
				// baseLethal
				// chance = -1 * -----------
				// (delta / 3)
				chance = (-3) * (baseLethal / (delta));
			}
			// delta [-infinite,-9[
			else
			{
				chance = baseLethal / 15d;
			}
		}
		else
		{
			chance = (baseLethal * ((double) activeChar.getLevel() / target.getLevel()));
		}
		
		return Rnd.get(1000) < (10 * activeChar.calcStat(Stats.LETHAL_RATE, chance, target, null));
	}
	
	public static final boolean calcLethalHit(L2Character activeChar, L2Character target, L2Skill skill)
	{
		if ((activeChar.isPlayer() && !activeChar.getAccessLevel().canGiveDamage()) || (((skill.getCondition() & L2Skill.COND_BEHIND) != 0) && !activeChar.isBehindTarget()))
		{
			return false;
		}
		if (target.isLethalable() && !target.isInvul())
		{
			// Lethal Strike
			if (calcLethalSuccess(activeChar, target, skill.getLethalStrikeRate(), skill.getMagicLevel()))
			{
				// for Players CP and HP is set to 1.
				if (target.isPlayer() && FormulasConfigs.ALLOW_LETHAL_STRIKES_ON_PLAYERS && !FormulasConfigs.ALLOW_LETHAL_STRIKES_ON_PLAYERS_CP_ONLY)
				{
					target.setCurrentCp(1);
					target.setCurrentHp(1);
					target.sendPacket(SystemMessageId.LETHAL_STRIKE);
				}
				else if (target.isPlayer() && FormulasConfigs.ALLOW_LETHAL_STRIKES_ON_PLAYERS && FormulasConfigs.ALLOW_LETHAL_STRIKES_ON_PLAYERS_CP_ONLY)
				{
					target.setCurrentCp(1);
					target.sendPacket(SystemMessageId.LETHAL_STRIKE);
				}
				else if (target.isSummon() && FormulasConfigs.ALLOW_LETHAL_STRIKES_ON_SUMMONS)
				{
					target.setCurrentHp(1);
				}
				// for Monsters HP is set to 1.
				else if (target.isMonster() && FormulasConfigs.ALLOW_LETHAL_STRIKES_ON_MOBS)
				{
					target.reduceCurrentHp(target.getCurrentHp() - 1, activeChar, skill);
				}
				
				activeChar.sendPacket(SystemMessageId.LETHAL_STRIKE_SUCCESSFUL);
			}
			// Half-Kill
			else if (calcLethalSuccess(activeChar, target, skill.getHalfKillRate(), skill.getMagicLevel()))
			{
				// for Players CP is set to 1.
				if (target.isPlayer() && FormulasConfigs.ALLOW_HALF_KILL_ON_PLAYERS)
				{
					target.setCurrentCp(1);
					target.sendPacket(SystemMessageId.HALF_KILL);
					target.sendPacket(SystemMessageId.CP_DISAPPEARS_WHEN_HIT_WITH_A_HALF_KILL_SKILL);
				}
				// for Monsters HP is set to 50%.
				else if (target.isMonster() && FormulasConfigs.ALLOW_HALF_KILL_ON_MOBS)
				{
					target.setCurrentHp(target.getCurrentHp() * 0.5);
				}
				else if (target.isSummon() && FormulasConfigs.ALLOW_HALF_KILL_ON_SUMMONS)
				{
					target.setCurrentHp(target.getCurrentHp() * 0.5);
				}
				activeChar.sendPacket(SystemMessageId.HALF_KILL);
			}
		}
		else
		{
			return false;
		}
		return true;
	}
	
	public static final boolean calcMCrit(double mRate)
	{
		return mRate > Rnd.get(1000);
	}
	
	/**
	 * @param target
	 * @param dmg
	 * @return true in case when ATTACK is canceled due to hit
	 */
	public static final boolean calcAtkBreak(L2Character target, double dmg)
	{
		if (target.isRaid() || target.isInvul())
		{
			return false; // No attack break
		}
		
		if (target.getFusionSkill() != null)
		{
			return true;
		}
		
		double init = (Config.ALT_GAME_CANCEL_CAST && target.isCastingNow()) || (Config.ALT_GAME_CANCEL_BOW && target.isAttackingNow() && (target.getActiveWeaponItem() != null) && (target.getActiveWeaponItem().getItemType() == WeaponType.BOW)) ? 15 : 0;
		if (init <= 0)
		{
			return false;
		}
		
		// Chance of break is higher with higher dmg
		init += Math.sqrt(13 * dmg);
		
		// Chance is affected by target MEN
		init -= ((BaseStats.MEN.calcBonus(target) * 100) - 100);
		
		// Calculate all modifiers for ATTACK_CANCEL
		double rate = target.calcStat(Stats.ATTACK_CANCEL, init, null, null);
		
		// Adjust the rate to be between 1 and 99
		rate = Math.max(Math.min(rate, FormulasConfigs.MAX_ABNORMAL_STATE_SUCCESS_RATE), 1);
		
		return Rnd.get(1000) < (rate * 10);
	}
	
	/**
	 * Calculate delay (in milliseconds) before next ATTACK
	 * @param attacker
	 * @param target
	 * @param rate
	 * @return
	 */
	public static final int calcPAtkSpd(L2Character attacker, L2Character target, double rate)
	{
		// measured Oct 2006 by Tank6585, formula by Sami
		// attack speed 312 equals 1500 ms delay... (or 300 + 40 ms delay?)
		if (rate < 2)
		{
			return 2700;
		}
		return (int) (470000 / rate);
	}
	
	/**
	 * Calculate delay (in milliseconds) for skills cast
	 * @param attacker
	 * @param skill
	 * @param skillTime
	 * @return
	 */
	public static final int calcAtkSpd(L2Character attacker, L2Skill skill, double skillTime)
	{
		if (skill.isMagic())
		{
			return (int) ((skillTime / attacker.getMAtkSpd()) * 333);
		}
		return (int) ((skillTime / attacker.getPAtkSpd()) * 300);
	}
	
	/**
	 * Formula based on http://l2p.l2wh.com/nonskillattacks.html
	 * @param attacker
	 * @param target
	 * @return {@code true} if hit missed (target evaded), {@code false} otherwise.
	 */
	public static boolean calcHitMiss(L2Character attacker, L2Character target)
	{
		int chance = (80 + (2 * (attacker.getAccuracy() - target.getEvasionRate(attacker)))) * 10;
		
		// Get additional bonus from the conditions when you are attacking
		chance *= HitConditionBonusData.getInstance().getConditionBonus(attacker, target);
		
		chance = Math.max(chance, 200);
		chance = Math.min(chance, 980);
		
		return chance < Rnd.get(1000);
	}
	
	/**
	 * Returns:<br>
	 * 0 = shield defense doesn't succeed<br>
	 * 1 = shield defense succeed<br>
	 * 2 = perfect block<br>
	 * @param attacker
	 * @param target
	 * @param skill
	 * @param sendSysMsg
	 * @return
	 */
	public static byte calcShldUse(L2Character attacker, L2Character target, L2Skill skill, boolean sendSysMsg)
	{
		if ((skill != null) && skill.ignoreShield())
		{
			return 0;
		}
		
		L2Item item = target.getSecondaryWeaponItem();
		if ((item == null) || !(item instanceof L2Armor) || (((L2Armor) item).getItemType() == ArmorType.SIGIL))
		{
			return 0;
		}
		
		double shldRate = target.calcStat(Stats.SHIELD_RATE, 0, attacker, null) * BaseStats.DEX.calcBonus(target);
		if (shldRate <= 1e-6)
		{
			return 0;
		}
		
		int degreeside = (int) target.calcStat(Stats.SHIELD_DEFENCE_ANGLE, 0, null, null) + 120;
		if ((degreeside < 360) && (!target.isFacing(attacker, degreeside)))
		{
			return 0;
		}
		
		byte shldSuccess = SHIELD_DEFENSE_FAILED;
		// if attacker
		// if attacker use bow and target wear shield, shield block rate is multiplied by 1.3 (30%)
		L2Weapon at_weapon = attacker.getActiveWeaponItem();
		if ((at_weapon != null) && (at_weapon.getItemType() == WeaponType.BOW))
		{
			shldRate *= 1.3;
		}
		
		if ((shldRate > 0) && ((100 - Config.ALT_PERFECT_SHLD_BLOCK) < Rnd.get(100)))
		{
			shldSuccess = SHIELD_DEFENSE_PERFECT_BLOCK;
		}
		else if (shldRate > Rnd.get(100))
		{
			shldSuccess = SHIELD_DEFENSE_SUCCEED;
		}
		
		if (sendSysMsg && target.isPlayer())
		{
			L2PcInstance enemy = target.getActingPlayer();
			
			switch (shldSuccess)
			{
				case SHIELD_DEFENSE_SUCCEED:
					enemy.sendPacket(SystemMessageId.SHIELD_DEFENCE_SUCCESSFULL);
					break;
				case SHIELD_DEFENSE_PERFECT_BLOCK:
					enemy.sendPacket(SystemMessageId.YOUR_EXCELLENT_SHIELD_DEFENSE_WAS_A_SUCCESS);
					break;
			}
		}
		
		return shldSuccess;
	}
	
	public static byte calcShldUse(L2Character attacker, L2Character target, L2Skill skill)
	{
		return calcShldUse(attacker, target, skill, true);
	}
	
	public static byte calcShldUse(L2Character attacker, L2Character target)
	{
		return calcShldUse(attacker, target, null, true);
	}
	
	public static boolean calcMagicAffected(L2Character actor, L2Character target, L2Skill skill)
	{
		double defence = 0;
		if (skill.isActive() && skill.isOffensive())
		{
			defence = target.getMDef(actor, skill);
		}
		
		double attack = 2 * actor.getMAtk(target, skill) * (1 + (calcSkillVulnerability(actor, target, skill) / 100));
		double d = (attack - defence) / (attack + defence);
		
		if (skill.isDebuff())
		{
			if (target.calcStat(Stats.DEBUFF_IMMUNITY, 0, null, skill) > 0)
			{
				return false;
			}
		}
		
		d += 0.5 * Rnd.nextGaussian();
		return d > 0;
	}
	
	public static double calcSkillVulnerability(L2Character attacker, L2Character target, L2Skill skill)
	{
		double multiplier = 0; // initialize...
		
		// Get the skill type to calculate its effect in function of base stats
		// of the L2Character target
		if (skill != null)
		{
			// Finally, calculate skill type vulnerabilities
			multiplier = calcSkillTraitVulnerability(multiplier, target, skill);
		}
		return multiplier;
	}
	
	public static double calcSkillTraitVulnerability(double multiplier, L2Character target, L2Skill skill)
	{
		if (skill == null)
		{
			return multiplier;
		}
		
		final L2TraitType trait = skill.getTraitType();
		// First check if skill have trait set
		// If yes, use correct vuln
		if ((trait != null) && (trait != L2TraitType.NONE))
		{
			switch (trait)
			{
				case BLEED:
					multiplier = target.calcStat(Stats.BLEED_VULN, multiplier, target, null);
					break;
				case BOSS:
					multiplier = target.calcStat(Stats.BOSS_VULN, multiplier, target, null);
					break;
				// case DEATH:
				case DERANGEMENT:
					multiplier = target.calcStat(Stats.DERANGEMENT_VULN, multiplier, target, null);
					break;
				// case ETC:
				case GUST:
					multiplier = target.calcStat(Stats.GUST_VULN, multiplier, target, null);
					break;
				case HOLD:
					multiplier = target.calcStat(Stats.ROOT_VULN, multiplier, target, null);
					break;
				case PARALYZE:
					multiplier = target.calcStat(Stats.PARALYZE_VULN, multiplier, target, null);
					break;
				case PHYSICAL_BLOCKADE:
					multiplier = target.calcStat(Stats.PHYSICALBLOCKADE_VULN, multiplier, target, null);
					break;
				case POISON:
					multiplier = target.calcStat(Stats.POISON_VULN, multiplier, target, null);
					break;
				case SHOCK:
					multiplier = target.calcStat(Stats.STUN_VULN, multiplier, target, null);
					break;
				case SLEEP:
					multiplier = target.calcStat(Stats.SLEEP_VULN, multiplier, target, null);
					break;
				case VALAKAS:
					multiplier = target.calcStat(Stats.VALAKAS_VULN, multiplier, target, null);
					break;
			}
		}
		else
		{
			// Since not all traits are handled by trait parameter
			// rest is checked by skillType or isDebuff Boolean.
			final L2SkillType type = skill.getSkillType();
			if (type == L2SkillType.BUFF)
			{
				multiplier = target.calcStat(Stats.BUFF_VULN, multiplier, target, null);
			}
			else if ((type == L2SkillType.DEBUFF) || (skill.isDebuff()))
			{
				multiplier = target.calcStat(Stats.DEBUFF_VULN, multiplier, target, null);
			}
		}
		return multiplier;
	}
	
	public static double calcSkillProficiency(L2Skill skill, L2Character attacker, L2Character target)
	{
		double multiplier = 0;
		
		if (skill != null)
		{
			// Calculate trait-type vulnerabilities
			multiplier = calcSkillTraitProficiency(multiplier, attacker, target, skill);
		}
		
		return multiplier;
	}
	
	public static double calcSkillTraitProficiency(double multiplier, L2Character attacker, L2Character target, L2Skill skill)
	{
		if (skill == null)
		{
			return multiplier;
		}
		
		final L2TraitType trait = skill.getTraitType();
		// First check if skill have trait set
		// If yes, use correct vuln
		if ((trait != null) && (trait != L2TraitType.NONE))
		{
			switch (trait)
			{
				case BLEED:
					multiplier = attacker.calcStat(Stats.BLEED_PROF, multiplier, target, null);
					break;
				// case BOSS:
				// case DEATH:
				case DERANGEMENT:
					multiplier = attacker.calcStat(Stats.DERANGEMENT_PROF, multiplier, target, null);
					break;
				// case ETC:
				// case GUST:
				case HOLD:
					multiplier = attacker.calcStat(Stats.ROOT_PROF, multiplier, target, null);
					break;
				case PARALYZE:
					multiplier = attacker.calcStat(Stats.PARALYZE_PROF, multiplier, target, null);
					break;
				// case PHYSICAL_BLOCKADE:
				case POISON:
					multiplier = attacker.calcStat(Stats.POISON_PROF, multiplier, target, null);
					break;
				case SHOCK:
					multiplier = attacker.calcStat(Stats.STUN_PROF, multiplier, target, null);
					break;
				case SLEEP:
					multiplier = attacker.calcStat(Stats.SLEEP_PROF, multiplier, target, null);
					break;
				case VALAKAS:
					multiplier = attacker.calcStat(Stats.VALAKAS_PROF, multiplier, target, null);
					break;
			}
		}
		else
		{
			// Since not all traits are handled by skill parameter
			// rest is checked by skillType or isDebuff Boolean.
			final L2SkillType type = skill.getSkillType();
			if ((type == L2SkillType.DEBUFF) || (skill.isDebuff()))
			{
				multiplier = target.calcStat(Stats.DEBUFF_PROF, multiplier, target, null);
			}
		}
		return multiplier;
	}
	
	public static double calcLvlBonusMod(L2Character attacker, L2Character target, L2Skill skill)
	{
		int attackerLvl = skill.getMagicLevel() > 0 ? skill.getMagicLevel() : attacker.getLevel();
		double skillLvlBonusRateMod = 1 + (skill.getLvlBonusRate() / 100.);
		double lvlMod = 1 + ((attackerLvl - target.getLevel()) / 100.);
		return skillLvlBonusRateMod * lvlMod;
	}
	
	public static double calcElementMod(L2Character attacker, L2Character target, L2Skill skill)
	{
		final byte skillElement = skill.getElement();
		if (skillElement == Elementals.NONE)
		{
			return 1;
		}
		
		int attackerElement = attacker.getAttackElement() == skillElement ? attacker.getAttackElementValue(skillElement) + skill.getElementPower() : attacker.getAttackElementValue(skillElement);
		int targetElement = target.getDefenseElementValue(skillElement);
		double elementMod = 1 + ((attackerElement - targetElement) / 1000.);
		return elementMod;
	}
	
	public static boolean calcEffectSuccess(L2Character attacker, L2Character target, EffectTemplate effect, L2Skill skill, byte shld, boolean ss, boolean sps, boolean bss)
	{
		// Perfect Shield Block.
		if (shld == SHIELD_DEFENSE_PERFECT_BLOCK)
		{
			if (attacker.isDebug())
			{
				attacker.sendDebugMessage(skill.getName() + " blocked by shield");
			}
			
			return false;
		}
		
		if (skill.isDebuff())
		{
			if (skill.getPower() == -1)
			{
				if (attacker.isDebug())
				{
					attacker.sendDebugMessage(skill.getName() + " ignoring resists");
				}
				return true;
			}
			
			if (target.calcStat(Stats.DEBUFF_IMMUNITY, 0, attacker, skill) > 0)
			{
				final SystemMessage sm = SystemMessage.getSystemMessage(SystemMessageId.C1_RESISTED_YOUR_S2);
				sm.addCharName(target);
				sm.addSkillName(skill);
				attacker.sendPacket(sm);
				return false;
			}
		}
		
		final double activateRate = (skill.getActivateRate() > 0) && (effect.effectPower < 0) ? skill.getPower() : effect.effectPower;
		// Force success some cancel effects cause they use other calculations
		if ((activateRate == -1) || skill.hasEffectType(L2EffectType.CANCEL, L2EffectType.DISPEL, L2EffectType.DISPEL_BY_SLOT, L2EffectType.STEAL_ABNORMAL))
		{
			return true;
		}
		
		if (skill.mustIncludeBasicProperty() && (skill.getBasicProperty() == BaseStats.NONE))
		{
			return true;
		}
		
		int magicLevel = skill.getMagicLevel();
		if (magicLevel <= -1)
		{
			magicLevel = target.getLevel() + 3;
		}
		
		int targetBaseStat = 0;
		switch (skill.getBasicProperty())
		{
			case STR:
				targetBaseStat = target.getSTR();
				break;
			case DEX:
				targetBaseStat = target.getDEX();
				break;
			case CON:
				targetBaseStat = target.getCON();
				break;
			case INT:
				targetBaseStat = target.getINT();
				break;
			case MEN:
				targetBaseStat = target.getMEN();
				break;
			case WIT:
				targetBaseStat = target.getWIT();
				break;
		}
		
		double baseMod = 0;
		double rate = 0;
		if (skill.mustIncludeBasicProperty())
		{
			// Calculate BaseRate.
			baseMod = ((((((magicLevel - target.getLevel()) + 3) * skill.getLvlBonusRate()) + activateRate) + 30.0) - targetBaseStat);
			rate = baseMod;
		}
		else
		{
			// Calculate BaseRate.
			baseMod = skill.getPower() / skill.getBasicProperty().calcBonus(target);
			rate = baseMod;
		}
		
		// Resists.
		double vuln = calcSkillTraitVulnerability(0, target, skill);
		double prof = calcSkillTraitProficiency(0, attacker, target, skill);
		double resMod = 1 + ((vuln + prof) / 100);
		
		// Check ResMod Limits.
		rate *= Math.min(Math.max(resMod, 0.1), 1.9);
		
		// Lvl Bonus Modifier.
		double lvlBonusMod = calcLvlBonusMod(attacker, target, skill);
		rate *= lvlBonusMod;
		
		// Element Modifier.
		double elementMod = calcElementMod(attacker, target, skill);
		rate *= elementMod;
		
		// Add Matk/Mdef Bonus
		double mAtkMod = 1.0;
		if (skill.isMagic())
		{
			double mAtk = attacker.getMAtk(null, null);
			double val = 0;
			if (attacker.isChargedShot(ShotType.BLESSED_SPIRITSHOTS))
			{
				val = mAtk * 3.0;// 3.0 is the blessed spiritshot multiplier
			}
			val += mAtk;
			val = (Math.sqrt(val) / target.getMDef(null, null)) * 11.0;
			mAtkMod = val;
		}
		
		rate *= mAtkMod;
		double finalRate = Math.min(Math.max(rate, skill.getMinChance()), skill.getMaxChance());
		
		boolean result = Rnd.chance(finalRate);
		if (attacker.isDebug() || Config.DEVELOPER)
		{
			final StatsSet set = new StatsSet();
			set.set("baseMod", baseMod);
			set.set("resMod", resMod);
			set.set("lvlBonusMod", lvlBonusMod);
			set.set("elementMod", elementMod);
			set.set("mAtkMod", mAtkMod);
			set.set("rate", rate);
			set.set("finalRate", finalRate);
			set.set("result", String.valueOf(result));
			Debug.sendSkillDebug(attacker, target, skill, set);
		}
		
		return result;
	}
	
	public static boolean calcSkillSuccess(L2Character attacker, L2Character target, L2Skill skill, byte shld, boolean ss, boolean sps, boolean bss)
	{
		// Perfect Shield Block.
		if (shld == SHIELD_DEFENSE_PERFECT_BLOCK)
		{
			if (attacker.isDebug())
			{
				attacker.sendDebugMessage(skill.getName() + " blocked by shield");
			}
			
			return false;
		}
		
		if (skill.isDebuff())
		{
			if (skill.getPower() == -1)
			{
				if (attacker.isDebug())
				{
					attacker.sendDebugMessage(skill.getName() + " ignoring resists");
				}
				return true;
			}
			
			if (target.calcStat(Stats.DEBUFF_IMMUNITY, 0, attacker, skill) > 0)
			{
				final SystemMessage sm = SystemMessage.getSystemMessage(SystemMessageId.C1_RESISTED_YOUR_S2);
				sm.addCharName(target);
				sm.addSkillName(skill);
				attacker.sendPacket(sm);
				return false;
			}
		}
		
		// Calculate BaseRate.
		double baseRate = skill.getPower();
		double statMod = skill.getBasicProperty().calcBonus(target);
		double rate = (baseRate / statMod);
		
		// Resists.
		double vuln = calcSkillTraitVulnerability(0, target, skill);
		double prof = calcSkillTraitProficiency(0, attacker, target, skill);
		double resMod = 1 + ((vuln + prof) / 100);
		
		// Check ResMod Limits.
		rate *= Math.min(Math.max(resMod, 0.1), 1.9);
		
		// Lvl Bonus Modifier.
		double lvlBonusMod = calcLvlBonusMod(attacker, target, skill);
		rate *= lvlBonusMod;
		
		// Element Modifier.
		double elementMod = calcElementMod(attacker, target, skill);
		rate *= elementMod;
		
		// Add Matk/Mdef Bonus
		double mAtkMod = 1.0;
		if (skill.isMagic())
		{
			double mAtk = attacker.getMAtk(null, null);
			double val = 0;
			if (attacker.isChargedShot(ShotType.BLESSED_SPIRITSHOTS))
			{
				val = mAtk * 3.0;// 3.0 is the blessed spiritshot multiplier
			}
			val += mAtk;
			val = (Math.sqrt(val) / target.getMDef(null, null)) * 11.0;
			mAtkMod = val;
		}
		
		rate *= mAtkMod;
		double finalRate = Math.min(Math.max(rate, skill.getMinChance()), skill.getMaxChance());
		
		boolean result = (finalRate * 10) > Rnd.get(1000);
		if (attacker.isDebug() || Config.DEVELOPER)
		{
			final StatsSet set = new StatsSet();
			set.set("baseRate", baseRate);
			set.set("resMod", resMod);
			set.set("lvlBonusMod", lvlBonusMod);
			set.set("elementMod", elementMod);
			set.set("mAtkMod", mAtkMod);
			set.set("rate", rate);
			set.set("finalRate", finalRate);
			set.set("result", String.valueOf(result));
			Debug.sendSkillDebug(attacker, target, skill, set);
		}
		
		return result;
	}
	
	public static boolean calcCubicSkillSuccess(CubicInstance attacker, L2Character target, L2Skill skill, byte shld)
	{
		if (skill.isDebuff())
		{
			if (skill.getPower() == -1)
			{
				return true;
			}
			else if (target.calcStat(Stats.DEBUFF_IMMUNITY, 0, null, skill) > 0)
			{
				return false;
			}
		}
		
		// Perfect Shield Block.
		if (shld == SHIELD_DEFENSE_PERFECT_BLOCK)
		{
			return false;
		}
		
		// if target reflect this skill then the effect will fail
		if (calcSkillReflect(target, skill) != SKILL_REFLECT_FAILED)
		{
			return false;
		}
		
		// Calculate BaseRate.
		double baseRate = skill.getPower();
		double statMod = skill.getBasicProperty().calcBonus(target);
		double rate = (baseRate / statMod);
		
		// Resists.
		double vuln = calcSkillVulnerability(attacker.getOwner(), target, skill);
		double prof = calcSkillProficiency(skill, attacker.getOwner(), target);
		double resMod = 1 + ((vuln + prof) / 100);
		
		// Check ResMod Limits.
		rate *= Math.min(Math.max(resMod, 0.1), 1.9);
		
		// Lvl Bonus Modifier.
		double lvlBonusMod = calcLvlBonusMod(attacker.getOwner(), target, skill);
		rate *= lvlBonusMod;
		
		// Element Modifier.
		double elementMod = calcElementMod(attacker.getOwner(), target, skill);
		rate *= elementMod;
		
		// Add Matk/Mdef Bonus
		double mAtkMod = 1.0;
		if (skill.isMagic())
		{
			double mAtk = attacker.getOwner().getMAtk(null, null);
			double val = 0;
			if (attacker.getOwner().isChargedShot(ShotType.BLESSED_SPIRITSHOTS))
			{
				val = mAtk * 3.0;// 3.0 is the blessed spiritshot multiplier
			}
			val += mAtk;
			val = (Math.sqrt(val) / target.getMDef(null, null)) * 11.0;
			mAtkMod = val;
		}
		
		rate *= mAtkMod;
		double finalRate = Math.min(Math.max(rate, skill.getMinChance()), skill.getMaxChance());
		
		boolean result = (finalRate * 10) > Rnd.get(1000);
		if (attacker.getOwner().isDebug())
		{
			final StatsSet set = new StatsSet();
			set.set("baseMod", baseRate);
			set.set("resMod", resMod);
			set.set("statMod", statMod);
			set.set("elementMod", elementMod);
			set.set("lvlBonusMod", lvlBonusMod);
			set.set("rate", rate);
			set.set("finalRate", finalRate);
			set.set("result", String.valueOf(result));
			Debug.sendSkillDebug(attacker.getOwner(), target, skill, set);
		}
		
		return result;
	}
	
	public static boolean calcMagicSuccess(L2Character attacker, L2Character target, L2Skill skill)
	{
		if (skill.getPower() == -1)
		{
			return true;
		}
		
		int lvlDifference = (target.getLevel() - (skill.hasEffectType(L2EffectType.SPOIL) ? skill.getMagicLevel() : attacker.getLevel()));
		double lvlModifier = Math.pow(1.3, lvlDifference);
		float targetModifier = 1;
		if (target.isAttackable() && !target.isRaid() && !target.isRaidMinion() && (target.getLevel() >= Config.MIN_NPC_LVL_MAGIC_PENALTY) && (attacker.getActingPlayer() != null) && ((target.getLevel() - attacker.getActingPlayer().getLevel()) >= 3))
		{
			int lvlDiff = target.getLevel() - attacker.getActingPlayer().getLevel() - 2;
			if (lvlDiff >= Config.NPC_SKILL_CHANCE_PENALTY.size())
			{
				targetModifier = Config.NPC_SKILL_CHANCE_PENALTY.get(Config.NPC_SKILL_CHANCE_PENALTY.size() - 1);
			}
			else
			{
				targetModifier = Config.NPC_SKILL_CHANCE_PENALTY.get(lvlDiff);
			}
		}
		// general magic resist
		final double resModifier = target.calcStat(Stats.MAGIC_SUCCESS_RES, 1, null, skill);
		// vGodFather: implement magic failure rate
		final double failureModifier = attacker.calcStat(Stats.MAGIC_FAILURE_RATE, 1, target, skill);
		int rate = 100 - Math.round((float) (lvlModifier * targetModifier * resModifier * failureModifier));
		
		boolean result = Rnd.chance(rate);
		if (attacker.isDebug())
		{
			final StatsSet set = new StatsSet();
			set.set("lvlDifference", lvlDifference);
			set.set("lvlModifier", lvlModifier);
			set.set("resModifier", resModifier);
			set.set("failureModifier", failureModifier);
			set.set("targetModifier", targetModifier);
			set.set("rate", rate);
			Debug.sendSkillDebug(attacker, target, skill, set);
		}
		
		return result;
	}
	
	public static double calcManaDam(L2Character attacker, L2Character target, L2Skill skill, byte shld, boolean sps, boolean bss, boolean mcrit)
	{
		// Formula: (SQR(M.Atk)*Power*(Target Max MP/97))/M.Def
		double mAtk = attacker.getMAtk(target, skill);
		double mDef = target.getMDef(attacker, skill);
		final boolean isPvP = attacker.isPlayable() && target.isPlayable();
		final boolean isPvE = attacker.isPlayable() && target.isAttackable();
		double mp = target.getMaxMp();
		
		switch (shld)
		{
			case SHIELD_DEFENSE_SUCCEED:
				mDef += target.getShldDef();
				break;
			case SHIELD_DEFENSE_PERFECT_BLOCK: // perfect block
				return 1;
		}
		
		// Bonus Spiritshot
		mAtk *= bss ? 4 : sps ? 2 : 1;
		
		double damage = (Math.sqrt(mAtk) * skill.getPower(attacker, target, isPvP, isPvE) * (mp / 97)) / mDef;
		damage *= (1 + (calcSkillVulnerability(attacker, target, skill) / 100));
		
		if (target.isAttackable())
		{
			damage *= attacker.calcStat(Stats.PVE_MAGICAL_DMG, 1, null, null);
			if (!target.isRaid() && !target.isRaidMinion() && (target.getLevel() >= Config.MIN_NPC_LVL_DMG_PENALTY) && (attacker.getActingPlayer() != null) && ((target.getLevel() - attacker.getActingPlayer().getLevel()) >= 2))
			{
				int lvlDiff = target.getLevel() - attacker.getActingPlayer().getLevel() - 1;
				if (lvlDiff >= Config.NPC_SKILL_DMG_PENALTY.size())
				{
					damage *= Config.NPC_SKILL_DMG_PENALTY.get(Config.NPC_SKILL_DMG_PENALTY.size() - 1);
				}
				else
				{
					damage *= Config.NPC_SKILL_DMG_PENALTY.get(lvlDiff);
				}
			}
		}
		
		// Failure calculation
		if (Config.ALT_GAME_MAGICFAILURES && !calcMagicSuccess(attacker, target, skill))
		{
			if (attacker.isPlayer())
			{
				SystemMessage sm = SystemMessage.getSystemMessage(SystemMessageId.DAMAGE_DECREASED_BECAUSE_C1_RESISTED_C2_MAGIC);
				sm.addCharName(target);
				sm.addCharName(attacker);
				attacker.sendPacket(sm);
				damage /= 2;
			}
			
			if (target.isPlayer())
			{
				SystemMessage sm2 = SystemMessage.getSystemMessage(SystemMessageId.C1_WEAKLY_RESISTED_C2_MAGIC);
				sm2.addCharName(target);
				sm2.addCharName(attacker);
				target.sendPacket(sm2);
			}
		}
		
		if (mcrit)
		{
			damage *= 3;
			attacker.sendPacket(SystemMessageId.CRITICAL_HIT_MAGIC);
		}
		return damage;
	}
	
	public static double calculateSkillResurrectRestorePercent(double baseRestorePercent, L2Character caster)
	{
		if ((baseRestorePercent == 0) || (baseRestorePercent == 100))
		{
			return baseRestorePercent;
		}
		
		double restorePercent = baseRestorePercent * BaseStats.WIT.calcBonus(caster);
		if ((restorePercent - baseRestorePercent) > 20.0)
		{
			restorePercent += 20.0;
		}
		
		restorePercent = Math.max(restorePercent, baseRestorePercent);
		restorePercent = Math.min(restorePercent, 90.0);
		
		return restorePercent;
	}
	
	public static boolean calcPhysicalSkillEvasion(L2Character activeChar, L2Character target, L2Skill skill)
	{
		if ((skill.isMagic() && (skill.getSkillType() != L2SkillType.BLOW)) || skill.isDebuff())
		{
			return false;
		}
		
		if (Rnd.get(100) < target.calcStat(Stats.P_SKILL_EVASION, 0, null, skill))
		{
			if (activeChar.isPlayer())
			{
				SystemMessage sm = SystemMessage.getSystemMessage(SystemMessageId.C1_DODGES_ATTACK);
				sm.addString(target.getName());
				activeChar.getActingPlayer().sendPacket(sm);
			}
			if (target.isPlayer())
			{
				SystemMessage sm = SystemMessage.getSystemMessage(SystemMessageId.AVOIDED_C1_ATTACK2);
				sm.addString(activeChar.getName());
				target.getActingPlayer().sendPacket(sm);
			}
			return true;
		}
		return false;
	}
	
	public static boolean calcSkillMastery(L2Character actor, L2Skill sk)
	{
		// Static Skills are not affected by Skill Mastery.
		if (sk.isStatic() || (actor == null))
		{
			return false;
		}
		
		final int val = (int) actor.getStat().calcStat(Stats.SKILL_CRITICAL, 0, null, null);
		
		if (val == 0)
		{
			return false;
		}
		
		if (actor.isPlayer())
		{
			double initVal = 0;
			switch (val)
			{
				case 1:
				{
					initVal = (BaseStats.STR).calcBonus(actor);
					break;
				}
				case 4:
				{
					initVal = (BaseStats.INT).calcBonus(actor);
					break;
				}
			}
			initVal *= actor.getStat().calcStat(Stats.SKILL_CRITICAL_PROBABILITY, 1, null, null);
			return (Rnd.get(1000) < (initVal * 10));
		}
		
		return false;
	}
	
	public static double calcValakasTrait(L2Character attacker, L2Character target, L2Skill skill)
	{
		double calcPower = 0;
		double calcDefen = 0;
		
		if ((skill != null) && (skill.getTraitType() == L2TraitType.VALAKAS))
		{
			calcPower = attacker.calcStat(Stats.VALAKAS_PROF, calcPower, target, skill);
			calcDefen = target.calcStat(Stats.VALAKAS_VULN, calcDefen, target, skill);
		}
		else
		{
			calcPower = attacker.calcStat(Stats.VALAKAS_PROF, calcPower, target, skill);
			if (calcPower > 0)
			{
				calcPower = attacker.calcStat(Stats.VALAKAS_PROF, calcPower, target, skill);
				calcDefen = target.calcStat(Stats.VALAKAS_VULN, calcDefen, target, skill);
			}
		}
		return 1 + ((calcDefen + calcPower) / 100);
	}
	
	public static double calcAttributeBonus(L2Character attacker, L2Character target, L2Skill skill)
	{
		int calcPower = 0;
		int calcDefen = 0;
		int calcTotal = 0;
		double result = 1.0;
		byte element;
		
		if (skill != null)
		{
			element = skill.getElement();
			if (element >= 0)
			{
				calcPower = skill.getElementPower();
				calcDefen = target.getDefenseElementValue(element);
				
				if (attacker.getAttackElement() == element)
				{
					calcPower += attacker.getAttackElementValue(element);
				}
				
				calcTotal = calcPower - calcDefen;
				if (calcTotal > 0)
				{
					if (calcTotal < 50)
					{
						result += calcTotal * 0.003948;
					}
					else if (calcTotal <= 150)
					{
						result = 1.1974;
					}
					else if (calcTotal <= 300)
					{
						result = 1.3973;
					}
					else
					{
						result = 1.6963;
					}
				}
				else if (calcTotal < -110)
				{
					if (attacker.isNpc())
					{
						if (calcTotal <= -170)
						{
							result = 0.8;
						}
						else
						{
							result = 1 - ((170 + calcTotal) * 0.0033d);
						}
					}
				}
				
				if (Config.DEVELOPER || attacker.isDebug())
				{
					_log.info(skill.getName() + ": " + calcPower + ", " + calcDefen + ", " + result + " Total: " + calcTotal);
				}
			}
		}
		else
		{
			element = attacker.getAttackElement();
			if (element >= 0)
			{
				calcPower = attacker.getAttackElementValue(element);
				calcDefen = target.getDefenseElementValue(element);
				
				calcTotal = calcPower - calcDefen;
				if (calcTotal < -110)
				{
					if (attacker.isNpc())
					{
						if (calcTotal <= -170)
						{
							result = 0.8;
						}
						else
						{
							result = 1 - ((170 + calcTotal) * 0.0033d);
						}
					}
				}
				else if (calcTotal > 0)
				{
					if (calcTotal < 50)
					{
						result += calcTotal * 0.003948;
					}
					else if (calcTotal <= 150)
					{
						result = 1.1974;
					}
					else if (calcTotal <= 300)
					{
						result = 1.3973;
					}
					else
					{
						result = 1.6963;
					}
				}
				
				if (Config.DEVELOPER || attacker.isDebug())
				{
					_log.info("Hit: " + calcPower + ", " + calcDefen + ", " + result + " Total: " + calcTotal);
				}
			}
		}
		return Math.max(result, 1.0);
	}
	
	/**
	 * Calculate skill reflection according these three possibilities:<br>
	 * <ul>
	 * <li>Reflect failed</li>
	 * <li>Normal reflect (just effects). <U>Only possible for skilltypes: BUFF, REFLECT, HEAL_PERCENT, MANAHEAL_PERCENT, HOT, CPHOT, MPHOT</U></li>
	 * <li>vengEance reflect (100% damage reflected but damage is also dealt to actor). <U>This is only possible for skills with skilltype PDAM, BLOW, CHARGEDAM, MDAM or DEATHLINK</U></li>
	 * </ul>
	 * @param target
	 * @param skill
	 * @return SKILL_REFLECTED_FAILED, SKILL_REFLECT_SUCCEED or SKILL_REFLECT_VENGEANCE
	 */
	public static byte calcSkillReflect(L2Character target, L2Skill skill)
	{
		// Neither some special skills (like hero debuffs...) or those skills ignoring resistances can be reflected
		if (!skill.canBeReflected() || (skill.getPower() == -1))
		{
			return SKILL_REFLECT_FAILED;
		}
		
		// Only magic and melee skills can be reflected
		if (!skill.isMagic() && ((skill.getCastRange() == -1) || (skill.getCastRange() > MELEE_ATTACK_RANGE)))
		{
			return SKILL_REFLECT_FAILED;
		}
		
		byte reflect = SKILL_REFLECT_FAILED;
		// Check for non-reflected skilltypes, need additional retail check
		
		if (skill.hasEffectType(L2EffectType.MAGICAL_ATTACK_MP, L2EffectType.PHYSICAL_ATTACK, L2EffectType.PHYSICAL_ATTACK_HP_LINK, L2EffectType.DEATH_LINK))
		{
			final Stats stat = skill.isMagic() ? Stats.VENGEANCE_SKILL_MAGIC_DAMAGE : Stats.VENGEANCE_SKILL_PHYSICAL_DAMAGE;
			final double venganceChance = target.getStat().calcStat(stat, 0, target, skill);
			if (venganceChance > Rnd.get(100))
			{
				reflect |= SKILL_REFLECT_VENGEANCE;
			}
		}
		
		switch (skill.getSkillType())
		{
			// vGodfather fixes
			case FEAR:
			case ROOT:
			case STUN:
			case MUTE:
			case BLEED:
			case PARALYZE:
			case SLEEP:
			case DEBUFF:
				// vGodFather fixes End
			case PDAM:
			case MDAM:
			case BLOW:
			case DRAIN:
			case CHARGEDAM:
				final Stats stat = skill.isMagic() ? Stats.VENGEANCE_SKILL_MAGIC_DAMAGE : Stats.VENGEANCE_SKILL_PHYSICAL_DAMAGE;
				final double venganceChance = target.getStat().calcStat(stat, 0, target, skill);
				if (venganceChance > Rnd.get(100))
				{
					reflect |= SKILL_REFLECT_VENGEANCE;
				}
				break;
			default:
			{
				return SKILL_REFLECT_FAILED;
			}
		}
		
		final double reflectChance = target.calcStat(skill.isMagic() ? Stats.REFLECT_SKILL_MAGIC : Stats.REFLECT_SKILL_PHYSIC, 0, null, skill);
		if (Rnd.get(1000) < (reflectChance * 10))
		{
			reflect |= SKILL_REFLECT_SUCCEED;
		}
		
		return reflect;
	}
	
	public static void calcDamageReflected(L2Character activeChar, L2Character target, L2Skill skill, double damage, boolean crit)
	{
		boolean reflect = true;
		if ((skill.getCastRange() == -1) || (skill.getCastRange() > MELEE_ATTACK_RANGE))
		{
			reflect = false;
		}
		
		if (reflect)
		{
			final double vengeanceChance = target.getStat().calcStat(Stats.VENGEANCE_SKILL_PHYSICAL_DAMAGE, 0, target, skill);
			if (vengeanceChance > Rnd.get(100))
			{
				if (target.isPlayer())
				{
					SystemMessage sm = SystemMessage.getSystemMessage(SystemMessageId.COUNTERED_C1_ATTACK);
					sm.addCharName(activeChar);
					target.sendPacket(sm);
				}
				if (activeChar.isPlayer())
				{
					SystemMessage sm = SystemMessage.getSystemMessage(SystemMessageId.C1_PERFORMING_COUNTERATTACK);
					sm.addCharName(target);
					activeChar.sendPacket(sm);
				}
				
				// TODO: Is this retail like?
				double vegdamage = (damage / 100) * vengeanceChance;
				activeChar.reduceCurrentHp(vegdamage, target, skill);
				target.notifyDamageReceived(vegdamage, activeChar, skill, crit, false, true);
			}
		}
	}
	
	/**
	 * Calculate damage caused by falling
	 * @param cha
	 * @param fallHeight
	 * @return damage
	 */
	public static double calcFallDam(L2Character cha, int fallHeight)
	{
		if (!Config.ENABLE_FALLING_DAMAGE || (fallHeight < 0))
		{
			return 0;
		}
		final double damage = cha.calcStat(Stats.FALL, (fallHeight * cha.getMaxHp()) / 1000.0, null, null);
		return damage;
	}
	
	public static boolean calcBlowSuccess(L2Character activeChar, L2Character target, L2Skill skill)
	{
		double dexMod = BaseStats.DEX.calcBonus(activeChar);
		// Apply DEX Mod.
		double blowChance = skill.getBlowChance();
		// Apply Position Bonus.
		double sideMod = (activeChar.isInFrontOfTarget()) ? 1 : (activeChar.isBehindTarget()) ? 1.5 : 1.25;
		// Apply all mods.
		double baseRate = blowChance * dexMod * sideMod;
		// Apply blow rates
		double rate = activeChar.calcStat(Stats.BLOW_RATE, baseRate, target, null);
		
		final double finalRate = Math.min(rate, 80);
		
		final boolean isBackstab = skill.getDmgDirectlyToHP();
		final boolean isOnAngle = Util.isOnAngle(target, activeChar, 180, 120);
		final boolean result = isBackstab && !isOnAngle ? false : Rnd.get(1000) < (finalRate * 10);
		// Debug
		if (activeChar.isDebug())
		{
			final StatsSet set = new StatsSet();
			set.set("Formula", "Blow success");
			set.set("dexMod", dexMod);
			set.set("blowChance", blowChance);
			set.set("sideMod", sideMod);
			set.set("baseRate", baseRate);
			set.set("isBackstab", isBackstab);
			set.set("behind", activeChar.isBehindTarget());
			set.set("isOnAngle", isOnAngle);
			set.set("rate", String.format("%.2f", rate));
			set.set("finalRate: (max 80 of 100)", String.format("%.2f", finalRate));
			set.set("result", String.valueOf(result));
			Debug.sendSkillDebug(activeChar, target, skill, set);
		}
		
		return result;
	}
	
	public static List<L2Effect> getCanceledEffects(L2Character activeChar, L2Character target, L2Skill skill, String slot, int rate, int max, boolean randomEffects)
	{
		final List<L2Effect> canceled = new ArrayList<>(max);
		switch (slot)
		{
			case "buff":
			{
				final L2Effect[] effects = target.getAllEffects();
				List<L2Effect> _musicList = new LinkedList<>();
				List<L2Effect> _buffList = new LinkedList<>();
				
				for (L2Effect eff : effects)
				{
					// Just in case of NPE and remove effect if can't be stolen
					if ((eff == null) || !eff.canBeStolen())
					{
						continue;
					}
					
					// Only Dances/Songs.
					if (eff.getSkill().isDance())
					{
						_musicList.add(eff);
					}
					else
					{
						_buffList.add(eff);
					}
				}
				
				// Reversing lists and adding to a new list
				List<L2Effect> _effectList = new LinkedList<>();
				Collections.reverse(_musicList);
				Collections.reverse(_buffList);
				_effectList.addAll(_musicList);
				_effectList.addAll(_buffList);
				if (randomEffects)
				{
					Collections.shuffle(_effectList);
				}
				
				if (!_effectList.isEmpty())
				{
					for (L2Effect e : _effectList)
					{
						if (!e.canBeStolen())
						{
							continue;
						}
						
						// Resist modifier
						if (!calcStealSuccess(activeChar, target, skill, rate))
						{
							continue;
						}
						
						canceled.add(e);
						if (canceled.size() >= max)
						{
							break;
						}
					}
				}
				break;
			}
			case "debuff":
			{
				for (L2Effect info : target.getAllEffects())
				{
					if ((info == null) || !info.getSkill().isOffensive() || !info.getSkill().canBeDispeled())
					{
						continue;
					}
					
					if (Rnd.get(100) <= rate)
					{
						canceled.add(info);
						if (canceled.size() >= max)
						{
							break;
						}
					}
				}
				break;
			}
		}
		return canceled;
	}
	
	public static boolean calcCancelSuccess(L2Effect info, int cancelMagicLvl, int rate, L2Skill skill)
	{
		// Lvl Bonus Modifier.
		rate *= info.getSkill().getMagicLevel() > 0 ? 1 + ((cancelMagicLvl - info.getSkill().getMagicLevel()) / 100.) : 1;
		return Rnd.get(100) < Util.constrain(rate, skill.getMinChance(), skill.getMaxChance());
	}
	
	/**
	 * Calculate Probability in following effects:<br>
	 * TargetCancel,<br>
	 * TargetMeProbability,<br>
	 * SkillTurning,<br>
	 * Betray,<br>
	 * Bluff,<br>
	 * DeleteHate,<br>
	 * RandomizeHate,<br>
	 * DeleteHateOfMe,<br>
	 * TransferHate,<br>
	 * Confuse<br>
	 * @param baseChance chance from effect parameter
	 * @param attacker
	 * @param target
	 * @param skill
	 * @return chance for effect to succeed
	 */
	public static boolean calcProbability(double baseChance, L2Character attacker, L2Character target, L2Skill skill)
	{
		return Rnd.get(100) < ((((((skill.getMagicLevel() + baseChance) - target.getLevel()) + 30) - target.getINT()) * calcAttributeBonus(attacker, target, skill)) * calcValakasTrait(attacker, target, skill));
	}
	
	/**
	 * Calculates karma lost upon death.
	 * @param player
	 * @param exp
	 * @return the amount of karma player has loosed.
	 */
	public static int calculateKarmaLost(L2PcInstance player, long exp)
	{
		double karmaLooseMul = KarmaData.getInstance().getMultiplier(player.getLevel());
		if (exp > 0) // Received exp
		{
			exp /= Config.RATE_KARMA_LOST;
		}
		return (int) ((Math.abs(exp) / karmaLooseMul) / 30);
	}
	
	/**
	 * Calculates karma gain upon playable kill.</br>
	 * Updated to High Five on 10.09.2014 by Zealar tested in retail.
	 * @param pkCount
	 * @param isSummon
	 * @return karma points that will be added to the player.
	 */
	public static int calculateKarmaGain(int pkCount, boolean isSummon)
	{
		int result = 43200;
		
		if (isSummon)
		{
			result = (int) ((((pkCount * 0.375) + 1) * 60) * 4) - 150;
			
			if (result > 10800)
			{
				return 10800;
			}
		}
		
		if (pkCount < 99)
		{
			result = (int) ((((pkCount * 0.5) + 1) * 60) * 12);
		}
		else if (pkCount < 180)
		{
			result = (int) ((((pkCount * 0.125) + 37.75) * 60) * 12);
		}
		
		return result;
	}
	
	public static boolean calcStealSuccess(L2Character activeChar, L2Character target, L2Skill skill, double power)
	{
		int cancelMagicLvl = skill.getMagicLevel();
		final double vuln = target.calcStat(Stats.CANCEL_VULN, 0, target, null);
		final double prof = activeChar.calcStat(Stats.CANCEL_PROF, 0, target, null);
		double resMod = 1 + (((vuln + prof) * -1) / 100);
		double rate = power / resMod;
		double finalRate = rate;
		
		finalRate += FormulasConfigs.MODIFY_CANCEL_SUCCESS_RATE;
		if (finalRate < 0)
		{
			finalRate = 0;
		}
		
		boolean result = Rnd.chance(finalRate);
		if (activeChar.isDebug())
		{
			final StatsSet set = new StatsSet();
			set.set("Formula", "calcStealSuccess");
			set.set("baseMod", rate);
			set.set("magicLevel", cancelMagicLvl);
			set.set("resMod", resMod);
			set.set("rate", rate);
			set.set("finalRate", finalRate);
			set.set("result", String.valueOf(result));
			Debug.sendSkillDebug(activeChar, target, skill, set);
		}
		
		return result;
	}
	
	/**
	 * Calculates the abnormal time for an effect.<br>
	 * The abnormal time is taken from the skill definition, and it's global for all effects present in the skills.
	 * @param caster the caster
	 * @param target the target
	 * @param effect the effect
	 * @return the time that the effect will last
	 */
	public static int calcEffectAbnormalTime(L2Character caster, L2Character target, L2Effect effect)
	{
		if (effect.getSkill().isPassive() || effect.getSkill().isToggle())
		{
			return effect.getEffectTemplate().abnormalTime;
		}
		
		int time = effect.getEffectTemplate().abnormalTime;
		int baseTime = time;
		
		// An herb buff will affect both master and servitor, but the buff duration will be half of the normal duration.
		// If a servitor is not summoned, the master will receive the full buff duration.
		if ((target != null) && (target.isServitor() || (target.isPlayer() && target.hasSummon() && target.getSummon().isServitor())) && effect.getSkill().isAbnormalInstant())
		{
			time /= 2;
		}
		
		// If the skill is a mastery skill, the effect will last twice the default time.
		boolean skillMasteryAffected = Formulas.calcSkillMastery(caster, effect.getSkill());
		if (skillMasteryAffected)
		{
			time *= 2;
		}
		
		// Debuffs Duration Affected by Resistances.
		if ((caster != null) && (target != null) && effect.getSkill().isDebuff())
		{
			final double statMod = effect.getSkill().getBasicProperty().calcBonus(target);
			double vuln = calcSkillTraitVulnerability(0, target, effect.getSkill());
			double prof = calcSkillTraitProficiency(0, caster, target, effect.getSkill());
			double resMod = 1 + ((vuln + prof) / 100);
			final double lvlBonusMod = calcLvlBonusMod(caster, target, effect.getSkill());
			final double elementMod = calcAttributeBonus(caster, target, effect.getSkill());
			time = (int) Math.ceil(Util.constrain(((time * resMod * lvlBonusMod * elementMod) / statMod), (time * 0.5), time));
			
			if (caster.isDebug())
			{
				caster.sendMessage("---------------------");
				caster.sendMessage("statMod: " + statMod);
				caster.sendMessage("resMod: " + resMod);
				caster.sendMessage("lvlBonusMod: " + lvlBonusMod);
				caster.sendMessage("elementMod: " + elementMod);
				caster.sendMessage("baseTime: " + baseTime);
				caster.sendMessage("skillMastery: " + skillMasteryAffected);
				caster.sendMessage("finaltime: " + time);
				caster.sendMessage("---------------------");
			}
		}
		
		int temp = (target != null) && target.isPlayable() && target.getActingPlayer().isPremium() && (PremiumServiceConfigs.PR_SKILL_DURATION_LIST != null) && !PremiumServiceConfigs.PR_SKILL_DURATION_LIST.isEmpty() && PremiumServiceConfigs.PR_SKILL_DURATION_LIST.containsKey(effect.getSkill().getId()) ? PremiumServiceConfigs.PR_SKILL_DURATION_LIST.get(effect.getSkill().getId()) : 0;
		return time + temp;
	}
	
	public static double calcWeaponTraitBonus(L2Character attacker, L2Character target)
	{
		// Defense modifier depending of the attacker weapon
		L2Weapon weapon = attacker.getActiveWeaponItem();
		Stats stat = null;
		if ((weapon != null) && !attacker.isTransformed())
		{
			switch (weapon.getItemType())
			{
				case BOW:
					stat = Stats.BOW_WPN_VULN;
					break;
				case CROSSBOW:
					stat = Stats.CROSSBOW_WPN_VULN;
					break;
				case BLUNT:
					stat = Stats.BLUNT_WPN_VULN;
					break;
				case DAGGER:
					stat = Stats.DAGGER_WPN_VULN;
					break;
				case DUAL:
					stat = Stats.DUAL_WPN_VULN;
					break;
				case DUALFIST:
					stat = Stats.DUALFIST_WPN_VULN;
					break;
				case ETC:
					stat = Stats.ETC_WPN_VULN;
					break;
				case FIST:
					stat = Stats.FIST_WPN_VULN;
					break;
				case POLE:
					stat = Stats.POLE_WPN_VULN;
					break;
				case SWORD:
					stat = Stats.SWORD_WPN_VULN;
					break;
				case DUALDAGGER:
					stat = Stats.DUALDAGGER_WPN_VULN;
					break;
				case RAPIER:
					stat = Stats.RAPIER_WPN_VULN;
					break;
				case ANCIENTSWORD:
					stat = Stats.ANCIENT_WPN_VULN;
					break;
			}
		}
		
		// for summon use pet weapon vuln, since they can't hold weapon
		if (attacker.isServitor())
		{
			stat = Stats.PET_WPN_VULN;
		}
		
		if (stat != null)
		{
			return target.calcStat(stat, 1, target, null);
		}
		return 1.0;
	}
}
