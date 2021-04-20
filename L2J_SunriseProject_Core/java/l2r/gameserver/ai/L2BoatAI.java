package l2r.gameserver.ai;

import l2r.gameserver.model.actor.L2Vehicle;
import l2r.gameserver.model.actor.instance.L2BoatInstance;

/**
 * @author vGodFather
 */
public class L2BoatAI extends L2VehicleAI
{
	public L2BoatAI(L2Vehicle creature)
	{
		super(creature);
	}
	
	@Override
	protected void onEvtArrived()
	{
		final L2BoatInstance actor = getActor();
		if (actor == null)
		{
			return;
		}
		
		actor.onEvtArrived();
	}
	
	@Override
	public L2BoatInstance getActor()
	{
		return (L2BoatInstance) _actor;
	}
}