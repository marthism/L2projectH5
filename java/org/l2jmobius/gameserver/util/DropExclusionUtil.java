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
package org.l2jmobius.gameserver.util;

import java.util.HashSet;
import java.util.Set;

import org.l2jmobius.gameserver.config.custom.DropExclusionConfig;
import org.l2jmobius.gameserver.data.xml.ItemData;
import org.l2jmobius.gameserver.model.item.ItemTemplate;

/**
 * Filters out trash-tier crafting drops (Common Item, Sealed gear, Recipes, Designs) by name pattern, regardless of which NPC drop list references them.
 * @author Mobius
 */
public class DropExclusionUtil
{
	private static final String[] EXCLUDED_NAME_PREFIXES =
	{
		"Common Item -",
		"Sealed ",
		"Recipe:",
		"Design:",
	};
	private static final String EXCLUDED_NAME_SUFFIX = " Design";

	private static volatile Set<Integer> excludedItemIds;

	private DropExclusionUtil()
	{
	}

	public static boolean isExcluded(int itemId)
	{
		if (!DropExclusionConfig.ENABLE_DROP_EXCLUSION)
		{
			return false;
		}

		return getExcludedItemIds().contains(itemId);
	}

	private static Set<Integer> getExcludedItemIds()
	{
		Set<Integer> ids = excludedItemIds;
		if (ids == null)
		{
			synchronized (DropExclusionUtil.class)
			{
				ids = excludedItemIds;
				if (ids == null)
				{
					ids = new HashSet<>();
					for (ItemTemplate item : ItemData.getInstance().getAllItems())
					{
						if (item == null)
						{
							continue;
						}

						final String name = item.getName();
						if (name == null)
						{
							continue;
						}

						if (name.endsWith(EXCLUDED_NAME_SUFFIX))
						{
							ids.add(item.getId());
							continue;
						}

						for (String prefix : EXCLUDED_NAME_PREFIXES)
						{
							if (name.startsWith(prefix))
							{
								ids.add(item.getId());
								break;
							}
						}
					}

					excludedItemIds = ids;
				}
			}
		}

		return ids;
	}
}
