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
package l2r.gameserver.util;

/**
 * This interface can be implemented by extensions to register simple functions with the DynamicExtension handler.<br>
 * It's in the responsibility of the extensions to interpret the get and set functions.
 * @author Galun
 */
public interface ExtensionFunction
{
	/**
	 * get an object identified with a name (should have a human readable output with toString())
	 * @param name the name of an object or a result of a function
	 * @return the object
	 */
	public Object get(String name);
	
	/**
	 * set the named object to the new value supplied in obj
	 * @param name the name of the object
	 * @param obj the new value
	 */
	public void set(String name, Object obj);
}
