package l2r.gameserver.network.clientpackets;

import java.util.Calendar;
import java.util.concurrent.TimeUnit;

import l2r.Config;
import l2r.gameserver.ThreadPoolManager;
import l2r.gameserver.ai.NextAction;
import l2r.gameserver.ai.NextAction.NextActionCallback;
import l2r.gameserver.enums.CtrlEvent;
import l2r.gameserver.enums.CtrlIntention;
import l2r.gameserver.enums.PcCondOverride;
import l2r.gameserver.enums.Race;
import l2r.gameserver.handler.IItemHandler;
import l2r.gameserver.handler.ItemHandler;
import l2r.gameserver.instancemanager.FortSiegeManager;
import l2r.gameserver.model.actor.instance.L2PcInstance;
import l2r.gameserver.model.actor.tasks.player.WeaponEquipTask;
import l2r.gameserver.model.holders.SkillHolder;
import l2r.gameserver.model.itemcontainer.Inventory;
import l2r.gameserver.model.items.L2EtcItem;
import l2r.gameserver.model.items.L2Item;
import l2r.gameserver.model.items.L2Weapon;
import l2r.gameserver.model.items.instance.L2ItemInstance;
import l2r.gameserver.model.items.type.ArmorType;
import l2r.gameserver.model.items.type.WeaponType;
import l2r.gameserver.model.skills.L2Skill;
import l2r.gameserver.model.skills.L2SkillType;
import l2r.gameserver.network.SystemMessageId;
import l2r.gameserver.network.serverpackets.ActionFailed;
import l2r.gameserver.network.serverpackets.ExUseSharedGroupItem;
import l2r.gameserver.network.serverpackets.SystemMessage;
import l2r.gameserver.util.Util;

import gr.sr.configsEngine.configs.impl.SecuritySystemConfigs;
import gr.sr.interf.SunriseEvents;

public final class UseItem extends L2GameClientPacket
{
	private static final String _C__19_USEITEM = "[C] 19 UseItem";
	
	private int _objectId;
	private boolean _ctrlPressed;
	private int _itemId;
	
	@Override
	protected void readImpl()
	{
		_objectId = readD();
		_ctrlPressed = readD() != 0;
	}
	
	@Override
	protected void runImpl()
	{
		final L2PcInstance activeChar = getClient().getActiveChar();
		if (activeChar == null)
		{
			return;
		}
		
		if (Config.DEBUG)
		{
			_log.info(activeChar + ": use item " + _objectId);
		}
		
		// Flood protect UseItem
		if (!getClient().getFloodProtectors().getUseItem().tryPerformAction("use item"))
		{
			return;
		}
		
		if (activeChar.getActiveTradeList() != null)
		{
			activeChar.cancelActiveTrade();
		}
		
		if (activeChar.isInStoreMode())
		{
			activeChar.sendPacket(SystemMessageId.CANNOT_TRADE_DISCARD_DROP_ITEM_WHILE_IN_SHOPMODE);
			activeChar.sendPacket(ActionFailed.STATIC_PACKET);
			return;
		}
		
		final L2ItemInstance item = activeChar.getInventory().getItemByObjectId(_objectId);
		if (item == null)
		{
			return;
		}
		
		if (item.getItem().getType2() == L2Item.TYPE2_QUEST)
		{
			activeChar.sendPacket(SystemMessageId.CANNOT_USE_QUEST_ITEMS);
			return;
		}
		
		// No UseItem is allowed while the player is in special conditions
		if (activeChar.isStunned() || activeChar.isParalyzed() || activeChar.isSleeping() || activeChar.isAfraid() || activeChar.isAlikeDead())
		{
			return;
		}
		
		_itemId = item.getId();
		
		// Char cannot use item when dead
		if (activeChar.isDead() || !activeChar.getInventory().canManipulateWithItemId(_itemId))
		{
			final SystemMessage sm = SystemMessage.getSystemMessage(SystemMessageId.S1_CANNOT_BE_USED);
			sm.addItemName(item);
			activeChar.sendPacket(sm);
			return;
		}
		
		if (SunriseEvents.isInEvent(activeChar))
		{
			if (!SunriseEvents.canUseItem(activeChar, item))
			{
				return;
			}
		}
		
		if (!item.isEquipped() && !item.getItem().checkCondition(activeChar, activeChar, true))
		{
			return;
		}
		
		if (activeChar.getFishingEx().isFishing() && ((_itemId < 6535) || (_itemId > 6540)))
		{
			// You cannot do anything else while fishing
			activeChar.sendPacket(SystemMessageId.CANNOT_DO_WHILE_FISHING_3);
			return;
		}
		
		if (!Config.ALT_GAME_KARMA_PLAYER_CAN_TELEPORT && (activeChar.getKarma() > 0))
		{
			for (SkillHolder sHolder : item.getItem().getSkills())
			{
				L2Skill skill = sHolder.getSkill();
				if ((skill != null) && ((skill.getSkillType() == L2SkillType.TELEPORT) || (skill.getSkillType() == L2SkillType.RECALL)))
				{
					return;
				}
			}
		}
		
		// If the item has reuse time and it has not passed.
		// Message from reuse delay must come from item.
		final int reuseDelay = item.getReuseDelay();
		final int sharedReuseGroup = item.getSharedReuseGroup();
		if (reuseDelay > 0)
		{
			final long reuse = activeChar.getItemRemainingReuseTime(item.getObjectId());
			if (reuse > 0)
			{
				reuseData(activeChar, item, reuse);
				sendSharedGroupUpdate(activeChar, sharedReuseGroup, reuse, reuseDelay);
				return;
			}
			
			final long reuseOnGroup = activeChar.getReuseDelayOnGroup(sharedReuseGroup);
			if (reuseOnGroup > 0)
			{
				reuseData(activeChar, item, reuseOnGroup);
				sendSharedGroupUpdate(activeChar, sharedReuseGroup, reuseOnGroup, reuseDelay);
				return;
			}
		}
		
		if (item.isEquipable())
		{
			// over enchanted item precaution
			if (!activeChar.isGM() && (item.getEnchantLevel() > SecuritySystemConfigs.MAX_ENCHANT_LEVEL) && (SecuritySystemConfigs.MAX_ENCHANT_LEVEL > 0))
			{
				activeChar.sendMessage("You will be kicked for using overenchanted item!");
				Util.handleIllegalPlayerAction(activeChar, "Player " + activeChar.getName() + " has overenchanted item.", Config.DEFAULT_PUNISH);
				return;
			}
			
			// Don't allow to put formal wear while a cursed weapon is equipped.
			if (activeChar.isCursedWeaponEquipped() && (_itemId == 6408))
			{
				return;
			}
			
			// Equip or unEquip
			if (FortSiegeManager.getInstance().isCombat(_itemId))
			{
				return; // no message
			}
			
			if (activeChar.isCombatFlagEquipped())
			{
				return;
			}
			
			switch (item.getItem().getBodyPart())
			{
				case L2Item.SLOT_LR_HAND:
				case L2Item.SLOT_L_HAND:
				case L2Item.SLOT_R_HAND:
				{
					// Prevent players to equip weapon while wearing combat flag
					if ((activeChar.getActiveWeaponItem() != null) && (activeChar.getActiveWeaponItem().getId() == 9819))
					{
						activeChar.sendPacket(SystemMessageId.CANNOT_EQUIP_ITEM_DUE_TO_BAD_CONDITION);
						return;
					}
					
					if (activeChar.isMounted())
					{
						activeChar.sendPacket(SystemMessageId.CANNOT_EQUIP_ITEM_DUE_TO_BAD_CONDITION);
						return;
					}
					if (activeChar.isDisarmed())
					{
						activeChar.sendPacket(SystemMessageId.CANNOT_EQUIP_ITEM_DUE_TO_BAD_CONDITION);
						return;
					}
					
					// Don't allow weapon/shield equipment if a cursed weapon is equipped.
					if (activeChar.isCursedWeaponEquipped())
					{
						return;
					}
					
					// Don't allow other Race to Wear Kamael exclusive Weapons.
					if (!item.isEquipped() && item.isWeapon() && !activeChar.canOverrideCond(PcCondOverride.ITEM_CONDITIONS))
					{
						L2Weapon wpn = (L2Weapon) item.getItem();
						
						switch (activeChar.getRace())
						{
							case KAMAEL:
							{
								switch (wpn.getItemType())
								{
									case NONE:
										activeChar.sendPacket(SystemMessageId.CANNOT_EQUIP_ITEM_DUE_TO_BAD_CONDITION);
										return;
								}
								break;
							}
							case HUMAN:
							case DWARF:
							case ELF:
							case DARK_ELF:
							case ORC:
							{
								switch (wpn.getItemType())
								{
									case RAPIER:
									case CROSSBOW:
									case ANCIENTSWORD:
										activeChar.sendPacket(SystemMessageId.CANNOT_EQUIP_ITEM_DUE_TO_BAD_CONDITION);
										return;
								}
								break;
							}
						}
					}
					break;
				}
				case L2Item.SLOT_CHEST:
				case L2Item.SLOT_BACK:
				case L2Item.SLOT_GLOVES:
				case L2Item.SLOT_FEET:
				case L2Item.SLOT_HEAD:
				case L2Item.SLOT_FULL_ARMOR:
				case L2Item.SLOT_LEGS:
				{
					if ((activeChar.getRace() == Race.KAMAEL) && ((item.getItem().getItemType() == ArmorType.HEAVY) || (item.getItem().getItemType() == ArmorType.MAGIC)))
					{
						activeChar.sendPacket(SystemMessageId.CANNOT_EQUIP_ITEM_DUE_TO_BAD_CONDITION);
						return;
					}
					break;
				}
				case L2Item.SLOT_DECO:
				{
					if (!item.isEquipped() && (activeChar.getInventory().getTalismanSlots() == 0))
					{
						activeChar.sendPacket(SystemMessageId.CANNOT_EQUIP_ITEM_DUE_TO_BAD_CONDITION);
						return;
					}
				}
			}
			
			if (activeChar.isCastingNow() || activeChar.isCastingSimultaneouslyNow())
			{
				// Creating next action class.
				final NextAction nextAction = new NextAction(CtrlEvent.EVT_FINISH_CASTING, CtrlIntention.AI_INTENTION_CAST, (NextActionCallback) () -> activeChar.useEquippableItem(item, true));
				
				// Binding next action to AI.
				activeChar.getAI().setNextAction(nextAction);
			}
			else if (activeChar.isAttackingNow())
			{
				ThreadPoolManager.getInstance().scheduleGeneral(new WeaponEquipTask(item.getObjectId(), activeChar), TimeUnit.MILLISECONDS.convert(activeChar.getAttackEndTime() - System.nanoTime(), TimeUnit.NANOSECONDS));
			}
			else
			{
				activeChar.useEquippableItem(item, true);
			}
		}
		else
		{
			if (activeChar.isCastingNow() && !(item.isPotion() || item.isElixir()))
			{
				return;
			}
			
			final L2Weapon weaponItem = activeChar.getActiveWeaponItem();
			if (((weaponItem != null) && (weaponItem.getItemType() == WeaponType.FISHINGROD)) && (((_itemId >= 6519) && (_itemId <= 6527)) || ((_itemId >= 7610) && (_itemId <= 7613)) || ((_itemId >= 7807) && (_itemId <= 7809)) || ((_itemId >= 8484) && (_itemId <= 8486)) || ((_itemId >= 8505) && (_itemId <= 8513)) || (_itemId == 8548)))
			{
				activeChar.getInventory().setPaperdollItem(Inventory.PAPERDOLL_LHAND, item);
				activeChar.broadcastUserInfo();
				// Send a Server->Client packet ItemList to this L2PcINstance to update left hand equipment.
				activeChar.sendItemList(false);
				return;
			}
			
			final L2EtcItem etcItem = item.getEtcItem();
			final IItemHandler handler = ItemHandler.getInstance().getHandler(etcItem);
			if (handler == null)
			{
				if ((etcItem != null) && (etcItem.getHandlerName() != null))
				{
					_log.warn("Unmanaged Item handler: " + etcItem.getHandlerName() + " for Item Id: " + _itemId + "!");
				}
				else if (Config.DEBUG)
				{
					_log.warn("No Item handler registered for Item Id: " + _itemId + "!");
				}
				return;
			}
			
			// Item reuse time should be added if the item is successfully used.
			// Skill reuse delay is done at handlers.itemhandlers.ItemSkillsTemplate;
			if (handler.useItem(activeChar, item, _ctrlPressed))
			{
				if (reuseDelay > 0)
				{
					activeChar.addTimeStampItem(item, reuseDelay);
					sendSharedGroupUpdate(activeChar, sharedReuseGroup, reuseDelay, reuseDelay);
				}
				else if (item.getItem().isReuseDaily())
				{
					Calendar reUse = Calendar.getInstance();
					reUse.set(Calendar.HOUR_OF_DAY, 6);
					reUse.set(Calendar.MINUTE, 30);
					long reUseDelay = reUse.getTimeInMillis();
					if (reUseDelay < System.currentTimeMillis())
					{
						reUseDelay += 86400000;
					}
					
					int delay = (int) (reUseDelay - System.currentTimeMillis());
					activeChar.addTimeStampItem(item, delay);
					sendSharedGroupUpdate(activeChar, sharedReuseGroup, delay, delay);
				}
			}
		}
		
		if (SunriseEvents.isInEvent(activeChar))
		{
			SunriseEvents.onUseItem(activeChar, item);
		}
	}
	
	private void reuseData(L2PcInstance activeChar, L2ItemInstance item, long remainingTime)
	{
		final int hours = (int) (remainingTime / 3600000L);
		final int minutes = (int) (remainingTime % 3600000L) / 60000;
		final int seconds = (int) ((remainingTime / 1000) % 60);
		final SystemMessage sm;
		if (hours > 0)
		{
			sm = SystemMessage.getSystemMessage(SystemMessageId.S2_HOURS_S3_MINUTES_S4_SECONDS_REMAINING_FOR_REUSE_S1);
			sm.addItemName(item);
			sm.addInt(hours);
			sm.addInt(minutes);
		}
		else if (minutes > 0)
		{
			sm = SystemMessage.getSystemMessage(SystemMessageId.S2_MINUTES_S3_SECONDS_REMAINING_FOR_REUSE_S1);
			sm.addItemName(item);
			sm.addInt(minutes);
		}
		else
		{
			sm = SystemMessage.getSystemMessage(SystemMessageId.S2_SECONDS_REMAINING_FOR_REUSE_S1);
			sm.addItemName(item);
		}
		sm.addInt(seconds);
		activeChar.sendPacket(sm);
	}
	
	private void sendSharedGroupUpdate(L2PcInstance activeChar, int group, long remaining, int reuse)
	{
		if (group > 0)
		{
			activeChar.sendPacket(new ExUseSharedGroupItem(_itemId, group, remaining, reuse));
		}
	}
	
	@Override
	public String getType()
	{
		return _C__19_USEITEM;
	}
	
	@Override
	protected boolean triggersOnActionRequest()
	{
		return !Config.SPAWN_PROTECTION_ALLOWED_ITEMS.contains(_itemId);
	}
}
