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
package l2r.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Simplifies loading of property files and adds logging if a non existing property is requested.
 * @author Nos
 */
public final class PropertiesParser
{
	private static final Logger _log = LoggerFactory.getLogger(PropertiesParser.class);
	
	private final Properties _properties = new Properties();
	private final File _file;
	
	public PropertiesParser(String name)
	{
		this(new File(name));
	}
	
	public PropertiesParser(File file)
	{
		_file = file;
		try (FileInputStream fileInputStream = new FileInputStream(file))
		{
			try (InputStreamReader inputStreamReader = new InputStreamReader(fileInputStream, Charset.defaultCharset()))
			{
				_properties.load(inputStreamReader);
			}
		}
		catch (Exception e)
		{
			_log.warn("[" + _file.getName() + "] There was an error loading config reason: " + e.getMessage());
		}
	}
	
	public boolean containskey(String key)
	{
		return _properties.containsKey(key);
	}
	
	private String getValue(String key)
	{
		String value = _properties.getProperty(key);
		return value != null ? value.trim() : null;
	}
	
	public boolean getBoolean(String key, boolean defaultValue)
	{
		String value = getValue(key);
		if (value == null)
		{
			_log.warn("[" + _file.getName() + "] missing property for key: " + key + " using default value: " + defaultValue);
			return defaultValue;
		}
		
		if (value.equalsIgnoreCase("true"))
		{
			return true;
		}
		else if (value.equalsIgnoreCase("false"))
		{
			return false;
		}
		else
		{
			_log.warn("[" + _file.getName() + "] Invalid value specified for key: " + key + " specified value: " + value + " should be \"boolean\" using default value: " + defaultValue);
			return defaultValue;
		}
	}
	
	public byte getByte(String key, byte defaultValue)
	{
		String value = getValue(key);
		if (value == null)
		{
			_log.warn("[" + _file.getName() + "] missing property for key: " + key + " using default value: " + defaultValue);
			return defaultValue;
		}
		
		try
		{
			return Byte.parseByte(value);
		}
		catch (NumberFormatException e)
		{
			_log.warn("[" + _file.getName() + "] Invalid value specified for key: " + key + " specified value: " + value + " should be \"byte\" using default value: " + defaultValue);
			return defaultValue;
		}
	}
	
	public short getShort(String key, short defaultValue)
	{
		String value = getValue(key);
		if (value == null)
		{
			_log.warn("[" + _file.getName() + "] missing property for key: " + key + " using default value: " + defaultValue);
			return defaultValue;
		}
		
		try
		{
			return Short.parseShort(value);
		}
		catch (NumberFormatException e)
		{
			_log.warn("[" + _file.getName() + "] Invalid value specified for key: " + key + " specified value: " + value + " should be \"short\" using default value: " + defaultValue);
			return defaultValue;
		}
	}
	
	public int getInt(String key, int defaultValue)
	{
		String value = getValue(key);
		if (value == null)
		{
			_log.warn("[" + _file.getName() + "] missing property for key: " + key + " using default value: " + defaultValue);
			return defaultValue;
		}
		
		try
		{
			return Integer.parseInt(value);
		}
		catch (NumberFormatException e)
		{
			_log.warn("[" + _file.getName() + "] Invalid value specified for key: " + key + " specified value: " + value + " should be \"int\" using default value: " + defaultValue);
			return defaultValue;
		}
	}
	
	public long getLong(String key, long defaultValue)
	{
		String value = getValue(key);
		if (value == null)
		{
			_log.warn("[" + _file.getName() + "] missing property for key: " + key + " using default value: " + defaultValue);
			return defaultValue;
		}
		
		try
		{
			return Long.parseLong(value);
		}
		catch (NumberFormatException e)
		{
			_log.warn("[" + _file.getName() + "] Invalid value specified for key: " + key + " specified value: " + value + " should be \"long\" using default value: " + defaultValue);
			return defaultValue;
		}
	}
	
	public float getFloat(String key, float defaultValue)
	{
		String value = getValue(key);
		if (value == null)
		{
			_log.warn("[" + _file.getName() + "] missing property for key: " + key + " using default value: " + defaultValue);
			return defaultValue;
		}
		
		try
		{
			return Float.parseFloat(value);
		}
		catch (NumberFormatException e)
		{
			_log.warn("[" + _file.getName() + "] Invalid value specified for key: " + key + " specified value: " + value + " should be \"float\" using default value: " + defaultValue);
			return defaultValue;
		}
	}
	
	public double getDouble(String key, double defaultValue)
	{
		String value = getValue(key);
		if (value == null)
		{
			_log.warn("[" + _file.getName() + "] missing property for key: " + key + " using default value: " + defaultValue);
			return defaultValue;
		}
		
		try
		{
			return Double.parseDouble(value);
		}
		catch (NumberFormatException e)
		{
			_log.warn("[" + _file.getName() + "] Invalid value specified for key: " + key + " specified value: " + value + " should be \"double\" using default value: " + defaultValue);
			return defaultValue;
		}
	}
	
	public String getProperty(String key, String defaultValue)
	{
		String value = getValue(key);
		if (value == null)
		{
			_log.warn("[" + _file.getName() + "] missing property for key: " + key + " using default value: " + defaultValue);
			return defaultValue;
		}
		return value;
	}
	
	public String getString(String key, String defaultValue)
	{
		String value = getValue(key);
		if (value == null)
		{
			_log.warn("[" + _file.getName() + "] missing property for key: " + key + " using default value: " + defaultValue);
			return defaultValue;
		}
		return value;
	}
	
	public <T extends Enum<T>> T getEnum(String key, Class<T> clazz, T defaultValue)
	{
		String value = getValue(key);
		if (value == null)
		{
			_log.warn("[" + _file.getName() + "] missing property for key: " + key + " using default value: " + defaultValue);
			return defaultValue;
		}
		
		try
		{
			return Enum.valueOf(clazz, value);
		}
		catch (IllegalArgumentException e)
		{
			_log.warn("[" + _file.getName() + "] Invalid value specified for key: " + key + " specified value: " + value + " should be enum value of \"" + clazz.getSimpleName() + "\" using default value: " + defaultValue);
			return defaultValue;
		}
	}
}
