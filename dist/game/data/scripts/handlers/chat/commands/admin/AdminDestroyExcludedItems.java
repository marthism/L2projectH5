/*
 * Copyright (c) 2013 L2jMobius
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR
 * IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package handlers.chat.commands.admin;

import java.util.ArrayList;
import java.util.List;

import org.l2jmobius.gameserver.handler.IAdminCommandHandler;
import org.l2jmobius.gameserver.model.WorldObject;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.item.enums.ItemProcessType;
import org.l2jmobius.gameserver.model.item.instance.Item;
import org.l2jmobius.gameserver.model.itemcontainer.PlayerInventory;
import org.l2jmobius.gameserver.network.serverpackets.InventoryUpdate;
import org.l2jmobius.gameserver.util.DropExclusionUtil;

/**
 * Removes items matching the drop exclusion filter (Common Item, Sealed, Recipe, Design) from the target's (or own) inventory.
 * @author Mobius
 */
public class AdminDestroyExcludedItems implements IAdminCommandHandler
{
	private static final String[] ADMIN_COMMANDS =
	{
		"admin_destroy_excluded_items"
	};

	@Override
	public boolean onCommand(String command, Player activeChar)
	{
		final WorldObject target = activeChar.getTarget();
		final Player player = (target != null) && target.isPlayer() ? target.asPlayer() : activeChar;

		final PlayerInventory inventory = player.getInventory();
		final InventoryUpdate iu = new InventoryUpdate();
		final List<Item> toRemove = new ArrayList<>();
		for (Item item : inventory.getItems())
		{
			if (DropExclusionUtil.isExcluded(item.getId()))
			{
				toRemove.add(item);
			}
		}

		for (Item item : toRemove)
		{
			iu.addRemovedItem(item);
			inventory.destroyItem(ItemProcessType.DESTROY, item, activeChar, null);
		}

		player.sendInventoryUpdate(iu);
		activeChar.sendSysMessage("Removed " + toRemove.size() + " excluded item(s) from " + player.getName() + "'s inventory.");
		return true;
	}

	@Override
	public String[] getCommandList()
	{
		return ADMIN_COMMANDS;
	}
}
