package l2r.gameserver.network.serverpackets;

/**
 * Sent whenever you forfeit Crateis Cube registration.
 * @author vGodFather
 */
public class ExPVPMatchCCRetire extends L2GameServerPacket
{
	public static final ExPVPMatchCCRetire PACKET = new ExPVPMatchCCRetire();
	
	@Override
	protected void writeImpl()
	{
		writeC(0xfe);
		writeH(0x8b);
	}
}
