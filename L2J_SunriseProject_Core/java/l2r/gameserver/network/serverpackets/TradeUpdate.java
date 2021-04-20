package l2r.gameserver.network.serverpackets;

import l2r.gameserver.model.TradeItem;

public class TradeUpdate extends AbstractItemPacket
{
	private final TradeItem _item;
	
	public TradeUpdate(TradeItem item)
	{
		_item = item;
	}
	
	@Override
	protected final void writeImpl()
	{
		writeC(0x81);
		writeH(1);
		writeH((_item.getCount() > 0) && _item.getItem().isStackable() ? 3 : 2);
		writeH(_item.getItem().getType1());
		writeItem(_item, true);
	}
}