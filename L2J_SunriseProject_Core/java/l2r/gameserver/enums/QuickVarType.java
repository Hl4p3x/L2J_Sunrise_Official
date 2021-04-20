/*
 * Copyright (C) L2J Sunrise
 * This file is part of L2J Sunrise.
 */
package l2r.gameserver.enums;

import gr.sr.interfaces.IFQuickVar;

/**
 * @author vGodFather
 */
public enum QuickVarType implements IFQuickVar
{
	NONE(""),
	PING("ping"),
	MTU("mtu"),
	PORTAL_WH("portalWh"),
	COMMUNITY_SELL("communitySell"),
	KRATEI_CUBE_LVL("KRATEI_CUBE_LVL");
	
	private String _command;
	
	QuickVarType(String value)
	{
		_command = value;
	}
	
	@Override
	public String getCommand()
	{
		return _command;
	}
}
