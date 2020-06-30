package l2r.gameserver.data.xml.impl;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import l2r.gameserver.model.StatsSet;
import l2r.gameserver.model.cubic.CubicSkill;
import l2r.gameserver.model.cubic.CubicTemplate;
import l2r.gameserver.model.cubic.ICubicConditionHolder;
import l2r.gameserver.model.cubic.conditions.HealthCondition;
import l2r.gameserver.model.cubic.conditions.HpCondition;
import l2r.gameserver.model.cubic.conditions.HpCondition.HpConditionType;
import l2r.gameserver.model.cubic.conditions.RangeCondition;
import l2r.util.data.xml.IXmlReader.IXmlReader;

import org.w3c.dom.Document;
import org.w3c.dom.Node;

public final class CubicData implements IXmlReader
{
	private final Map<Integer, Map<Integer, CubicTemplate>> _cubics = new HashMap<>();
	
	protected CubicData()
	{
		load();
	}
	
	@Override
	public void load()
	{
		parseDatapackDirectory("data/xml/stats/cubics", true);
		LOGGER.info("Loaded {} cubics.", _cubics.size());
	}
	
	@Override
	public void parseDocument(Document doc)
	{
		forEach(doc, "list", listNode -> forEach(listNode, "cubic", cubicNode ->
		{
			parseTemplate(cubicNode, new CubicTemplate(new StatsSet(parseAttributes(cubicNode))));
		}));
	}
	
	/**
	 * @param cubicNode
	 * @param template
	 */
	private void parseTemplate(Node cubicNode, CubicTemplate template)
	{
		forEach(cubicNode, IXmlReader::isNode, innerNode ->
		{
			switch (innerNode.getNodeName())
			{
				case "conditions":
				{
					parseConditions(innerNode, template, template);
					break;
				}
				case "skills":
				{
					parseSkills(innerNode, template);
					break;
				}
			}
		});
		_cubics.computeIfAbsent(template.getId(), key -> new HashMap<>()).put(template.getLevel(), template);
	}
	
	/**
	 * @param cubicNode
	 * @param template
	 * @param holder
	 */
	private void parseConditions(Node cubicNode, CubicTemplate template, ICubicConditionHolder holder)
	{
		forEach(cubicNode, IXmlReader::isNode, conditionNode ->
		{
			switch (conditionNode.getNodeName())
			{
				case "hp":
				{
					final HpConditionType type = parseEnum(conditionNode.getAttributes(), HpConditionType.class, "type");
					final int hpPer = parseInteger(conditionNode.getAttributes(), "percent");
					holder.addCondition(new HpCondition(type, hpPer));
					break;
				}
				case "range":
				{
					final int range = parseInteger(conditionNode.getAttributes(), "value");
					holder.addCondition(new RangeCondition(range));
					break;
				}
				case "healthPercent":
				{
					final int min = parseInteger(conditionNode.getAttributes(), "min");
					final int max = parseInteger(conditionNode.getAttributes(), "max");
					holder.addCondition(new HealthCondition(min, max));
					break;
				}
				default:
				{
					LOGGER.warn("Attempting to use not implemented condition: (" + conditionNode.getNodeName() + ") for cubic id: " + template.getId() + " level: " + template.getLevel());
					break;
				}
			}
		});
	}
	
	/**
	 * @param cubicNode
	 * @param template
	 */
	private void parseSkills(Node cubicNode, CubicTemplate template)
	{
		forEach(cubicNode, "skill", skillNode ->
		{
			final CubicSkill skill = new CubicSkill(new StatsSet(parseAttributes(skillNode)));
			template.getSkills().add(skill);
			
			forEach(skillNode, "conditions", conditionNode -> parseConditions(conditionNode, template, skill));
		});
	}
	
	public int getLoadedElementsCount()
	{
		return _cubics.size();
	}
	
	/**
	 * @param id
	 * @param level
	 * @return the L2CubicTemplate for specified id and level
	 */
	public CubicTemplate getCubicTemplate(int id, int level)
	{
		return _cubics.getOrDefault(id, Collections.emptyMap()).get(level);
	}
	
	/**
	 * Gets the single instance of CubicData.
	 * @return single instance of CubicData
	 */
	public static CubicData getInstance()
	{
		return SingletonHolder._instance;
	}
	
	private static class SingletonHolder
	{
		protected static final CubicData _instance = new CubicData();
	}
}
