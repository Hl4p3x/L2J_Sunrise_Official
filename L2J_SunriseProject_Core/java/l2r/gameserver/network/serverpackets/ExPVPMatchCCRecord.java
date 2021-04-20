package l2r.gameserver.network.serverpackets;

import java.util.List;

import l2r.gameserver.instancemanager.KrateiCubeManager.PlayerScore;

/**
 * Sent at the end of Crateis Cube OR when you click the "Match results" icon during the match.
 * @author vGodFather
 */
public class ExPVPMatchCCRecord extends L2GameServerPacket
{
	private final int _state;
	private final List<PlayerScore> _pScores;
	
	public ExPVPMatchCCRecord(int state, List<PlayerScore> pScores)
	{
		_state = state;
		_pScores = pScores;
	}
	
	@Override
	protected void writeImpl()
	{
		writeC(0xfe);
		writeH(0x89);
		
		writeD(_state); // 0x01 - in progress, 0x02 - finished
		writeD(_pScores.size());
		for (PlayerScore ps : _pScores)
		{
			writeS(ps.getName());
			writeD(ps.getPoints());
		}
	}
}