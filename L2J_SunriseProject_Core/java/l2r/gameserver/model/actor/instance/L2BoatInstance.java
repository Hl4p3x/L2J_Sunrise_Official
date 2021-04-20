package l2r.gameserver.model.actor.instance;

import l2r.gameserver.enums.InstanceType;
import l2r.gameserver.model.Location;
import l2r.gameserver.model.actor.L2Vehicle;
import l2r.gameserver.model.actor.templates.L2CharTemplate;
import l2r.gameserver.network.serverpackets.GetOffVehicle;
import l2r.gameserver.network.serverpackets.GetOnVehicle;
import l2r.gameserver.network.serverpackets.L2GameServerPacket;
import l2r.gameserver.network.serverpackets.MoveToLocationInVehicle;
import l2r.gameserver.network.serverpackets.StopMove;
import l2r.gameserver.network.serverpackets.StopMoveInVehicle;
import l2r.gameserver.network.serverpackets.ValidateLocationInVehicle;
import l2r.gameserver.network.serverpackets.VehicleCheckLocation;
import l2r.gameserver.network.serverpackets.VehicleDeparture;
import l2r.gameserver.network.serverpackets.VehicleInfo;
import l2r.gameserver.network.serverpackets.VehicleStarted;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author vGodFather
 */
public class L2BoatInstance extends L2Vehicle
{
	protected static final Logger _logBoat = LoggerFactory.getLogger(L2BoatInstance.class);
	
	/**
	 * Creates a boat.
	 * @param template the boat template
	 */
	public L2BoatInstance(L2CharTemplate template)
	{
		super(template);
		setInstanceType(InstanceType.L2BoatInstance);
	}
	
	@Override
	public boolean isBoat()
	{
		return true;
	}
	
	@Override
	public int getId()
	{
		return 0;
	}
	
	@Override
	public L2GameServerPacket movePacket()
	{
		return new VehicleDeparture(this);
	}
	
	@Override
	public L2GameServerPacket stopMovePacket()
	{
		return new StopMove(this);
	}
	
	@Override
	public L2GameServerPacket validateLocationPacket(L2PcInstance player)
	{
		return new ValidateLocationInVehicle(player);
	}
	
	@Override
	public L2GameServerPacket getOnPacket(final L2PcInstance player, final Location location)
	{
		return new GetOnVehicle(player.getObjectId(), getObjectId(), location);
	}
	
	@Override
	public L2GameServerPacket getOffPacket(final L2PcInstance player, final Location location)
	{
		return new GetOffVehicle(player.getObjectId(), getObjectId(), location.getX(), location.getY(), location.getZ());
	}
	
	@Override
	public L2GameServerPacket inMovePacket(final L2PcInstance player, final Location src, final Location desc)
	{
		return new MoveToLocationInVehicle(player, src, desc);
	}
	
	@Override
	public L2GameServerPacket inStopMovePacket(final L2PcInstance player)
	{
		return new StopMoveInVehicle(player, getObjectId());
	}
	
	@Override
	public L2GameServerPacket startPacket()
	{
		return new VehicleStarted(this, _runState);
	}
	
	@Override
	public L2GameServerPacket checkLocationPacket()
	{
		return new VehicleCheckLocation(this);
	}
	
	@Override
	public L2GameServerPacket infoPacket()
	{
		return new VehicleInfo(this);
	}
}
