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
package custom.MedalDrop;

import java.util.Set;

import org.l2jmobius.commons.util.Rnd;
import org.l2jmobius.gameserver.model.actor.Creature;
import org.l2jmobius.gameserver.model.actor.Npc;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.events.Containers;
import org.l2jmobius.gameserver.model.events.EventType;
import org.l2jmobius.gameserver.model.events.holders.actor.creature.OnCreatureKilled;
import org.l2jmobius.gameserver.model.events.listeners.ConsumerEventListener;
import org.l2jmobius.gameserver.model.script.Script;

/**
 * Drops Silver Medal / Gold Medal on monster kills, scaled by level, zone and raid status.
 * @author Mobius
 */
public class MedalDrop extends Script
{
	private static final int SILVER_MEDAL_ID = 90000;
	private static final int GOLD_MEDAL_ID = 90001;

	private static final int SILVER_MEDAL_MIN_LEVEL = 76;
	private static final int MAX_LEVEL = 85;
	private static final int SILVER_MEDAL_MAX_AMOUNT = 50;

	private static final int GOLD_MEDAL_CHANCE = 5; // Percent, Primeval Isle only.
	private static final int GOLD_MEDAL_MIN_AMOUNT = 1;
	private static final int GOLD_MEDAL_MAX_AMOUNT = 10;

	private static final int RAID_SILVER_MEDAL_MAX_AMOUNT = 1000;
	private static final int RAID_GOLD_MEDAL_MAX_AMOUNT = 50;

	private static final Set<Integer> PRIMEVAL_ISLE_NPCS = Set.of(22196, 22198, 22199, 22202, 22205, 22210, 22213, 22214, 22215, 22216, 22217, 22218, 22219, 22220, 22221, 22222, 22223, 22224, 22225, 22742, 22743, 18344, 18345, 18346);

	private MedalDrop()
	{
		Containers.Global().addListener(new ConsumerEventListener(Containers.Global(), EventType.ON_CREATURE_KILLED, (OnCreatureKilled event) -> onCreatureKilled(event), this));
	}

	private void onCreatureKilled(OnCreatureKilled event)
	{
		final Creature target = event.getTarget();
		if ((target == null) || !target.isAttackable())
		{
			return;
		}

		final Creature killer = event.getAttacker();
		final Player player = (killer == null) ? null : killer.asPlayer();
		if (player == null)
		{
			return;
		}

		final Npc npc = (Npc) target;
		final int level = target.getLevel();

		if (target.isRaid())
		{
			npc.dropItem(player, SILVER_MEDAL_ID, calculateRaidAmount(level, RAID_SILVER_MEDAL_MAX_AMOUNT));
			npc.dropItem(player, GOLD_MEDAL_ID, calculateRaidAmount(level, RAID_GOLD_MEDAL_MAX_AMOUNT));
			return;
		}

		if (PRIMEVAL_ISLE_NPCS.contains(npc.getId()) && (Rnd.get(100) < GOLD_MEDAL_CHANCE))
		{
			npc.dropItem(player, GOLD_MEDAL_ID, Rnd.get(GOLD_MEDAL_MIN_AMOUNT, GOLD_MEDAL_MAX_AMOUNT));
		}

		if (level > 75)
		{
			npc.dropItem(player, SILVER_MEDAL_ID, calculateSilverAmount(level));
		}
	}

	private int calculateSilverAmount(int level)
	{
		final int clampedLevel = Math.min(level, MAX_LEVEL);
		final double ratio = (double) (clampedLevel - SILVER_MEDAL_MIN_LEVEL) / (MAX_LEVEL - SILVER_MEDAL_MIN_LEVEL);
		final int amount = (int) Math.round(SILVER_MEDAL_MAX_AMOUNT - (ratio * (SILVER_MEDAL_MAX_AMOUNT - 1)));
		return Math.max(1, Math.min(SILVER_MEDAL_MAX_AMOUNT, amount));
	}

	private int calculateRaidAmount(int level, int maxAmount)
	{
		final double ratio = Math.min(1.0, (double) level / MAX_LEVEL);
		final int amount = (int) Math.round(1 + (ratio * (maxAmount - 1)));
		return Math.max(1, Math.min(maxAmount, amount));
	}

	public static void main(String[] args)
	{
		new MedalDrop();
	}
}
