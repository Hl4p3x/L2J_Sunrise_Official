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
package l2r.gameserver.model;

import l2r.gameserver.GeoData;
import l2r.gameserver.model.interfaces.ILocational;
import l2r.gameserver.model.interfaces.IPositionable;
import l2r.geoserver.model.MoveTrick;

/**
 * Location data transfer object.<br>
 * Contains coordinates data, heading and instance Id.
 * @author Zoey76
 */
public class Location implements IPositionable
{
	public int x;
	public int y;
	public int z;
	public int heading;
	private int instanceId;
	private MoveTrick[] tricks;
	
	public Location()
	{
	}
	
	public Location(int x, int y, int z)
	{
		this(x, y, z, 0, -1);
	}
	
	public Location(int x, int y, int z, int heading)
	{
		this(x, y, z, heading, -1);
	}
	
	public Location(L2Object obj)
	{
		this(obj.getX(), obj.getY(), obj.getZ(), obj.getHeading(), obj.getInstanceId());
	}
	
	public Location(int x, int y, int z, int heading, int instanceId)
	{
		this.x = x;
		this.y = y;
		this.z = z;
		this.heading = heading;
		this.instanceId = instanceId;
	}
	
	/**
	 * Get the x coordinate.
	 * @return the x coordinate
	 */
	@Override
	public int getX()
	{
		return this.x;
	}
	
	/**
	 * Set the x coordinate.
	 * @param x the x coordinate
	 */
	@Override
	public void setX(int x)
	{
		this.x = x;
	}
	
	/**
	 * Get the y coordinate.
	 * @return the y coordinate
	 */
	@Override
	public int getY()
	{
		return this.y;
	}
	
	/**
	 * Set the y coordinate.
	 * @param y the x coordinate
	 */
	@Override
	public void setY(int y)
	{
		this.y = y;
	}
	
	/**
	 * Get the z coordinate.
	 * @return the z coordinate
	 */
	@Override
	public int getZ()
	{
		return this.z;
	}
	
	/**
	 * Set the z coordinate.
	 * @param z the z coordinate
	 */
	@Override
	public void setZ(int z)
	{
		this.z = z;
	}
	
	public Location setZAndGet(int z)
	{
		this.z = z;
		return this;
	}
	
	/**
	 * Set the x, y, z coordinates.
	 * @param x the x coordinate
	 * @param y the y coordinate
	 * @param z the z coordinate
	 */
	@Override
	public void setXYZ(int x, int y, int z)
	{
		setX(x);
		setY(y);
		setZ(z);
	}
	
	/**
	 * Set the x, y, z coordinates.
	 * @param loc The location.
	 */
	@Override
	public void setXYZ(ILocational loc)
	{
		setXYZ(loc.getX(), loc.getY(), loc.getZ());
	}
	
	/**
	 * Get the heading.
	 * @return the heading
	 */
	@Override
	public int getHeading()
	{
		return this.heading;
	}
	
	/**
	 * Set the heading.
	 * @param heading the heading
	 */
	@Override
	public void setHeading(int heading)
	{
		this.heading = heading;
	}
	
	/**
	 * Get the instance Id.
	 * @return the instance Id
	 */
	@Override
	public int getInstanceId()
	{
		return this.instanceId;
	}
	
	/**
	 * Set the instance Id.
	 * @param instanceId the instance Id to set
	 */
	@Override
	public void setInstanceId(int instanceId)
	{
		this.instanceId = instanceId;
	}
	
	@Override
	public IPositionable getLocation()
	{
		return this;
	}
	
	@Override
	public void setLocation(Location loc)
	{
		this.x = loc.getX();
		this.y = loc.getY();
		this.z = loc.getZ();
		this.heading = loc.getHeading();
		this.instanceId = loc.getInstanceId();
	}
	
	public synchronized void setTricks(MoveTrick[] mt)
	{
		this.tricks = mt;
	}
	
	public MoveTrick[] getTricks()
	{
		return this.tricks;
	}
	
	public void setAll(int x, int y, int z)
	{
		this.x = x;
		this.y = y;
		this.z = z;
	}
	
	public Location geo2world()
	{
		// the size of one block of (16 * 16) points + 8 + 8 is its middle
		this.x = (this.x << 4) + L2World.MAP_MIN_X + 8;
		this.y = (this.y << 4) + L2World.MAP_MIN_Y + 8;
		return this;
	}
	
	public Location world2geo()
	{
		this.x = (this.x - L2World.MAP_MIN_X) >> 4;
		this.y = (this.y - L2World.MAP_MIN_Y) >> 4;
		return this;
	}
	
	public Location correctGeoZ()
	{
		this.z = GeoData.getInstance().getHeight(this.x, this.y, this.z, 0);
		return this;
	}
	
	@Override
	public Location clone()
	{
		return new Location(this.x, this.y, this.z, this.heading, this.instanceId);
	}
	
	public double distance(Location loc)
	{
		return distance(loc.x, loc.y);
	}
	
	public double distance(int x, int y)
	{
		long dx = this.x - x;
		long dy = this.y - y;
		return Math.sqrt((dx * dx) + (dy * dy));
	}
	
	public double distance3D(Location loc)
	{
		return distance3D(loc.x, loc.y, loc.z);
	}
	
	public double distance3D(int x, int y, int z)
	{
		long dx = this.x - x;
		long dy = this.y - y;
		long dz = this.z - z;
		return Math.sqrt((dx * dx) + (dy * dy) + (dz * dz));
	}
	
	public Location set(Location loc)
	{
		this.x = loc.getX();
		this.y = loc.getY();
		this.z = loc.getZ();
		this.heading = loc.heading;
		return this;
	}
	
	public Location set(int x, int y, int z, int h)
	{
		set(x, y, z);
		this.heading = h;
		return this;
	}
	
	public Location set(int x, int y, int z)
	{
		this.x = x;
		this.y = y;
		this.z = z;
		return this;
	}
	
	public Location setH(int h)
	{
		this.heading = h;
		return this;
	}
	
	public boolean equals(Location loc)
	{
		return (loc.getX() == this.x) && (loc.getY() == this.y) && (loc.getZ() == this.z);
	}
	
	public boolean equals(int x, int y, int z)
	{
		return (this.x == x) && (this.y == y) && (this.z == z);
	}
	
	@Override
	public boolean equals(Object obj)
	{
		if ((obj != null) && (obj instanceof Location))
		{
			final Location loc = (Location) obj;
			return (getX() == loc.getX()) && (getY() == loc.getY()) && (getZ() == loc.getZ()) && (getHeading() == loc.getHeading()) && (getInstanceId() == loc.getInstanceId());
		}
		return false;
	}
	
	@Override
	public String toString()
	{
		return "[" + getClass().getSimpleName() + "] X: " + getX() + " Y: " + getY() + " Z: " + getZ() + " Heading: " + this.heading + " InstanceId: " + this.instanceId;
	}
}
