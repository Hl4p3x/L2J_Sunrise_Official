package handlers.effecthandlers;

import l2r.gameserver.instancemanager.TerritoryWarManager;
import l2r.gameserver.model.actor.instance.L2PcInstance;
import l2r.gameserver.model.effects.EffectTemplate;
import l2r.gameserver.model.effects.L2Effect;
import l2r.gameserver.model.stats.Env;

/**
 * Outpost Destroy effect implementation.
 * @author vGodFather
 */
public class OutpostDestroy extends L2Effect
{
	public OutpostDestroy(Env env, EffectTemplate template)
	{
		super(env, template);
	}
	
	@Override
	public boolean isInstant()
	{
		return true;
	}
	
	@Override
	public boolean onStart()
	{
		final L2PcInstance player = getEffector().getActingPlayer();
		if (!player.isClanLeader() || (player.getClan().getCastleId() <= 0))
		{
			return false;
		}
		
		if (TerritoryWarManager.getInstance().isTWInProgress())
		{
			TerritoryWarManager.getInstance().removeHQForClan(player.getClan());
		}
		return true;
	}
}
