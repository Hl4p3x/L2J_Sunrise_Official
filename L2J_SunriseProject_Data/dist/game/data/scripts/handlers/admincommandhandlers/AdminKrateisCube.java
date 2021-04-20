package handlers.admincommandhandlers;

import java.util.StringTokenizer;

import l2r.gameserver.ThreadPoolManager;
import l2r.gameserver.handler.IAdminCommandHandler;
import l2r.gameserver.instancemanager.KrateiCubeManager;
import l2r.gameserver.instancemanager.QuestManager;
import l2r.gameserver.model.actor.instance.L2PcInstance;

import instances.KrateiCube.KrateiCube;
import instances.KrateiCube.KrateiCube.Event;
import instances.KrateiCube.KrateiCube.TaskManager;

/**
 * @author vGodFather
 */
public class AdminKrateisCube implements IAdminCommandHandler
{
	private static final String[] ADMIN_COMMANDS =
	{
		"admin_start_krateis_cube",
		"admin_stop_krateis_cube",
		"admin_register_krateis_cube",
		"admin_unregister_krateis_cube",
	};
	
	@Override
	public boolean useAdminCommand(String command, L2PcInstance activeChar)
	{
		StringTokenizer st = new StringTokenizer(command, " ");
		String cmd = st.nextToken();
		
		if (cmd.equals("admin_start_krateis_cube"))
		{
			KrateiCube k = (KrateiCube) QuestManager.getInstance().getQuest("KrateiCube");
			TaskManager t = k.new TaskManager(Event.tele_task);
			ThreadPoolManager.getInstance().scheduleGeneral(t, 1000); // teleport immediatly (1 sec.)
			return true;
		}
		
		if (activeChar.getTarget() instanceof L2PcInstance)
		{
			L2PcInstance target;
			target = (L2PcInstance) activeChar.getTarget();
			
			if (cmd.equals("admin_register_krateis_cube"))
			{
				int enterLevel = 0;
				if ((target.getLevel() >= 70) && (target.getLevel() < 76))
				{
					enterLevel = 0;
				}
				else if ((target.getLevel() >= 76) && (target.getLevel() < 80))
				{
					enterLevel = 1;
				}
				else if (target.getLevel() >= 80)
				{
					enterLevel = 2;
				}
				
				if (KrateiCubeManager.getInstance().getNPlayers().contains(target))
				{
					activeChar.sendMessage("This player is already registered.");
				}
				else
				{
					KrateiCubeManager.getInstance().registerPlayer(target, enterLevel);
					if (target == activeChar)
					{
						activeChar.sendMessage("You have successfully registered for the next Krateis Cube match.");
					}
					else
					{
						target.sendMessage("An admin registered you for the next Krateis Cube match.");
					}
				}
			}
			else if (cmd.equals("admin_unregister_krateis_cube"))
			{
				if (!KrateiCubeManager.getInstance().getNPlayers().contains(target))
				{
					activeChar.sendMessage("This player is not registered.");
				}
				else
				{
					KrateiCubeManager.getInstance().removePlayer(target);
					target.sendMessage("An admin removed you from Krateis Cube playerlist.");
				}
			}
		}
		else
		{
			activeChar.sendMessage("Target must be a player!");
			return false;
		}
		return true;
	}
	
	@Override
	public String[] getAdminCommandList()
	{
		return ADMIN_COMMANDS;
	}
}