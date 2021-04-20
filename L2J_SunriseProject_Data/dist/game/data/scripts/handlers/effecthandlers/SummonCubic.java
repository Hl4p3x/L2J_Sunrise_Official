/*
 * Copyright (C) L2J Sunrise
 * This file is part of L2J Sunrise.
 */
package handlers.effecthandlers;

import l2r.gameserver.data.xml.impl.CubicData;
import l2r.gameserver.model.actor.instance.L2PcInstance;
import l2r.gameserver.model.cubic.CubicInstance;
import l2r.gameserver.model.cubic.CubicTemplate;
import l2r.gameserver.model.effects.EffectTemplate;
import l2r.gameserver.model.effects.L2Effect;
import l2r.gameserver.model.stats.Env;
import l2r.util.Rnd;

public class SummonCubic extends L2Effect
{
	private final int _cubicId;
	private final int _cubicLvl;
	
	public SummonCubic(Env env, EffectTemplate template)
	{
		super(env, template);
		
		_cubicId = template.getParameters().getInt("cubicId");
		_cubicLvl = template.getParameters().getInt("cubicLvl");
	}
	
	@Override
	public boolean isInstant()
	{
		return true;
	}
	
	@Override
	public boolean onStart()
	{
		if ((getEffected() == null) || !getEffected().isPlayer() || !getEffector().isPlayer() || getEffected().isAlikeDead() || getEffected().getActingPlayer().inObserverMode())
		{
			return false;
		}
		
		if (_cubicId < 0)
		{
			_log.warn(SummonCubic.class.getSimpleName() + ": Invalid NPC Id:" + _cubicId + " in skill Id: " + getSkill().getId());
			return false;
		}
		
		final L2PcInstance player = getEffected().getActingPlayer();
		if (player.inObserverMode() || player.isMounted())
		{
			return false;
		}
		
		// If cubic is already present, it's replaced.
		final CubicInstance cubic = player.getCubicById(_cubicId);
		if (cubic != null)
		{
			if (cubic.getTemplate().getLevel() > _cubicLvl)
			{
				// What do we do in such case?
				return false;
			}
			
			cubic.deactivate();
		}
		else
		{
			// If maximum amount is reached, random cubic is removed.
			// Players with no mastery can have only one cubic.
			final int allowedCubicCount = getEffected().getActingPlayer().getStat().getMaxCubicCount();
			final int currentCubicCount = player.getCubics().size();
			// Extra cubics are removed, one by one, randomly.
			for (int i = 0; i <= (currentCubicCount - allowedCubicCount); i++)
			{
				final int removedCubicId = (int) player.getCubics().keySet().toArray()[Rnd.get(currentCubicCount)];
				final CubicInstance removedCubic = player.getCubicById(removedCubicId);
				removedCubic.deactivate();
			}
		}
		
		final CubicTemplate template = CubicData.getInstance().getCubicTemplate(_cubicId, _cubicLvl);
		if (template == null)
		{
			_log.warn("Attempting to summon cubic without existing template id: {} level: {}", _cubicId, _cubicLvl);
			return false;
		}
		
		// Adding a new cubic.
		player.addCubic(new CubicInstance(getEffector().getActingPlayer(), player, template));
		player.broadcastUserInfo();
		return true;
	}
}