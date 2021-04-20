package l2r.gameserver.network.clientpackets;

import l2r.gameserver.model.actor.instance.L2PcInstance;
import l2r.gameserver.network.serverpackets.ExListMpccWaiting;

/**
 * @author vGodFather
 */
public final class RequestExListMpccWaiting extends L2GameClientPacket
{
	private int _listId;
	private int _locationId;
	private boolean _allLevels;
	
	@Override
	protected void readImpl()
	{
		_listId = readD();
		_locationId = readD();
		_allLevels = readD() == 1;
	}
	
	@Override
	protected void runImpl()
	{
		final L2PcInstance activeChar = getClient().getActiveChar();
		if (activeChar == null)
		{
			return;
		}
		
		activeChar.sendPacket(new ExListMpccWaiting(activeChar, _listId, _locationId, _allLevels));
	}
}
