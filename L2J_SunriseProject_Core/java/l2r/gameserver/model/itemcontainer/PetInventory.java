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
package l2r.gameserver.model.itemcontainer;

import l2r.gameserver.data.xml.impl.ItemData;
import l2r.gameserver.enums.BroadcastStatusType;
import l2r.gameserver.enums.ItemLocation;
import l2r.gameserver.model.actor.instance.L2PetInstance;
import l2r.gameserver.model.items.L2Item;
import l2r.gameserver.model.items.instance.L2ItemInstance;

public class PetInventory extends Inventory
{
	private final L2PetInstance _owner;
	
	public PetInventory(L2PetInstance owner)
	{
		_owner = owner;
	}
	
	@Override
	public L2PetInstance getOwner()
	{
		return _owner;
	}
	
	@Override
	public int getOwnerId()
	{
		// gets the L2PcInstance-owner's ID
		int id;
		try
		{
			id = _owner.getOwner().getObjectId();
		}
		catch (NullPointerException e)
		{
			return 0;
		}
		return id;
	}
	
	/**
	 * Refresh the weight of equipment loaded
	 */
	@Override
	protected void refreshWeight()
	{
		super.refreshWeight();
		getOwner().updateAndBroadcastStatus(BroadcastStatusType.DEFAULT);
	}
	
	public boolean validateCapacity(L2ItemInstance item)
	{
		int slots = 0;
		
		if (!(item.isStackable() && (getItemByItemId(item.getId()) != null)) && !item.getItem().hasExImmediateEffect())
		{
			slots++;
		}
		
		return validateCapacity(slots);
	}
	
	@Override
	public boolean validateCapacity(long slots)
	{
		return ((_items.size() + slots) <= _owner.getInventoryLimit());
	}
	
	public boolean validateWeight(L2ItemInstance item, long count)
	{
		int weight = 0;
		L2Item template = ItemData.getInstance().getTemplate(item.getId());
		if (template == null)
		{
			return false;
		}
		weight += count * template.getWeight();
		return validateWeight(weight);
	}
	
	@Override
	public boolean validateWeight(long weight)
	{
		return ((_totalWeight + weight) <= _owner.getMaxLoad());
	}
	
	@Override
	protected ItemLocation getBaseLocation()
	{
		return ItemLocation.PET;
	}
	
	@Override
	protected ItemLocation getEquipLocation()
	{
		return ItemLocation.PET_EQUIP;
	}
	
	@Override
	public void restore()
	{
		super.restore();
		// check for equiped items from other pets
		for (L2ItemInstance item : _items)
		{
			if (item.isEquipped())
			{
				if (!item.getItem().checkCondition(getOwner(), getOwner(), false))
				{
					unEquipItemInSlot(item.getLocationSlot());
				}
			}
		}
	}
	
	public void transferItemsToOwner()
	{
		for (L2ItemInstance item : _items)
		{
			getOwner().transferItem("return", item.getObjectId(), item.getCount(), getOwner().getOwner().getInventory(), getOwner().getOwner(), getOwner());
		}
	}
}
