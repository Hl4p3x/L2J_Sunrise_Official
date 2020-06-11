package l2r.gameserver.communitybbs.SunriseBoards;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import l2r.L2DatabaseFactory;
import l2r.gameserver.data.sql.NpcTable;

/**
 * @author L2jSunrise Team
 * @Website www.l2jsunrise.com
 */
public class GrandBossList extends AbstractSunriseBoards
{
	private final StringBuilder _list = new StringBuilder();
	
	@Override
	public void load()
	{
		_list.setLength(0);
		int pos = 0;
		
		try (Connection con = L2DatabaseFactory.getInstance().getConnection())
		{
			PreparedStatement statement = con.prepareStatement("SELECT boss_id, status FROM grandboss_data");
			ResultSet result = statement.executeQuery();
			
			while (result.next())
			{
				int npcid = result.getInt("boss_id");
				int status = result.getInt("status");
				if (npcid == 29062)
				{
					continue;
				}
				
				pos++;
				String name = NpcTable.getInstance().getTemplate(npcid).getName();
				boolean rstatus = status == 0;
				addGrandBossToList(pos, name, rstatus);
			}
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}
	
	private void addGrandBossToList(int pos, String npcname, boolean rstatus)
	{
		_list.append("<table width=680 bgcolor=111111 height=16 border=0 cellspacing=0 cellpadding=0>");
		_list.append("<tr>");
		_list.append("<td FIXWIDTH=30>" + pos + "</td>");
		_list.append("<td FIXWIDTH=30>" + npcname + "</td>");
		_list.append("<td FIXWIDTH=30 align=center>" + ((rstatus) ? "<font color=99FF00>Alive</font>" : "<font color=CC0000>Dead</font>") + "</td>");
		_list.append("</tr>");
		_list.append("</table>");
		_list.append("<img src=\"L2UI.Squaregray\" width=\"680\" height=\"1\">");
	}
	
	@Override
	public String getList()
	{
		return _list.toString();
	}
	
	public static GrandBossList getInstance()
	{
		return SingletonHolder._instance;
	}
	
	private static class SingletonHolder
	{
		protected static final GrandBossList _instance = new GrandBossList();
	}
}
