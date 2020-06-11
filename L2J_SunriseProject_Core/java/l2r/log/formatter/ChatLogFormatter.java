/*
 * Copyright (C) 2004-2015 L2J Server
 * 
 * This file is part of L2J Server.
 * 
 * L2J Server is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * L2J Server is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package l2r.log.formatter;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

import l2r.Config;
import l2r.util.StringUtil;

public class ChatLogFormatter extends Formatter
{
	private final SimpleDateFormat dateFmt = new SimpleDateFormat("dd MMM H:mm:ss");
	
	@Override
	public String format(LogRecord record)
	{
		Object[] params = record.getParameters();
		final StringBuilder output = StringUtil.startAppend(30 + record.getMessage().length() + (params != null ? 10 * params.length : 0), "[", dateFmt.format(new Date(record.getMillis())), "] ");
		
		if (params != null)
		{
			for (Object p : params)
			{
				StringUtil.append(output, String.valueOf(p), " ");
			}
		}
		
		StringUtil.append(output, record.getMessage(), Config.EOL);
		
		return output.toString();
	}
	
	public static String format(String message, Object[] params)
	{
		StringBuilder output = StringUtil.startAppend(30 + message.length() + ((params == null) ? 0 : params.length * 10), new String[]
		{
			message
		});
		
		if (params != null)
		{
			for (Object p : params)
			{
				StringUtil.append(output, String.valueOf(p), " ");
			}
		}
		
		return output.toString();
	}
}
