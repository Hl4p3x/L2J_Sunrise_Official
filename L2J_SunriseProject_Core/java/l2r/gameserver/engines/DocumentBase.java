package l2r.gameserver.engines;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.StringTokenizer;

import javax.xml.parsers.DocumentBuilderFactory;

import l2r.Config;
import l2r.gameserver.data.xml.impl.ItemData;
import l2r.gameserver.enums.CategoryType;
import l2r.gameserver.enums.InstanceType;
import l2r.gameserver.enums.Race;
import l2r.gameserver.model.ChanceCondition;
import l2r.gameserver.model.StatsSet;
import l2r.gameserver.model.base.PlayerState;
import l2r.gameserver.model.conditions.Condition;
import l2r.gameserver.model.conditions.ConditionCategoryType;
import l2r.gameserver.model.conditions.ConditionChangeWeapon;
import l2r.gameserver.model.conditions.ConditionGameChance;
import l2r.gameserver.model.conditions.ConditionGameTime;
import l2r.gameserver.model.conditions.ConditionGameTime.CheckGameTime;
import l2r.gameserver.model.conditions.ConditionLogicAnd;
import l2r.gameserver.model.conditions.ConditionLogicNot;
import l2r.gameserver.model.conditions.ConditionLogicOr;
import l2r.gameserver.model.conditions.ConditionMinDistance;
import l2r.gameserver.model.conditions.ConditionMinimumVitalityPoints;
import l2r.gameserver.model.conditions.ConditionPlayerActiveEffectId;
import l2r.gameserver.model.conditions.ConditionPlayerActiveSkillId;
import l2r.gameserver.model.conditions.ConditionPlayerAgathionId;
import l2r.gameserver.model.conditions.ConditionPlayerCallPc;
import l2r.gameserver.model.conditions.ConditionPlayerCanCreateBase;
import l2r.gameserver.model.conditions.ConditionPlayerCanCreateOutpost;
import l2r.gameserver.model.conditions.ConditionPlayerCanEscape;
import l2r.gameserver.model.conditions.ConditionPlayerCanRefuelAirship;
import l2r.gameserver.model.conditions.ConditionPlayerCanResurrect;
import l2r.gameserver.model.conditions.ConditionPlayerCanSummon;
import l2r.gameserver.model.conditions.ConditionPlayerCanSummonSiegeGolem;
import l2r.gameserver.model.conditions.ConditionPlayerCanSweep;
import l2r.gameserver.model.conditions.ConditionPlayerCanTakeCastle;
import l2r.gameserver.model.conditions.ConditionPlayerCanTakeFort;
import l2r.gameserver.model.conditions.ConditionPlayerCanTakePcBangPoints;
import l2r.gameserver.model.conditions.ConditionPlayerCanTransform;
import l2r.gameserver.model.conditions.ConditionPlayerCanUntransform;
import l2r.gameserver.model.conditions.ConditionPlayerCharges;
import l2r.gameserver.model.conditions.ConditionPlayerClassIdRestriction;
import l2r.gameserver.model.conditions.ConditionPlayerCloakStatus;
import l2r.gameserver.model.conditions.ConditionPlayerCp;
import l2r.gameserver.model.conditions.ConditionPlayerFlyMounted;
import l2r.gameserver.model.conditions.ConditionPlayerGrade;
import l2r.gameserver.model.conditions.ConditionPlayerHasCastle;
import l2r.gameserver.model.conditions.ConditionPlayerHasClanHall;
import l2r.gameserver.model.conditions.ConditionPlayerHasFort;
import l2r.gameserver.model.conditions.ConditionPlayerHasPet;
import l2r.gameserver.model.conditions.ConditionPlayerHasServitor;
import l2r.gameserver.model.conditions.ConditionPlayerHp;
import l2r.gameserver.model.conditions.ConditionPlayerInInstance;
import l2r.gameserver.model.conditions.ConditionPlayerInsideZoneId;
import l2r.gameserver.model.conditions.ConditionPlayerInstanceId;
import l2r.gameserver.model.conditions.ConditionPlayerInvSize;
import l2r.gameserver.model.conditions.ConditionPlayerIsClanLeader;
import l2r.gameserver.model.conditions.ConditionPlayerIsHero;
import l2r.gameserver.model.conditions.ConditionPlayerLandingZone;
import l2r.gameserver.model.conditions.ConditionPlayerLevel;
import l2r.gameserver.model.conditions.ConditionPlayerLevelRange;
import l2r.gameserver.model.conditions.ConditionPlayerMp;
import l2r.gameserver.model.conditions.ConditionPlayerPkCount;
import l2r.gameserver.model.conditions.ConditionPlayerPledgeClass;
import l2r.gameserver.model.conditions.ConditionPlayerRace;
import l2r.gameserver.model.conditions.ConditionPlayerRangeFromNpc;
import l2r.gameserver.model.conditions.ConditionPlayerSex;
import l2r.gameserver.model.conditions.ConditionPlayerSiegeSide;
import l2r.gameserver.model.conditions.ConditionPlayerSouls;
import l2r.gameserver.model.conditions.ConditionPlayerState;
import l2r.gameserver.model.conditions.ConditionPlayerSubclass;
import l2r.gameserver.model.conditions.ConditionPlayerTransformationId;
import l2r.gameserver.model.conditions.ConditionPlayerVehicleMounted;
import l2r.gameserver.model.conditions.ConditionPlayerWeight;
import l2r.gameserver.model.conditions.ConditionSiegeZone;
import l2r.gameserver.model.conditions.ConditionSlotItemId;
import l2r.gameserver.model.conditions.ConditionTargetAbnormal;
import l2r.gameserver.model.conditions.ConditionTargetActiveEffectId;
import l2r.gameserver.model.conditions.ConditionTargetActiveSkillId;
import l2r.gameserver.model.conditions.ConditionTargetAggro;
import l2r.gameserver.model.conditions.ConditionTargetClassIdRestriction;
import l2r.gameserver.model.conditions.ConditionTargetInvSize;
import l2r.gameserver.model.conditions.ConditionTargetLevel;
import l2r.gameserver.model.conditions.ConditionTargetLevelRange;
import l2r.gameserver.model.conditions.ConditionTargetMyPartyExceptMe;
import l2r.gameserver.model.conditions.ConditionTargetNpcId;
import l2r.gameserver.model.conditions.ConditionTargetNpcRace;
import l2r.gameserver.model.conditions.ConditionTargetNpcType;
import l2r.gameserver.model.conditions.ConditionTargetPlayable;
import l2r.gameserver.model.conditions.ConditionTargetPlayer;
import l2r.gameserver.model.conditions.ConditionTargetRace;
import l2r.gameserver.model.conditions.ConditionTargetSiegeHammer;
import l2r.gameserver.model.conditions.ConditionTargetUsesWeaponKind;
import l2r.gameserver.model.conditions.ConditionTargetWeight;
import l2r.gameserver.model.conditions.ConditionUsingItemType;
import l2r.gameserver.model.conditions.ConditionUsingSkill;
import l2r.gameserver.model.conditions.ConditionUsingSlotType;
import l2r.gameserver.model.conditions.ConditionWithSkill;
import l2r.gameserver.model.effects.AbnormalEffect;
import l2r.gameserver.model.effects.EffectTemplate;
import l2r.gameserver.model.items.L2Item;
import l2r.gameserver.model.items.type.ArmorType;
import l2r.gameserver.model.items.type.WeaponType;
import l2r.gameserver.model.skills.L2Skill;
import l2r.gameserver.model.stats.Stats;
import l2r.gameserver.model.stats.functions.FuncTemplate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

/**
 * @author mkizub, vGodFather
 */
public abstract class DocumentBase
{
	protected final Logger _log = LoggerFactory.getLogger(getClass().getName());
	
	private final File _file;
	protected final Map<String, String[]> _tables = new HashMap<>();
	
	protected DocumentBase(File pFile)
	{
		_file = pFile;
	}
	
	public Document parse()
	{
		Document doc;
		try
		{
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			factory.setValidating(false);
			factory.setIgnoringComments(true);
			doc = factory.newDocumentBuilder().parse(_file);
		}
		catch (Exception e)
		{
			_log.error("Error loading file " + _file, e);
			return null;
		}
		try
		{
			parseDocument(doc);
		}
		catch (Exception e)
		{
			_log.error("Error in file " + _file, e);
			return null;
		}
		return doc;
	}
	
	protected abstract void parseDocument(Document doc);
	
	protected abstract StatsSet getStatsSet();
	
	protected abstract String getTableValue(String name);
	
	protected abstract String getTableValue(String name, int idx);
	
	protected void resetTable()
	{
		_tables.clear();
	}
	
	protected void setTable(String name, String[] table)
	{
		_tables.put(name, table);
	}
	
	protected void parseTemplate(Node n, Object template)
	{
		Condition condition = null;
		n = n.getFirstChild();
		if (n == null)
		{
			return;
		}
		if ("cond".equalsIgnoreCase(n.getNodeName()))
		{
			condition = parseCondition(n.getFirstChild(), template);
			Node msg = n.getAttributes().getNamedItem("msg");
			Node msgId = n.getAttributes().getNamedItem("msgId");
			if ((condition != null) && (msg != null))
			{
				condition.setMessage(msg.getNodeValue());
			}
			else if ((condition != null) && (msgId != null))
			{
				condition.setMessageId(Integer.decode(getValue(msgId.getNodeValue(), null)));
				Node addName = n.getAttributes().getNamedItem("addName");
				if ((addName != null) && (Integer.decode(getValue(msgId.getNodeValue(), null)) > 0))
				{
					condition.addName();
				}
			}
			n = n.getNextSibling();
		}
		for (; n != null; n = n.getNextSibling())
		{
			final String name = n.getNodeName().toLowerCase();
			
			switch (name)
			{
				case "effect":
				{
					if (template instanceof EffectTemplate)
					{
						throw new RuntimeException("Nested effects");
					}
					attachEffect(n, template, condition);
					break;
				}
				case "add":
				case "sub":
				case "mul":
				case "div":
				case "set":
				case "share":
				case "enchant":
				case "enchanthp":
				case "basemul":
				{
					attachFunc(n, template, name, condition);
				}
			}
		}
	}
	
	protected void attachFunc(Node n, Object template, String functionName, Condition attachCond)
	{
		final Stats stat = Stats.valueOfXml(n.getAttributes().getNamedItem("stat").getNodeValue());
		int order = -1;
		final Node orderNode = n.getAttributes().getNamedItem("order");
		if (orderNode != null)
		{
			order = Integer.parseInt(orderNode.getNodeValue());
		}
		
		final double value = getValue(n);
		final Condition applayCond = parseCondition(n.getFirstChild(), template);
		final FuncTemplate ft = new FuncTemplate(attachCond, applayCond, functionName, order, stat, value);
		if (template instanceof L2Item)
		{
			((L2Item) template).attach(ft);
		}
		else if (template instanceof L2Skill)
		{
			((L2Skill) template).attach(ft);
		}
		else if (template instanceof EffectTemplate)
		{
			((EffectTemplate) template).attach(ft);
		}
	}
	
	protected void attachEffect(Node n, Object template, Condition attachCond)
	{
		final NamedNodeMap attrs = n.getAttributes();
		final String name = getValue(attrs.getNamedItem("name").getNodeValue().intern(), template);
		
		/**
		 * Keep this values as default ones, DP needs it
		 */
		int abnormalTime = 1;
		int count = 1;
		
		if (attrs.getNamedItem("count") != null)
		{
			count = Integer.decode(getValue(attrs.getNamedItem("count").getNodeValue(), template));
		}
		
		if (attrs.getNamedItem("abnormalTime") != null)
		{
			abnormalTime = Integer.decode(getValue(attrs.getNamedItem("abnormalTime").getNodeValue(), template));
			if (Config.ENABLE_MODIFY_SKILL_DURATION)
			{
				if (Config.SKILL_DURATION_LIST.containsKey(((L2Skill) template).getId()))
				{
					if (((L2Skill) template).getLevel() < 100)
					{
						abnormalTime = Config.SKILL_DURATION_LIST.get(((L2Skill) template).getId());
					}
					else if ((((L2Skill) template).getLevel() >= 100) && (((L2Skill) template).getLevel() < 140))
					{
						abnormalTime += Config.SKILL_DURATION_LIST.get(((L2Skill) template).getId());
					}
					else if (((L2Skill) template).getLevel() > 140)
					{
						abnormalTime = Config.SKILL_DURATION_LIST.get(((L2Skill) template).getId());
					}
					if (Config.DEBUG_SKILL_DURATION)
					{
						_log.info("*** Skill " + ((L2Skill) template).getName() + " (" + ((L2Skill) template).getLevel() + ") changed duration to " + abnormalTime + " seconds.");
					}
				}
			}
		}
		
		boolean self = false;
		if (attrs.getNamedItem("self") != null)
		{
			if (Integer.decode(getValue(attrs.getNamedItem("self").getNodeValue(), template)) == 1)
			{
				self = true;
			}
		}
		
		boolean icon = true;
		if (attrs.getNamedItem("noicon") != null)
		{
			if (Integer.decode(getValue(attrs.getNamedItem("noicon").getNodeValue(), template)) == 1)
			{
				icon = false;
			}
		}
		
		final StatsSet parameters = parseParameters(n.getFirstChild(), template);
		double value = getValue(n);
		final Condition applayCond = parseCondition(n.getFirstChild(), template);
		AbnormalEffect abnormalVisualEffect = AbnormalEffect.NULL;
		if (attrs.getNamedItem("abnormalVisualEffect") != null)
		{
			String abn = attrs.getNamedItem("abnormalVisualEffect").getNodeValue();
			abnormalVisualEffect = AbnormalEffect.getByName(abn);
		}
		
		AbnormalEffect[] special = null;
		if (attrs.getNamedItem("special") != null)
		{
			final String[] specials = attrs.getNamedItem("special").getNodeValue().split(",");
			special = new AbnormalEffect[specials.length];
			for (int s = 0; s < specials.length; s++)
			{
				special[s] = AbnormalEffect.getByName(specials[s]);
			}
		}
		
		AbnormalEffect event = AbnormalEffect.NULL;
		if (attrs.getNamedItem("event") != null)
		{
			String spc = attrs.getNamedItem("event").getNodeValue();
			event = AbnormalEffect.getByName(spc);
		}
		
		byte abnormalLvl = 0;
		String abnormalType = "none";
		if (attrs.getNamedItem("abnormalType") != null)
		{
			abnormalType = attrs.getNamedItem("abnormalType").getNodeValue();
		}
		if (attrs.getNamedItem("abnormalLvl") != null)
		{
			abnormalLvl = Byte.parseByte(getValue(attrs.getNamedItem("abnormalLvl").getNodeValue(), template));
		}
		
		double effectPower = -1;
		if (attrs.getNamedItem("effectPower") != null)
		{
			effectPower = Double.parseDouble(getValue(attrs.getNamedItem("effectPower").getNodeValue(), template));
		}
		
		final boolean isChanceSkillTrigger = name.equals("ChanceSkillTrigger");
		int trigId = 0;
		if (attrs.getNamedItem("triggeredId") != null)
		{
			trigId = Integer.parseInt(getValue(attrs.getNamedItem("triggeredId").getNodeValue(), template));
		}
		else if (isChanceSkillTrigger)
		{
			throw new NoSuchElementException(name + " requires triggerId");
		}
		
		int trigLvl = 1;
		if (attrs.getNamedItem("triggeredLevel") != null)
		{
			trigLvl = Integer.parseInt(getValue(attrs.getNamedItem("triggeredLevel").getNodeValue(), template));
		}
		
		String chanceCond = null;
		if (attrs.getNamedItem("chanceType") != null)
		{
			chanceCond = getValue(attrs.getNamedItem("chanceType").getNodeValue(), template);
		}
		else if (isChanceSkillTrigger)
		{
			throw new NoSuchElementException(name + " requires chanceType");
		}
		
		int activationChance = -1;
		if (attrs.getNamedItem("activationChance") != null)
		{
			activationChance = Integer.parseInt(getValue(attrs.getNamedItem("activationChance").getNodeValue(), template));
		}
		
		ChanceCondition chance = ChanceCondition.parse(chanceCond, activationChance);
		
		if ((chance == null) && isChanceSkillTrigger)
		{
			throw new NoSuchElementException("Invalid chance condition: " + chanceCond + " " + activationChance);
		}
		
		final EffectTemplate lt = new EffectTemplate(attachCond, applayCond, name, value, count, abnormalTime, abnormalVisualEffect, special, event, abnormalType, abnormalLvl, icon, effectPower, trigId, trigLvl, chance, parameters);
		parseTemplate(n, lt);
		if (template instanceof L2Item)
		{
			((L2Item) template).attach(lt);
		}
		else if (template instanceof L2Skill)
		{
			if (self)
			{
				((L2Skill) template).attachSelf(lt);
			}
			else if (((L2Skill) template).isPassive())
			{
				((L2Skill) template).attachPassive(lt);
			}
			else
			{
				((L2Skill) template).attach(lt);
			}
		}
	}
	
	private StatsSet parseParameters(Node n, Object template)
	{
		StatsSet parameters = null;
		while ((n != null))
		{
			// Parse all parameters.
			if ((n.getNodeType() == Node.ELEMENT_NODE) && "param".equals(n.getNodeName()))
			{
				if (parameters == null)
				{
					parameters = new StatsSet();
				}
				
				final NamedNodeMap params = n.getAttributes();
				for (int i = 0; i < params.getLength(); i++)
				{
					Node att = params.item(i);
					parameters.set(att.getNodeName(), getValue(att.getNodeValue(), template));
				}
			}
			n = n.getNextSibling();
		}
		return parameters == null ? StatsSet.EMPTY_STATSET : parameters;
	}
	
	protected Condition parseCondition(Node n, Object template)
	{
		while ((n != null) && (n.getNodeType() != Node.ELEMENT_NODE))
		{
			n = n.getNextSibling();
		}
		
		Condition condition = null;
		if (n != null)
		{
			switch (n.getNodeName().toLowerCase())
			{
				case "and":
				{
					condition = parseLogicAnd(n, template);
					break;
				}
				case "or":
				{
					condition = parseLogicOr(n, template);
					break;
				}
				case "not":
				{
					condition = parseLogicNot(n, template);
					break;
				}
				case "player":
				{
					condition = parsePlayerCondition(n, template);
					break;
				}
				case "target":
				{
					condition = parseTargetCondition(n, template);
					break;
				}
				case "using":
				{
					condition = parseUsingCondition(n);
					break;
				}
				case "game":
				{
					condition = parseGameCondition(n);
					break;
				}
			}
		}
		return condition;
	}
	
	protected Condition parseLogicAnd(Node n, Object template)
	{
		final ConditionLogicAnd cond = new ConditionLogicAnd();
		for (n = n.getFirstChild(); n != null; n = n.getNextSibling())
		{
			if (n.getNodeType() == Node.ELEMENT_NODE)
			{
				cond.add(parseCondition(n, template));
			}
		}
		if ((cond.conditions == null) || (cond.conditions.length == 0))
		{
			_log.error("Empty <and> condition in " + _file);
		}
		return cond;
	}
	
	protected Condition parseLogicOr(Node n, Object template)
	{
		final ConditionLogicOr cond = new ConditionLogicOr();
		for (n = n.getFirstChild(); n != null; n = n.getNextSibling())
		{
			if (n.getNodeType() == Node.ELEMENT_NODE)
			{
				cond.add(parseCondition(n, template));
			}
		}
		
		if ((cond.conditions == null) || (cond.conditions.length == 0))
		{
			_log.error("Empty <or> condition in " + _file);
		}
		return cond;
	}
	
	protected Condition parseLogicNot(Node n, Object template)
	{
		for (n = n.getFirstChild(); n != null; n = n.getNextSibling())
		{
			if (n.getNodeType() == Node.ELEMENT_NODE)
			{
				return new ConditionLogicNot(parseCondition(n, template));
			}
		}
		_log.error("Empty <not> condition in " + _file);
		return null;
	}
	
	protected Condition parsePlayerCondition(Node n, Object template)
	{
		Condition cond = null;
		final NamedNodeMap attrs = n.getAttributes();
		for (int i = 0; i < attrs.getLength(); i++)
		{
			final Node a = attrs.item(i);
			switch (a.getNodeName().toLowerCase())
			{
				case "races":
				{
					final String[] racesVal = a.getNodeValue().split(",");
					final Race[] races = new Race[racesVal.length];
					for (int r = 0; r < racesVal.length; r++)
					{
						if (racesVal[r] != null)
						{
							races[r] = Race.valueOf(racesVal[r]);
						}
					}
					cond = joinAnd(cond, new ConditionPlayerRace(races));
					break;
				}
				case "level":
				{
					int lvl = Integer.decode(getValue(a.getNodeValue(), template));
					cond = joinAnd(cond, new ConditionPlayerLevel(lvl));
					break;
				}
				case "levelrange":
				{
					String[] range = getValue(a.getNodeValue(), template).split(";");
					if (range.length == 2)
					{
						int[] lvlRange = new int[2];
						lvlRange[0] = Integer.decode(getValue(a.getNodeValue(), template).split(";")[0]);
						lvlRange[1] = Integer.decode(getValue(a.getNodeValue(), template).split(";")[1]);
						cond = joinAnd(cond, new ConditionPlayerLevelRange(lvlRange));
					}
					break;
				}
				case "resting":
				{
					boolean val = Boolean.parseBoolean(a.getNodeValue());
					cond = joinAnd(cond, new ConditionPlayerState(PlayerState.RESTING, val));
					break;
				}
				case "flying":
				{
					boolean val = Boolean.parseBoolean(a.getNodeValue());
					cond = joinAnd(cond, new ConditionPlayerState(PlayerState.FLYING, val));
					break;
				}
				case "moving":
				{
					boolean val = Boolean.parseBoolean(a.getNodeValue());
					cond = joinAnd(cond, new ConditionPlayerState(PlayerState.MOVING, val));
					break;
				}
				case "running":
				{
					boolean val = Boolean.parseBoolean(a.getNodeValue());
					cond = joinAnd(cond, new ConditionPlayerState(PlayerState.RUNNING, val));
					break;
				}
				case "standing":
				{
					boolean val = Boolean.parseBoolean(a.getNodeValue());
					cond = joinAnd(cond, new ConditionPlayerState(PlayerState.STANDING, val));
					break;
				}
				case "behind":
				{
					boolean val = Boolean.parseBoolean(a.getNodeValue());
					cond = joinAnd(cond, new ConditionPlayerState(PlayerState.BEHIND, val));
					break;
				}
				case "front":
				{
					boolean val = Boolean.parseBoolean(a.getNodeValue());
					cond = joinAnd(cond, new ConditionPlayerState(PlayerState.FRONT, val));
					break;
				}
				case "chaotic":
				{
					boolean val = Boolean.parseBoolean(a.getNodeValue());
					cond = joinAnd(cond, new ConditionPlayerState(PlayerState.CHAOTIC, val));
					break;
				}
				case "olympiad":
				{
					boolean val = Boolean.parseBoolean(a.getNodeValue());
					cond = joinAnd(cond, new ConditionPlayerState(PlayerState.OLYMPIAD, val));
					break;
				}
				case "ishero":
				{
					boolean val = Boolean.parseBoolean(a.getNodeValue());
					cond = joinAnd(cond, new ConditionPlayerIsHero(val));
					break;
				}
				case "transformationid":
				{
					int id = Integer.parseInt(a.getNodeValue());
					cond = joinAnd(cond, new ConditionPlayerTransformationId(id));
					break;
				}
				case "hp":
				{
					int hp = Integer.decode(getValue(a.getNodeValue(), null));
					cond = joinAnd(cond, new ConditionPlayerHp(hp));
					break;
				}
				case "mp":
				{
					int hp = Integer.decode(getValue(a.getNodeValue(), null));
					cond = joinAnd(cond, new ConditionPlayerMp(hp));
					break;
				}
				case "cp":
				{
					int cp = Integer.decode(getValue(a.getNodeValue(), null));
					cond = joinAnd(cond, new ConditionPlayerCp(cp));
					break;
				}
				case "grade":
				{
					int expIndex = Integer.decode(getValue(a.getNodeValue(), template));
					cond = joinAnd(cond, new ConditionPlayerGrade(expIndex));
					break;
				}
				case "pkcount":
				{
					int expIndex = Integer.decode(getValue(a.getNodeValue(), template));
					cond = joinAnd(cond, new ConditionPlayerPkCount(expIndex));
					break;
				}
				case "siegezone":
				{
					int value = Integer.decode(getValue(a.getNodeValue(), null));
					cond = joinAnd(cond, new ConditionSiegeZone(value, true));
					break;
				}
				case "siegeside":
				{
					int value = Integer.decode(getValue(a.getNodeValue(), null));
					cond = joinAnd(cond, new ConditionPlayerSiegeSide(value));
					break;
				}
				case "charges":
				{
					int value = Integer.decode(getValue(a.getNodeValue(), template));
					cond = joinAnd(cond, new ConditionPlayerCharges(value));
					break;
				}
				case "souls":
				{
					int value = Integer.decode(getValue(a.getNodeValue(), template));
					cond = joinAnd(cond, new ConditionPlayerSouls(value));
					break;
				}
				case "weight":
				{
					int weight = Integer.decode(getValue(a.getNodeValue(), null));
					cond = joinAnd(cond, new ConditionPlayerWeight(weight));
					break;
				}
				case "invsize":
				{
					int size = Integer.decode(getValue(a.getNodeValue(), null));
					cond = joinAnd(cond, new ConditionPlayerInvSize(size));
					break;
				}
				case "isclanleader":
				{
					boolean val = Boolean.parseBoolean(a.getNodeValue());
					cond = joinAnd(cond, new ConditionPlayerIsClanLeader(val));
					break;
				}
				case "pledgeclass":
				{
					int pledgeClass = Integer.decode(getValue(a.getNodeValue(), null));
					cond = joinAnd(cond, new ConditionPlayerPledgeClass(pledgeClass));
					break;
				}
				case "clanhall":
				{
					StringTokenizer st = new StringTokenizer(a.getNodeValue(), ",");
					ArrayList<Integer> array = new ArrayList<>(st.countTokens());
					while (st.hasMoreTokens())
					{
						String item = st.nextToken().trim();
						array.add(Integer.decode(getValue(item, null)));
					}
					cond = joinAnd(cond, new ConditionPlayerHasClanHall(array));
					break;
				}
				case "fort":
				{
					int fort = Integer.decode(getValue(a.getNodeValue(), null));
					cond = joinAnd(cond, new ConditionPlayerHasFort(fort));
					break;
				}
				case "castle":
				{
					int castle = Integer.decode(getValue(a.getNodeValue(), null));
					cond = joinAnd(cond, new ConditionPlayerHasCastle(castle));
					break;
				}
				case "sex":
				{
					int sex = Integer.decode(getValue(a.getNodeValue(), null));
					cond = joinAnd(cond, new ConditionPlayerSex(sex));
					break;
				}
				case "flymounted":
				{
					boolean val = Boolean.parseBoolean(a.getNodeValue());
					cond = joinAnd(cond, new ConditionPlayerFlyMounted(val));
					break;
				}
				case "vehiclemounted":
				{
					boolean val = Boolean.parseBoolean(a.getNodeValue());
					cond = joinAnd(cond, new ConditionPlayerVehicleMounted(val));
					break;
				}
				case "landingzone":
				{
					boolean val = Boolean.parseBoolean(a.getNodeValue());
					cond = joinAnd(cond, new ConditionPlayerLandingZone(val));
					break;
				}
				case "active_effect_id":
				{
					int effect_id = Integer.decode(getValue(a.getNodeValue(), template));
					cond = joinAnd(cond, new ConditionPlayerActiveEffectId(effect_id));
					break;
				}
				case "active_effect_id_lvl":
				{
					String val = getValue(a.getNodeValue(), template);
					int effect_id = Integer.decode(getValue(val.split(",")[0], template));
					int effect_lvl = Integer.decode(getValue(val.split(",")[1], template));
					cond = joinAnd(cond, new ConditionPlayerActiveEffectId(effect_id, effect_lvl));
					break;
				}
				case "active_skill_id":
				{
					int skill_id = Integer.decode(getValue(a.getNodeValue(), template));
					cond = joinAnd(cond, new ConditionPlayerActiveSkillId(skill_id));
					break;
				}
				case "active_skill_id_lvl":
				{
					String val = getValue(a.getNodeValue(), template);
					int skill_id = Integer.decode(getValue(val.split(",")[0], template));
					int skill_lvl = Integer.decode(getValue(val.split(",")[1], template));
					cond = joinAnd(cond, new ConditionPlayerActiveSkillId(skill_id, skill_lvl));
					break;
				}
				case "class_id_restriction":
				{
					StringTokenizer st = new StringTokenizer(a.getNodeValue(), ",");
					ArrayList<Integer> array = new ArrayList<>(st.countTokens());
					while (st.hasMoreTokens())
					{
						String item = st.nextToken().trim();
						array.add(Integer.decode(getValue(item, null)));
					}
					cond = joinAnd(cond, new ConditionPlayerClassIdRestriction(array));
					break;
				}
				case "subclass":
				{
					boolean val = Boolean.parseBoolean(a.getNodeValue());
					cond = joinAnd(cond, new ConditionPlayerSubclass(val));
					break;
				}
				case "instanceid":
				{
					StringTokenizer st = new StringTokenizer(a.getNodeValue(), ",");
					ArrayList<Integer> array = new ArrayList<>(st.countTokens());
					while (st.hasMoreTokens())
					{
						String item = st.nextToken().trim();
						array.add(Integer.decode(getValue(item, null)));
					}
					cond = joinAnd(cond, new ConditionPlayerInstanceId(array));
					break;
				}
				case "agathionid":
				{
					int agathionId = Integer.decode(a.getNodeValue());
					cond = joinAnd(cond, new ConditionPlayerAgathionId(agathionId));
					break;
				}
				case "cloakstatus":
				{
					boolean val = Boolean.parseBoolean(a.getNodeValue());
					cond = joinAnd(cond, new ConditionPlayerCloakStatus(val));
					break;
				}
				case "haspet":
				{
					StringTokenizer st = new StringTokenizer(a.getNodeValue(), ",");
					ArrayList<Integer> array = new ArrayList<>(st.countTokens());
					while (st.hasMoreTokens())
					{
						String item = st.nextToken().trim();
						array.add(Integer.decode(getValue(item, null)));
					}
					cond = joinAnd(cond, new ConditionPlayerHasPet(array));
					break;
				}
				case "hasservitor":
				{
					cond = joinAnd(cond, new ConditionPlayerHasServitor());
					break;
				}
				case "npcidradius":
				{
					final StringTokenizer st = new StringTokenizer(a.getNodeValue(), ",");
					if (st.countTokens() == 3)
					{
						final String[] ids = st.nextToken().split(";");
						final int[] npcIds = new int[ids.length];
						for (int index = 0; index < ids.length; index++)
						{
							npcIds[index] = Integer.parseInt(getValue(ids[index], template));
						}
						final int radius = Integer.parseInt(st.nextToken());
						final boolean val = Boolean.parseBoolean(st.nextToken());
						cond = joinAnd(cond, new ConditionPlayerRangeFromNpc(npcIds, radius, val));
					}
					break;
				}
				case "callpc":
				{
					cond = joinAnd(cond, new ConditionPlayerCallPc(Boolean.parseBoolean(a.getNodeValue())));
					break;
				}
				case "cancreatebase":
				{
					cond = joinAnd(cond, new ConditionPlayerCanCreateBase(Boolean.parseBoolean(a.getNodeValue())));
					break;
				}
				case "cancreateoutpost":
				{
					cond = joinAnd(cond, new ConditionPlayerCanCreateOutpost(Boolean.parseBoolean(a.getNodeValue())));
					break;
				}
				case "canescape":
				{
					cond = joinAnd(cond, new ConditionPlayerCanEscape(Boolean.parseBoolean(a.getNodeValue())));
					break;
				}
				
				case "canrefuelairship":
				{
					cond = joinAnd(cond, new ConditionPlayerCanRefuelAirship(Integer.parseInt(a.getNodeValue())));
					break;
				}
				case "canresurrect":
				{
					cond = joinAnd(cond, new ConditionPlayerCanResurrect(Boolean.parseBoolean(a.getNodeValue())));
					break;
				}
				case "cansummon":
				{
					cond = joinAnd(cond, new ConditionPlayerCanSummon(Boolean.parseBoolean(a.getNodeValue())));
					break;
				}
				case "cansummonsiegegolem":
				{
					cond = joinAnd(cond, new ConditionPlayerCanSummonSiegeGolem(Boolean.parseBoolean(a.getNodeValue())));
					break;
				}
				case "cansweep":
				{
					cond = joinAnd(cond, new ConditionPlayerCanSweep(Boolean.parseBoolean(a.getNodeValue())));
					break;
				}
				case "cantakecastle":
				{
					cond = joinAnd(cond, new ConditionPlayerCanTakeCastle(Boolean.parseBoolean(a.getNodeValue())));
					break;
				}
				case "cantakefort":
				{
					cond = joinAnd(cond, new ConditionPlayerCanTakeFort(Boolean.parseBoolean(a.getNodeValue())));
					break;
				}
				case "cantransform":
				{
					cond = joinAnd(cond, new ConditionPlayerCanTransform(Boolean.parseBoolean(a.getNodeValue())));
					break;
				}
				case "canuntransform":
				{
					cond = joinAnd(cond, new ConditionPlayerCanUntransform(Boolean.parseBoolean(a.getNodeValue())));
					break;
				}
				case "insidezoneid":
				{
					StringTokenizer st = new StringTokenizer(a.getNodeValue(), ",");
					ArrayList<Integer> array = new ArrayList<>(st.countTokens());
					while (st.hasMoreTokens())
					{
						String item = st.nextToken().trim();
						array.add(Integer.decode(getValue(item, null)));
					}
					cond = joinAnd(cond, new ConditionPlayerInsideZoneId(array));
					break;
				}
				case "categorytype":
				{
					final String[] values = a.getNodeValue().split(",");
					final Set<CategoryType> array = new HashSet<>(values.length);
					for (String value : values)
					{
						array.add(CategoryType.valueOf(getValue(value, null)));
					}
					cond = joinAnd(cond, new ConditionCategoryType(array));
					break;
				}
				case "checkpcbangpoint":
				{
					cond = joinAnd(cond, new ConditionPlayerCanTakePcBangPoints(Boolean.parseBoolean(a.getNodeValue())));
					break;
				}
				case "player":
				{
					cond = joinAnd(cond, new ConditionTargetPlayer());
					break;
				}
				case "isonside":
				{
					// cond = joinAnd(cond, new ConditionPlayerIsOnSide(Enum.valueOf(CastleSide.class, a.getNodeValue())));
					break;
				}
				case "ininstance":
				{
					cond = joinAnd(cond, new ConditionPlayerInInstance(Boolean.parseBoolean(a.getNodeValue())));
					break;
				}
				case "minimumvitalitypoints":
				{
					final int count = Integer.decode(getValue(a.getNodeValue(), null));
					cond = joinAnd(cond, new ConditionMinimumVitalityPoints(count));
					break;
				}
				default:
					_log.error("Unrecognized <" + a.getNodeName().toLowerCase() + "> condition in " + _file);
					break;
			}
		}
		
		return cond;
	}
	
	protected Condition parseTargetCondition(Node n, Object template)
	{
		Condition cond = null;
		final NamedNodeMap attrs = n.getAttributes();
		for (int i = 0; i < attrs.getLength(); i++)
		{
			final Node a = attrs.item(i);
			switch (a.getNodeName().toLowerCase())
			{
				case "aggro":
				{
					boolean val = Boolean.parseBoolean(a.getNodeValue());
					cond = joinAnd(cond, new ConditionTargetAggro(val));
					break;
				}
				case "siegezone":
				{
					int value = Integer.decode(getValue(a.getNodeValue(), null));
					cond = joinAnd(cond, new ConditionSiegeZone(value, false));
					break;
				}
				case "level":
				{
					int lvl = Integer.decode(getValue(a.getNodeValue(), template));
					cond = joinAnd(cond, new ConditionTargetLevel(lvl));
					break;
				}
				case "levelrange":
				{
					String[] range = getValue(a.getNodeValue(), template).split(";");
					if (range.length == 2)
					{
						int[] lvlRange = new int[2];
						lvlRange[0] = Integer.decode(getValue(a.getNodeValue(), template).split(";")[0]);
						lvlRange[1] = Integer.decode(getValue(a.getNodeValue(), template).split(";")[1]);
						cond = joinAnd(cond, new ConditionTargetLevelRange(lvlRange));
					}
					break;
				}
				case "mypartyexceptme":
				{
					cond = joinAnd(cond, new ConditionTargetMyPartyExceptMe(Boolean.parseBoolean(a.getNodeValue())));
					break;
				}
				case "playable":
				{
					cond = joinAnd(cond, new ConditionTargetPlayable());
					break;
				}
				case "class_id_restriction":
				{
					StringTokenizer st = new StringTokenizer(a.getNodeValue(), ",");
					ArrayList<Integer> array = new ArrayList<>(st.countTokens());
					while (st.hasMoreTokens())
					{
						String item = st.nextToken().trim();
						array.add(Integer.decode(getValue(item, null)));
					}
					cond = joinAnd(cond, new ConditionTargetClassIdRestriction(array));
					break;
				}
				case "active_effect_id":
				{
					int effect_id = Integer.decode(getValue(a.getNodeValue(), template));
					cond = joinAnd(cond, new ConditionTargetActiveEffectId(effect_id));
					break;
				}
				case "active_effect_id_lvl":
				{
					String val = getValue(a.getNodeValue(), template);
					int effect_id = Integer.decode(getValue(val.split(",")[0], template));
					int effect_lvl = Integer.decode(getValue(val.split(",")[1], template));
					cond = joinAnd(cond, new ConditionTargetActiveEffectId(effect_id, effect_lvl));
					break;
				}
				case "active_skill_id":
				{
					int skill_id = Integer.decode(getValue(a.getNodeValue(), template));
					cond = joinAnd(cond, new ConditionTargetActiveSkillId(skill_id));
					break;
				}
				case "active_skill_id_lvl":
				{
					String val = getValue(a.getNodeValue(), template);
					int skill_id = Integer.decode(getValue(val.split(",")[0], template));
					int skill_lvl = Integer.decode(getValue(val.split(",")[1], template));
					cond = joinAnd(cond, new ConditionTargetActiveSkillId(skill_id, skill_lvl));
					break;
				}
				case "abnormal":
				{
					int abnormalId = Integer.decode(getValue(a.getNodeValue(), template));
					cond = joinAnd(cond, new ConditionTargetAbnormal(abnormalId));
					break;
				}
				case "mindistance":
				{
					int distance = Integer.decode(getValue(a.getNodeValue(), null));
					cond = joinAnd(cond, new ConditionMinDistance(distance * distance));
					break;
				}
				case "npcrace":
				{
					// used for npc race
					final String[] values = a.getNodeValue().split(",");
					final Set<Race> array = new HashSet<>(values.length);
					for (String value : values)
					{
						array.add(Race.valueOf(getValue(value, null)));
					}
					cond = joinAnd(cond, new ConditionTargetNpcRace(array));
					break;
				}
				case "races":
				{
					// used for pc race
					final String[] racesVal = a.getNodeValue().split(",");
					final Race[] races = new Race[racesVal.length];
					for (int r = 0; r < racesVal.length; r++)
					{
						if (racesVal[r] != null)
						{
							races[r] = Race.valueOf(racesVal[r]);
						}
					}
					cond = joinAnd(cond, new ConditionTargetRace(races));
					break;
				}
				case "using":
				{
					int mask = 0;
					StringTokenizer st = new StringTokenizer(a.getNodeValue(), ",");
					while (st.hasMoreTokens())
					{
						String item = st.nextToken().trim();
						for (WeaponType wt : WeaponType.values())
						{
							if (wt.name().equals(item))
							{
								mask |= wt.mask();
								break;
							}
						}
						for (ArmorType at : ArmorType.values())
						{
							if (at.name().equals(item))
							{
								mask |= at.mask();
								break;
							}
						}
					}
					cond = joinAnd(cond, new ConditionTargetUsesWeaponKind(mask));
					break;
				}
				case "npcid":
				{
					StringTokenizer st = new StringTokenizer(a.getNodeValue(), ",");
					ArrayList<Integer> array = new ArrayList<>(st.countTokens());
					while (st.hasMoreTokens())
					{
						String item = st.nextToken().trim();
						array.add(Integer.decode(getValue(item, null)));
					}
					cond = joinAnd(cond, new ConditionTargetNpcId(array));
					break;
				}
				case "npctype":
				{
					String values = getValue(a.getNodeValue(), template).trim();
					String[] valuesSplit = values.split(",");
					InstanceType[] types = new InstanceType[valuesSplit.length];
					InstanceType type;
					for (int j = 0; j < valuesSplit.length; j++)
					{
						type = Enum.valueOf(InstanceType.class, valuesSplit[j]);
						if (type == null)
						{
							throw new IllegalArgumentException("Instance type not recognized: " + valuesSplit[j]);
						}
						types[j] = type;
					}
					cond = joinAnd(cond, new ConditionTargetNpcType(types));
					break;
				}
				case "weight":
				{
					int weight = Integer.decode(getValue(a.getNodeValue(), null));
					cond = joinAnd(cond, new ConditionTargetWeight(weight));
					break;
				}
				case "invsize":
				{
					int size = Integer.decode(getValue(a.getNodeValue(), null));
					cond = joinAnd(cond, new ConditionTargetInvSize(size));
					break;
				}
				case "op_siege_hammer":
				{
					cond = joinAnd(cond, new ConditionTargetSiegeHammer());
					break;
				}
			}
		}
		
		if (cond == null)
		{
			_log.error("Unrecognized <target> condition in " + _file);
		}
		return cond;
	}
	
	protected Condition parseUsingCondition(Node n)
	{
		Condition cond = null;
		final NamedNodeMap attrs = n.getAttributes();
		for (int i = 0; i < attrs.getLength(); i++)
		{
			final Node a = attrs.item(i);
			switch (a.getNodeName().toLowerCase())
			{
				case "kind":
				{
					int mask = 0;
					StringTokenizer st = new StringTokenizer(a.getNodeValue(), ",");
					while (st.hasMoreTokens())
					{
						int old = mask;
						String item = st.nextToken().trim();
						for (WeaponType wt : WeaponType.values())
						{
							if (wt.name().equals(item))
							{
								mask |= wt.mask();
							}
						}
						
						for (ArmorType at : ArmorType.values())
						{
							if (at.name().equals(item))
							{
								mask |= at.mask();
							}
						}
						
						if (old == mask)
						{
							_log.info("[parseUsingCondition=\"kind\"] Unknown item type name: " + item);
						}
					}
					cond = joinAnd(cond, new ConditionUsingItemType(mask));
					break;
				}
				case "slot":
				{
					int mask = 0;
					StringTokenizer st = new StringTokenizer(a.getNodeValue(), ",");
					while (st.hasMoreTokens())
					{
						int old = mask;
						String item = st.nextToken().trim();
						if (ItemData.SLOTS.containsKey(item))
						{
							mask |= ItemData.SLOTS.get(item);
						}
						
						if (old == mask)
						{
							_log.info("[parseUsingCondition=\"slot\"] Unknown item slot name: " + item);
						}
					}
					cond = joinAnd(cond, new ConditionUsingSlotType(mask));
					break;
				}
				case "skill":
				{
					int id = Integer.parseInt(a.getNodeValue());
					cond = joinAnd(cond, new ConditionUsingSkill(id));
					break;
				}
				case "slotitem":
				{
					StringTokenizer st = new StringTokenizer(a.getNodeValue(), ";");
					int id = Integer.parseInt(st.nextToken().trim());
					int slot = Integer.parseInt(st.nextToken().trim());
					int enchant = 0;
					if (st.hasMoreTokens())
					{
						enchant = Integer.parseInt(st.nextToken().trim());
					}
					cond = joinAnd(cond, new ConditionSlotItemId(slot, id, enchant));
					break;
				}
				case "weaponchange":
				{
					boolean val = Boolean.parseBoolean(a.getNodeValue());
					cond = joinAnd(cond, new ConditionChangeWeapon(val));
					break;
				}
			}
		}
		
		if (cond == null)
		{
			_log.error("Unrecognized <using> condition in " + _file);
		}
		return cond;
	}
	
	protected Condition parseGameCondition(Node n)
	{
		Condition cond = null;
		final NamedNodeMap attrs = n.getAttributes();
		for (int i = 0; i < attrs.getLength(); i++)
		{
			final Node a = attrs.item(i);
			if ("skill".equalsIgnoreCase(a.getNodeName()))
			{
				boolean val = Boolean.parseBoolean(a.getNodeValue());
				cond = joinAnd(cond, new ConditionWithSkill(val));
			}
			if ("night".equalsIgnoreCase(a.getNodeName()))
			{
				boolean val = Boolean.parseBoolean(a.getNodeValue());
				cond = joinAnd(cond, new ConditionGameTime(CheckGameTime.NIGHT, val));
			}
			if ("chance".equalsIgnoreCase(a.getNodeName()))
			{
				int val = Integer.decode(getValue(a.getNodeValue(), null));
				cond = joinAnd(cond, new ConditionGameChance(val));
			}
		}
		if (cond == null)
		{
			_log.error("Unrecognized <game> condition in " + _file);
		}
		return cond;
	}
	
	protected void parseTable(Node n)
	{
		final NamedNodeMap attrs = n.getAttributes();
		final String name = attrs.getNamedItem("name").getNodeValue();
		if (name.charAt(0) != '#')
		{
			throw new IllegalArgumentException("Table name must start with #");
		}
		
		final StringTokenizer data = new StringTokenizer(n.getFirstChild().getNodeValue());
		final List<String> array = new ArrayList<>(data.countTokens());
		while (data.hasMoreTokens())
		{
			array.add(data.nextToken());
		}
		setTable(name, array.toArray(new String[array.size()]));
	}
	
	protected void parseBeanSet(Node n, StatsSet set, Integer level)
	{
		final String name = n.getAttributes().getNamedItem("name").getNodeValue().trim();
		final String value = n.getAttributes().getNamedItem("val").getNodeValue().trim();
		final char ch = value.isEmpty() ? ' ' : value.charAt(0);
		if ((ch == '#') || (ch == '-') || Character.isDigit(ch))
		{
			set.set(name, String.valueOf(getValue(value, level)));
		}
		else
		{
			set.set(name, value);
		}
	}
	
	protected void setExtractableSkillData(StatsSet set, String value)
	{
		set.set("capsuled_items_skill", value);
	}
	
	protected double getValue(Node n)
	{
		final Node nval = n.getAttributes().getNamedItem("val");
		if (nval != null)
		{
			String val = nval.getNodeValue();
			if (val.charAt(0) == '#')
			{
				// table by level
				return Double.parseDouble(getTableValue(val));
			}
			return Double.parseDouble(val);
		}
		return 0;
	}
	
	protected String getValue(String value, Object template)
	{
		if (value.charAt(0) == '#')
		{
			if (template instanceof L2Skill)
			{
				return getTableValue(value);
			}
			else if (template instanceof Integer)
			{
				return getTableValue(value, ((Integer) template).intValue());
			}
			else
			{
				throw new IllegalStateException();
			}
		}
		return value;
	}
	
	protected Condition joinAnd(Condition cond, Condition c)
	{
		if (cond == null)
		{
			return c;
		}
		
		if (cond instanceof ConditionLogicAnd)
		{
			((ConditionLogicAnd) cond).add(c);
			return cond;
		}
		
		final ConditionLogicAnd and = new ConditionLogicAnd();
		and.add(cond);
		and.add(c);
		return and;
	}
}
