package l2r.gameserver.model;

import l2r.gameserver.model.buylist.Product;
import l2r.gameserver.model.items.L2Item;
import l2r.gameserver.model.items.instance.L2ItemInstance;

/**
 * Get all information from L2ItemInstance to generate ItemInfo.
 */
public class ItemInfo
{
	/** Identifier of the L2ItemInstance */
	private int _objectId;
	
	/** The L2Item template of the L2ItemInstance */
	private L2Item _item;
	
	/** The level of enchant on the L2ItemInstance */
	private int _enchant;
	
	/** The augmentation of the item */
	private int _augmentation;
	
	/** The quantity of L2ItemInstance */
	private long _count;
	
	/** The price of the L2ItemInstance */
	private int _price;
	
	/** The custom L2ItemInstance types (used loto, race tickets) */
	private int _type1;
	private int _type2;
	
	/** If True the L2ItemInstance is equipped */
	private int _equipped;
	
	/** The action to do clientside (1=ADD, 2=MODIFY, 3=REMOVE) */
	private int _change;
	
	/** The mana of this item */
	private int _mana;
	private int _time;
	
	private int _location;
	
	private int _elemAtkType = -2;
	private int _elemAtkPower = 0;
	private final int[] _elemDefAttr =
	{
		0,
		0,
		0,
		0,
		0,
		0
	};
	
	private int[] _option = L2ItemInstance.DEFAULT_ENCHANT_OPTIONS;
	
	/**
	 * Get all information from L2ItemInstance to generate ItemInfo.
	 * @param item
	 */
	public ItemInfo(L2ItemInstance item)
	{
		if (item == null)
		{
			return;
		}
		
		// Get the Identifier of the L2ItemInstance
		_objectId = item.getObjectId();
		
		// Get the L2Item of the L2ItemInstance
		_item = item.getItem();
		
		// Get the enchant level of the L2ItemInstance
		_enchant = item.getEnchantLevel();
		
		// Get the augmentation boni
		if (item.isAugmented())
		{
			_augmentation = item.getAugmentation().getAugmentationId();
		}
		else
		{
			_augmentation = 0;
		}
		
		// Get the quantity of the L2ItemInstance
		_count = item.getCount();
		
		// Get custom item types (used loto, race tickets)
		_type1 = item.getCustomType1();
		_type2 = item.getCustomType2();
		
		// Verify if the L2ItemInstance is equipped
		_equipped = item.isEquipped() ? 1 : 0;
		
		// Get the action to do clientside
		switch (item.getLastChange())
		{
			case (L2ItemInstance.ADDED):
			{
				_change = 1;
				break;
			}
			case (L2ItemInstance.MODIFIED):
			{
				_change = 2;
				break;
			}
			case (L2ItemInstance.REMOVED):
			{
				_change = 3;
				break;
			}
		}
		
		// Get shadow item mana
		_mana = item.getMana();
		_time = item.isTimeLimitedItem() ? (int) (item.getRemainingTime() / 1000) : -9999;
		_location = item.getLocationSlot();
		
		_elemAtkType = item.getAttackElementType();
		_elemAtkPower = item.getAttackElementPower();
		for (byte i = 0; i < 6; i++)
		{
			_elemDefAttr[i] = item.getElementDefAttr(i);
		}
		_option = item.getEnchantOptions();
	}
	
	public ItemInfo(L2ItemInstance item, int change)
	{
		if (item == null)
		{
			return;
		}
		
		// Get the Identifier of the L2ItemInstance
		_objectId = item.getObjectId();
		
		// Get the L2Item of the L2ItemInstance
		_item = item.getItem();
		
		// Get the enchant level of the L2ItemInstance
		_enchant = item.getEnchantLevel();
		
		// Get the augmentation boni
		if (item.isAugmented())
		{
			_augmentation = item.getAugmentation().getAugmentationId();
		}
		else
		{
			_augmentation = 0;
		}
		
		// Get the quantity of the L2ItemInstance
		_count = item.getCount();
		
		// Get custom item types (used loto, race tickets)
		_type1 = item.getCustomType1();
		_type2 = item.getCustomType2();
		
		// Verify if the L2ItemInstance is equipped
		_equipped = item.isEquipped() ? 1 : 0;
		
		// Get the action to do clientside
		_change = change;
		
		// Get shadow item mana
		_mana = item.getMana();
		_time = item.isTimeLimitedItem() ? (int) (item.getRemainingTime() / 1000) : -9999;
		
		_location = item.getLocationSlot();
		
		_elemAtkType = item.getAttackElementType();
		_elemAtkPower = item.getAttackElementPower();
		for (byte i = 0; i < 6; i++)
		{
			_elemDefAttr[i] = item.getElementDefAttr(i);
		}
		
		_option = item.getEnchantOptions();
	}
	
	public ItemInfo(TradeItem item)
	{
		if (item == null)
		{
			return;
		}
		
		// Get the Identifier of the L2ItemInstance
		_objectId = item.getObjectId();
		
		// Get the L2Item of the L2ItemInstance
		_item = item.getItem();
		
		// Get the enchant level of the L2ItemInstance
		_enchant = item.getEnchant();
		
		// Get the augmentation boni
		_augmentation = 0;
		
		// Get the quantity of the L2ItemInstance
		_count = item.getCount();
		
		// Get custom item types (used loto, race tickets)
		_type1 = item.getCustomType1();
		_type2 = item.getCustomType2();
		
		// Verify if the L2ItemInstance is equipped
		_equipped = 0;
		
		// Get the action to do clientside
		_change = 0;
		
		// Get shadow item mana
		_mana = -1;
		_time = -9999;
		
		_location = item.getLocationSlot();
		
		_elemAtkType = item.getAttackElementType();
		_elemAtkPower = item.getAttackElementPower();
		for (byte i = 0; i < 6; i++)
		{
			_elemDefAttr[i] = item.getElementDefAttr(i);
		}
		
		_option = item.getEnchantOptions();
	}
	
	public ItemInfo(Product item)
	{
		if (item == null)
		{
			return;
		}
		
		// Get the Identifier of the L2ItemInstance
		_objectId = 0;
		
		// Get the L2Item of the L2ItemInstance
		_item = item.getItem();
		
		// Get the enchant level of the L2ItemInstance
		_enchant = 0;
		
		// Get the augmentation bonus
		_augmentation = 0;
		
		// Get the quantity of the L2ItemInstance
		_count = item.getCount();
		
		// Get custom item types (used loto, race tickets)
		_type1 = item.getItem().getType1();
		_type2 = item.getItem().getType2();
		
		// Verify if the L2ItemInstance is equipped
		_equipped = 0;
		
		// Get the action to do clientside
		_change = 0;
		
		// Get shadow item mana
		_mana = -1;
		_time = -9999;
		
		_location = 0;
	}
	
	public int getObjectId()
	{
		return _objectId;
	}
	
	public L2Item getItem()
	{
		return _item;
	}
	
	public int getEnchant()
	{
		return _enchant;
	}
	
	public int getAugmentationBonus()
	{
		return _augmentation;
	}
	
	public long getCount()
	{
		return _count;
	}
	
	public int getPrice()
	{
		return _price;
	}
	
	public int getCustomType1()
	{
		return _type1;
	}
	
	public int getCustomType2()
	{
		return _type2;
	}
	
	public int getEquipped()
	{
		return _equipped;
	}
	
	public int getChange()
	{
		return _change;
	}
	
	public int getMana()
	{
		return _mana;
	}
	
	public int getTime()
	{
		return _time;
	}
	
	public int getLocation()
	{
		return _location;
	}
	
	public int getAttackElementType()
	{
		return _elemAtkType;
	}
	
	public int getAttackElementPower()
	{
		return _elemAtkPower;
	}
	
	public int getElementDefAttr(byte i)
	{
		return _elemDefAttr[i];
	}
	
	public int[] getEnchantOptions()
	{
		return _option;
	}
}
