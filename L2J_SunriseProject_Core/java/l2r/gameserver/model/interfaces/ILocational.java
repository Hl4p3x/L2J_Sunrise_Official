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
package l2r.gameserver.model.interfaces;

import l2r.gameserver.enums.Position;
import l2r.gameserver.util.Util;

/**
 * Simple interface for location of object.
 * @author xban1x
 */
public interface ILocational
{
	public int getX();
	
	public int getY();
	
	public int getZ();
	
	public int getHeading();
	
	public int getInstanceId();
	
	public ILocational getLocation();
	
	/**
	 * @param to
	 * @return the heading to the target specified
	 */
	default int calculateHeadingTo(ILocational to)
	{
		return Util.calculateHeadingFrom(getX(), getY(), to.getX(), to.getY());
	}
	
	/**
	 * Computes the 3D Euclidean distance between this locational and (x, y, z).
	 * @param x the x coordinate
	 * @param y the y coordinate
	 * @param z the z coordinate
	 * @return the 3D Euclidean distance between this locational and (x, y, z)
	 */
	default double distance3d(double x, double y, double z)
	{
		return Math.sqrt(Math.pow(getX() - x, 2) + Math.pow(getY() - y, 2) + Math.pow(getZ() - z, 2));
	}
	
	/**
	 * Checks if this locational is in 3D Euclidean radius of (x, y, z)
	 * @param x the x coordinate
	 * @param y the y coordinate
	 * @param z the z coordinate
	 * @param radius the radius
	 * @return {@code true} if this locational is in radius of (x, y, z), {@code false} otherwise
	 */
	default boolean isInRadius3d(double x, double y, double z, double radius)
	{
		return distance3d(x, y, z) <= radius;
	}
	
	/**
	 * Checks if this locational is in 3D Euclidean radius of locational loc
	 * @param loc the locational
	 * @param radius the radius
	 * @return {@code true} if this locational is in radius of locational loc, {@code false} otherwise
	 */
	default boolean isInRadius3d(ILocational loc, double radius)
	{
		return isInRadius3d(loc.getX(), loc.getY(), loc.getZ(), radius);
	}
	
	/**
	 * @param target
	 * @return {@code true} if this location is in front of the target location based on the game's concept of position.
	 */
	default boolean isInFrontOf(ILocational target)
	{
		return Position.FRONT.equals(Position.getPosition(this, target));
	}
	
	/**
	 * @param target
	 * @return {@code true} if this location is in one of the sides of the target location based on the game's concept of position.
	 */
	default boolean isOnSideOf(ILocational target)
	{
		return Position.SIDE.equals(Position.getPosition(this, target));
	}
	
	/**
	 * @param target
	 * @return {@code true} if this location is behind the target location based on the game's concept of position.
	 */
	default boolean isBehind(ILocational target)
	{
		return Position.BACK.equals(Position.getPosition(this, target));
	}
}
