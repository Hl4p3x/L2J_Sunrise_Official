/*
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * 
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
package l2r.gameserver.model.actor.instance;

import l2r.Config;
import l2r.gameserver.enums.CtrlIntention;
import l2r.gameserver.model.actor.L2Character;
import l2r.gameserver.model.actor.templates.L2NpcTemplate;
import l2r.gameserver.network.serverpackets.ActionFailed;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class L2EventMapGuardInstance extends L2GuardInstance
{
	private static Logger _log = LoggerFactory.getLogger(L2GuardInstance.class);
	
	public L2EventMapGuardInstance(L2NpcTemplate template)
	{
		super(template);
	}
	
	@Override
	public boolean isAutoAttackable(L2Character attacker)
	{
		return false;
	}
	
	/**
	 * Manage actions when a player click on the L2GuardInstance.<BR>
	 * <BR>
	 * <B><U> Actions on first click on the L2GuardInstance (Select it)</U> :</B><BR>
	 * <BR>
	 * <li>Set the L2GuardInstance as target of the L2PcInstance player (if necessary)</li>
	 * <li>Send a Server->Client packet MyTargetSelected to the L2PcInstance player (display the select window)</li>
	 * <li>Set the L2PcInstance Intention to AI_INTENTION_IDLE</li>
	 * <li>Send a Server->Client packet ValidateLocation to correct the L2GuardInstance position and heading on the client</li><BR>
	 * <BR>
	 * <B><U> Actions on second click on the L2GuardInstance (Attack it/Interact with it)</U> :</B><BR>
	 * <BR>
	 * <li>If L2PcInstance is in the _aggroList of the L2GuardInstance, set the L2PcInstance Intention to AI_INTENTION_ATTACK</li>
	 * <li>If L2PcInstance is NOT in the _aggroList of the L2GuardInstance, set the L2PcInstance Intention to AI_INTENTION_INTERACT (after a distance verification) and show message</li><BR>
	 * <BR>
	 * <B><U> Example of use </U> :</B><BR>
	 * <BR>
	 * <li>Client packet : Action, AttackRequest</li><BR>
	 * <BR>
	 * @param player The L2PcInstance that start an action on the L2GuardInstance
	 */
	@Override
	public void onAction(L2PcInstance player)
	{
		if (!canTarget(player))
		{
			return;
		}
		
		// Check if the L2PcInstance already target the L2GuardInstance
		if (getObjectId() != player.getTargetId())
		{
			if (Config.DEBUG)
			{
				_log.info(player.getObjectId() + ": Targetted guard " + getObjectId());
			}
			
			// Set the target of the L2PcInstance player
			player.setTarget(this);
		}
		else
		{
			// Check if the L2PcInstance is in the _aggroList of the L2GuardInstance
			if (isInAggroList(player))
			{
				if (Config.DEBUG)
				{
					_log.info(player.getObjectId() + ": Attacked guard " + getObjectId());
				}
				
				// Set the L2PcInstance Intention to AI_INTENTION_ATTACK
				player.getAI().setIntention(CtrlIntention.AI_INTENTION_ATTACK, this);
			}
			else
			{
				// Calculate the distance between the L2PcInstance and the L2NpcInstance
				if (!canInteract(player))
				{
					// Set the L2PcInstance Intention to AI_INTENTION_INTERACT
					player.getAI().setIntention(CtrlIntention.AI_INTENTION_INTERACT, this);
				}
				else
				{
					player.sendMessage("Did you know that you are on the event right now?");
					player.sendPacket(ActionFailed.STATIC_PACKET);
				}
			}
		}
		// Send a Server->Client ActionFailed to the L2PcInstance in order to avoid that the client wait another packet
		player.sendPacket(ActionFailed.STATIC_PACKET);
	}
}
