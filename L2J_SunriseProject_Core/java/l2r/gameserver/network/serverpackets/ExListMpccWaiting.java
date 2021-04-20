package l2r.gameserver.network.serverpackets;

import java.util.ArrayList;
import java.util.List;

import l2r.gameserver.model.PartyMatchRoom;
import l2r.gameserver.model.PartyMatchRoomList;
import l2r.gameserver.model.actor.instance.L2PcInstance;

/**
 * @author vGodFather
 */
public class ExListMpccWaiting extends L2GameServerPacket
{
	private static final int PAGE_SIZE = 10;
	private final int _fullSize;
	private final List<PartyMatchRoom> _list;
	
	public ExListMpccWaiting(final L2PcInstance player, final int page, final int location, final boolean allLevels)
	{
		final int first = (page - 1) * PAGE_SIZE;
		final int firstNot = page * PAGE_SIZE;
		int i = 0;
		final PartyMatchRoom[] all = PartyMatchRoomList.getInstance().getRooms();
		_fullSize = all.length;
		_list = new ArrayList<>(PAGE_SIZE);
		for (final PartyMatchRoom c : all)
		{
			if (i >= first)
			{
				if (i >= firstNot)
				{
					continue;
				}
				_list.add(c);
				++i;
			}
		}
	}
	
	@Override
	protected void writeImpl()
	{
		writeC(0xFE);
		writeH(0x9C);
		
		writeD(_fullSize);
		writeD(_list.size());
		for (final PartyMatchRoom room : _list)
		{
			writeD(room.getId());
			writeS(room.getOwner().getName());
			writeD(room.getMembers());
			writeD(room.getMinLvl());
			writeD(room.getMaxLvl());
			writeD(1);
			writeD(room.getMaxMembers());
			writeS(room.getTitle());
		}
	}
}