package l2r.gameserver.network.serverpackets;

/**
 * Sent at the beginning of Crateis Cube and each time KP increases.<BR>
 * Time is counted down automatically from the first time this packet is sent.
 * @author vGodFather
 */
public class ExPVPMatchCCMyRecord extends L2GameServerPacket
{
	private final int _kp;
	
	public ExPVPMatchCCMyRecord(int killPts)
	{
		_kp = killPts;
	}
	
	@Override
	protected void writeImpl()
	{
		writeC(0xfe);
		writeH(0x8a);
		
		writeD(_kp);
	}
}
