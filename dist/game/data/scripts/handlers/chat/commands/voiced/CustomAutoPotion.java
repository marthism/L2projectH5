package handlers.chat.commands.voiced;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.l2jmobius.commons.threads.ThreadPool;
import org.l2jmobius.gameserver.config.custom.AutoPotionsConfig;
import org.l2jmobius.gameserver.handler.IItemHandler;
import org.l2jmobius.gameserver.handler.IVoicedCommandHandler;
import org.l2jmobius.gameserver.handler.ItemHandler;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.item.instance.Item;
import org.l2jmobius.gameserver.network.serverpackets.NpcHtmlMessage;

/** Compact, per-character auto-potion panel opened with .autopotion. */
public class CustomAutoPotion implements IVoicedCommandHandler, Runnable
{
	private static final String[] COMMANDS = { "autopotion" };
	private static final String ENABLED = "CustomAutoPotionEnabled";
	private static final String HP_ITEM = "CustomAutoPotionHpItem";
	private static final String MP_ITEM = "CustomAutoPotionMpItem";
	private static final String CP_ITEM = "CustomAutoPotionCpItem";
	private static final String HP_PERCENT = "CustomAutoPotionHpPercent";
	private static final String MP_PERCENT = "CustomAutoPotionMpPercent";
	private static final String CP_PERCENT = "CustomAutoPotionCpPercent";
	private static final Set<Player> PLAYERS = ConcurrentHashMap.newKeySet();

	public CustomAutoPotion()
	{
		ThreadPool.schedulePriorityTaskAtFixedRate(this, 1000, 1000);
	}

	@Override
	public String[] getCommandList()
	{
		return COMMANDS;
	}

	@Override
	public boolean onCommand(String command, Player player, String params)
	{
		if (!AutoPotionsConfig.AUTO_POTIONS_ENABLED || (player == null))
		{
			return false;
		}
		final String[] parts = (params == null) || params.isBlank() ? new String[0] : params.trim().split(" ");
		if ((parts.length > 0) && "toggle".equalsIgnoreCase(parts[0]))
		{
			final boolean enabled = !player.getVariables().getBoolean(ENABLED, false);
			player.getVariables().set(ENABLED, enabled);
			if (enabled)
			{
				PLAYERS.add(player);
			}
			else
			{
				PLAYERS.remove(player);
			}
		}
		else if ((parts.length > 1) && "select".equalsIgnoreCase(parts[0]))
		{
			sendPotionSelector(player, parts[1].toLowerCase());
			return true;
		}
		else if ((parts.length > 2) && "choose".equalsIgnoreCase(parts[0]))
		{
			choosePotion(player, parts[1].toLowerCase(), parts[2]);
		}
		else if ((parts.length > 1) && "editpanel".equalsIgnoreCase(parts[0]))
		{
			sendWindow(player, parts[1].toLowerCase());
			return true;
		}
		else if ((parts.length > 2) && "percent".equalsIgnoreCase(parts[0]))
		{
			setPercent(player, percentKey(parts[1].toLowerCase()), parts[2]);
		}
		sendWindow(player, null);
		return true;
	}

	private static void setPercent(Player player, String key, String value)
	{
		try
		{
			player.getVariables().set(key, Math.max(1, Math.min(100, Integer.parseInt(value))));
		}
		catch (NumberFormatException e)
		{
			player.sendMessage("Use um percentual entre 1 e 100.");
		}
	}

	private static String percentKey(String type)
	{
		return "hp".equals(type) ? HP_PERCENT : ("mp".equals(type) ? MP_PERCENT : CP_PERCENT);
	}

	private static Set<Integer> getPotionIds(String type)
	{
		switch (type)
		{
			case "hp": return AutoPotionsConfig.AUTO_HP_ITEM_IDS;
			case "mp": return AutoPotionsConfig.AUTO_MP_ITEM_IDS;
			default: return AutoPotionsConfig.AUTO_CP_ITEM_IDS;
		}
	}

	private static String getItemVariable(String type)
	{
		return "hp".equals(type) ? HP_ITEM : ("mp".equals(type) ? MP_ITEM : CP_ITEM);
	}

	private static List<Integer> availablePotions(Player player, String type)
	{
		final List<Integer> available = new ArrayList<>();
		for (int itemId : getPotionIds(type))
		{
			if (player.getInventory().getInventoryItemCount(itemId, -1) > 0)
			{
				available.add(itemId);
			}
		}
		Collections.sort(available);
		return available;
	}

	private static void choosePotion(Player player, String type, String value)
	{
		try
		{
			final int itemId = Integer.parseInt(value);
			if (availablePotions(player, type).contains(itemId))
			{
				player.getVariables().set(getItemVariable(type), itemId);
			}
		}
		catch (NumberFormatException e)
		{
			player.sendMessage("Pocao invalida.");
		}
	}

	private static Item potionItem(Player player, String type)
	{
		final int itemId = player.getVariables().getInt(getItemVariable(type), 0);
		return player.getInventory().getItemByItemId(itemId);
	}

	private static int percent(Player player, String key, int fallback)
	{
		return player.getVariables().getInt(key, fallback);
	}

	private static void sendWindow(Player player, String editingType)
	{
		final boolean enabled = player.getVariables().getBoolean(ENABLED, false);
		final NpcHtmlMessage html = new NpcHtmlMessage();
		final StringBuilder content = new StringBuilder(900);
		content.append("<html noscrollbar><title>Auto Potion</title><body><center>");
		content.append("<table width=270 cellpadding=0 cellspacing=0 bgcolor=000000><tr><td align=center><font color=LEVEL>AUTO POTION</font></td></tr></table>");
		content.append("<table width=270 cellpadding=2 cellspacing=0><tr><td width=190>Ativado</td><td width=80 align=right><button value=\"").append(enabled ? "ON" : "OFF").append("\" action=\"bypass voice .autopotion toggle\" width=70 height=20 back=\"L2UI_CT1.Button_DF\" fore=\"L2UI_CT1.Button_DF\"></td></tr></table>");
		content.append("<table width=270 cellpadding=6 cellspacing=0><tr>");
		appendPotionColumn(content, player, "HP", "hp");
		appendPotionColumn(content, player, "MP", "mp");
		appendPotionColumn(content, player, "CP", "cp");
		content.append("</tr></table>");
		if (editingType != null)
		{
			final int current = percent(player, percentKey(editingType), AutoPotionsConfig.AUTO_HP_PERCENTAGE);
			content.append("<table width=270 cellpadding=2 cellspacing=0><tr><td align=center>Ativar ").append(editingType.toUpperCase()).append(" abaixo de <edit var=\"pct\" width=30 height=15> % (atual: <font color=LEVEL>").append(current).append("%</font>)</td></tr>");
			content.append("<tr><td align=center><button value=\"Salvar\" action=\"bypass voice .autopotion percent ").append(editingType).append(" $pct\" width=80 height=22 back=\"L2UI_CT1.Button_DF\" fore=\"L2UI_CT1.Button_DF\"></td></tr></table>");
		}
		content.append("</center></body></html>");
		html.setHtml(content.toString());
		player.sendPacket(html);
	}

	private static void appendPotionColumn(StringBuilder content, Player player, String label, String type)
	{
		final Item item = potionItem(player, type);
		final String icon = item == null ? "icon.etc_reagent_white_i00" : item.getTemplate().getIcon();
		content.append("<td width=90 align=center>");
		content.append("<font color=LEVEL>").append(label).append("</font><br1>");
		content.append("<img src=\"").append(icon).append("\" width=32 height=32><br1>");
		content.append("<button value=\"Selecionar\" action=\"bypass voice .autopotion select ").append(type).append("\" width=80 height=20 back=\"L2UI_CT1.Button_DF\" fore=\"L2UI_CT1.Button_DF\"><br1>");
		content.append("<button value=\"Editar\" action=\"bypass voice .autopotion editpanel ").append(type).append("\" width=80 height=20 back=\"L2UI_CT1.Button_DF\" fore=\"L2UI_CT1.Button_DF\">");
		content.append("</td>");
	}

	private static void sendPotionSelector(Player player, String type)
	{
		if (!("hp".equals(type) || "mp".equals(type) || "cp".equals(type)))
		{
			sendWindow(player, null);
			return;
		}
		final NpcHtmlMessage html = new NpcHtmlMessage();
		final StringBuilder content = new StringBuilder("<html noscrollbar><title>Auto Potion</title><body><center><table width=190 bgcolor=000000><tr><td align=center><font color=LEVEL>Selecionar pocao ").append(type.toUpperCase()).append("</font></td></tr></table><br>");
		final List<Integer> potions = availablePotions(player, type);
		if (potions.isEmpty())
		{
			content.append("Nenhuma pocao compativel no inventario.<br>");
		}
		else
		{
			for (int itemId : potions)
			{
				final Item item = player.getInventory().getItemByItemId(itemId);
				content.append("<button value=\"").append(item.getName()).append("\" action=\"bypass voice .autopotion choose ").append(type).append(" ").append(itemId).append("\" width=150 height=22 back=\"L2UI_CT1.Button_DF\" fore=\"L2UI_CT1.Button_DF\"><br1>");
			}
		}
		content.append("<br><button value=\"Voltar\" action=\"bypass voice .autopotion\" width=100 height=22 back=\"L2UI_CT1.Button_DF\" fore=\"L2UI_CT1.Button_DF\"></center></body></html>");
		html.setHtml(content.toString());
		player.sendPacket(html);
	}

	@Override
	public void run()
	{
		for (Player player : PLAYERS)
		{
			if ((player == null) || !player.isOnline() || player.isAlikeDead() || !player.getVariables().getBoolean(ENABLED, false) || (!AutoPotionsConfig.AUTO_POTIONS_IN_OLYMPIAD && player.isInOlympiadMode()))
			{
				PLAYERS.remove(player);
				continue;
			}
			tryUse(player, HP_ITEM, HP_PERCENT, player.getStatus().getCurrentHp(), player.getMaxHp(), AutoPotionsConfig.AUTO_HP_PERCENTAGE);
			tryUse(player, MP_ITEM, MP_PERCENT, player.getStatus().getCurrentMp(), player.getMaxMp(), AutoPotionsConfig.AUTO_MP_PERCENTAGE);
			tryUse(player, CP_ITEM, CP_PERCENT, player.getStatus().getCurrentCp(), player.getMaxCp(), AutoPotionsConfig.AUTO_CP_PERCENTAGE);
		}
	}

	private static void tryUse(Player player, String itemKey, String percentKey, double current, int maximum, int fallback)
	{
		if ((maximum <= 0) || ((current * 100.0 / maximum) > percent(player, percentKey, fallback)))
		{
			return;
		}
		final Item item = player.getInventory().getItemByItemId(player.getVariables().getInt(itemKey, 0));
		if ((item == null) || (item.getCount() < 1))
		{
			return;
		}
		final IItemHandler handler = ItemHandler.getInstance().getHandler(item.getEtcItem());
		if (handler != null)
		{
			handler.onItemUse(player, item, false);
		}
	}
}
