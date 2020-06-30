package l2r.gameserver.enums;

/**
 * @author vGodFather
 */
public enum BroadcastStatusType
{
	TELEPORTED(0),
	DEFAULT(1),
	SUMMONED(2);
	
	public int _update;
	
	private BroadcastStatusType(int value)
	{
		_update = value;
	}
	
	public int get()
	{
		return _update;
	}
}
