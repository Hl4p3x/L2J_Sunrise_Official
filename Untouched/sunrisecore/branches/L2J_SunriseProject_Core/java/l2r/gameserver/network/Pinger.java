package l2r.gameserver.network;

import l2r.gameserver.ThreadPoolManager;
import l2r.gameserver.enums.QuickVarType;
import l2r.gameserver.model.actor.instance.L2PcInstance;
import l2r.gameserver.network.serverpackets.NetPingPacket;

/**
 * @author vGodFather
 */
public class Pinger
{
	public static boolean getPing(L2PcInstance activeChar)
	{
		activeChar.sendMessage("Processing request...");
		activeChar.sendPacket(new NetPingPacket(activeChar));
		ThreadPoolManager.getInstance().scheduleGeneral(new AnswerTask(activeChar), 3000L);
		return true;
	}
	
	private static final class AnswerTask implements Runnable
	{
		private final L2PcInstance _player;
		
		public AnswerTask(L2PcInstance player)
		{
			_player = player;
		}
		
		@Override
		public void run()
		{
			int ping = _player.getQuickVarI(QuickVarType.PING.getCommand(), -1);
			int mtu = _player.getQuickVarI(QuickVarType.MTU.getCommand(), -1);
			if ((ping > -1) && (mtu > -1))
			{
				_player.sendMessage("Status: PING: " + ping + " ms/MTU: " + mtu);
			}
			else
			{
				_player.sendMessage("The data from the client was not received.");
			}
			
			_player.setQuickVar(QuickVarType.PING.getCommand(), -1);
			_player.setQuickVar(QuickVarType.MTU.getCommand(), -1);
		}
	}
}