package l2r.gameserver.network.clientpackets;

import l2r.gameserver.enums.QuickVarType;
import l2r.gameserver.instancemanager.KrateiCubeManager;
import l2r.gameserver.model.actor.instance.L2PcInstance;
import l2r.gameserver.network.serverpackets.ExPVPMatchCCRecord;

/**
 * @author vGodFather
 */
public class RequestStartShowKrateiCubeRank extends L2GameClientPacket
{
	protected int _unkn;
	
	@Override
	public String getType()
	{
		return "[C] D0:54 RequestStartShowKrateiCubeRank";
	}
	
	@Override
	protected void readImpl()
	{
		// nothing _unkn = readC();
	}
	
	@Override
	protected void runImpl()
	{
		L2PcInstance activeChar = getClient().getActiveChar();
		if (activeChar == null)
		{
			return;
		}
		
		int inProgress = (KrateiCubeManager.getInstance().isInProgress(activeChar.getQuickVarI(QuickVarType.KRATEI_CUBE_LVL.getCommand(), -1))) ? 1 : 2;
		{
			activeChar.sendPacket(new ExPVPMatchCCRecord(inProgress, KrateiCubeManager.getInstance().sortCPlayersByPoint()));
		}
	}
}
