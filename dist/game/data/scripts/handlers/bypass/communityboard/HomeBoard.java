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
package handlers.bypass.communityboard;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.commons.threads.ThreadPool;
import org.l2jmobius.gameserver.cache.HtmCache;
import org.l2jmobius.gameserver.config.custom.CommunityBoardConfig;
import org.l2jmobius.gameserver.config.custom.PremiumSystemConfig;
import org.l2jmobius.gameserver.config.custom.SchemeBufferConfig;
import org.l2jmobius.gameserver.data.SchemeBufferTable;
import org.l2jmobius.gameserver.data.sql.ClanTable;
import org.l2jmobius.gameserver.data.xml.BuyListData;
import org.l2jmobius.gameserver.data.xml.ExperienceData;
import org.l2jmobius.gameserver.data.xml.MultisellData;
import org.l2jmobius.gameserver.data.xml.EnchantItemData;
import org.l2jmobius.gameserver.data.xml.OptionData;
import org.l2jmobius.gameserver.data.xml.SkillData;
import org.l2jmobius.gameserver.data.xml.SkillTreeData;
import org.l2jmobius.gameserver.handler.CommunityBoardHandler;
import org.l2jmobius.gameserver.handler.IParseBoardHandler;
import org.l2jmobius.gameserver.data.xml.NpcData;
import org.l2jmobius.gameserver.managers.PcCafePointsManager;
import org.l2jmobius.gameserver.managers.PremiumManager;
import org.l2jmobius.gameserver.managers.RaidBossSpawnManager;
import org.l2jmobius.gameserver.model.StatSet;
import org.l2jmobius.gameserver.model.actor.enums.npc.RaidBossStatus;
import org.l2jmobius.gameserver.model.actor.templates.NpcTemplate;
import org.l2jmobius.gameserver.model.spawns.Spawn;
import org.l2jmobius.gameserver.model.actor.Creature;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.Summon;
import org.l2jmobius.gameserver.model.actor.holders.npc.BuffSkillHolder;
import org.l2jmobius.gameserver.model.item.enchant.EnchantResultType;
import org.l2jmobius.gameserver.model.item.enchant.EnchantScroll;
import org.l2jmobius.gameserver.model.item.enums.ItemProcessType;
import org.l2jmobius.gameserver.model.item.instance.Item;
import org.l2jmobius.gameserver.model.options.OptionSkillHolder;
import org.l2jmobius.gameserver.model.options.Options;
import org.l2jmobius.gameserver.model.skill.BuffInfo;
import org.l2jmobius.gameserver.model.skill.Skill;
import org.l2jmobius.gameserver.model.skill.enums.AcquireSkillType;
import org.l2jmobius.gameserver.model.skill.holders.SkillLearn;
import org.l2jmobius.gameserver.config.PlayerConfig;
import org.l2jmobius.gameserver.data.AugmentationData;
import org.l2jmobius.gameserver.data.enums.CategoryType;
import org.l2jmobius.gameserver.data.xml.ClassListData;
import org.l2jmobius.gameserver.model.item.Armor;
import org.l2jmobius.gameserver.model.item.Weapon;
import org.l2jmobius.gameserver.model.item.enums.BodyPart;
import org.l2jmobius.gameserver.model.item.type.CrystalType;
import org.l2jmobius.gameserver.model.options.Augmentation;
import org.l2jmobius.gameserver.network.serverpackets.InventoryUpdate;
import org.l2jmobius.gameserver.model.olympiad.OlympiadManager;
import org.l2jmobius.gameserver.model.actor.enums.player.PlayerClass;
import org.l2jmobius.gameserver.model.actor.holders.player.SubClassHolder;
import org.l2jmobius.gameserver.model.actor.instance.VillageMaster;
import org.l2jmobius.gameserver.model.script.QuestState;
import org.l2jmobius.gameserver.model.zone.ZoneId;
import org.l2jmobius.gameserver.network.SystemMessageId;
import org.l2jmobius.gameserver.network.serverpackets.AcquireSkillList;
import org.l2jmobius.gameserver.network.serverpackets.BuyList;
import org.l2jmobius.gameserver.network.serverpackets.ExBuySellList;
import org.l2jmobius.gameserver.network.serverpackets.HennaEquipList;
import org.l2jmobius.gameserver.network.serverpackets.HennaRemoveList;
import org.l2jmobius.gameserver.network.serverpackets.MagicSkillUse;
import org.l2jmobius.gameserver.network.serverpackets.ShowBoard;
import org.l2jmobius.gameserver.network.serverpackets.SystemMessage;

/**
 * Home board.
 * @author Zoey76, Mobius
 */
public class HomeBoard implements IParseBoardHandler
{
	// SQL Queries
	private static final String COUNT_FAVORITES = "SELECT COUNT(*) AS favorites FROM `bbs_favorites` WHERE `playerId`=?";
	private static final String NAVIGATION_PATH = "data/html/CommunityBoard/Custom/navigation.html";
	
	private static final String[] COMMANDS =
	{
		"_bbshome",
		"_bbstop",
	};
	
	private static final String[] CUSTOM_COMMANDS =
	{
		PremiumSystemConfig.PREMIUM_SYSTEM_ENABLED && CommunityBoardConfig.COMMUNITY_PREMIUM_SYSTEM_ENABLED ? "_bbspremium" : null,
		CommunityBoardConfig.COMMUNITYBOARD_ENABLE_MULTISELLS ? "_bbsexcmultisell" : null,
		CommunityBoardConfig.COMMUNITYBOARD_ENABLE_MULTISELLS ? "_bbsmultisell" : null,
		CommunityBoardConfig.COMMUNITYBOARD_ENABLE_MULTISELLS ? "_bbssell" : null,
		CommunityBoardConfig.COMMUNITYBOARD_ENABLE_TELEPORTS ? "_bbsteleport" : null,
		CommunityBoardConfig.COMMUNITYBOARD_ENABLE_BUFFS ? "_bbsbuff" : null,
		CommunityBoardConfig.COMMUNITYBOARD_ENABLE_BUFFS ? "_bbsbufflist" : null,
		CommunityBoardConfig.COMMUNITYBOARD_ENABLE_BUFFS ? "_bbscastbuff" : null,
		CommunityBoardConfig.COMMUNITYBOARD_ENABLE_BUFFS ? "_bbspreset" : null,
		CommunityBoardConfig.COMMUNITYBOARD_ENABLE_BUFFS ? "_bbscleanup" : null,
		CommunityBoardConfig.COMMUNITYBOARD_ENABLE_HEAL ? "_bbsheal" : null,
		CommunityBoardConfig.COMMUNITYBOARD_ENABLE_BUFFS ? "_bbsscheme" : null,
		CommunityBoardConfig.COMMUNITYBOARD_ENABLE_DELEVEL ? "_bbsdelevel" : null,
		"_bbssubclass",
		"_bbsblacksmith",
		"_bbssymbol",
		"_bbsraidboss",
		"_bbsmassenchant",
		"_bbsaugmentpick"
	};
	
	// Preset buff packages, casted with a single click from the buffer main page.
	// Skill levels come from SchemeBufferSkills.xml (best +30 enchant routes).
	private static final int[] PRESET_FIGHTER =
	{
		// @formatter:off
		1204, 1086, 1068, 1388, 1077, 1242, 1240, 1045, 1048, 1062, 1268,
		1040, 1389, 1036, 1035, 1044, 1243, 1087, 1363, 1356,
		1352, 1353, 1354, 1032, 1259, 1033,
		271, 274, 275, 272, 277, 310,
		264, 269, 268, 304, 364, 349, 266, 267, 265,
		// @formatter:on
	};

	private static final int[] PRESET_TANKER =
	{
		// @formatter:off
		1204, 1086, 1068, 1388, 1040, 1389, 1036, 1035, 1045, 1048, 1243,
		1304, 1044, 1268, 1077, 1363, 1356,
		1352, 1353, 1354, 1032, 1259, 1033, 1182, 1189, 1191, 1392, 1393,
		311, 307, 309, 275,
		264, 304, 265, 267, 305, 306, 308, 270, 266,
		// @formatter:on
	};

	private static final int[] PRESET_SORCERER =
	{
		// @formatter:off
		1204, 1085, 1059, 1062, 1078, 1303, 1036, 1035, 1048, 1045, 1397,
		1355, 1363, 1040, 1389, 1044,
		1352, 1353, 1354, 1259, 1033, 1392, 1393,
		273, 276, 365, 530,
		349, 363, 264, 267, 304, 266, 529,
		// @formatter:on
	};

	private static final BiPredicate<String, Player> COMBAT_CHECK = (command, player) ->
	{
		boolean commandCheck = false;
		for (String c : CUSTOM_COMMANDS)
		{
			if ((c != null) && command.startsWith(c))
			{
				commandCheck = true;
				break;
			}
		}
		
		return commandCheck && (player.isCastingNow() || player.isCastingSimultaneouslyNow() || player.isInCombat() || player.isInDuel() || player.isInOlympiadMode() || player.isInsideZone(ZoneId.SIEGE) || player.isInsideZone(ZoneId.PVP) || (player.getPvpFlag() > 0) || player.isAlikeDead() || player.isOnEvent() || player.isInStoreMode());
	};
	
	private static final Predicate<Player> KARMA_CHECK = player -> CommunityBoardConfig.COMMUNITYBOARD_KARMA_DISABLED && (player.getKarma() > 0);
	
	@Override
	public String[] getCommandList()
	{
		final List<String> commands = new ArrayList<>();
		commands.addAll(Arrays.asList(COMMANDS));
		commands.addAll(Arrays.asList(CUSTOM_COMMANDS));
		return commands.stream().filter(Objects::nonNull).toArray(String[]::new);
	}
	
	@Override
	public boolean onCommand(String command, Player player)
	{
		// Old custom conditions check move to here
		if (CommunityBoardConfig.COMMUNITYBOARD_COMBAT_DISABLED && COMBAT_CHECK.test(command, player))
		{
			player.sendMessage("You can't use the Community Board right now.");
			return false;
		}
		
		if (KARMA_CHECK.test(player))
		{
			player.sendMessage("Players with Karma cannot use the Community Board.");
			return false;
		}
		
		if (CommunityBoardConfig.COMMUNITYBOARD_PEACE_ONLY && !player.isInsideZone(ZoneId.PEACE))
		{
			player.sendMessage("Community Board cannot be used out of peace zone.");
			return false;
		}
		
		String returnHtml = null;
		String navigation = null;
		
		if (CommunityBoardConfig.CUSTOM_CB_ENABLED)
		{
			navigation = HtmCache.getInstance().getHtm(player, NAVIGATION_PATH);
		}
		
		if (command.equals("_bbshome") || command.equals("_bbstop"))
		{
			final String customPath = CommunityBoardConfig.CUSTOM_CB_ENABLED ? "Custom/" : "";
			CommunityBoardHandler.getInstance().addBypass(player, "Home", command);
			returnHtml = HtmCache.getInstance().getHtm(player, "data/html/CommunityBoard/" + customPath + "home.html");
			if (!CommunityBoardConfig.CUSTOM_CB_ENABLED)
			{
				returnHtml = returnHtml.replace("%fav_count%", Integer.toString(getFavoriteCount(player)));
				returnHtml = returnHtml.replace("%region_count%", Integer.toString(getRegionCount(player)));
				returnHtml = returnHtml.replace("%clan_count%", Integer.toString(ClanTable.getInstance().getClanCount()));
			}
		}
		else if (command.startsWith("_bbstop;"))
		{
			final String customPath = CommunityBoardConfig.CUSTOM_CB_ENABLED ? "Custom/" : "";
			final String path = command.replace("_bbstop;", "");
			if ((path.length() > 0) && path.endsWith(".html"))
			{
				returnHtml = HtmCache.getInstance().getHtm(player, "data/html/CommunityBoard/" + customPath + path);
			}
		}
		else if (command.startsWith("_bbsmultisell"))
		{
			final String fullBypass = command.replace("_bbsmultisell;", "");
			final String[] buypassOptions = fullBypass.split(",");
			final int multisellId = Integer.parseInt(buypassOptions[0]);
			final String page = buypassOptions[1];
			returnHtml = HtmCache.getInstance().getHtm(player, "data/html/CommunityBoard/Custom/" + page + ".html");
			ThreadPool.schedule(() -> MultisellData.getInstance().separateAndSend(multisellId, player, null, false), 100);
		}
		else if (command.startsWith("_bbsexcmultisell"))
		{
			final String fullBypass = command.replace("_bbsexcmultisell;", "");
			final String[] buypassOptions = fullBypass.split(",");
			final int multisellId = Integer.parseInt(buypassOptions[0]);
			final String page = buypassOptions[1];
			returnHtml = HtmCache.getInstance().getHtm(player, "data/html/CommunityBoard/Custom/" + page + ".html");
			ThreadPool.schedule(() -> MultisellData.getInstance().separateAndSend(multisellId, player, null, true), 100);
		}
		else if (command.startsWith("_bbssell"))
		{
			final String page = command.replace("_bbssell;", "");
			returnHtml = HtmCache.getInstance().getHtm(player, "data/html/CommunityBoard/Custom/" + page + ".html");
			ThreadPool.schedule(() ->
			{
				player.sendPacket(new BuyList(BuyListData.getInstance().getBuyList(423), player.getAdena(), 0));
				player.sendPacket(new ExBuySellList(player, false));
			}, 100);
		}
		else if (command.startsWith("_bbsteleport"))
		{
			final String teleBuypass = command.replace("_bbsteleport;", "");
			if (player.getInventory().getInventoryItemCount(CommunityBoardConfig.COMMUNITYBOARD_CURRENCY, -1) < CommunityBoardConfig.COMMUNITYBOARD_TELEPORT_PRICE)
			{
				player.sendMessage("Not enough currency!");
			}
			else if (CommunityBoardConfig.COMMUNITY_AVAILABLE_TELEPORTS.get(teleBuypass) != null)
			{
				player.disableAllSkills();
				player.sendPacket(new ShowBoard());
				player.destroyItemByItemId(ItemProcessType.FEE, CommunityBoardConfig.COMMUNITYBOARD_CURRENCY, CommunityBoardConfig.COMMUNITYBOARD_TELEPORT_PRICE, player, true);
				player.setIn7sDungeon(false);
				player.setInstanceId(0);
				player.teleToLocation(CommunityBoardConfig.COMMUNITY_AVAILABLE_TELEPORTS.get(teleBuypass), 0);
				ThreadPool.schedule(player::enableAllSkills, 3000);
			}
		}
		else if (command.startsWith("_bbsbuff;"))
		{
			final String fullBypass = command.replace("_bbsbuff;", "");
			final String[] buypassOptions = fullBypass.split(";");
			final int buffCount = buypassOptions.length - 1;
			final String page = buypassOptions[buffCount];
			if (player.getInventory().getInventoryItemCount(CommunityBoardConfig.COMMUNITYBOARD_CURRENCY, -1) < (CommunityBoardConfig.COMMUNITYBOARD_BUFF_PRICE * buffCount))
			{
				player.sendMessage("Not enough currency!");
			}
			else
			{
				player.destroyItemByItemId(ItemProcessType.FEE, CommunityBoardConfig.COMMUNITYBOARD_CURRENCY, CommunityBoardConfig.COMMUNITYBOARD_BUFF_PRICE * buffCount, player, true);
				final Summon pet = player.getSummon();
				final List<Creature> targets = new ArrayList<>(4);
				targets.add(player);
				if (pet != null)
				{
					targets.add(pet);
				}
				
				for (int i = 0; i < buffCount; i++)
				{
					final Skill skill = SkillData.getInstance().getSkill(Integer.parseInt(buypassOptions[i].split(",")[0]), Integer.parseInt(buypassOptions[i].split(",")[1]));
					if (!CommunityBoardConfig.COMMUNITY_AVAILABLE_BUFFS.contains(skill.getId()))
					{
						continue;
					}
					
					for (Creature target : targets)
					{
						applyBuff(player, target, skill);
						if (CommunityBoardConfig.COMMUNITYBOARD_CAST_ANIMATIONS)
						{
							player.sendPacket(new MagicSkillUse(player, target, skill.getId(), skill.getLevel(), skill.getHitTime(), skill.getReuseDelay()));

							// not recommend broadcast
							// player.broadcastPacket(new MagicSkillUse(player, target, skill.getId(), skill.getLevel(), skill.getHitTime(), skill.getReuseDelay()));
						}
					}
				}

				for (Creature target : targets)
				{
					target.getEffectList().updateEffectIcons(false);
				}
			}
			
			returnHtml = HtmCache.getInstance().getHtm(player, "data/html/CommunityBoard/Custom/" + page + ".html");
		}
		else if (command.startsWith("_bbspreset;"))
		{
			final String[] parts = command.split(";");
			if (parts.length >= 2)
			{
				final int[] presetSkillIds;
				final String presetName;
				switch (parts[1].toLowerCase())
				{
					case "fighter":
					{
						presetSkillIds = PRESET_FIGHTER;
						presetName = "Fighter";
						break;
					}
					case "tanker":
					{
						presetSkillIds = PRESET_TANKER;
						presetName = "Tanker";
						break;
					}
					case "sorcerer":
					{
						presetSkillIds = PRESET_SORCERER;
						presetName = "Sorcerer";
						break;
					}
					default:
					{
						presetSkillIds = null;
						presetName = null;
						break;
					}
				}

				if (presetSkillIds != null)
				{
					final SchemeBufferTable table = SchemeBufferTable.getInstance();
					for (int skillId : presetSkillIds)
					{
						final BuffSkillHolder holder = table.getAvailableBuff(skillId);
						final Skill skill = holder != null ? SkillData.getInstance().getSkill(skillId, holder.getLevel()) : null;
						if (skill != null)
						{
							applyBuff(player, player, skill);
						}
					}
					player.getEffectList().updateEffectIcons(false);
					player.sendMessage("Pacote " + presetName + " aplicado com sucesso!");
				}
			}
			returnHtml = HtmCache.getInstance().getHtm(player, "data/html/CommunityBoard/Custom/buffer/main.html");
		}
		else if (command.startsWith("_bbscastbuff;"))
		{
			final String[] parts = command.split(";");
			if (parts.length >= 4)
			{
				final String category = parts[1];
				final int skillId = Integer.parseInt(parts[2]);
				final int page = Integer.parseInt(parts[3]);
				if (category.toUpperCase().startsWith("VIP") && !player.hasPremiumStatus())
				{
					player.sendMessage("Esta categoria e exclusiva para contas VIP.");
				}
				else
				{
					final BuffSkillHolder holder = SchemeBufferTable.getInstance().getAvailableBuff(category, skillId);
					final Skill skill = holder != null ? SkillData.getInstance().getSkill(skillId, holder.getLevel()) : null;
					if (skill != null)
					{
						applyBuff(player, player, skill);
						player.getEffectList().updateEffectIcons(false);
					}
				}
				returnHtml = buildBuffCategory(player, category, page);
			}
		}
		else if (command.equals("_bbscleanup"))
		{
			player.stopAllEffects();
			final Summon summon = player.getSummon();
			if (summon != null)
			{
				summon.stopAllEffects();
			}
			returnHtml = HtmCache.getInstance().getHtm(player, "data/html/CommunityBoard/Custom/buffer/main.html");
		}
		else if (command.startsWith("_bbsbufflist;"))
		{
			final String[] parts = command.split(";");
			returnHtml = buildBuffCategory(player, parts[1], (parts.length > 2) ? Integer.parseInt(parts[2]) : 1);
		}
		else if (command.startsWith("_bbsheal"))
		{
			final String page = command.replace("_bbsheal;", "");
			if (player.getInventory().getInventoryItemCount(CommunityBoardConfig.COMMUNITYBOARD_CURRENCY, -1) < (CommunityBoardConfig.COMMUNITYBOARD_HEAL_PRICE))
			{
				player.sendMessage("Not enough currency!");
			}
			else
			{
				player.destroyItemByItemId(ItemProcessType.FEE, CommunityBoardConfig.COMMUNITYBOARD_CURRENCY, CommunityBoardConfig.COMMUNITYBOARD_HEAL_PRICE, player, true);
				player.setCurrentHp(player.getMaxHp());
				player.setCurrentMp(player.getMaxMp());
				player.setCurrentCp(player.getMaxCp());
				if (player.hasSummon())
				{
					player.getSummon().setCurrentHp(player.getSummon().getMaxHp());
					player.getSummon().setCurrentMp(player.getSummon().getMaxMp());
					player.getSummon().setCurrentCp(player.getSummon().getMaxCp());
				}
				
				player.updateUserInfo();
				player.sendMessage("You used heal!");
			}
			
			returnHtml = HtmCache.getInstance().getHtm(player, "data/html/CommunityBoard/Custom/" + page + ".html");
		}
		else if (command.startsWith("_bbsscheme"))
		{
			returnHtml = handleSchemeCommand(command, player);
		}
		else if (command.startsWith("_bbssubclass"))
		{
			returnHtml = handleSubclassCommand(command, player);
		}
		else if (command.startsWith("_bbsblacksmith"))
		{
			returnHtml = handleBlacksmithCommand(command, player);
		}
		else if (command.startsWith("_bbsraidboss"))
		{
			returnHtml = handleRaidBossCommand(command, player);
		}
		else if (command.startsWith("_bbsmassenchant"))
		{
			returnHtml = handleMassEnchantCommand(command, player);
		}
		else if (command.startsWith("_bbsaugmentpick"))
		{
			returnHtml = handleAugmentPickCommand(command, player);
		}
		else if (command.startsWith("_bbssymbol"))
		{
			final String[] symbolParts = command.split(";");
			if ((symbolParts.length > 1) && symbolParts[1].equals("remove"))
			{
				player.sendPacket(new HennaRemoveList(player));
			}
			else
			{
				player.sendPacket(new HennaEquipList(player));
			}
			returnHtml = HtmCache.getInstance().getHtm(player, "data/html/CommunityBoard/Custom/symbolmaker/main.html");
		}
		else if (command.equals("_bbsdelevel"))
		{
			if (player.getInventory().getInventoryItemCount(CommunityBoardConfig.COMMUNITYBOARD_CURRENCY, -1) < CommunityBoardConfig.COMMUNITYBOARD_DELEVEL_PRICE)
			{
				player.sendMessage("Not enough currency!");
			}
			else if (player.getLevel() == 1)
			{
				player.sendMessage("You are at minimum level!");
			}
			else
			{
				player.destroyItemByItemId(ItemProcessType.FEE, CommunityBoardConfig.COMMUNITYBOARD_CURRENCY, CommunityBoardConfig.COMMUNITYBOARD_DELEVEL_PRICE, player, true);
				final int newLevel = player.getLevel() - 1;
				player.setExp(ExperienceData.getInstance().getExpForLevel(newLevel));
				player.getStat().setLevel((byte) newLevel);
				player.setCurrentHpMp(player.getMaxHp(), player.getMaxMp());
				player.setCurrentCp(player.getMaxCp());
				player.broadcastUserInfo();
				player.checkPlayerSkills(); // Adjust skills according to new level.
				returnHtml = HtmCache.getInstance().getHtm(player, "data/html/CommunityBoard/Custom/delevel/complete.html");
			}
		}
		else if (command.startsWith("_bbspremium"))
		{
			final String fullBypass = command.replace("_bbspremium;", "");
			final String[] buypassOptions = fullBypass.split(",");
			final int premiumDays = Integer.parseInt(buypassOptions[0]);
			if ((premiumDays < 1) || (premiumDays > 30) || (player.getInventory().getInventoryItemCount(CommunityBoardConfig.COMMUNITY_PREMIUM_COIN_ID, -1) < (CommunityBoardConfig.COMMUNITY_PREMIUM_PRICE_PER_DAY * premiumDays)))
			{
				player.sendMessage("Not enough currency!");
			}
			else
			{
				player.destroyItemByItemId(ItemProcessType.FEE, CommunityBoardConfig.COMMUNITY_PREMIUM_COIN_ID, CommunityBoardConfig.COMMUNITY_PREMIUM_PRICE_PER_DAY * premiumDays, player, true);
				PremiumManager.getInstance().addPremiumTime(player.getAccountName(), premiumDays, TimeUnit.DAYS);
				player.sendMessage("Your account will now have premium status until " + new SimpleDateFormat("dd.MM.yyyy HH:mm").format(PremiumManager.getInstance().getPremiumExpiration(player.getAccountName())) + ".");
				if (PremiumSystemConfig.PC_CAFE_RETAIL_LIKE)
				{
					PcCafePointsManager.getInstance().run(player);
				}
				
				returnHtml = HtmCache.getInstance().getHtm(player, "data/html/CommunityBoard/Custom/premium/thankyou.html");
			}
		}
		
		if (returnHtml != null)
		{
			if (CommunityBoardConfig.CUSTOM_CB_ENABLED)
			{
				returnHtml = returnHtml.replace("%navigation%", navigation);
			}
			
			CommunityBoardHandler.separateAndSend(returnHtml, player);
		}
		
		return false;
	}

	private static String handleSchemeCommand(String command, Player player)
	{
		final SchemeBufferTable table = SchemeBufferTable.getInstance();
		final String[] parts = command.split(";", 6);
		if (parts.length > 1)
		{
			final String action = parts[1];
			if ("create".equals(action) && (parts.length > 2))
			{
				final String name = parts[2].trim();
				final Map<String, List<Integer>> schemes = table.getPlayerSchemes(player.getObjectId());
				if (!name.matches("[A-Za-z0-9_-]{1,16}"))
				{
					player.sendMessage("Use de 1 a 16 letras ou numeros no nome da scheme.");
				}
				else if ((schemes != null) && schemes.containsKey(name))
				{
					player.sendMessage("Ja existe uma scheme com esse nome.");
				}
				else if ((schemes != null) && (schemes.size() >= SchemeBufferConfig.BUFFER_MAX_SCHEMES))
				{
					player.sendMessage("Limite maximo de schemes atingido.");
				}
				else
				{
					table.setScheme(player.getObjectId(), name, new ArrayList<>());
				}
			}
			else if ("delete".equals(action) && (parts.length > 2))
			{
				table.deleteScheme(player.getObjectId(), parts[2]);
			}
			else if ("use".equals(action) && (parts.length > 2))
			{
				final List<Integer> skills = table.getScheme(player.getObjectId(), parts[2]);
				final Creature target = ((parts.length > 3) && "pet".equals(parts[3])) ? player.getSummon() : player;
				if (target == null)
				{
					player.sendMessage("Voce nao possui um pet invocado.");
				}
				else
				{
					for (int skillId : skills)
					{
						final BuffSkillHolder holder = table.getAvailableBuff(skillId);
						final Skill skill = holder != null ? SkillData.getInstance().getSkill(skillId, holder.getLevel()) : null;
						if (skill != null)
						{
							applyBuff(player, target, skill);
						}
					}
					target.getEffectList().updateEffectIcons(false);
				}
			}
			else if ("toggle".equals(action) && (parts.length > 5))
			{
				final String name = parts[2];
				final int skillId = Integer.parseInt(parts[4]);
				final List<Integer> skills = table.getScheme(player.getObjectId(), name);
				if (skills.contains(skillId))
				{
					table.removeSkillFromScheme(player.getObjectId(), name, skillId);
				}
				else if (table.getAvailableBuff(parts[3], skillId) != null)
				{
					table.addSkillToScheme(player.getObjectId(), name, skillId);
				}
				return buildSchemeEditor(player, name, parts[3], Integer.parseInt(parts[5]));
			}
			else if ("edit".equals(action) && (parts.length > 4))
			{
				return buildSchemeEditor(player, parts[2], parts[3], Integer.parseInt(parts[4]));
			}
		}
		return buildSchemeList(player);
	}

	private static String buildBuffCategory(Player player, String category, int page)
	{
		final SchemeBufferTable table = SchemeBufferTable.getInstance();
		final List<Integer> ids = table.getSkillsIdsByType(category);
		final int perPage = 12;
		final int pages = Math.max(1, (ids.size() + perPage - 1) / perPage);
		page = Math.max(1, Math.min(page, pages));

		final boolean vip = category.toUpperCase().startsWith("VIP");
		final String displayName = vip ? category.substring(4) + " +30" : category;
		final String backPage = vip ? "buffer/vip.html" : "buffer/main.html";

		// Title, duration, skill grid and footer each live in their own table row.
		// Mixing inline text and a nested table inside the same cell makes the client
		// render the table over the text, so every block gets a dedicated row.
		final StringBuilder html = new StringBuilder(4500);
		html.append("<html noscrollbar><body><table width=700><tr><td width=205>%navigation%</td><td width=480><center>");
		html.append("<table width=455 background=\"L2UI_CT1.Windows_DF_TooltipBG\">");
		html.append("<tr><td height=24 align=center></td></tr>");
		html.append("<tr><td align=center><font name=hs12 color=CDB67F>Buffer - ").append(displayName).append("</font></td></tr>");
		html.append("<tr><td align=center><font color=AAAAAA>Duration: 3 hours</font></td></tr>");
		html.append("<tr><td height=12></td></tr>");

		html.append("<tr><td align=center>");
		if (ids.isEmpty())
		{
			html.append("<font color=AAAAAA>Nenhum buff nesta categoria.</font>");
		}
		else
		{
			html.append("<table width=440>");
			final int start = (page - 1) * perPage;
			final int end = Math.min(start + perPage, ids.size());
			for (int i = start; i < end; i++)
			{
				if (((i - start) % 2) == 0)
				{
					html.append("<tr>");
				}

				final int skillId = ids.get(i);
				final BuffSkillHolder holder = table.getAvailableBuff(category, skillId);
				final Skill skill = holder != null ? SkillData.getInstance().getSkill(skillId, holder.getLevel()) : null;
				final String name = skill != null ? skill.getName() : Integer.toString(skillId);
				final String icon = skill != null ? skill.getIcon() : "icon.skill0000";
				html.append("<td width=220 height=40><table width=216><tr>");
				html.append("<td width=36 align=center><img src=\"").append(icon).append("\" width=32 height=32></td>");
				html.append("<td width=118>").append(name).append("</td>");
				html.append("<td width=62 align=center><button value=\"Buff\" action=\"bypass _bbscastbuff;").append(category).append(";").append(skillId).append(";").append(page).append("\" width=58 height=22 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td>");
				html.append("</tr></table></td>");

				if (i == (end - 1))
				{
					if (((i - start) % 2) == 0)
					{
						html.append("<td width=220></td>");
					}
					html.append("</tr>");
				}
				else if (((i - start) % 2) == 1)
				{
					html.append("</tr>");
				}
			}
			html.append("</table>");
		}
		html.append("</td></tr>");

		html.append("<tr><td height=12></td></tr>");
		html.append("<tr><td align=center><table><tr>");
		if (page > 1)
		{
			html.append("<td><button value=\"Previous\" action=\"bypass _bbsbufflist;").append(category).append(";").append(page - 1).append("\" width=80 height=22 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td>");
		}
		html.append("<td width=90 align=center>Page ").append(page).append("/").append(pages).append("</td>");
		if (page < pages)
		{
			html.append("<td><button value=\"Next\" action=\"bypass _bbsbufflist;").append(category).append(";").append(page + 1).append("\" width=80 height=22 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td>");
		}
		html.append("</tr></table></td></tr>");
		html.append("<tr><td align=center><button value=\"Back\" action=\"bypass _bbstop;").append(backPage).append("\" width=90 height=24 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td></tr>");
		html.append("<tr><td height=16></td></tr>");
		html.append("</table></center></td></tr></table></body></html>");
		return html.toString();
	}

	private static String buildSchemeList(Player player)
	{
		final Map<String, List<Integer>> schemes = SchemeBufferTable.getInstance().getPlayerSchemes(player.getObjectId());
		final StringBuilder html = new StringBuilder(3000);
		html.append("<html noscrollbar><body><table width=700><tr><td width=205>%navigation%</td><td width=480><center>");
		html.append("<table width=455 background=\"L2UI_CT1.Windows_DF_TooltipBG\">");
		html.append("<tr><td height=22></td></tr>");
		html.append("<tr><td align=center><font name=hs12 color=CDB67F>Scheme Buffer</font></td></tr>");
		html.append("<tr><td align=center><font color=AAAAAA>Crie perfis e aplique todos os buffs com um clique.</font></td></tr>");
		html.append("<tr><td height=12></td></tr>");

		html.append("<tr><td align=center><table width=430>");
		for (int slot = 1; slot <= SchemeBufferConfig.BUFFER_MAX_SCHEMES; slot++)
		{
			final String slotName = "Scheme-" + slot;
			String existing = null;
			if (schemes != null)
			{
				for (String name : schemes.keySet())
				{
					if (name.equalsIgnoreCase(slotName))
					{
						existing = name;
						break;
					}
				}
			}

			html.append("<tr><td width=430 height=34><table width=426><tr>");
			if (existing != null)
			{
				final int count = schemes.get(existing).size();
				html.append("<td width=130><font color=LEVEL>").append(existing).append("</font> (").append(count).append(" buffs)</td>");
				html.append("<td width=74><button value=\"Usar\" action=\"bypass _bbsscheme;use;").append(existing).append(";me\" width=70 height=22 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td>");
				html.append("<td width=74><button value=\"No Pet\" action=\"bypass _bbsscheme;use;").append(existing).append(";pet\" width=70 height=22 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td>");
				html.append("<td width=74><button value=\"Editar\" action=\"bypass _bbsscheme;edit;").append(existing).append(";Prophet;1\" width=70 height=22 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td>");
				html.append("<td width=74><button value=\"Excluir\" action=\"bypass _bbsscheme;delete;").append(existing).append("\" width=70 height=22 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td>");
			}
			else
			{
				html.append("<td width=130><font color=808080>").append(slotName).append(" (vazio)</font></td>");
				html.append("<td width=296><button value=\"Criar\" action=\"bypass _bbsscheme;create;").append(slotName).append("\" width=70 height=22 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td>");
			}
			html.append("</tr></table></td></tr>");
		}
		html.append("</table></td></tr>");

		html.append("<tr><td height=12></td></tr>");
		html.append("<tr><td align=center><button value=\"Voltar\" action=\"bypass _bbstop;buffer/main.html\" width=100 height=25 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td></tr>");
		html.append("<tr><td height=18></td></tr>");
		html.append("</table></center></td></tr></table></body></html>");
		return html.toString();
	}

	private static String buildSchemeEditor(Player player, String name, String category, int page)
	{
		final SchemeBufferTable table = SchemeBufferTable.getInstance();
		final List<Integer> categorySkills = table.getSkillsIdsByType(category);
		final List<Integer> selected = table.getScheme(player.getObjectId(), name);
		final int perPage = 10;
		final int pages = Math.max(1, (categorySkills.size() + perPage - 1) / perPage);
		page = Math.max(1, Math.min(page, pages));

		final StringBuilder html = new StringBuilder(4000);
		html.append("<html noscrollbar><body><table width=700><tr><td width=205>%navigation%</td><td width=480><center>");
		html.append("<table width=455 background=\"L2UI_CT1.Windows_DF_TooltipBG\">");
		html.append("<tr><td height=22></td></tr>");
		html.append("<tr><td align=center><font name=hs12 color=CDB67F>Editar: ").append(name).append("</font></td></tr>");
		html.append("<tr><td height=8></td></tr>");

		// Category tabs, 4 per row.
		html.append("<tr><td align=center><table>");
		int col = 0;
		for (String type : table.getSkillTypes())
		{
			if (col == 0)
			{
				html.append("<tr>");
			}
			html.append("<td><button value=\"").append(type).append("\" action=\"bypass _bbsscheme;edit;").append(name).append(";").append(type).append(";1\" width=105 height=22 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td>");
			col++;
			if (col == 4)
			{
				html.append("</tr>");
				col = 0;
			}
		}
		if (col != 0)
		{
			html.append("</tr>");
		}
		html.append("</table></td></tr>");
		html.append("<tr><td height=8></td></tr>");

		html.append("<tr><td align=center><table width=430>");
		final int start = (page - 1) * perPage;
		for (int i = start; i < Math.min(start + perPage, categorySkills.size()); i++)
		{
			final int skillId = categorySkills.get(i);
			final BuffSkillHolder holder = table.getAvailableBuff(category, skillId);
			final Skill skill = holder != null ? SkillData.getInstance().getSkill(skillId, holder.getLevel()) : null;
			final String skillName = skill != null ? skill.getName() : Integer.toString(skillId);
			final String icon = skill != null ? skill.getIcon() : "icon.skill0000";
			final boolean active = selected.contains(skillId);
			html.append("<tr><td width=430 height=34><table width=426><tr>");
			html.append("<td width=36><img src=\"").append(icon).append("\" width=32 height=32></td>");
			html.append("<td width=280>").append(skillName).append("</td>");
			html.append("<td width=100><button value=\"").append(active ? "Remover" : "Adicionar").append("\" action=\"bypass _bbsscheme;toggle;").append(name).append(";").append(category).append(";").append(skillId).append(";").append(page).append("\" width=90 height=22 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td>");
			html.append("</tr></table></td></tr>");
		}
		html.append("</table></td></tr>");

		html.append("<tr><td height=8></td></tr>");
		html.append("<tr><td align=center><table><tr>");
		if (page > 1)
		{
			html.append("<td><button value=\"Anterior\" action=\"bypass _bbsscheme;edit;").append(name).append(";").append(category).append(";").append(page - 1).append("\" width=80 height=22 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td>");
		}
		html.append("<td width=90 align=center>Pagina ").append(page).append("/").append(pages).append("</td>");
		if (page < pages)
		{
			html.append("<td><button value=\"Proxima\" action=\"bypass _bbsscheme;edit;").append(name).append(";").append(category).append(";").append(page + 1).append("\" width=80 height=22 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td>");
		}
		html.append("</tr></table></td></tr>");
		html.append("<tr><td align=center><button value=\"Voltar para Schemes\" action=\"bypass _bbsscheme\" width=130 height=25 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td></tr>");
		html.append("<tr><td height=18></td></tr>");
		html.append("</table></center></td></tr></table></body></html>");
		return html.toString();
	}

	private static String handleSubclassCommand(String command, Player player)
	{
		if (player.isCastingNow() || player.isAllSkillsDisabled())
		{
			player.sendMessage("Subclasses nao podem ser alteradas enquanto uma skill esta em uso.");
			return buildSubclassMenu(player);
		}
		if (player.getTransformation() != null)
		{
			player.sendMessage("Subclasses nao podem ser alteradas enquanto transformado.");
			return buildSubclassMenu(player);
		}
		if (player.hasSummon())
		{
			player.sendMessage("Dispense seu pet/summon antes de alterar subclasses.");
			return buildSubclassMenu(player);
		}
		if (!player.isInventoryUnder90(true) || (player.getWeightPenalty() >= 2))
		{
			player.sendMessage("Esvazie um pouco o inventario antes de alterar subclasses.");
			return buildSubclassMenu(player);
		}

		if (OlympiadManager.getInstance().isRegisteredInComp(player))
		{
			OlympiadManager.getInstance().unRegisterNoble(player);
		}

		final String[] parts = command.split(";");
		final String action = parts.length > 1 ? parts[1] : "menu";
		switch (action)
		{
			case "addlist":
			{
				return buildSubclassAddList(player);
			}
			case "add":
			{
				if ((parts.length > 2) && player.getClient().getFloodProtectors().canChangeSubclass())
				{
					final int classId = Integer.parseInt(parts[2]);
					if (canAddSubclass(player) && isValidNewSubClass(player, classId) && player.addSubClass(classId, player.getTotalSubClasses() + 1))
					{
						player.setActiveClass(player.getTotalSubClasses());
						player.sendMessage("Subclasse " + ClassListData.getInstance().getClass(classId).getClassName() + " adicionada com sucesso!");
					}
					else
					{
						player.sendMessage("Nao foi possivel adicionar essa subclasse.");
					}
				}
				return buildSubclassMenu(player);
			}
			case "changelist":
			{
				return buildSubclassChangeList(player);
			}
			case "change":
			{
				if ((parts.length > 2) && player.getClient().getFloodProtectors().canChangeSubclass())
				{
					final int classIndex = Integer.parseInt(parts[2]);
					if (classIndex == player.getClassIndex())
					{
						player.sendMessage("Essa ja e sua classe ativa.");
					}
					else if ((classIndex == 0) || player.getSubClasses().containsKey(classIndex))
					{
						player.setActiveClass(classIndex);
						player.sendMessage("Voce trocou para a classe " + ClassListData.getInstance().getClass(player.getPlayerClass().getId()).getClassName() + ".");
					}
				}
				return buildSubclassMenu(player);
			}
			case "modifylist":
			{
				return buildSubclassModifyList(player);
			}
			case "modifyslot":
			{
				if (parts.length > 2)
				{
					return buildSubclassModifyOptions(player, Integer.parseInt(parts[2]));
				}
				return buildSubclassMenu(player);
			}
			case "modify":
			{
				if ((parts.length > 3) && player.getClient().getFloodProtectors().canChangeSubclass())
				{
					final int classIndex = Integer.parseInt(parts[2]);
					final int newClassId = Integer.parseInt(parts[3]);
					if ((classIndex >= 1) && (classIndex <= PlayerConfig.MAX_SUBCLASS) && player.getSubClasses().containsKey(classIndex) && isValidNewSubClass(player, newClassId))
					{
						if (player.modifySubClass(classIndex, newClassId))
						{
							player.abortCast();
							player.stopAllEffectsExceptThoseThatLastThroughDeath();
							player.stopAllEffectsNotStayOnSubclassChange();
							player.stopCubics();
							player.setActiveClass(classIndex);
							player.sendMessage("Subclasse substituida por " + ClassListData.getInstance().getClass(newClassId).getClassName() + "!");
						}
						else
						{
							player.setActiveClass(0);
							player.sendMessage("A subclasse nao pode ser alterada. Voce voltou para a classe base.");
						}
					}
					else
					{
						player.sendMessage("Nao foi possivel substituir essa subclasse.");
					}
				}
				return buildSubclassMenu(player);
			}
			case "certification":
			{
				grantSubclassCertifications(player);
				return buildSubclassMenu(player);
			}
			case "learnskill":
			{
				openCertificationSkillWindow(player);
				return buildSubclassMenu(player);
			}
			default:
			{
				return buildSubclassMenu(player);
			}
		}
	}

	// Same item ids and variable-key format used by ai.others.SubclassCertification, so certifications
	// granted here or at a village master NPC never double-award the same tier.
	private static final int CERTIFICATE_EMERGENT_ABILITY = 10280;
	private static final int CERTIFICATE_MASTER_ABILITY = 10612;
	private static final Map<Integer, Integer> ABILITY_CERTIFICATES = new HashMap<>();
	private static final Map<Integer, Integer> TRANSFORMATION_SEALBOOKS = new HashMap<>();
	static
	{
		ABILITY_CERTIFICATES.put(0, 10281); // Certificate - Warrior Ability
		ABILITY_CERTIFICATES.put(1, 10283); // Certificate - Rogue Ability
		ABILITY_CERTIFICATES.put(2, 10282); // Certificate - Knight Ability
		ABILITY_CERTIFICATES.put(3, 10286); // Certificate - Summoner Ability
		ABILITY_CERTIFICATES.put(4, 10284); // Certificate - Wizard Ability
		ABILITY_CERTIFICATES.put(5, 10285); // Certificate - Healer Ability
		ABILITY_CERTIFICATES.put(6, 10287); // Certificate - Enchanter Ability

		TRANSFORMATION_SEALBOOKS.put(0, 10289); // Transformation Sealbook: Divine Warrior
		TRANSFORMATION_SEALBOOKS.put(1, 10290); // Transformation Sealbook: Divine Rogue
		TRANSFORMATION_SEALBOOKS.put(2, 10288); // Transformation Sealbook: Divine Knight
		TRANSFORMATION_SEALBOOKS.put(3, 10294); // Transformation Sealbook: Divine Summoner
		TRANSFORMATION_SEALBOOKS.put(4, 10292); // Transformation Sealbook: Divine Wizard
		TRANSFORMATION_SEALBOOKS.put(5, 10291); // Transformation Sealbook: Divine Healer
		TRANSFORMATION_SEALBOOKS.put(6, 10293); // Transformation Sealbook: Divine Enchanter
	}
	private static final int SUBCLASS_CERTIFICATION_MIN_LEVEL = 65;

	private static int getAbilityGroupIndex(Player player)
	{
		if (player.isInCategory(CategoryType.SUB_GROUP_WARRIOR))
		{
			return 0;
		}
		else if (player.isInCategory(CategoryType.SUB_GROUP_ROGUE))
		{
			return 1;
		}
		else if (player.isInCategory(CategoryType.SUB_GROUP_KNIGHT))
		{
			return 2;
		}
		else if (player.isInCategory(CategoryType.SUB_GROUP_SUMMONER))
		{
			return 3;
		}
		else if (player.isInCategory(CategoryType.SUB_GROUP_WIZARD))
		{
			return 4;
		}
		else if (player.isInCategory(CategoryType.SUB_GROUP_HEALER))
		{
			return 5;
		}
		else if (player.isInCategory(CategoryType.SUB_GROUP_ENCHANTER))
		{
			return 6;
		}
		return -1;
	}

	private static void grantSubclassCertification(Player player, String variable, Integer itemId, int level, List<String> granted)
	{
		if (itemId == null)
		{
			return;
		}

		final String var = variable + level + "-" + player.getClassIndex();
		if (player.getVariables().hasVariable(var) && !player.getVariables().getString(var).equals("0"))
		{
			return;
		}
		if (player.getLevel() < level)
		{
			return;
		}

		final Item item = player.getInventory().addItem(ItemProcessType.QUEST, itemId, 1, player, null);
		if (item == null)
		{
			return;
		}

		final SystemMessage smsg = new SystemMessage(SystemMessageId.YOU_HAVE_EARNED_S1);
		smsg.addItemName(item);
		player.sendPacket(smsg);
		player.getVariables().set(var, String.valueOf(item.getObjectId()));
		granted.add(item.getName());
	}

	private static void grantSubclassCertifications(Player player)
	{
		if (player.getLevel() < SUBCLASS_CERTIFICATION_MIN_LEVEL)
		{
			player.sendMessage("Certification exige level " + SUBCLASS_CERTIFICATION_MIN_LEVEL + "+.");
			return;
		}

		final List<String> granted = new ArrayList<>();
		grantSubclassCertification(player, "EmergentAbility", CERTIFICATE_EMERGENT_ABILITY, 65, granted);
		grantSubclassCertification(player, "EmergentAbility", CERTIFICATE_EMERGENT_ABILITY, 70, granted);
		grantSubclassCertification(player, "ClassAbility", ABILITY_CERTIFICATES.get(getAbilityGroupIndex(player)), 75, granted);
		grantSubclassCertification(player, "ClassAbility", TRANSFORMATION_SEALBOOKS.get(getAbilityGroupIndex(player)), 80, granted);

		if (granted.isEmpty())
		{
			player.sendMessage("Nenhuma certificacao nova disponivel (ja obtidas ou level insuficiente).");
		}
		else
		{
			player.sendMessage("Certificacoes recebidas: " + String.join(", ", granted) + ".");
		}
	}

	// Native AcquireSkillList/RequestAcquireSkill flow for AcquireSkillType.SUBCLASS is normally only
	// reachable by talking to a Village Master NPC. Opened here directly from the Community Board instead.
	private static void openCertificationSkillWindow(Player player)
	{
		if (player.isSubClassActive())
		{
			player.sendMessage("Troque para a classe base antes de aprender a skill de certificacao.");
			return;
		}

		final List<SkillLearn> skills = SkillTreeData.getInstance().getAvailableSubClassSkills(player);
		final AcquireSkillList asl = new AcquireSkillList(AcquireSkillType.SUBCLASS);
		int count = 0;
		for (SkillLearn s : skills)
		{
			if (SkillData.getInstance().getSkill(s.getSkillId(), s.getSkillLevel()) != null)
			{
				asl.addSkill(s.getSkillId(), s.getSkillLevel(), s.getSkillLevel(), 0, 1);
				count++;
			}
		}

		if (count == 0)
		{
			player.sendMessage("Nenhuma skill de certificacao disponivel agora (sem certificado, level insuficiente ou ja aprendida).");
			return;
		}

		player.sendPacket(asl);
	}

	private static boolean canAddSubclass(Player player)
	{
		if ((player.getTotalSubClasses() >= PlayerConfig.MAX_SUBCLASS) || (player.getLevel() < 75))
		{
			player.sendMessage("Requisitos: level 75+ e um slot de subclasse livre.");
			return false;
		}
		for (SubClassHolder sub : player.getSubClasses().values())
		{
			if (sub.getLevel() < 75)
			{
				player.sendMessage("Todas as suas subclasses precisam estar no level 75+.");
				return false;
			}
		}
		if (!PlayerConfig.ALT_GAME_SUBCLASS_WITHOUT_QUESTS && !player.isNoble())
		{
			QuestState qs = player.getQuestState("Q00234_FatesWhisper");
			if ((qs == null) || !qs.isCompleted())
			{
				player.sendMessage("A quest Fates Whisper e necessaria.");
				return false;
			}
			qs = player.getQuestState("Q00235_MimirsElixir");
			if ((qs == null) || !qs.isCompleted())
			{
				player.sendMessage("A quest Mimirs Elixir e necessaria.");
				return false;
			}
		}
		return true;
	}

	private static java.util.Set<PlayerClass> getAvailableSubClasses(Player player)
	{
		final PlayerClass baseCID = PlayerClass.getPlayerClass(player.getBaseClass());
		final int baseClassId = baseCID.level() > 2 ? baseCID.getParent().getId() : player.getBaseClass();
		final java.util.Set<PlayerClass> availSubs = VillageMaster.getSubclasses(player, baseClassId);
		if ((availSubs != null) && !availSubs.isEmpty())
		{
			for (java.util.Iterator<PlayerClass> it = availSubs.iterator(); it.hasNext();)
			{
				final PlayerClass pclass = it.next();
				for (SubClassHolder sub : player.getSubClasses().values())
				{
					if (PlayerClass.getPlayerClass(sub.getId()).equalsOrChildOf(pclass))
					{
						it.remove();
						break;
					}
				}
			}
		}
		return availSubs;
	}

	private static boolean isValidNewSubClass(Player player, int classId)
	{
		final java.util.Set<PlayerClass> availSubs = getAvailableSubClasses(player);
		if (availSubs == null)
		{
			return false;
		}
		for (PlayerClass pclass : availSubs)
		{
			if (pclass.getId() == classId)
			{
				return true;
			}
		}
		return false;
	}

	private static StringBuilder subclassPageHeader(String title, String subtitle)
	{
		final StringBuilder html = new StringBuilder(4000);
		html.append("<html noscrollbar><body><table width=700><tr><td width=205>%navigation%</td><td width=480><center>");
		html.append("<table width=455 background=\"L2UI_CT1.Windows_DF_TooltipBG\">");
		html.append("<tr><td height=22></td></tr>");
		html.append("<tr><td align=center><font name=hs12 color=CDB67F>").append(title).append("</font></td></tr>");
		html.append("<tr><td align=center><font color=AAAAAA>").append(subtitle).append("</font></td></tr>");
		html.append("<tr><td height=12></td></tr>");
		return html;
	}

	private static String subclassPageFooter(StringBuilder html, String backBypass)
	{
		html.append("<tr><td height=12></td></tr>");
		html.append("<tr><td align=center><button value=\"Voltar\" action=\"bypass ").append(backBypass).append("\" width=100 height=25 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td></tr>");
		html.append("<tr><td height=18></td></tr>");
		html.append("</table></center></td></tr></table></body></html>");
		return html.toString();
	}

	private static String buildSubclassMenu(Player player)
	{
		final StringBuilder html = subclassPageHeader("Subclass Manager", "Level 75+ para adicionar. Maximo de " + PlayerConfig.MAX_SUBCLASS + " subclasses.");
		html.append("<tr><td align=center><font color=LEVEL>Classe ativa: ").append(ClassListData.getInstance().getClass(player.getPlayerClass().getId()).getClassName()).append("</font></td></tr>");
		html.append("<tr><td align=center>Base: ").append(ClassListData.getInstance().getClass(player.getBaseClass()).getClassName()).append("</td></tr>");
		for (SubClassHolder sub : player.getSubClasses().values())
		{
			html.append("<tr><td align=center>Sub ").append(sub.getClassIndex()).append(": ").append(ClassListData.getInstance().getClass(sub.getId()).getClassName()).append(" (Lv ").append(sub.getLevel()).append(")</td></tr>");
		}
		html.append("<tr><td height=12></td></tr>");
		html.append("<tr><td align=center><button value=\"Adicionar Subclasse\" action=\"bypass _bbssubclass;addlist\" width=180 height=28 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td></tr>");
		html.append("<tr><td align=center><button value=\"Trocar Classe Ativa\" action=\"bypass _bbssubclass;changelist\" width=180 height=28 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td></tr>");
		html.append("<tr><td align=center><button value=\"Substituir Subclasse\" action=\"bypass _bbssubclass;modifylist\" width=180 height=28 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td></tr>");
		html.append("<tr><td height=10></td></tr>");
		html.append("<tr><td align=center><button value=\"Certification (Classe Ativa)\" action=\"bypass _bbssubclass;certification\" width=200 height=28 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td></tr>");
		html.append("<tr><td align=center><font color=AAAAAA>Pega o certificado (65/70/75/80) da classe ativa no momento - troque a classe ativa antes de repetir para outra.</font></td></tr>");
		html.append("<tr><td height=6></td></tr>");
		html.append("<tr><td align=center><button value=\"Learn Certification Skill\" action=\"bypass _bbssubclass;learnskill\" width=200 height=28 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td></tr>");
		html.append("<tr><td align=center><font color=AAAAAA>Abre a janela de skills de certificacao disponiveis para aprender (precisa ja ter o certificado e estar na classe base).</font></td></tr>");
		return subclassPageFooter(html, "_bbstop;class/main.html");
	}

	private static void appendClassButtons(StringBuilder html, java.util.Set<PlayerClass> classes, String bypassPrefix)
	{
		html.append("<tr><td align=center><table>");
		int col = 0;
		for (PlayerClass pclass : classes)
		{
			if (col == 0)
			{
				html.append("<tr>");
			}
			html.append("<td><button value=\"").append(ClassListData.getInstance().getClass(pclass.getId()).getClassName()).append("\" action=\"bypass ").append(bypassPrefix).append(pclass.getId()).append("\" width=140 height=25 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td>");
			col++;
			if (col == 3)
			{
				html.append("</tr>");
				col = 0;
			}
		}
		if (col != 0)
		{
			html.append("</tr>");
		}
		html.append("</table></td></tr>");
	}

	private static String buildSubclassAddList(Player player)
	{
		final StringBuilder html = subclassPageHeader("Adicionar Subclasse", "Escolha a nova subclasse (comeca no level 40).");
		final java.util.Set<PlayerClass> availSubs = getAvailableSubClasses(player);
		if ((availSubs == null) || availSubs.isEmpty())
		{
			html.append("<tr><td align=center><font color=AAAAAA>Nenhuma subclasse disponivel.</font></td></tr>");
		}
		else
		{
			appendClassButtons(html, availSubs, "_bbssubclass;add;");
		}
		return subclassPageFooter(html, "_bbssubclass");
	}

	private static String buildSubclassChangeList(Player player)
	{
		final StringBuilder html = subclassPageHeader("Trocar Classe Ativa", "Escolha para qual classe deseja trocar.");
		html.append("<tr><td align=center><button value=\"Base: ").append(ClassListData.getInstance().getClass(player.getBaseClass()).getClassName()).append("\" action=\"bypass _bbssubclass;change;0\" width=200 height=25 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td></tr>");
		for (SubClassHolder sub : player.getSubClasses().values())
		{
			html.append("<tr><td align=center><button value=\"Sub ").append(sub.getClassIndex()).append(": ").append(ClassListData.getInstance().getClass(sub.getId()).getClassName()).append("\" action=\"bypass _bbssubclass;change;").append(sub.getClassIndex()).append("\" width=200 height=25 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td></tr>");
		}
		return subclassPageFooter(html, "_bbssubclass");
	}

	private static String buildSubclassModifyList(Player player)
	{
		final StringBuilder html = subclassPageHeader("Substituir Subclasse", "A subclasse escolhida sera removida e trocada por uma nova (volta ao level 40).");
		if (player.getSubClasses().isEmpty())
		{
			html.append("<tr><td align=center><font color=AAAAAA>Voce nao possui subclasses.</font></td></tr>");
		}
		for (SubClassHolder sub : player.getSubClasses().values())
		{
			html.append("<tr><td align=center><button value=\"Sub ").append(sub.getClassIndex()).append(": ").append(ClassListData.getInstance().getClass(sub.getId()).getClassName()).append("\" action=\"bypass _bbssubclass;modifyslot;").append(sub.getClassIndex()).append("\" width=200 height=25 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td></tr>");
		}
		return subclassPageFooter(html, "_bbssubclass");
	}

	private static String buildSubclassModifyOptions(Player player, int classIndex)
	{
		final StringBuilder html = subclassPageHeader("Substituir Subclasse " + classIndex, "Escolha a nova subclasse para este slot.");
		final java.util.Set<PlayerClass> availSubs = getAvailableSubClasses(player);
		if ((availSubs == null) || availSubs.isEmpty())
		{
			html.append("<tr><td align=center><font color=AAAAAA>Nenhuma subclasse disponivel.</font></td></tr>");
		}
		else
		{
			appendClassButtons(html, availSubs, "_bbssubclass;modify;" + classIndex + ";");
		}
		return subclassPageFooter(html, "_bbssubclass;modifylist");
	}

	/**
	 * Applies a buff skill on the target and overrides its duration with {@link SchemeBufferConfig#BUFFER_BUFF_DURATION},<br>
	 * so community board buffs always share the same duration regardless of the skill enchant route.
	 * @param caster
	 * @param target
	 * @param skill
	 */
	private static void applyBuff(Player caster, Creature target, Skill skill)
	{
		skill.applyEffects(caster, target);

		final BuffInfo info = target.getEffectList().getBuffInfoBySkillId(skill.getId());
		if (info != null)
		{
			info.setAbnormalTime(SchemeBufferConfig.BUFFER_BUFF_DURATION);
		}
	}

	/**
	 * Gets the Favorite links for the given player.
	 * @param player the player
	 * @return the favorite links count
	 */
	private static int getFavoriteCount(Player player)
	{
		int count = 0;
		try (Connection con = DatabaseFactory.getConnection();
			PreparedStatement ps = con.prepareStatement(COUNT_FAVORITES))
		{
			ps.setInt(1, player.getObjectId());
			try (ResultSet rs = ps.executeQuery())
			{
				if (rs.next())
				{
					count = rs.getInt("favorites");
				}
			}
		}
		catch (Exception e)
		{
			LOG.warning(FavoriteBoard.class.getSimpleName() + ": Coudn't load favorites count for " + player);
		}
		
		return count;
	}
	
	/**
	 * Gets the registered regions count for the given player.
	 * @param player the player
	 * @return the registered regions count
	 */
	private static int getRegionCount(Player player)
	{
		return 0; // TODO: Implement.
	}

	// ============ Blacksmith (weapon augmentation) ============
	// Reimplements the same rules used by the native client-driven augment dialog
	// (see clientpackets.AbstractRefinePacket/RequestRefine), but menu-driven from the Community Board.
	private static final int LIFESTONE_MID_LV80 = 9574;
	private static final int LIFESTONE_TOP_LV85 = 16163;
	private static final int[] BLACKSMITH_LIFESTONES = { LIFESTONE_MID_LV80, LIFESTONE_TOP_LV85 };
	private static final int BLACKSMITH_GEMSTONE_D = 2130;
	private static final int BLACKSMITH_GEMSTONE_C = 2131;
	private static final int BLACKSMITH_GEMSTONE_B = 2132;

	private static String blacksmithLifeStoneName(int id)
	{
		return (id == LIFESTONE_MID_LV80) ? "Mid-Grade Life Stone (Lv80)" : "Top-Grade Life Stone (Lv85)";
	}

	private static int blacksmithGemstoneId(CrystalType grade)
	{
		switch (grade)
		{
			case C:
			case B:
			{
				return BLACKSMITH_GEMSTONE_D;
			}
			case A:
			case S:
			{
				return BLACKSMITH_GEMSTONE_C;
			}
			case S80:
			case S84:
			{
				return BLACKSMITH_GEMSTONE_B;
			}
			default:
			{
				return 0;
			}
		}
	}

	private static int blacksmithGemstoneCount(CrystalType grade)
	{
		switch (grade)
		{
			case C:
			{
				return 20;
			}
			case B:
			{
				return 30;
			}
			case A:
			{
				return 20;
			}
			case S:
			{
				return 25;
			}
			case S80:
			case S84:
			{
				return 36;
			}
			default:
			{
				return 0;
			}
		}
	}

	/**
	 * @return null if the item can be augmented, otherwise a short human-readable rejection reason.
	 */
	private static String blacksmithRejectReason(Player player, Item item)
	{
		if (item == null)
		{
			return "item nao encontrado";
		}
		if (item.getOwnerId() != player.getObjectId())
		{
			return "nao pertence a voce";
		}
		if (item.isAugmented())
		{
			return "ja augmentado";
		}
		if (item.isHeroItem() || item.isShadowItem() || item.isCommonItem() || item.isEtcItem() || item.isTimeLimitedItem())
		{
			return "tipo de item nao augmentavel";
		}
		if (item.getTemplate().getCrystalType().isLesser(CrystalType.C))
		{
			return "grade abaixo de C";
		}
		switch (item.getItemLocation())
		{
			case INVENTORY:
			case PAPERDOLL:
			{
				break;
			}
			default:
			{
				return "localizacao invalida (armazem/loja/etc)";
			}
		}
		if (!(item.getTemplate() instanceof Weapon))
		{
			return "nao e uma arma";
		}
		switch (((Weapon) item.getTemplate()).getItemType())
		{
			case NONE:
			case FISHINGROD:
			{
				return "tipo de arma nao augmentavel";
			}
			default:
			{
				return null;
			}
		}
	}

	private static boolean isBlacksmithAugmentable(Player player, Item item)
	{
		return blacksmithRejectReason(player, item) == null;
	}

	private static String handleBlacksmithCommand(String command, Player player)
	{
		final String[] parts = command.split(";");
		final String action = parts.length > 1 ? parts[1] : "menu";
		switch (action)
		{
			case "augmentlist":
			{
				return buildBlacksmithAugmentList(player);
			}
			case "stonelist":
			{
				if (parts.length > 2)
				{
					return buildBlacksmithStoneList(player, Integer.parseInt(parts[2]));
				}
				return buildBlacksmithAugmentList(player);
			}
			case "apply":
			{
				if (parts.length > 3)
				{
					return applyBlacksmithAugment(player, Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
				}
				return buildBlacksmithAugmentList(player);
			}
			case "removelist":
			{
				return buildBlacksmithRemoveList(player);
			}
			case "remove":
			{
				if (parts.length > 2)
				{
					return removeBlacksmithAugment(player, Integer.parseInt(parts[2]));
				}
				return buildBlacksmithRemoveList(player);
			}
			default:
			{
				return HtmCache.getInstance().getHtm(player, "data/html/CommunityBoard/Custom/custom/blacksmith.html");
			}
		}
	}

	private static String buildBlacksmithAugmentList(Player player)
	{
		final StringBuilder html = subclassPageHeader("Augmentar Arma", "Escolha a arma que deseja augmentar.");
		boolean any = false;
		final List<String> rejected = new ArrayList<>();
		for (Item item : player.getInventory().getItems())
		{
			final String reason = blacksmithRejectReason(player, item);
			if (reason != null)
			{
				if (item.getTemplate() instanceof Weapon)
				{
					rejected.add(item.getName() + " (" + reason + ")");
				}
				continue;
			}
			any = true;
			html.append("<tr><td align=center><table width=430><tr>");
			html.append("<td width=36 align=center><img src=\"").append(item.getTemplate().getIcon()).append("\" width=32 height=32></td>");
			html.append("<td width=290>").append(item.getName()).append(item.getEnchantLevel() > 0 ? (" +" + item.getEnchantLevel()) : "").append("</td>");
			html.append("<td width=100 align=center><button value=\"Escolher\" action=\"bypass _bbsblacksmith;stonelist;").append(item.getObjectId()).append("\" width=90 height=22 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td>");
			html.append("</tr></table></td></tr>");
		}
		if (!any)
		{
			html.append("<tr><td align=center><font color=AAAAAA>Nenhuma arma augmentavel encontrada.</font></td></tr>");
			for (String line : rejected)
			{
				html.append("<tr><td align=center><font color=808080>").append(line).append("</font></td></tr>");
			}
		}
		return subclassPageFooter(html, "_bbstop;custom/blacksmith.html");
	}

	private static String buildBlacksmithStoneList(Player player, int itemObjId)
	{
		final Item target = player.getInventory().getItemByObjectId(itemObjId);
		if (!isBlacksmithAugmentable(player, target))
		{
			player.sendMessage("Esse item nao esta mais disponivel para augment.");
			return buildBlacksmithAugmentList(player);
		}

		final CrystalType grade = target.getTemplate().getCrystalType();
		final int gemId = blacksmithGemstoneId(grade);
		final int gemCount = blacksmithGemstoneCount(grade);
		final String gemName = (gemId == BLACKSMITH_GEMSTONE_D) ? "Gemstone D" : (gemId == BLACKSMITH_GEMSTONE_C) ? "Gemstone C" : "Gemstone B";
		final long gemsOwned = player.getInventory().getInventoryItemCount(gemId, -1);

		final StringBuilder html = subclassPageHeader("Augmentar: " + target.getName(), "Grade " + grade + " precisa de 1 life stone + " + gemCount + "x " + gemName + " (voce tem " + gemsOwned + ").");
		for (int stoneId : BLACKSMITH_LIFESTONES)
		{
			final long owned = player.getInventory().getInventoryItemCount(stoneId, -1);
			html.append("<tr><td align=center><table width=430><tr>");
			html.append("<td width=250>").append(blacksmithLifeStoneName(stoneId)).append(" (voce tem: ").append(owned).append(")</td>");
			html.append("<td width=180 align=center><button value=\"Usar esta stone\" action=\"bypass _bbsblacksmith;apply;").append(itemObjId).append(";").append(stoneId).append("\" width=170 height=22 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td>");
			html.append("</tr></table></td></tr>");
		}
		html.append("<tr><td height=8></td></tr>");
		html.append("<tr><td align=center><button value=\"Escolher novo item\" action=\"bypass _bbsblacksmith;augmentlist\" width=200 height=24 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td></tr>");
		return subclassPageFooter(html, "_bbstop;custom/blacksmith.html");
	}

	private static String applyBlacksmithAugment(Player player, int itemObjId, int stoneId)
	{
		final Item target = player.getInventory().getItemByObjectId(itemObjId);
		if (!isBlacksmithAugmentable(player, target))
		{
			player.sendMessage("Esse item nao esta mais disponivel para augment.");
			return buildBlacksmithAugmentList(player);
		}

		final Item stone = player.getInventory().getItemByItemId(stoneId);
		if (stone == null)
		{
			player.sendMessage("Voce nao tem " + blacksmithLifeStoneName(stoneId) + ".");
			return buildBlacksmithStoneList(player, itemObjId);
		}

		final CrystalType grade = target.getTemplate().getCrystalType();
		final int gemId = blacksmithGemstoneId(grade);
		final int gemCount = blacksmithGemstoneCount(grade);
		final Item gems = player.getInventory().getItemByItemId(gemId);
		final long gemsOwned = gems != null ? gems.getCount() : 0;
		if (gemsOwned < gemCount)
		{
			final String gemName = (gemId == BLACKSMITH_GEMSTONE_D) ? "Gemstone D" : (gemId == BLACKSMITH_GEMSTONE_C) ? "Gemstone C" : "Gemstone B";
			player.sendMessage("Faltam gemstones: precisa de " + gemCount + "x " + gemName + ", voce tem " + gemsOwned + ".");
			return buildBlacksmithStoneList(player, itemObjId);
		}

		if (target.isEquipped())
		{
			final InventoryUpdate iu = new InventoryUpdate();
			for (Item unequipped : player.getInventory().unEquipItemInSlotAndRecord(target.getLocationSlot()))
			{
				iu.addModifiedItem(unequipped);
			}
			player.sendPacket(iu);
			player.broadcastUserInfo();
		}

		if (!player.destroyItem(ItemProcessType.FEE, stone, 1, null, true) || !player.destroyItem(ItemProcessType.FEE, gems, gemCount, null, true))
		{
			return buildBlacksmithStoneList(player, itemObjId);
		}

		final int lifeStoneLevel = (stoneId == LIFESTONE_MID_LV80) ? 10 : 13;
		final int lifeStoneGrade = (stoneId == LIFESTONE_MID_LV80) ? 1 : 3; // GRADE_MID = 1, GRADE_TOP = 3
		final Augmentation aug = AugmentationData.getInstance().generateRandomAugmentation(lifeStoneLevel, lifeStoneGrade, target.getTemplate().getBodyPart(), stoneId, target);
		target.setAugmentation(aug);

		final InventoryUpdate iu = new InventoryUpdate();
		iu.addModifiedItem(target);
		player.sendPacket(iu);

		final StringBuilder html = subclassPageHeader("Augment aplicado!", "Confira o efeito passando o mouse sobre o item no inventario.");
		html.append("<tr><td align=center><font color=LEVEL>").append(target.getName()).append("</font> foi augmentada.</td></tr>");
		html.append("<tr><td height=10></td></tr>");
		html.append("<tr><td align=center><button value=\"Continuar (re-rolar)\" action=\"bypass _bbsblacksmith;apply;").append(itemObjId).append(";").append(stoneId).append("\" width=200 height=26 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td></tr>");
		html.append("<tr><td align=center><button value=\"Remover Augment\" action=\"bypass _bbsblacksmith;remove;").append(itemObjId).append("\" width=200 height=26 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td></tr>");
		html.append("<tr><td height=6></td></tr>");
		html.append("<tr><td align=center><button value=\"Escolher novo item\" action=\"bypass _bbsblacksmith;augmentlist\" width=200 height=24 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td></tr>");
		return subclassPageFooter(html, "_bbstop;custom/blacksmith.html");
	}

	private static String buildBlacksmithRemoveList(Player player)
	{
		final StringBuilder html = subclassPageHeader("Remover Augment", "Escolha a arma augmentada para remover o augment (gratis).");
		boolean any = false;
		for (Item item : player.getInventory().getItems())
		{
			if ((item.getOwnerId() != player.getObjectId()) || !item.isAugmented())
			{
				continue;
			}
			any = true;
			html.append("<tr><td align=center><table width=430><tr>");
			html.append("<td width=36 align=center><img src=\"").append(item.getTemplate().getIcon()).append("\" width=32 height=32></td>");
			html.append("<td width=290>").append(item.getName()).append("</td>");
			html.append("<td width=100 align=center><button value=\"Remover\" action=\"bypass _bbsblacksmith;remove;").append(item.getObjectId()).append("\" width=90 height=22 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td>");
			html.append("</tr></table></td></tr>");
		}
		if (!any)
		{
			html.append("<tr><td align=center><font color=AAAAAA>Nenhum item augmentado encontrado.</font></td></tr>");
		}
		return subclassPageFooter(html, "_bbstop;custom/blacksmith.html");
	}

	private static String removeBlacksmithAugment(Player player, int itemObjId)
	{
		final Item item = player.getInventory().getItemByObjectId(itemObjId);
		if ((item == null) || (item.getOwnerId() != player.getObjectId()) || !item.isAugmented())
		{
			player.sendMessage("Esse item nao esta augmentado.");
			return buildBlacksmithRemoveList(player);
		}

		item.removeAugmentation();
		final InventoryUpdate iu = new InventoryUpdate();
		iu.addModifiedItem(item);
		player.sendPacket(iu);
		player.sendMessage("Augment removido de " + item.getName() + ".");
		// Stay on this same item's stone picker so re-augmenting doesn't require reselecting it from the list.
		return buildBlacksmithStoneList(player, itemObjId);
	}

	// ============ Mass Enchant (repeats enchant attempts server-side, no native window) ============
	private static final int[] MASSENCHANT_QUANTITIES = { 1, 5, 10, 25, 1000 };

	private static String handleMassEnchantCommand(String command, Player player)
	{
		final String[] parts = command.split(";");
		final String action = parts.length > 1 ? parts[1] : "itemlist";
		switch (action)
		{
			case "itemlist":
			{
				return buildMassEnchantItemList(player);
			}
			case "scroll":
			{
				if (parts.length > 2)
				{
					return buildMassEnchantScrollList(player, Integer.parseInt(parts[2]));
				}
				return buildMassEnchantItemList(player);
			}
			case "run":
			{
				if (parts.length > 4)
				{
					return runMassEnchant(player, Integer.parseInt(parts[2]), Integer.parseInt(parts[3]), Integer.parseInt(parts[4]));
				}
				return buildMassEnchantItemList(player);
			}
			default:
			{
				return buildMassEnchantItemList(player);
			}
		}
	}

	private static String buildMassEnchantItemList(Player player)
	{
		final StringBuilder html = subclassPageHeader("Enchant Rapido", "Escolha o item que deseja enchantar. Sem precisar arrastar nada, so escolher o scroll e a quantidade.");
		html.append("<tr><td align=center><button value=\"Comprar Scrolls\" action=\"bypass _bbsmultisell;600012,custom/main\" width=180 height=26 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td></tr>");
		html.append("<tr><td height=8></td></tr>");
		boolean any = false;
		for (Item item : player.getInventory().getItems())
		{
			if ((item.getOwnerId() != player.getObjectId()) || !item.isEnchantable())
			{
				continue;
			}
			any = true;
			html.append("<tr><td align=center><table width=430><tr>");
			html.append("<td width=36 align=center><img src=\"").append(item.getTemplate().getIcon()).append("\" width=32 height=32></td>");
			html.append("<td width=290>").append(item.getName()).append(item.getEnchantLevel() > 0 ? (" +" + item.getEnchantLevel()) : "").append(item.isEquipped() ? " (equipado)" : "").append("</td>");
			html.append("<td width=100 align=center><button value=\"Escolher\" action=\"bypass _bbsmassenchant;scroll;").append(item.getObjectId()).append("\" width=90 height=22 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td>");
			html.append("</tr></table></td></tr>");
		}
		if (!any)
		{
			html.append("<tr><td align=center><font color=AAAAAA>Nenhum item enchantavel encontrado.</font></td></tr>");
		}
		return subclassPageFooter(html, "_bbstop;custom/main.html");
	}

	private static String buildMassEnchantScrollList(Player player, int itemObjId)
	{
		final Item target = player.getInventory().getItemByObjectId(itemObjId);
		if ((target == null) || (target.getOwnerId() != player.getObjectId()) || !target.isEnchantable())
		{
			player.sendMessage("Esse item nao esta mais disponivel para enchant.");
			return buildMassEnchantItemList(player);
		}

		final StringBuilder html = subclassPageHeader("Enchant Rapido: " + target.getName(), "Escolha o scroll e quantas tentativas seguidas aplicar.");
		final java.util.Set<Integer> seenScrollIds = new java.util.HashSet<>();
		boolean any = false;
		for (Item stack : player.getInventory().getItems())
		{
			if (!seenScrollIds.add(stack.getId()))
			{
				continue;
			}
			final EnchantScroll scrollTemplate = EnchantItemData.getInstance().getEnchantScroll(stack);
			if ((scrollTemplate == null) || !scrollTemplate.isValid(target, null))
			{
				continue;
			}
			any = true;
			final long owned = player.getInventory().getInventoryItemCount(stack.getId(), -1);
			html.append("<tr><td align=center><table width=440><tr>");
			html.append("<td width=36 align=center><img src=\"").append(stack.getTemplate().getIcon()).append("\" width=32 height=32></td>");
			html.append("<td width=230>").append(stack.getName()).append(" (voce tem: ").append(owned).append(")</td>");
			for (int qty : MASSENCHANT_QUANTITIES)
			{
				final String label = (qty >= 1000) ? "Max" : ("x" + qty);
				html.append("<td width=").append(qty >= 1000 ? 45 : 35).append(" align=center><button value=\"").append(label).append("\" action=\"bypass _bbsmassenchant;run;").append(itemObjId).append(";").append(stack.getId()).append(";").append(qty).append("\" width=").append(qty >= 1000 ? 42 : 32).append(" height=22 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td>");
			}
			html.append("</tr></table></td></tr>");
		}
		if (!any)
		{
			html.append("<tr><td align=center><font color=AAAAAA>Nenhum scroll compativel encontrado no seu inventario.</font></td></tr>");
		}
		html.append("<tr><td height=8></td></tr>");
		html.append("<tr><td align=center><button value=\"Escolher novo item\" action=\"bypass _bbsmassenchant;itemlist\" width=200 height=24 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td></tr>");
		return subclassPageFooter(html, "_bbstop;custom/main.html");
	}

	private static String runMassEnchant(Player player, int itemObjId, int scrollItemId, int attempts)
	{
		final Item item = player.getInventory().getItemByObjectId(itemObjId);
		if ((item == null) || (item.getOwnerId() != player.getObjectId()) || !item.isEnchantable())
		{
			player.sendMessage("Esse item nao esta mais disponivel para enchant.");
			return buildMassEnchantItemList(player);
		}

		final int startLevel = item.getEnchantLevel();
		int successes = 0;
		int failures = 0;
		boolean destroyed = false;
		String stopReason = "Tentativas concluidas.";

		for (int i = 0; i < attempts; i++)
		{
			final Item scroll = player.getInventory().getItemByItemId(scrollItemId);
			if (scroll == null)
			{
				stopReason = "Acabaram os scrolls.";
				break;
			}

			final EnchantScroll scrollTemplate = EnchantItemData.getInstance().getEnchantScroll(scroll);
			if (scrollTemplate == null)
			{
				stopReason = "Scroll invalido.";
				break;
			}

			if (!scrollTemplate.isValid(item, null) || (PlayerConfig.DISABLE_OVER_ENCHANTING && (item.getEnchantLevel() == scrollTemplate.getMaxEnchantLevel())))
			{
				stopReason = "Item chegou no limite desse scroll.";
				break;
			}

			if ((PlayerConfig.NORMAL_SCROLL_MAX_ENCHANT > 0) && !scrollTemplate.isBlessed() && !scrollTemplate.isSafe() && (item.getEnchantLevel() >= PlayerConfig.NORMAL_SCROLL_MAX_ENCHANT))
			{
				stopReason = "A partir de +" + PlayerConfig.NORMAL_SCROLL_MAX_ENCHANT + " use um Blessed Scroll para continuar.";
				break;
			}

			if (player.getInventory().destroyItem(ItemProcessType.FEE, scroll.getObjectId(), 1, player, item) == null)
			{
				stopReason = "Falha ao consumir o scroll.";
				break;
			}

			final EnchantResultType resultType = scrollTemplate.calculateSuccess(player, item, null);
			if (resultType == EnchantResultType.SUCCESS)
			{
				if (scrollTemplate.getChance(player, item) > 0)
				{
					item.setEnchantLevel(item.getEnchantLevel() + 1);
					item.updateDatabase();
				}
				successes++;
				if (item.isArmor() && (item.getEnchantLevel() == 4) && item.isEquipped())
				{
					final Skill enchant4Skill = item.getTemplate().getEnchant4Skill();
					if (enchant4Skill != null)
					{
						player.addSkill(enchant4Skill, false);
					}
				}
			}
			else if (resultType == EnchantResultType.FAILURE)
			{
				failures++;
				if (!scrollTemplate.isSafe() && !scrollTemplate.isBlessed())
				{
					player.getInventory().destroyItem(ItemProcessType.DESTROY, item, player, null);
					destroyed = true;
					stopReason = "O item quebrou.";
					break;
				}
			}
			else
			{
				stopReason = "Condicoes de enchant invalidas.";
				break;
			}
		}

		player.sendItemList(false);
		player.broadcastUserInfo();

		final StringBuilder html = subclassPageHeader("Resultado", stopReason);
		html.append("<tr><td align=center>Sucessos: <font color=LEVEL>").append(successes).append("</font> / Falhas: <font color=FF6666>").append(failures).append("</font></td></tr>");
		if (destroyed)
		{
			html.append("<tr><td align=center><font color=FF6666>").append(item.getName()).append(" foi destruido.</font></td></tr>");
		}
		else
		{
			html.append("<tr><td align=center>").append(item.getName()).append(": +").append(startLevel).append(" -> +").append(item.getEnchantLevel()).append("</td></tr>");
			html.append("<tr><td height=8></td></tr>");
			html.append("<tr><td align=center><button value=\"Continuar com esse item/scroll\" action=\"bypass _bbsmassenchant;scroll;").append(itemObjId).append("\" width=220 height=26 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td></tr>");
		}
		html.append("<tr><td height=6></td></tr>");
		html.append("<tr><td align=center><button value=\"Escolher novo item\" action=\"bypass _bbsmassenchant;itemlist\" width=200 height=24 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td></tr>");
		return subclassPageFooter(html, "_bbstop;custom/main.html");
	}

	// ============ Augment Pick (choose an exact augment option, pay adena, apply to a chosen weapon) ============
	private static final long AUGMENTPICK_PRICE = 10000000L;
	private static final int AUGMENTPICK_PAGE_SIZE = 15;
	private static final int AUGMENTPICK_MAX_OPTION_ID = 30000;
	// Retail always packs a real stat option in the low slot (stat12) and the skill in the high slot
	// (stat34) -- see AugmentationData's own generators, which always do "(stat34 << 16) + stat12" with
	// stat12 pulled from a stats-only id range. A skill-only augment (skill id in stat12, 0 in stat34) is
	// backwards from that convention and renders as a broken/black icon client-side. Option id 1 is a
	// harmless small P.Def bonus (+15.45) used here purely to keep the low slot valid.
	private static final int AUGMENTPICK_FILLER_STAT_OPTION_ID = 1;
	private static List<AugmentOptionEntry> augmentPickPassive;
	private static List<AugmentOptionEntry> augmentPickChance;
	private static List<AugmentOptionEntry> augmentPickActive;

	private static final class AugmentOptionEntry
	{
		final int id;
		final String name;

		AugmentOptionEntry(int id, String name)
		{
			this.id = id;
			this.name = name;
		}
	}

	// Options are read once from OptionData (already fully loaded at server start) and cached, since
	// scanning ~30000 ids on every page view would be wasteful.
	private static synchronized void ensureAugmentPickOptionsLoaded()
	{
		if (augmentPickPassive != null)
		{
			return;
		}

		final List<AugmentOptionEntry> passive = new ArrayList<>();
		final List<AugmentOptionEntry> chance = new ArrayList<>();
		final List<AugmentOptionEntry> active = new ArrayList<>();
		for (int id = 0; id < AUGMENTPICK_MAX_OPTION_ID; id++)
		{
			final Options option = OptionData.getInstance().getOptions(id);
			if (option == null)
			{
				continue;
			}
			if (option.hasPassiveSkill())
			{
				final Skill skill = option.getPassiveSkill();
				passive.add(new AugmentOptionEntry(id, skill.getName() + " Lv" + skill.getLevel()));
			}
			else if (option.hasActiveSkill())
			{
				final Skill skill = option.getActiveSkill();
				active.add(new AugmentOptionEntry(id, skill.getName() + " Lv" + skill.getLevel()));
			}
			else if (option.hasActivationSkills())
			{
				final OptionSkillHolder holder = option.getActivationSkills().get(0);
				final Skill skill = holder.getSkill();
				chance.add(new AugmentOptionEntry(id, skill.getName() + " Lv" + skill.getLevel() + " (chance " + holder.getChance() + "%, " + holder.getSkillType() + ")"));
			}
		}
		augmentPickPassive = passive;
		augmentPickChance = chance;
		augmentPickActive = active;
	}

	private static List<AugmentOptionEntry> getAugmentPickOptions(String category)
	{
		ensureAugmentPickOptionsLoaded();
		switch (category)
		{
			case "PASSIVE":
			{
				return augmentPickPassive;
			}
			case "CHANCE":
			{
				return augmentPickChance;
			}
			case "ACTIVE":
			{
				return augmentPickActive;
			}
			default:
			{
				return java.util.Collections.emptyList();
			}
		}
	}

	private static String augmentPickCategoryLabel(String category)
	{
		if ("PASSIVE".equals(category))
		{
			return "Passivos";
		}
		if ("CHANCE".equals(category))
		{
			return "Chance";
		}
		return "Ativos";
	}

	private static String handleAugmentPickCommand(String command, Player player)
	{
		final String[] parts = command.split(";");
		final String action = parts.length > 1 ? parts[1] : "menu";
		switch (action)
		{
			case "list":
			{
				if (parts.length > 3)
				{
					return buildAugmentPickList(player, parts[2], Integer.parseInt(parts[3]));
				}
				return buildAugmentPickMenu(player);
			}
			case "pick":
			{
				if (parts.length > 2)
				{
					return buildAugmentPickWeaponList(player, Integer.parseInt(parts[2]));
				}
				return buildAugmentPickMenu(player);
			}
			case "apply":
			{
				if (parts.length > 3)
				{
					return applyAugmentPick(player, Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
				}
				return buildAugmentPickMenu(player);
			}
			default:
			{
				return buildAugmentPickMenu(player);
			}
		}
	}

	private static String buildAugmentPickMenu(Player player)
	{
		final StringBuilder html = subclassPageHeader("Augment Pick", "Escolha a categoria. Preco fixo de " + AUGMENTPICK_PRICE + " adena por aplicacao.");
		html.append("<tr><td align=center><button value=\"Passivos\" action=\"bypass _bbsaugmentpick;list;PASSIVE;0\" width=180 height=28 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td></tr>");
		html.append("<tr><td align=center><button value=\"Chance\" action=\"bypass _bbsaugmentpick;list;CHANCE;0\" width=180 height=28 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td></tr>");
		html.append("<tr><td align=center><button value=\"Ativos\" action=\"bypass _bbsaugmentpick;list;ACTIVE;0\" width=180 height=28 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td></tr>");
		return subclassPageFooter(html, "_bbstop;custom/blacksmith.html");
	}

	private static String buildAugmentPickList(Player player, String category, int page)
	{
		final List<AugmentOptionEntry> options = getAugmentPickOptions(category);
		final int totalPages = Math.max(1, (options.size() + AUGMENTPICK_PAGE_SIZE - 1) / AUGMENTPICK_PAGE_SIZE);
		final int safePage = Math.max(0, Math.min(page, totalPages - 1));
		final StringBuilder html = subclassPageHeader("Augment Pick: " + augmentPickCategoryLabel(category), "Pagina " + (safePage + 1) + " de " + totalPages + " (" + options.size() + " opcoes).");
		final int start = safePage * AUGMENTPICK_PAGE_SIZE;
		final int end = Math.min(start + AUGMENTPICK_PAGE_SIZE, options.size());
		for (int i = start; i < end; i++)
		{
			final AugmentOptionEntry entry = options.get(i);
			html.append("<tr><td align=center><table width=440><tr>");
			html.append("<td width=340>").append(entry.name).append("</td>");
			html.append("<td width=100 align=center><button value=\"Selecionar\" action=\"bypass _bbsaugmentpick;pick;").append(entry.id).append("\" width=90 height=22 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td>");
			html.append("</tr></table></td></tr>");
		}
		html.append("<tr><td height=8></td></tr>");
		html.append("<tr><td align=center><table><tr>");
		if (safePage > 0)
		{
			html.append("<td><button value=\"Anterior\" action=\"bypass _bbsaugmentpick;list;").append(category).append(";").append(safePage - 1).append("\" width=90 height=22 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td>");
		}
		if (safePage < (totalPages - 1))
		{
			html.append("<td><button value=\"Proximo\" action=\"bypass _bbsaugmentpick;list;").append(category).append(";").append(safePage + 1).append("\" width=90 height=22 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td>");
		}
		html.append("</tr></table></td></tr>");
		return subclassPageFooter(html, "_bbsaugmentpick");
	}

	private static String buildAugmentPickWeaponList(Player player, int optionId)
	{
		if (OptionData.getInstance().getOptions(optionId) == null)
		{
			player.sendMessage("Augment invalido.");
			return buildAugmentPickMenu(player);
		}

		final StringBuilder html = subclassPageHeader("Escolha a arma", "O augment sera aplicado na arma escolhida, custando " + AUGMENTPICK_PRICE + " adena.");
		boolean any = false;
		for (Item item : player.getInventory().getItems())
		{
			if (blacksmithRejectReason(player, item) != null)
			{
				continue;
			}
			any = true;
			html.append("<tr><td align=center><table width=430><tr>");
			html.append("<td width=36 align=center><img src=\"").append(item.getTemplate().getIcon()).append("\" width=32 height=32></td>");
			html.append("<td width=290>").append(item.getName()).append(item.getEnchantLevel() > 0 ? (" +" + item.getEnchantLevel()) : "").append("</td>");
			html.append("<td width=100 align=center><button value=\"Aplicar\" action=\"bypass _bbsaugmentpick;apply;").append(optionId).append(";").append(item.getObjectId()).append("\" width=90 height=22 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td>");
			html.append("</tr></table></td></tr>");
		}
		if (!any)
		{
			html.append("<tr><td align=center><font color=AAAAAA>Nenhuma arma augmentavel encontrada (precisa ser C-grade+, nao augmentada e um tipo de arma valido).</font></td></tr>");
		}
		return subclassPageFooter(html, "_bbsaugmentpick");
	}

	private static String applyAugmentPick(Player player, int optionId, int itemObjId)
	{
		if (OptionData.getInstance().getOptions(optionId) == null)
		{
			player.sendMessage("Augment invalido.");
			return buildAugmentPickMenu(player);
		}

		final Item target = player.getInventory().getItemByObjectId(itemObjId);
		if (!isBlacksmithAugmentable(player, target))
		{
			player.sendMessage("Esse item nao esta mais disponivel para augment.");
			return buildAugmentPickMenu(player);
		}

		if (player.getInventory().getInventoryItemCount(57, -1) < AUGMENTPICK_PRICE)
		{
			player.sendMessage("Voce precisa de " + AUGMENTPICK_PRICE + " adena.");
			return buildAugmentPickWeaponList(player, optionId);
		}

		if (target.isEquipped())
		{
			final InventoryUpdate iu = new InventoryUpdate();
			for (Item unequipped : player.getInventory().unEquipItemInSlotAndRecord(target.getLocationSlot()))
			{
				iu.addModifiedItem(unequipped);
			}
			player.sendPacket(iu);
			player.broadcastUserInfo();
		}

		if (!player.reduceAdena(ItemProcessType.FEE, AUGMENTPICK_PRICE, target, true))
		{
			player.sendMessage("Falha ao cobrar adena.");
			return buildAugmentPickWeaponList(player, optionId);
		}

		target.setAugmentation(new Augmentation((optionId << 16) + AUGMENTPICK_FILLER_STAT_OPTION_ID));

		final InventoryUpdate iu = new InventoryUpdate();
		iu.addModifiedItem(target);
		player.sendPacket(iu);

		final StringBuilder html = subclassPageHeader("Augment aplicado!", "Confira o efeito passando o mouse sobre o item no inventario.");
		html.append("<tr><td align=center><font color=LEVEL>").append(target.getName()).append("</font> recebeu o augment escolhido.</td></tr>");
		html.append("<tr><td height=10></td></tr>");
		html.append("<tr><td align=center><button value=\"Remover Augment\" action=\"bypass _bbsblacksmith;remove;").append(itemObjId).append("\" width=200 height=26 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td></tr>");
		return subclassPageFooter(html, "_bbsaugmentpick");
	}

	// ============ Raid Boss Info (dynamic status/respawn/teleport) ============
	// @formatter:off
	private static final int[] RAIDBOSS_LV20 = {25001,25019,25038,25060,25076,25095,25127,25146,25149,25166,25272,25357,25360,25362,25365,25366,25369,25372,25373,25375,25378,25380,25426,25429};
	private static final int[] RAIDBOSS_LV30 = {25004,25020,25023,25041,25063,25079,25082,25098,25112,25118,25128,25152,25169,25170,25185,25188,25189,25211,25223,25352,25354,25383,25385,25388,25391,25392,25394,25398,25401,25404};
	private static final int[] RAIDBOSS_LV40 = {25007,25026,25044,25047,25057,25064,25085,25088,25099,25102,25115,25134,25155,25158,25173,25192,25208,25214,25260,25395,25410,25412,25415,25418,25420,25431,25437,25438,25441,25456,25487,25490,25498};
	private static final int[] RAIDBOSS_LV50 = {25010,25013,25029,25032,25050,25067,25070,25089,25103,25119,25122,25131,25137,25159,25176,25182,25217,25230,25238,25241,25259,25273,25277,25280,25434,25460,25463,25473,25475,25481,25484,25493,25496};
	private static final int[] RAIDBOSS_LV60 = {25016,25051,25073,25106,25125,25140,25162,25179,25226,25233,25234,25255,25256,25263,25322,25407,25423,25444,25467,25470,25478};
	private static final int[] RAIDBOSS_LV70 = {25035,25054,25092,25109,25126,25143,25163,25198,25199,25202,25205,25220,25229,25235,25244,25245,25248,25249,25252,25266,25269,25276,25281,25282,25293,25325,25328,25447,25450,25453,25524};
	private static final int[] RAIDBOSS_LV80 = {25283,25286,25299,25302,25305,25306,25309,25312,25315,25316,25319,25514,25527,25539,25623,25624,25625,25626,29062,29065,29096};
	// @formatter:on
	private static final int RAIDBOSS_PAGE_LIMIT = 10;
	private static final int RAIDBOSS_TELEPORT_COST = 100000; // Adena.

	private static int[] raidBossRangeIds(String range)
	{
		switch (range)
		{
			case "20":
			{
				return RAIDBOSS_LV20;
			}
			case "30":
			{
				return RAIDBOSS_LV30;
			}
			case "40":
			{
				return RAIDBOSS_LV40;
			}
			case "50":
			{
				return RAIDBOSS_LV50;
			}
			case "60":
			{
				return RAIDBOSS_LV60;
			}
			case "70":
			{
				return RAIDBOSS_LV70;
			}
			case "80":
			{
				return RAIDBOSS_LV80;
			}
			default:
			{
				return new int[0];
			}
		}
	}

	private static String handleRaidBossCommand(String command, Player player)
	{
		final String[] parts = command.split(";");
		final String action = parts.length > 1 ? parts[1] : "menu";
		switch (action)
		{
			case "list":
			{
				final String range = parts.length > 2 ? parts[2] : "20";
				final int page = (parts.length > 3) ? Integer.parseInt(parts[3]) : 1;
				return buildRaidBossList(player, range, page);
			}
			case "teleport":
			{
				if (parts.length > 2)
				{
					return teleportToRaidBoss(player, Integer.parseInt(parts[2]), parts.length > 3 ? parts[3] : "20", parts.length > 4 ? Integer.parseInt(parts[4]) : 1);
				}
				return buildRaidBossMenu(player);
			}
			default:
			{
				return buildRaidBossMenu(player);
			}
		}
	}

	private static String buildRaidBossMenu(Player player)
	{
		final StringBuilder html = subclassPageHeader("Raid Boss Info", "Status ao vivo, timer de respawn e teleporte ate a sala.");
		final String[][] ranges = { { "20", "Level 20-29" }, { "30", "Level 30-39" }, { "40", "Level 40-49" }, { "50", "Level 50-59" }, { "60", "Level 60-69" }, { "70", "Level 70-79" }, { "80", "Level 80+" } };
		html.append("<tr><td align=center><table>");
		int col = 0;
		for (String[] range : ranges)
		{
			if (col == 0)
			{
				html.append("<tr>");
			}
			html.append("<td><button value=\"").append(range[1]).append("\" action=\"bypass _bbsraidboss;list;").append(range[0]).append(";1\" width=150 height=27 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td>");
			col++;
			if (col == 2)
			{
				html.append("</tr>");
				col = 0;
			}
		}
		if (col != 0)
		{
			html.append("</tr>");
		}
		html.append("</table></td></tr>");
		html.append("<tr><td height=10></td></tr>");
		html.append("<tr><td align=center><font color=LEVEL>Teleporte custa ").append(RAIDBOSS_TELEPORT_COST).append(" adena e so funciona com o boss vivo.</font></td></tr>");
		return subclassPageFooter(html, "_bbstop;custom/main.html");
	}

	private static String formatRaidBossTimeLeft(long millis)
	{
		if (millis <= 0)
		{
			return "a qualquer momento";
		}
		final long totalSeconds = millis / 1000;
		final long hours = totalSeconds / 3600;
		final long minutes = (totalSeconds % 3600) / 60;
		if (hours > 0)
		{
			return hours + "h " + minutes + "min";
		}
		return minutes + "min";
	}

	private static String buildRaidBossList(Player player, String range, int pageValue)
	{
		final int[] ids = raidBossRangeIds(range);
		final RaidBossSpawnManager manager = RaidBossSpawnManager.getInstance();
		final int maxPage = Math.max(1, (ids.length + RAIDBOSS_PAGE_LIMIT - 1) / RAIDBOSS_PAGE_LIMIT);
		final int page = Math.max(1, Math.min(pageValue, maxPage));

		final StringBuilder html = subclassPageHeader("Raid Boss - Level " + range, "Status atualizado em tempo real.");
		html.append("<tr><td align=center><table width=440>");
		final int start = (page - 1) * RAIDBOSS_PAGE_LIMIT;
		final int end = Math.min(start + RAIDBOSS_PAGE_LIMIT, ids.length);
		for (int i = start; i < end; i++)
		{
			final int bossId = ids[i];
			final NpcTemplate template = NpcData.getInstance().getTemplate(bossId);
			final String name = template != null ? template.getName() : ("Boss #" + bossId);
			final RaidBossStatus status = manager.getRaidBossStatusId(bossId);
			final boolean alive = status == RaidBossStatus.ALIVE;

			html.append("<tr><td height=32><table width=436><tr>");
			html.append("<td width=190>").append(name).append("</td>");
			if (alive)
			{
				html.append("<td width=90 align=center><font color=55FF55>Vivo</font></td>");
				html.append("<td width=156 align=center><button value=\"Teleportar\" action=\"bypass _bbsraidboss;teleport;").append(bossId).append(";").append(range).append(";").append(page).append("\" width=90 height=22 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td>");
			}
			else
			{
				html.append("<td width=90 align=center><font color=FF5555>Morto</font></td>");
				final StatSet info = manager.getStoredInfo().get(bossId);
				final long respawnTime = info != null ? info.getLong("respawnTime", 0) : 0;
				final String timeLeft = formatRaidBossTimeLeft(respawnTime - System.currentTimeMillis());
				html.append("<td width=156 align=center><font color=AAAAAA>Respawn: ").append(timeLeft).append("</font></td>");
			}
			html.append("</tr></table></td></tr>");
		}
		html.append("</table></td></tr>");

		html.append("<tr><td height=8></td></tr>");
		html.append("<tr><td align=center><table><tr>");
		if (page > 1)
		{
			html.append("<td><button value=\"Anterior\" action=\"bypass _bbsraidboss;list;").append(range).append(";").append(page - 1).append("\" width=80 height=22 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td>");
		}
		html.append("<td width=90 align=center>Pagina ").append(page).append("/").append(maxPage).append("</td>");
		if (page < maxPage)
		{
			html.append("<td><button value=\"Proxima\" action=\"bypass _bbsraidboss;list;").append(range).append(";").append(page + 1).append("\" width=80 height=22 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td>");
		}
		html.append("</tr></table></td></tr>");
		return subclassPageFooter(html, "_bbsraidboss");
	}

	private static String teleportToRaidBoss(Player player, int bossId, String range, int page)
	{
		final RaidBossSpawnManager manager = RaidBossSpawnManager.getInstance();
		if (manager.getRaidBossStatusId(bossId) != RaidBossStatus.ALIVE)
		{
			player.sendMessage("Esse raid boss nao esta vivo no momento.");
			return buildRaidBossList(player, range, page);
		}

		final Spawn spawn = manager.getSpawns().get(bossId);
		if (spawn == null)
		{
			player.sendMessage("Local de spawn nao encontrado.");
			return buildRaidBossList(player, range, page);
		}

		if (player.getAdena() < RAIDBOSS_TELEPORT_COST)
		{
			player.sendMessage("Voce precisa de " + RAIDBOSS_TELEPORT_COST + " adena para teleportar.");
			return buildRaidBossList(player, range, page);
		}

		player.reduceAdena(ItemProcessType.FEE, RAIDBOSS_TELEPORT_COST, player, true);
		player.teleToLocation(spawn.getX(), spawn.getY(), spawn.getZ(), 0);
		return buildRaidBossMenu(player);
	}
}
