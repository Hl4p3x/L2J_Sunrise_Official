package l2r.gameserver.model.zone.type;

import l2r.Config;
import l2r.gameserver.GeoData;
import l2r.gameserver.enums.ZoneIdType;
import l2r.gameserver.model.Location;
import l2r.gameserver.model.actor.L2Character;
import l2r.gameserver.model.actor.instance.L2PcInstance;
import l2r.gameserver.model.zone.L2ZoneRespawn;
import l2r.gameserver.network.serverpackets.ValidateLocation;

import gr.sr.utils.Rnd;

/**
 * @author vGodFather
 */
public class L2CallBugWallArena extends L2ZoneRespawn
{
	public L2CallBugWallArena(int id)
	{
		super(id);
	}
	
	@Override
	protected void onEnter(L2Character character)
	{
		if (!Config.ENABLE_AUTO_CORRECT_LOCATION)
		{
			return;
		}
		
		if (character instanceof L2PcInstance)
		{
			character.setInsideZone(ZoneIdType.CALL_BUGWALL_ARENA, true);
			
			while (true)
			{
				// In to avoid multiple failed loops
				character.getAI()._onFailedPath++;
				if (character.getAI()._onFailedPath >= 10)
				{
					character.getAI()._onFailedPath = 0;
					
					character.abortAttack();
					character.abortCast();
					character.teleToLocation(getSpawnLoc());
					break;
				}
				
				// try move player a bit and validate his position ;)
				final Location loc = GeoData.getInstance().moveCheck(character.getLocation().getX() + Rnd.get(-100, +100), character.getLocation().getY() + Rnd.get(-100, +100), character.getLocation().getZ(), character.getLocation().getX() + Rnd.get(-100, 100), character.getLocation().getY() + Rnd.get(-100, 100), character.getLocation().getZ(), character.getInstanceId());
				
				// Skip Location if z coords are greater than
				final int dz2 = Math.abs(loc.getZ() - character.getZ());
				if (dz2 >= 200)
				{
					continue;
				}
				
				if (!isInsideZone(loc))
				{
					character.getAI()._onFailedPath = 0;
					
					character.setXYZ(loc);
					character.abortAttack();
					character.abortCast();
					character.stopMove(loc);
					broadcastPacket(new ValidateLocation(character));
					character.sendPacket(new ValidateLocation(character));
					break;
				}
			}
		}
	}
	
	@Override
	protected void onExit(L2Character character)
	{
		if (!Config.ENABLE_AUTO_CORRECT_LOCATION)
		{
			return;
		}
		
		character.setInsideZone(ZoneIdType.CALL_BUGWALL_ARENA, false);
	}
}
