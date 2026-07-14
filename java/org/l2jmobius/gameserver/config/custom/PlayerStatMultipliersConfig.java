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
package org.l2jmobius.gameserver.config.custom;

import org.l2jmobius.commons.util.ConfigReader;

/**
 * This class loads all the custom Player stat multiplier related configurations.
 * @author Mobius
 */
public class PlayerStatMultipliersConfig
{
	// File
	private static final String PLAYER_STAT_MULTIPLIERS_CONFIG_FILE = "./config/Custom/PlayerStatMultipliers.ini";

	// Constants
	public static boolean ENABLE_PLAYER_STAT_MULTIPLIERS;
	public static double PLAYER_HP_MULTIPLIER;
	public static double PLAYER_MP_MULTIPLIER;
	public static double PLAYER_CP_MULTIPLIER;
	public static double PLAYER_PATK_MULTIPLIER;
	public static double PLAYER_MATK_MULTIPLIER;
	public static double PLAYER_PDEF_MULTIPLIER;
	public static double PLAYER_MDEF_MULTIPLIER;

	public static void load()
	{
		final ConfigReader config = new ConfigReader(PLAYER_STAT_MULTIPLIERS_CONFIG_FILE);
		ENABLE_PLAYER_STAT_MULTIPLIERS = config.getBoolean("EnablePlayerStatMultipliers", false);
		PLAYER_HP_MULTIPLIER = config.getDouble("PlayerHP", 1.0);
		PLAYER_MP_MULTIPLIER = config.getDouble("PlayerMP", 1.0);
		PLAYER_CP_MULTIPLIER = config.getDouble("PlayerCP", 1.0);
		PLAYER_PATK_MULTIPLIER = config.getDouble("PlayerPAtk", 1.0);
		PLAYER_MATK_MULTIPLIER = config.getDouble("PlayerMAtk", 1.0);
		PLAYER_PDEF_MULTIPLIER = config.getDouble("PlayerPDef", 1.0);
		PLAYER_MDEF_MULTIPLIER = config.getDouble("PlayerMDef", 1.0);
	}
}
