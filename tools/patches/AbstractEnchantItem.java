package org.l2jmobius.gameserver.model.item.enchant;

import java.util.EnumSet;
import java.util.Set;
import java.util.logging.Logger;

import org.l2jmobius.gameserver.data.xml.ItemData;
import org.l2jmobius.gameserver.model.StatSet;
import org.l2jmobius.gameserver.model.item.ItemTemplate;
import org.l2jmobius.gameserver.model.item.instance.Item;
import org.l2jmobius.gameserver.model.item.type.CrystalType;
import org.l2jmobius.gameserver.model.item.type.EtcItemType;

/**
 * Allows S-grade enchant scrolls to be used with S, S80 and S84 equipment.
 */
public abstract class AbstractEnchantItem
{
	protected static final Logger LOGGER = Logger.getLogger(AbstractEnchantItem.class.getName());
	private static final Set<EtcItemType> ENCHANT_TYPES = EnumSet.of(EtcItemType.ANCIENT_CRYSTAL_ENCHANT_AM, EtcItemType.ANCIENT_CRYSTAL_ENCHANT_WP, EtcItemType.BLESS_SCRL_ENCHANT_AM, EtcItemType.BLESS_SCRL_ENCHANT_WP, EtcItemType.SCRL_ENCHANT_AM, EtcItemType.SCRL_ENCHANT_WP, EtcItemType.SCRL_INC_ENCHANT_PROP_AM, EtcItemType.SCRL_INC_ENCHANT_PROP_WP);

	private final int _id;
	private final CrystalType _grade;
	private final int _maxEnchantLevel;
	private final double _bonusRate;

	public AbstractEnchantItem(StatSet set)
	{
		_id = set.getInt("id");
		if (getItem() == null)
		{
			throw new NullPointerException();
		}
		if (!ENCHANT_TYPES.contains(getItem().getItemType()))
		{
			throw new IllegalAccessError();
		}
		_grade = set.getEnum("targetGrade", CrystalType.class, CrystalType.NONE);
		_maxEnchantLevel = set.getInt("maxEnchant", 65535);
		_bonusRate = set.getDouble("bonusRate", 0);
	}

	public int getId()
	{
		return _id;
	}

	public double getBonusRate()
	{
		return _bonusRate;
	}

	public ItemTemplate getItem()
	{
		return ItemData.getInstance().getTemplate(_id);
	}

	public CrystalType getGrade()
	{
		return _grade;
	}

	public abstract boolean isWeapon();

	public int getMaxEnchantLevel()
	{
		return _maxEnchantLevel;
	}

	public boolean isValid(Item item, EnchantSupportItem supportItem)
	{
		if ((item == null) || !item.isEnchantable() || !isValidItemType(item.getTemplate().getType2()) || ((_maxEnchantLevel != 0) && (item.getEnchantLevel() >= _maxEnchantLevel)))
		{
			return false;
		}

		final CrystalType itemGrade = item.getTemplate().getCrystalTypePlus();
		return (_grade == itemGrade) || ((_grade == CrystalType.S) && ((itemGrade == CrystalType.S80) || (itemGrade == CrystalType.S84)));
	}

	private boolean isValidItemType(int type)
	{
		if (type == 0)
		{
			return isWeapon();
		}
		return ((type == 1) || (type == 2)) && !isWeapon();
	}
}
