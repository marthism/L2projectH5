$ErrorActionPreference = 'Stop'

$serverGame = 'C:\L2Server\HighFive\game'
$sourceGame = 'C:\Users\Marthismo\Desktop\L2J_Mobius-master-L2J_Mobius_CT_2.6_HighFive\dist\game'
$itemPath = Join-Path $serverGame 'data\stats\items'
$grades = @('D', 'C', 'B', 'A', 'S', 'S80', 'S84')
$gradeBase = @{ D = 100000; C = 500000; B = 2000000; A = 8000000; S = 25000000; S80 = 60000000; S84 = 120000000 }
$gradeCode = @{ D = 1; C = 2; B = 3; A = 4; S = 5; S80 = 6; S84 = 7 }
$blocked = '(?i)(shadow|common item|event|\bsealed\b|masterwork|foundation|time-limited|durability|\d+ day|\d+-day|combat weapon|monster only|commendation|recommendation|\{pvp\}|\btattoo\b|- (force|shield|weapon|bow|dagger|fist|sword|magic|health|focus) master$)'
$accessoryParts = @('underwear', 'belt', 'lbracelet', 'rbracelet', 'talisman')
$bossJewelIds = @(6656, 6657, 6658, 6659, 6660, 6661, 6662, 8191, 10170, 10314, 16025, 16026)
$jewelParts = @('rear', 'lear', 'neck', 'rfinger', 'lfinger')
$items = [System.Collections.Generic.List[object]]::new()
Get-ChildItem -LiteralPath $itemPath -Filter '*.xml' -File | ForEach-Object {
    try { [xml]$doc = Get-Content -LiteralPath $_.FullName -Raw } catch { return }
    foreach ($item in $doc.list.item) {
        if (-not $item.id -or -not $item.type -or ($item.name -match $blocked)) { continue }
        $sets = @{}
        foreach ($set in $item.set) { $sets[[string]$set.name] = [string]$set.val }
        $grade = $sets['crystal_type']
        if ([string]::IsNullOrWhiteSpace($grade)) {
            $grade = 'NONE'
        } elseif ($grade -notin $grades) {
            continue
        }
        $price = 0
        [void][long]::TryParse($sets['price'], [ref]$price)
        $minimum = if ($gradeBase.ContainsKey($grade)) { [long]$gradeBase[$grade] } else { [long]1000000 }
        $factor = 105 + (([int]$item.id * 17) % 31)
        $salePrice = [long][Math]::Max($minimum, [Math]::Round(($price * $factor / 100.0) / 1000) * 1000)
        $items.Add([pscustomobject]@{ Id=[int]$item.id; Name=[string]$item.name; Type=[string]$item.type; Grade=$grade; BodyPart=$sets['bodypart']; Price=$salePrice })
    }
}

# Rebuild the Scheme Buffer catalog by the classes that provide each buff.
[xml]$schemeDoc = Get-Content (Join-Path $serverGame 'data\SchemeBufferSkills.xml') -Raw
$buffById = [ordered]@{}
foreach ($buff in $schemeDoc.list.category.buff) {
    $id = [int]$buff.id
    if ($id -le 0) { continue }
    $key = [string]$id
    if (-not $buffById.Contains($key)) {
        $buffById[$key] = [pscustomobject]@{ Id=$id; Level=[int]$buff.level; Price=[int]$buff.price; Desc=[string]$buff.desc }
    }
}
$classNames = @('Prophet','Warcryer','Overlord','Bladedancer','Swordsinger','ElvenElder','ShillienElder')
$classBuffs = [ordered]@{}
foreach ($className in $classNames) {
    [xml]$tree = Get-Content (Join-Path $serverGame "data\stats\players\skillTrees\2ndClass\$className.xml") -Raw
    $learned = @($tree.SelectNodes('//skill') | ForEach-Object { [int]$_.skillId } | Select-Object -Unique)
    $classBuffs[$className] = @($buffById.Values | Where-Object { $_.Id -in $learned })
}
# Overlord/Dominator Pa'agrio buffs are not part of the scheme catalog source, seed them explicitly.
$classBuffs['Overlord'] = @(
    [pscustomobject]@{Id=1004;Level=130;Price=0;Desc='Increases Casting Spd. by 15%.'},
    [pscustomobject]@{Id=1005;Level=130;Price=0;Desc='Increases P. Def. by 8%.'},
    [pscustomobject]@{Id=1008;Level=130;Price=0;Desc='Increases M. Def. by 15%.'},
    [pscustomobject]@{Id=1249;Level=130;Price=0;Desc='Increases Accuracy.'},
    [pscustomobject]@{Id=1250;Level=130;Price=0;Desc='Increases shield defense by 30%.'},
    [pscustomobject]@{Id=1260;Level=130;Price=0;Desc='Increases Evasion.'},
    [pscustomobject]@{Id=1261;Level=130;Price=0;Desc='Reduces def. and increases atk. power.'},
    [pscustomobject]@{Id=1536;Level=1;Price=0;Desc='Increases P. Atk. and P. Def. by 15%.'},
    [pscustomobject]@{Id=1537;Level=1;Price=0;Desc='Increases critical rate and critical damage.'},
    [pscustomobject]@{Id=1538;Level=1;Price=0;Desc='Increases Max HP and Max MP by 35%.'},
    [pscustomobject]@{Id=1563;Level=2;Price=0;Desc='Increases Atk. Spd. by 15%.'},
    [pscustomobject]@{Id=1364;Level=1;Price=0;Desc='Increases Accuracy and decreases critical damage received.'},
    [pscustomobject]@{Id=1365;Level=1;Price=0;Desc='Increases M. Atk. by 75% and M. Def. by 30%.'},
    [pscustomobject]@{Id=1414;Level=315;Price=0;Desc='Increases all combat abilities of clan members.'},
    [pscustomobject]@{Id=1415;Level=115;Price=0;Desc='Increases resistance to buff cancel and de-buffs.'},
    [pscustomobject]@{Id=1416;Level=115;Price=0;Desc='Increases Max CP and restores CP.'}
)
$assigned = @($classBuffs.Values | ForEach-Object { $_.Id } | Select-Object -Unique)
$brokenBuffIds = @(1375, 1376) # Heroic Grandeur / Heroic Dread - do not work when cast outside real Hero status.
$classBuffs['Special'] = @($buffById.Values | Where-Object { ($_.Id -notin $assigned) -and ($_.Id -notin $brokenBuffIds) })
$classBuffs['Dwarf'] = @(825..830 | ForEach-Object { [pscustomobject]@{Id=$_;Level=1;Price=0;Desc='Dwarven equipment support'} })
$classBuffs['Noblesse'] = @([pscustomobject]@{Id=1323;Level=1;Price=0;Desc='Preserves buffs after death'})
$classBuffs['Improved'] = @(
    [pscustomobject]@{Id=1499;Level=1;Price=0;Desc='Increases P. Atk. and P. Def. by 15% for 40 min.'},
    [pscustomobject]@{Id=1500;Level=1;Price=0;Desc='Increases M. Atk. by 75% and M. Def. by 30% for 40 min.'},
    [pscustomobject]@{Id=1501;Level=1;Price=0;Desc='Increases Max HP and Max MP by 35% for 40 min.'},
    [pscustomobject]@{Id=1502;Level=1;Price=0;Desc='Increases Critical Rate by 30% and Critical Damage by 35% for 40 min.'},
    [pscustomobject]@{Id=1503;Level=1;Price=0;Desc='Increases Shield Defense Rate and Power for 40 min.'},
    [pscustomobject]@{Id=1504;Level=1;Price=0;Desc='Increases Speed and Evasion for 40 min.'}
)
$classBuffs['Hero'] = @(
    [pscustomobject]@{Id=395;Level=1;Price=0;Desc='Heroic Miracle'},
    [pscustomobject]@{Id=396;Level=1;Price=0;Desc='Heroic Berserker'},
    [pscustomobject]@{Id=1374;Level=1;Price=0;Desc='Heroic Valor'}
)
foreach ($vipSource in @($classNames + @('Special', 'Hero', 'Dwarf', 'Noblesse'))) {
    $classBuffs["VIP-$vipSource"] = @($classBuffs[$vipSource])
}

function Write-SchemeCatalog([string]$root) {
    $lines = [Collections.Generic.List[string]]::new()
    $lines.Add('<?xml version="1.0" encoding="UTF-8"?>')
    $lines.Add('<list xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:noNamespaceSchemaLocation="xsd/SchemeBufferSkills.xsd">')
    foreach ($category in $classBuffs.Keys) {
        $lines.Add("`t<category type=`"$category`">")
        foreach ($buff in $classBuffs[$category]) {
            $desc = [Security.SecurityElement]::Escape($buff.Desc)
            $lines.Add("`t`t<buff id=`"$($buff.Id)`" level=`"$($buff.Level)`" price=`"$($buff.Price)`" desc=`"$desc`" />")
        }
        $lines.Add("`t</category>")
    }
    $lines.Add('</list>')
    [IO.File]::WriteAllLines((Join-Path $root 'data\SchemeBufferSkills.xml'), $lines, [Text.UTF8Encoding]::new($false))
}

$durationIds = @($classBuffs.Values | ForEach-Object { $_.Id } | Sort-Object -Unique)
$durationList = ($durationIds | ForEach-Object { "$_,10800" }) -join ';'
foreach ($root in @($serverGame, $sourceGame)) {
    Write-SchemeCatalog $root
    $playerIni = Join-Path $root 'config\Player.ini'
    $playerText = Get-Content $playerIni -Raw
    $playerText = $playerText -replace 'EnableModifySkillDuration\s*=\s*False', 'EnableModifySkillDuration = True'
    $playerText = [regex]::Replace($playerText, '(?m)^SkillDurationList\s*=.*$', "SkillDurationList = $durationList")
    [IO.File]::WriteAllText($playerIni, $playerText, [Text.UTF8Encoding]::new($false))
}

function Get-EntryId([object]$entry) {
    if ($null -eq $entry) { return 0 }
    if ($entry -is [int]) { return [int]$entry }
    $property = $entry.PSObject.Properties | Where-Object Name -eq 'Id' | Select-Object -First 1
    if (($null -ne $property) -and ($null -ne $property.Value) -and ($property.Value -ne '')) {
        return [int]$property.Value
    }
    return 0
}

$bossWeaponNamePattern = '(?i)(Frintezza|Valakas|Antharas|Zaken|Freya|Beleth|Baylor|Tiat|Mardil|Mamba|Blood Brother|Eternal Core|Lava Saw|Archangel|Hellblade|Claw of Destruction|Sword of Valakas|Vesper .*(Thunder|Gale|Cleverness|Hail|Clairvoyance|Landslide|Destruction)|Vesper (Dual Daggers|Dual Sword) - (Gale|Destruction))'
function Test-BossWeapon([object]$item) {
    return (($item.Type -eq 'Weapon') -and ($item.Name -match $bossWeaponNamePattern))
}

function Write-Multisell([string]$root, [int]$id, [object[]]$entries) {
    $path = Join-Path $root "data\multisell\custom\$id.xml"
    $lines = [System.Collections.Generic.List[string]]::new()
    $lines.Add('<?xml version="1.0" encoding="UTF-8"?>')
    $lines.Add('<list xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:noNamespaceSchemaLocation="../../xsd/multisell.xsd">')
    $lines.Add("`t<npcs><npc>-1</npc></npcs>")
    foreach ($entry in ($entries | Sort-Object Name,Id -Descending)) {
        $entryId = Get-EntryId $entry
        if ($entryId -le 0) { continue }
        $productProperty = $entry.PSObject.Properties | Where-Object Name -eq 'Products' | Select-Object -First 1
        $products = if (($null -ne $productProperty) -and ($null -ne $productProperty.Value)) { @($productProperty.Value) } else { @() }
        if ($products.Count -gt 0) {
            $line = "`t<item><ingredient count=`"$($entry.Price)`" id=`"57`" />"
            foreach ($product in $products) {
                $productId = Get-EntryId $product
                if ($productId -gt 0) {
                    $line += "<production count=`"1`" id=`"$productId`" />"
                }
            }
            $line += '</item>'
            $lines.Add($line)
        } else {
            $lines.Add("`t<item><ingredient count=`"$($entry.Price)`" id=`"57`" /><production count=`"1`" id=`"$entryId`" /></item>")
        }
    }
    $lines.Add('</list>')
    [IO.File]::WriteAllLines($path, $lines, [Text.UTF8Encoding]::new($false))
}

# Armor sets are sourced verbatim from pecas.xlsx (Grau/Set/Tipo/Ordem/Peca columns), which lists the
# exact pieces of every real set. "(Cla)" sets are intentionally excluded per the client's request.
# Two grade fields exist per set because Dynasty's own crystal_type in this data is "S" even though the
# spreadsheet groups it under "S80" (alongside Moirai) -- LookupGrade finds the real items, Grade places
# the set on the right shop page.
function Normalize-ArmorName([string]$s) {
    return ($s -replace '[^a-zA-Z0-9]', '').ToLower()
}

$armorSetDefs = @(
    @{ Grade='NONE'; Kind='Light'; Name='Native Set'; Pieces=@('Native Helmet','Native Tunic','Native Pants') }
    @{ Grade='NONE'; Kind='Light'; Name='Wooden Set'; Pieces=@('Wooden Helmet','Wooden Breastplate','Wooden Gaiters') }
    @{ Grade='NONE'; Kind='Magic'; Name='Devotion Set'; Pieces=@('Leather Helmet','Tunic of Devotion','Stockings of Devotion') }
    @{ Grade='D'; Kind='Heavy'; Name='Brigandine Set'; Pieces=@('Brigandine Helmet','Brigandine Tunic','Brigandine Gaiters') }
    @{ Grade='D'; Kind='Heavy'; Name='Mithril Set'; Pieces=@('Helmet','Mithril Breastplate','Mithril Gaiters') }
    @{ Grade='D'; Kind='Light'; Name='Manticore Set'; Pieces=@('Manticore Skin Shirt','Manticore Skin Gaiters','Manticore Skin Boots') }
    @{ Grade='D'; Kind='Light'; Name='Reinforced Leather Set'; Pieces=@('Reinforced Leather Shirt','Reinforced Leather Gaiters','Reinforced Leather Boots') }
    @{ Grade='D'; Kind='Magic'; Name='Elven Mithril Set'; Pieces=@('Mithril Tunic','Mithril Stockings','Mithril Gloves') }
    @{ Grade='D'; Kind='Magic'; Name='Knowledge Set'; Pieces=@('Tunic of Knowledge','Stockings of Knowledge','Gloves of Knowledge') }
    @{ Grade='C'; Kind='Heavy'; Name='Chain Set'; Pieces=@('Chain Hood','Chain Mail Shirt','Chain Gaiters') }
    @{ Grade='C'; Kind='Heavy'; Name='Composite Set'; Pieces=@('Composite Helmet','Composite Armor') }
    @{ Grade='C'; Kind='Heavy'; Name='Full Plate Set'; Pieces=@('Full Plate Helmet','Full Plate Armor') }
    @{ Grade='C'; Kind='Light'; Name='Drake Leather Set'; Pieces=@('Drake Leather Armor','Drake Leather Boots') }
    @{ Grade='C'; Kind='Light'; Name='Plate Leather Set'; Pieces=@('Plated Leather','Plated Leather Gaiters','Plated Leather Boots') }
    @{ Grade='C'; Kind='Light'; Name='Tempered Mithril Set'; Pieces=@('Mithril Shirt','Reinforced Mithril Gaiters','Reinforced Mithril Boots') }
    @{ Grade='C'; Kind='Light'; Name='Theca Leather Set'; Pieces=@('Theca Leather Armor','Theca Leather Gaiters','Theca Leather Boots') }
    @{ Grade='C'; Kind='Magic'; Name='Demon Set'; Pieces=@("Demon's Tunic","Demon's Stockings","Demon's Gloves") }
    @{ Grade='C'; Kind='Magic'; Name='Divine Set'; Pieces=@('Divine Tunic','Divine Stockings','Divine Gloves') }
    @{ Grade='C'; Kind='Magic'; Name='Karmian Set'; Pieces=@('Karmian Tunic','Karmian Stockings','Karmian Gloves') }
    @{ Grade='B'; Kind='Heavy'; Name='Avadon Heavy Set'; Pieces=@('Avadon Circlet','Avadon Breastplate','Avadon Gaiters','Avadon Boots Heavy Armor','Avadon Gloves Heavy Armor') }
    @{ Grade='B'; Kind='Heavy'; Name='Blue Wolf Heavy Set'; Pieces=@('Blue Wolf Helmet','Blue Wolf Breastplate','Blue Wolf Gaiters','Blue Wolf Boots Heavy Armor','Blue Wolf Gloves Heavy Armor') }
    @{ Grade='B'; Kind='Heavy'; Name='Doom Heavy Set'; Pieces=@('Doom Helmet','Doom Plate Armor','Doom Boots Heavy Armor','Doom Gloves Heavy Armor') }
    @{ Grade='B'; Kind='Heavy'; Name='Zubei Heavy Set'; Pieces=@("Zubei's Helmet","Zubei's Breastplate","Zubei's Gaiters","Zubei's Boots Heavy Armor","Zubei's Gauntlets Heavy Armor") }
    @{ Grade='B'; Kind='Light'; Name='Avadon Light Set'; Pieces=@('Avadon Circlet','Avadon Leather Armor','Avadon Boots Light Armor','Avadon Gloves Light Armor') }
    @{ Grade='B'; Kind='Light'; Name='Blue Wolf Light Set'; Pieces=@('Blue Wolf Helmet','Blue Wolf Leather Armor','Blue Wolf Boots Light Armor','Blue Wolf Gloves Light Armor') }
    @{ Grade='B'; Kind='Light'; Name='Doom Light Set'; Pieces=@('Doom Helmet','Leather Armor of Doom','Doom Boots Light Armor','Doom Gloves Light Armor') }
    @{ Grade='B'; Kind='Light'; Name='Zubei Light Set'; Pieces=@("Zubei's Helmet","Zubei's Leather Shirt","Zubei's Leather Gaiters","Zubei's Boots Light Armor","Zubei's Gauntlets Light Armor") }
    @{ Grade='B'; Kind='Magic'; Name='Avadon Magic Set'; Pieces=@('Avadon Circlet','Avadon Robe','Avadon Boots Robe','Avadon Gloves Robe') }
    @{ Grade='B'; Kind='Magic'; Name='Blue Wolf Magic Set'; Pieces=@('Blue Wolf Helmet','Blue Wolf Tunic','Blue Wolf Stockings','Blue Wolf Boots Robe','Blue Wolf Gloves Robe') }
    @{ Grade='B'; Kind='Magic'; Name='Doom Magic Set'; Pieces=@('Doom Helmet','Tunic of Doom','Stockings of Doom','Doom Boots Robe','Doom Gloves Robe') }
    @{ Grade='B'; Kind='Magic'; Name='Zubei Magic Set'; Pieces=@("Zubei's Helmet","Tunic of Zubei","Stockings of Zubei","Zubei's Boots Robe","Zubei's Gauntlets Robe") }
    @{ Grade='A'; Kind='Heavy'; Name='Dark Crystal Heavy Set'; Pieces=@('Dark Crystal Helmet','Dark Crystal Breastplate','Dark Crystal Gaiters','Dark Crystal Boots Heavy Armor','Dark Crystal Gloves Heavy Armor') }
    @{ Grade='A'; Kind='Heavy'; Name='Majestic Heavy Set'; Pieces=@('Majestic Circlet','Majestic Plate Armor','Majestic Boots Heavy Armor','Majestic Gauntlets Heavy Armor') }
    @{ Grade='A'; Kind='Heavy'; Name='Nightmare Heavy Set'; Pieces=@('Helm of Nightmare','Armor of Nightmare','Boots of Nightmare Heavy Armor','Gauntlets of Nightmare Heavy Armor') }
    @{ Grade='A'; Kind='Heavy'; Name='Tallum Heavy Set'; Pieces=@('Tallum Helm','Tallum Plate Armor','Tallum Boots Heavy Armor','Tallum Gloves Heavy Armor') }
    @{ Grade='A'; Kind='Light'; Name='Dark Crystal Light Set'; Pieces=@('Dark Crystal Helmet','Dark Crystal Leather Armor','Dark Crystal Leggings','Dark Crystal Boots Light Armor','Dark Crystal Gloves Light Armor') }
    @{ Grade='A'; Kind='Light'; Name='Majestic Light Set'; Pieces=@('Majestic Circlet','Majestic Leather Armor','Majestic Boots Light Armor','Majestic Gauntlets Light Armor') }
    @{ Grade='A'; Kind='Light'; Name='Nightmare Light Set'; Pieces=@('Helm of Nightmare','Leather Armor of Nightmare','Boots of Nightmare Light Armor','Gauntlets of Nightmare Light Armor') }
    @{ Grade='A'; Kind='Light'; Name='Tallum Light Set'; Pieces=@('Tallum Helm','Tallum Leather Armor','Tallum Boots Light Armor','Tallum Gloves Light Armor') }
    @{ Grade='A'; Kind='Magic'; Name='Dark Crystal Magic Set'; Pieces=@('Dark Crystal Helmet','Dark Crystal Robe','Dark Crystal Boots Robe','Dark Crystal Gloves Robe') }
    @{ Grade='A'; Kind='Magic'; Name='Majestic Magic Set'; Pieces=@('Majestic Circlet','Majestic Robe','Majestic Boots Robe','Majestic Gauntlets Robe') }
    @{ Grade='A'; Kind='Magic'; Name='Nightmare Magic Set'; Pieces=@('Helm of Nightmare','Robe of Nightmare','Boots of Nightmare Robe','Gauntlets of Nightmare Robe') }
    @{ Grade='A'; Kind='Magic'; Name='Tallum Magic Set'; Pieces=@('Tallum Helm','Tallum Tunic','Tallum Stockings','Tallum Boots Robe','Tallum Gloves Robe') }
    @{ Grade='S'; Kind='Heavy'; Name='Imperial Crusader Set'; Pieces=@('Imperial Crusader Helmet','Imperial Crusader Breastplate','Imperial Crusader Gaiters','Imperial Crusader Gauntlets','Imperial Crusader Boots') }
    @{ Grade='S'; Kind='Light'; Name='Drakonic Set'; Pieces=@('Draconic Leather Helmet','Draconic Leather Armor','Draconic Leather Gloves','Draconic Leather Boots') }
    @{ Grade='S'; Kind='Magic'; Name='Major Arcana Set'; Pieces=@('Major Arcana Circlet','Major Arcana Robe','Major Arcana Gloves','Major Arcana Boots') }
    @{ Grade='S80'; LookupGrade='S'; Kind='Heavy'; Name='Dynasty Heavy Set'; Pieces=@('Dynasty Helmet','Dynasty Breast Plate','Dynasty Gaiters','Dynasty Gauntlet Heavy Armor','Dynasty Boots Heavy Armor') }
    @{ Grade='S80'; Kind='Heavy'; Name='Moirai Heavy Set'; Pieces=@('Moirai Helmet','Moirai Breastplate','Moirai Gaiter','Moirai Gauntlet','Moirai Boots') }
    @{ Grade='S80'; LookupGrade='S'; Kind='Light'; Name='Dynasty Light Set'; Pieces=@('Dynasty Leather Helmet','Dynasty Leather Armor','Dynasty Leather Leggings','Dynasty Leather Gloves Light Armor','Dynasty Leather Boots Light Armor') }
    @{ Grade='S80'; Kind='Light'; Name='Moirai Light Set'; Pieces=@('Moirai Leather Helmet','Moirai Leather Breastplate','Moirai Leather Legging','Moirai Leather Gloves','Moirai Leather Boots') }
    @{ Grade='S80'; LookupGrade='S'; Kind='Magic'; Name='Dynasty Robe Set'; Pieces=@('Dynasty Circlet','Dynasty Tunic','Dynasty Stockings','Dynasty Gloves Robe','Dynasty Shoes Robe') }
    @{ Grade='S80'; Kind='Magic'; Name='Moirai Magic Set'; Pieces=@('Moirai Circlet','Moirai Tunic','Moirai Stockings','Moirai Gloves','Moirai Shoes') }
    @{ Grade='S84'; Kind='Heavy'; Name='Elegia Heavy Set'; Pieces=@('Elegia Helmet','Elegia Breastplate','Elegia Gaiter','Elegia Gauntlet','Elegia Boots') }
    @{ Grade='S84'; Kind='Heavy'; Name='Vesper Heavy Set'; Pieces=@('Vesper Helmet','Vesper Breastplate','Vesper Gaiters','Vesper Gauntlet','Vesper Boots') }
    @{ Grade='S84'; Kind='Heavy'; Name='Vesper Noble Heavy Set'; Pieces=@('Vesper Noble Helmet','Vesper Noble Breastplate','Vesper Noble Gaiters','Vesper Noble Gauntlet','Vesper Noble Boots') }
    @{ Grade='S84'; Kind='Heavy'; Name='Vorpal Heavy Set'; Pieces=@('Vorpal Helmet','Vorpal Breastplate','Vorpal Gaiter','Vorpal Gauntlet','Vorpal Boots') }
    @{ Grade='S84'; Kind='Light'; Name='Elegia Light Set'; Pieces=@('Elegia Leather Helmet','Elegia Leather Breastplate','Elegia Leather Legging','Elegia Leather Gloves','Elegia Leather Boots') }
    @{ Grade='S84'; Kind='Light'; Name='Vesper Light Set'; Pieces=@('Vesper Leather Helmet','Vesper Leather Breastplate','Vesper Leather Leggings','Vesper Leather Gloves','Vesper Leather Boots') }
    @{ Grade='S84'; Kind='Light'; Name='Vesper Noble Light Set'; Pieces=@('Vesper Noble Leather Helmet','Vesper Noble Leather Breastplate','Vesper Noble Leather Leggings','Vesper Noble Leather Gloves','Vesper Noble Leather Boots') }
    @{ Grade='S84'; Kind='Light'; Name='Vorpal Light Set'; Pieces=@('Vorpal Leather Helmet','Vorpal Leather Breastplate','Vorpal Leather Legging','Vorpal Leather Gloves','Vorpal Leather Boots') }
    @{ Grade='S84'; Kind='Magic'; Name='Elegia Magic Set'; Pieces=@('Elegia Circlet','Elegia Tunic','Elegia Stockings','Elegia Gloves','Elegia Shoes') }
    @{ Grade='S84'; Kind='Magic'; Name='Vesper Noble Robe Set'; Pieces=@('Vesper Noble Circlet','Vesper Noble Tunic','Vesper Noble Stockings','Vesper Noble Gloves','Vesper Noble Shoes') }
    @{ Grade='S84'; Kind='Magic'; Name='Vesper Robe Set'; Pieces=@('Vesper Circlet','Vesper Tunic','Vesper Stockings','Vesper Gloves','Vesper Shoes') }
    @{ Grade='S84'; Kind='Magic'; Name='Vorpal Magic Set'; Pieces=@('Vorpal Circlet','Vorpal Tunic','Vorpal Stockings','Vorpal Gloves','Vorpal Shoes') }
)

# Themed shield added to each Heavy set, per the client's request. Families with no matching shield
# item in this data (Mithril, Blue Wolf, Majestic, Tallum, Vesper Noble) are intentionally left out.
$armorShieldDefs = @{
    'Brigandine Set' = @{ Grade='D'; Shield='Brigandine Shield' }
    'Chain Set' = @{ Grade='C'; Shield='Chain Shield' }
    'Composite Set' = @{ Grade='C'; Shield='Composite Shield' }
    'Full Plate Set' = @{ Grade='C'; Shield='Full Plate Shield' }
    'Avadon Heavy Set' = @{ Grade='B'; Shield='Avadon Shield' }
    'Doom Heavy Set' = @{ Grade='B'; Shield='Doom Shield' }
    'Zubei Heavy Set' = @{ Grade='B'; Shield="Zubei's Shield" }
    'Dark Crystal Heavy Set' = @{ Grade='A'; Shield='Dark Crystal Shield' }
    'Nightmare Heavy Set' = @{ Grade='A'; Shield='Shield of Nightmare' }
    'Imperial Crusader Set' = @{ Grade='S'; Shield='Imperial Crusader Shield' }
    'Dynasty Heavy Set' = @{ Grade='S'; Shield='Dynasty Shield' }
    'Moirai Heavy Set' = @{ Grade='S80'; Shield='Moirai Shield' }
    'Elegia Heavy Set' = @{ Grade='S84'; Shield='Elegia Shield' }
    'Vesper Heavy Set' = @{ Grade='S84'; Shield='Vesper Shield' }
    'Vorpal Heavy Set' = @{ Grade='S84'; Shield='Vorpal Shield' }
}

function Get-ArmorPieceByName([object[]]$pool, [string]$pieceName) {
    $norm = Normalize-ArmorName $pieceName
    return @($pool | Where-Object { (Normalize-ArmorName $_.Name) -eq $norm } | Sort-Object Id | Select-Object -First 1)
}

function Build-ArmorSetsFromTable([object[]]$allItems, [string]$grade) {
    $sets = [System.Collections.Generic.List[object]]::new()
    foreach ($def in ($armorSetDefs | Where-Object Grade -eq $grade)) {
        $lookupGrade = if ($def.ContainsKey('LookupGrade')) { $def.LookupGrade } else { $def.Grade }
        $pool = @($allItems | Where-Object { $_.Grade -eq $lookupGrade })
        $members = [System.Collections.Generic.List[object]]::new()
        $missing = $false
        foreach ($pieceName in $def.Pieces) {
            $found = @(Get-ArmorPieceByName $pool $pieceName)
            if ($found.Count -eq 0) { $missing = $true; break }
            $members.Add($found[0])
        }
        if ($missing) { continue }
        if ($def.Kind -eq 'Heavy' -and $armorShieldDefs.ContainsKey($def.Name)) {
            $shieldDef = $armorShieldDefs[$def.Name]
            $shieldPool = @($allItems | Where-Object { ($_.Grade -eq $shieldDef.Grade) -and ($_.BodyPart -eq 'lhand') })
            $shield = @(Get-ArmorPieceByName $shieldPool $shieldDef.Shield)
            if ($shield.Count -gt 0) { $members.Add($shield[0]) }
        }
        $setPrice = [long](($members | Measure-Object Price -Sum).Sum)
        $sets.Add([pscustomobject]@{
            Id = $members[0].Id
            Name = $def.Name
            Price = $setPrice
            Products = @($members)
        })
    }
    return @($sets)
}

function Write-Page([string]$root, [string]$relative, [string]$content) {
    $path = Join-Path $root "data\html\CommunityBoard\Custom\$relative"
    New-Item -ItemType Directory -Path (Split-Path $path) -Force | Out-Null
    $content = $content.Replace('<table width=455 height=390 background="L2UI_CT1.Windows_DF_TooltipBG"><tr><td align=center>', '<table width=455 height=390 background="L2UI_CT1.Windows_DF_TooltipBG"><tr><td height=18></td></tr><tr><td align=center>')
    [IO.File]::WriteAllText($path, $content, [Text.UTF8Encoding]::new($false))
}

foreach ($root in @($serverGame, $sourceGame)) {
    foreach ($grade in $grades) {
        $code = $gradeCode[$grade]
        $gradeItems = @($items | Where-Object Grade -eq $grade)
        $allWeapons = @($gradeItems | Where-Object { ($_.Type -eq 'Weapon') -and -not (Test-BossWeapon $_) })
        $saWeapons = @($allWeapons | Where-Object Name -match '(?i) - (focus|health|acumen|haste|guidance|evasion|critical|mana|conversion|cheap|anger|light|rsk|empower|magic|quick|wide|long|back blow|hp drain|hp regeneration|mp regeneration|critical slow|critical stun|critical damage|magic hold|mana up|cheap shot)')
        $jewels = @($gradeItems | Where-Object { ($_.Type -eq 'Armor') -and ($_.BodyPart -match '(?i)(rear|lear|neck|finger)') -and ($_.Id -notin $bossJewelIds) -and ($_.Name -notmatch '(?i)blessed|enchanted') })
        Write-Multisell $root (610000 + ($code * 10) + 1) $saWeapons
        Write-Multisell $root (610000 + ($code * 10) + 3) (Build-ArmorSetsFromTable $items $grade)
        Write-Multisell $root (610000 + ($code * 10) + 4) $jewels
        $page = @"
<html noscrollbar><body><table width=700><tr><td width=205>%navigation%</td><td width=480><center><table width=455 background="L2UI_CT1.Windows_DF_TooltipBG">
<tr><td height=26></td></tr>
<tr><td align=center><font name="hs12" color="CDB67F">Grade $grade</font></td></tr>
<tr><td align=center><font color="AAAAAA">Weapons come with Special Abilities.</font></td></tr>
<tr><td height=14></td></tr>
<tr><td align=center><button value="Weapon" action="bypass _bbsmultisell;$(610000 + ($code * 10) + 1),merchant/grade-$grade" width=160 height=27 back="L2UI_CT1.Button_DF_Down" fore="L2UI_CT1.Button_DF"></td></tr>
<tr><td align=center><button value="Armor" action="bypass _bbsmultisell;$(610000 + ($code * 10) + 3),merchant/grade-$grade" width=160 height=27 back="L2UI_CT1.Button_DF_Down" fore="L2UI_CT1.Button_DF"></td></tr>
<tr><td align=center><button value="Jewelry" action="bypass _bbsmultisell;$(610000 + ($code * 10) + 4),merchant/grade-$grade" width=160 height=27 back="L2UI_CT1.Button_DF_Down" fore="L2UI_CT1.Button_DF"></td></tr>
<tr><td height=14></td></tr>
<tr><td align=center><button value="Back to Shop" action="bypass _bbstop;merchant/main.html" width=160 height=27 back="L2UI_CT1.Button_DF_Down" fore="L2UI_CT1.Button_DF"></td></tr>
<tr><td height=18></td></tr>
</table></center></td></tr></table></body></html>
"@
        Write-Page $root "merchant\grade-$grade.html" $page
    }
    $shirts = @($items | Where-Object { ($_.Type -eq 'Armor') -and ($_.BodyPart -eq 'underwear') -and ($_.Grade -in @('S', 'S80', 'S84')) })
    $belts = @($items | Where-Object { ($_.Type -eq 'Armor') -and ($_.Name -match '(?i)\bBelt\b') -and ($_.Name -match '(?i)(High-grade|Top-grade|Mithril|Iron|Cloth|Vitality)') })
    $bracelets = @($items | Where-Object { ($_.Type -eq 'Armor') -and ($_.BodyPart -in @('lbracelet', 'rbracelet')) -and ($_.Grade -in @('S', 'S80', 'S84')) })
    $talismans = @($items | Where-Object { ($_.Type -eq 'Armor') -and (($_.BodyPart -match '(?i)^deco[1-6]$') -or ($_.Name -match '(?i)\bTalisman\b')) })
    $cloaks = @($items | Where-Object { ($_.Type -eq 'Armor') -and ($_.BodyPart -eq 'back') -and ($_.Grade -in @('S', 'S80', 'S84')) })
    Write-Multisell $root 610090 $shirts
    Write-Multisell $root 610092 $belts
    Write-Multisell $root 610093 $bracelets
    Write-Multisell $root 610094 $talismans
    Write-Multisell $root 610097 $cloaks
    $bossJewels = @($items | Where-Object { ($_.Id -in $bossJewelIds) -and ($_.Name -notmatch '(?i)blessed|enchanted') })
    Write-Multisell $root 610091 $bossJewels

    $noGradeItems = @($items | Where-Object Grade -eq 'NONE')
    $noGradeWeapons = @($noGradeItems | Where-Object Type -eq 'Weapon')
    $noGradeJewels = @($noGradeItems | Where-Object { ($_.Type -eq 'Armor') -and ($_.BodyPart -match '(?i)(rear|lear|neck|finger)') -and ($_.Name -notmatch '(?i)blessed|enchanted') })
    Write-Multisell $root 610098 $noGradeWeapons
    Write-Multisell $root 610099 (Build-ArmorSetsFromTable $items 'NONE')
    Write-Multisell $root 610100 $noGradeJewels

    $tattoos = @(
        [pscustomobject]@{ Id=12067; Name='Tattoo of Fighter I'; Price=500000 },
        [pscustomobject]@{ Id=12140; Name='Tattoo of Fighter II'; Price=1500000 },
        [pscustomobject]@{ Id=12191; Name='Tattoo of Fighter III'; Price=3000000 },
        [pscustomobject]@{ Id=12281; Name='Tattoo of Fighter IV'; Price=6000000 },
        [pscustomobject]@{ Id=10207; Name='Tattoo of Fighter V'; Price=12000000 },
        [pscustomobject]@{ Id=12040; Name='Tattoo of Mage I'; Price=500000 },
        [pscustomobject]@{ Id=12117; Name='Tattoo of Mage II'; Price=1500000 },
        [pscustomobject]@{ Id=12174; Name='Tattoo of Mage III'; Price=3000000 },
        [pscustomobject]@{ Id=12219; Name='Tattoo of Mage IV'; Price=6000000 },
        [pscustomobject]@{ Id=12290; Name='Tattoo of Mage V'; Price=12000000 }
    )
    Write-Multisell $root 610101 $tattoos

    $noGradePage = @"
<html noscrollbar><body><table width=700><tr><td width=205>%navigation%</td><td width=480><center><table width=455 background="L2UI_CT1.Windows_DF_TooltipBG">
<tr><td height=26></td></tr>
<tr><td align=center><font name="hs12" color="CDB67F">No Grade</font></td></tr>
<tr><td align=center><font color="AAAAAA">Equipamentos sem grade, apenas versoes normais.</font></td></tr>
<tr><td height=14></td></tr>
<tr><td align=center><button value="Weapon" action="bypass _bbsmultisell;610098,merchant/nograde" width=160 height=27 back="L2UI_CT1.Button_DF_Down" fore="L2UI_CT1.Button_DF"></td></tr>
<tr><td align=center><button value="Armor" action="bypass _bbsmultisell;610099,merchant/nograde" width=160 height=27 back="L2UI_CT1.Button_DF_Down" fore="L2UI_CT1.Button_DF"></td></tr>
<tr><td align=center><button value="Jewelry" action="bypass _bbsmultisell;610100,merchant/nograde" width=160 height=27 back="L2UI_CT1.Button_DF_Down" fore="L2UI_CT1.Button_DF"></td></tr>
<tr><td height=14></td></tr>
<tr><td align=center><button value="Back to Shop" action="bypass _bbstop;merchant/main.html" width=160 height=27 back="L2UI_CT1.Button_DF_Down" fore="L2UI_CT1.Button_DF"></td></tr>
<tr><td height=18></td></tr>
</table></center></td></tr></table></body></html>
"@
    Write-Page $root "merchant\nograde.html" $noGradePage

    $main = @'
<html noscrollbar><body><table width=700><tr><td width=205>%navigation%</td><td width=480><center>
<table width=455 background="L2UI_CT1.Windows_DF_TooltipBG">
<tr><td height=22></td></tr>
<tr><td align=center><font name="hs12" color="CDB67F">Community Shop</font></td></tr>
<tr><td align=center><font color="AAAAAA">Equipment, supplies and consumables.</font></td></tr>
<tr><td height=10></td></tr>
<tr><td align=center><font color="LEVEL">Equipment by Grade</font></td></tr>
<tr><td align=center><table>
<tr><td><button value="D Grade" action="bypass _bbstop;merchant/grade-D.html" width=110 height=25 back="L2UI_CT1.Button_DF_Down" fore="L2UI_CT1.Button_DF"></td><td><button value="C Grade" action="bypass _bbstop;merchant/grade-C.html" width=110 height=25 back="L2UI_CT1.Button_DF_Down" fore="L2UI_CT1.Button_DF"></td><td><button value="B Grade" action="bypass _bbstop;merchant/grade-B.html" width=110 height=25 back="L2UI_CT1.Button_DF_Down" fore="L2UI_CT1.Button_DF"></td></tr>
<tr><td><button value="A Grade" action="bypass _bbstop;merchant/grade-A.html" width=110 height=25 back="L2UI_CT1.Button_DF_Down" fore="L2UI_CT1.Button_DF"></td><td><button value="S Grade" action="bypass _bbstop;merchant/grade-S.html" width=110 height=25 back="L2UI_CT1.Button_DF_Down" fore="L2UI_CT1.Button_DF"></td><td><button value="S80 Grade" action="bypass _bbstop;merchant/grade-S80.html" width=110 height=25 back="L2UI_CT1.Button_DF_Down" fore="L2UI_CT1.Button_DF"></td></tr>
<tr><td><button value="Grade S84" action="bypass _bbstop;merchant/grade-S84.html" width=110 height=25 back="L2UI_CT1.Button_DF_Down" fore="L2UI_CT1.Button_DF"></td><td><button value="Boss Jewelry" action="bypass _bbsmultisell;610091,merchant/main" width=110 height=25 back="L2UI_CT1.Button_DF_Down" fore="L2UI_CT1.Button_DF"></td><td><button value="No Grade" action="bypass _bbstop;merchant/nograde.html" width=110 height=25 back="L2UI_CT1.Button_DF_Down" fore="L2UI_CT1.Button_DF"></td></tr>
</table></td></tr>
<tr><td height=10></td></tr>
<tr><td align=center><font color="LEVEL">Accessories and Supplies</font></td></tr>
<tr><td align=center><table>
<tr><td><button value="Shirts" action="bypass _bbsmultisell;610090,merchant/main" width=110 height=25 back="L2UI_CT1.Button_DF_Down" fore="L2UI_CT1.Button_DF"></td><td><button value="Belts" action="bypass _bbsmultisell;610092,merchant/main" width=110 height=25 back="L2UI_CT1.Button_DF_Down" fore="L2UI_CT1.Button_DF"></td><td><button value="Bracelets" action="bypass _bbsmultisell;610093,merchant/main" width=110 height=25 back="L2UI_CT1.Button_DF_Down" fore="L2UI_CT1.Button_DF"></td></tr>
<tr><td><button value="Talismans" action="bypass _bbsmultisell;610094,merchant/main" width=110 height=25 back="L2UI_CT1.Button_DF_Down" fore="L2UI_CT1.Button_DF"></td><td><button value="Cloaks" action="bypass _bbsmultisell;610097,merchant/main" width=110 height=25 back="L2UI_CT1.Button_DF_Down" fore="L2UI_CT1.Button_DF"></td><td><button value="Tattoos" action="bypass _bbsmultisell;610101,merchant/main" width=110 height=25 back="L2UI_CT1.Button_DF_Down" fore="L2UI_CT1.Button_DF"></td></tr>
<tr><td><button value="Scrolls" action="bypass _bbsmultisell;600012,merchant/main" width=110 height=25 back="L2UI_CT1.Button_DF_Down" fore="L2UI_CT1.Button_DF"></td><td><button value="Consumables" action="bypass _bbsmultisell;600030,merchant/main" width=110 height=25 back="L2UI_CT1.Button_DF_Down" fore="L2UI_CT1.Button_DF"></td><td><button value="Others" action="bypass _bbsmultisell;600031,merchant/main" width=110 height=25 back="L2UI_CT1.Button_DF_Down" fore="L2UI_CT1.Button_DF"></td></tr>
</table></td></tr>
<tr><td height=10></td></tr>
<tr><td align=center><button value="Sell Items" action="bypass _bbssell;merchant/main" width=110 height=25 back="L2UI_CT1.Button_DF_Down" fore="L2UI_CT1.Button_DF"></td></tr>
<tr><td height=18></td></tr>
</table></center></td></tr></table></body></html>
'@
    Write-Page $root 'merchant\main.html' $main

    $servicesMain = @'
<html noscrollbar><body><table width=700><tr><td width=205>%navigation%</td><td width=480><center>
<table width=455 background="L2UI_CT1.Windows_DF_TooltipBG">
<tr><td height=22></td></tr>
<tr><td align=center><font name="hs12" color="CDB67F">Services</font></td></tr>
<tr><td height=14></td></tr>
<tr><td align=center><button value="All Cities" action="bypass _bbstop;services/cities.html" width=170 height=27 back="L2UI_CT1.Button_DF_Down" fore="L2UI_CT1.Button_DF"></td></tr>
<tr><td align=center><button value="Hunting Regions" action="bypass _bbstop;services/regions.html" width=170 height=27 back="L2UI_CT1.Button_DF_Down" fore="L2UI_CT1.Button_DF"></td></tr>
<tr><td align=center><button value="Instances / Epic Entrances" action="bypass _bbstop;services/instances.html" width=170 height=27 back="L2UI_CT1.Button_DF_Down" fore="L2UI_CT1.Button_DF"></td></tr>
<tr><td height=10></td></tr>
<tr><td align=center><font color="AAAAAA">Instance rules and entry checks remain active.</font></td></tr>
<tr><td height=18></td></tr>
</table></center></td></tr></table></body></html>
'@
    $cities = @'
<html noscrollbar><body><table width=700><tr><td width=205>%navigation%</td><td width=480><center>
<table width=455 background="L2UI_CT1.Windows_DF_TooltipBG">
<tr><td height=22></td></tr>
<tr><td align=center><font name="hs12" color="CDB67F">All Cities</font></td></tr>
<tr><td height=14></td></tr>
<tr><td align=center><table>
<tr><td><button value="Giran" action="bypass _bbsteleport;Giran" width=140 height=25 back="L2UI_CT1.Button_DF_Down" fore="L2UI_CT1.Button_DF"></td><td><button value="Aden" action="bypass _bbsteleport;Aden" width=140 height=25 back="L2UI_CT1.Button_DF_Down" fore="L2UI_CT1.Button_DF"></td></tr>
<tr><td><button value="Goddard" action="bypass _bbsteleport;Goddard" width=140 height=25 back="L2UI_CT1.Button_DF_Down" fore="L2UI_CT1.Button_DF"></td><td><button value="Rune" action="bypass _bbsteleport;Rune" width=140 height=25 back="L2UI_CT1.Button_DF_Down" fore="L2UI_CT1.Button_DF"></td></tr>
<tr><td><button value="Dion" action="bypass _bbsteleport;Dion" width=140 height=25 back="L2UI_CT1.Button_DF_Down" fore="L2UI_CT1.Button_DF"></td><td><button value="Oren" action="bypass _bbsteleport;Oren" width=140 height=25 back="L2UI_CT1.Button_DF_Down" fore="L2UI_CT1.Button_DF"></td></tr>
<tr><td><button value="Gludio" action="bypass _bbsteleport;Gludio" width=140 height=25 back="L2UI_CT1.Button_DF_Down" fore="L2UI_CT1.Button_DF"></td><td><button value="Schuttgart" action="bypass _bbsteleport;Schuttgart" width=140 height=25 back="L2UI_CT1.Button_DF_Down" fore="L2UI_CT1.Button_DF"></td></tr>
<tr><td><button value="Heine" action="bypass _bbsteleport;Heine" width=140 height=25 back="L2UI_CT1.Button_DF_Down" fore="L2UI_CT1.Button_DF"></td><td><button value="Gludin" action="bypass _bbsteleport;Gludin" width=140 height=25 back="L2UI_CT1.Button_DF_Down" fore="L2UI_CT1.Button_DF"></td></tr>
<tr><td><button value="Hunters Village" action="bypass _bbsteleport;Hunters" width=140 height=25 back="L2UI_CT1.Button_DF_Down" fore="L2UI_CT1.Button_DF"></td></tr>
</table></td></tr>
<tr><td height=14></td></tr>
<tr><td align=center><button value="Back" action="bypass _bbstop;gatekeeper/main.html" width=100 height=25 back="L2UI_CT1.Button_DF_Down" fore="L2UI_CT1.Button_DF"></td></tr>
<tr><td height=18></td></tr>
</table></center></td></tr></table></body></html>
'@
    $regions = @'
<html noscrollbar><body><table width=700><tr><td width=205>%navigation%</td><td width=480><center>
<table width=455 background="L2UI_CT1.Windows_DF_TooltipBG">
<tr><td height=22></td></tr>
<tr><td align=center><font name="hs12" color="CDB67F">Hunting Regions</font></td></tr>
<tr><td height=14></td></tr>
<tr><td align=center><table>
<tr><td><button value="Cruma Tower" action="bypass _bbsteleport;CrumaTower" width=140 height=25 back="L2UI_CT1.Button_DF_Down" fore="L2UI_CT1.Button_DF"></td><td><button value="Dragon Valley" action="bypass _bbsteleport;DragonValley" width=140 height=25 back="L2UI_CT1.Button_DF_Down" fore="L2UI_CT1.Button_DF"></td></tr>
<tr><td><button value="Tower of Insolence" action="bypass _bbsteleport;TowerInsolence" width=140 height=25 back="L2UI_CT1.Button_DF_Down" fore="L2UI_CT1.Button_DF"></td><td><button value="Giant's Cave" action="bypass _bbsteleport;GiantsCave" width=140 height=25 back="L2UI_CT1.Button_DF_Down" fore="L2UI_CT1.Button_DF"></td></tr>
<tr><td><button value="Blazing Swamp" action="bypass _bbsteleport;BlazingSwamp" width=140 height=25 back="L2UI_CT1.Button_DF_Down" fore="L2UI_CT1.Button_DF"></td><td><button value="Forge of Gods" action="bypass _bbsteleport;ForgeGods" width=140 height=25 back="L2UI_CT1.Button_DF_Down" fore="L2UI_CT1.Button_DF"></td></tr>
<tr><td><button value="Ketra Orc Outpost" action="bypass _bbsteleport;Ketra" width=140 height=25 back="L2UI_CT1.Button_DF_Down" fore="L2UI_CT1.Button_DF"></td><td><button value="Varka Silenos" action="bypass _bbsteleport;Varka" width=140 height=25 back="L2UI_CT1.Button_DF_Down" fore="L2UI_CT1.Button_DF"></td></tr>
<tr><td><button value="Hot Springs" action="bypass _bbsteleport;HotSprings" width=140 height=25 back="L2UI_CT1.Button_DF_Down" fore="L2UI_CT1.Button_DF"></td><td><button value="Monastery" action="bypass _bbsteleport;Monastery" width=140 height=25 back="L2UI_CT1.Button_DF_Down" fore="L2UI_CT1.Button_DF"></td></tr>
<tr><td><button value="Valley of Saints" action="bypass _bbsteleport;ValleySaints" width=140 height=25 back="L2UI_CT1.Button_DF_Down" fore="L2UI_CT1.Button_DF"></td><td><button value="Stakato Nest" action="bypass _bbsteleport;StakatoNest" width=140 height=25 back="L2UI_CT1.Button_DF_Down" fore="L2UI_CT1.Button_DF"></td></tr>
<tr><td><button value="Isle of Prayer" action="bypass _bbsteleport;IslePrayer" width=140 height=25 back="L2UI_CT1.Button_DF_Down" fore="L2UI_CT1.Button_DF"></td><td><button value="Primeval Isle" action="bypass _bbsteleport;PrimevalWharf" width=140 height=25 back="L2UI_CT1.Button_DF_Down" fore="L2UI_CT1.Button_DF"></td></tr>
<tr><td><button value="Pavel Ruins" action="bypass _bbsteleport;PavelRuins" width=140 height=25 back="L2UI_CT1.Button_DF_Down" fore="L2UI_CT1.Button_DF"></td></tr>
</table></td></tr>
<tr><td height=14></td></tr>
<tr><td align=center><button value="Back" action="bypass _bbstop;gatekeeper/main.html" width=100 height=25 back="L2UI_CT1.Button_DF_Down" fore="L2UI_CT1.Button_DF"></td></tr>
<tr><td height=18></td></tr>
</table></center></td></tr></table></body></html>
'@
    $instances = @'
<html noscrollbar><body><table width=700><tr><td width=205>%navigation%</td><td width=480><center>
<table width=455 background="L2UI_CT1.Windows_DF_TooltipBG">
<tr><td height=22></td></tr>
<tr><td align=center><font name="hs12" color="CDB67F">Instances and Epic Entrances</font></td></tr>
<tr><td height=14></td></tr>
<tr><td align=center><table>
<tr><td><button value="Antharas Lair" action="bypass _bbsteleport;AntharasLair" width=140 height=25 back="L2UI_CT1.Button_DF_Down" fore="L2UI_CT1.Button_DF"></td><td><button value="Zaken / Devil's Isle" action="bypass _bbsteleport;DevilsIsle" width=140 height=25 back="L2UI_CT1.Button_DF_Down" fore="L2UI_CT1.Button_DF"></td></tr>
<tr><td><button value="Giran Harbor" action="bypass _bbsteleport;GiranHarbor" width=140 height=25 back="L2UI_CT1.Button_DF_Down" fore="L2UI_CT1.Button_DF"></td><td><button value="Forge of Gods" action="bypass _bbsteleport;ForgeGods" width=140 height=25 back="L2UI_CT1.Button_DF_Down" fore="L2UI_CT1.Button_DF"></td></tr>
<tr><td><button value="Monastery" action="bypass _bbsteleport;Monastery" width=140 height=25 back="L2UI_CT1.Button_DF_Down" fore="L2UI_CT1.Button_DF"></td><td><button value="Mithril Mines" action="bypass _bbsteleport;MithrilMines" width=140 height=25 back="L2UI_CT1.Button_DF_Down" fore="L2UI_CT1.Button_DF"></td></tr>
</table></td></tr>
<tr><td height=10></td></tr>
<tr><td align=center><font color="AAAAAA">Use the local NPC to enter the instance.</font></td></tr>
<tr><td height=10></td></tr>
<tr><td align=center><button value="Back" action="bypass _bbstop;gatekeeper/main.html" width=100 height=25 back="L2UI_CT1.Button_DF_Down" fore="L2UI_CT1.Button_DF"></td></tr>
<tr><td height=18></td></tr>
</table></center></td></tr></table></body></html>
'@
    Write-Page $root 'services\main.html' $servicesMain
    Write-Page $root 'services\cities.html' $cities
    Write-Page $root 'services\regions.html' $regions
    Write-Page $root 'services\instances.html' $instances
    $gatekeeper = @'
<html noscrollbar><body><table width=700><tr><td width=205>%navigation%</td><td width=480><center>
<table width=455 background="L2UI_CT1.Windows_DF_TooltipBG">
<tr><td height=22></td></tr>
<tr><td align=center><font name="hs12" color="CDB67F">Gatekeeper</font></td></tr>
<tr><td align=center><font color="AAAAAA">Choose a city to see its surrounding areas</font></td></tr>
<tr><td height=12></td></tr>
<tr><td align=center><table>
<tr><td><button value="Giran" action="bypass _bbstop;gatekeeper/giran.html" width=150 height=27 back="L2UI_CT1.Button_DF_Down" fore="L2UI_CT1.Button_DF"></td><td><button value="Aden" action="bypass _bbstop;gatekeeper/aden.html" width=150 height=27 back="L2UI_CT1.Button_DF_Down" fore="L2UI_CT1.Button_DF"></td></tr>
<tr><td><button value="Oren" action="bypass _bbstop;gatekeeper/oren.html" width=150 height=27 back="L2UI_CT1.Button_DF_Down" fore="L2UI_CT1.Button_DF"></td><td><button value="Goddard" action="bypass _bbstop;gatekeeper/goddard.html" width=150 height=27 back="L2UI_CT1.Button_DF_Down" fore="L2UI_CT1.Button_DF"></td></tr>
<tr><td><button value="Rune" action="bypass _bbstop;gatekeeper/rune.html" width=150 height=27 back="L2UI_CT1.Button_DF_Down" fore="L2UI_CT1.Button_DF"></td><td><button value="Dion" action="bypass _bbstop;gatekeeper/dion.html" width=150 height=27 back="L2UI_CT1.Button_DF_Down" fore="L2UI_CT1.Button_DF"></td></tr>
<tr><td><button value="Heine" action="bypass _bbstop;gatekeeper/heine.html" width=150 height=27 back="L2UI_CT1.Button_DF_Down" fore="L2UI_CT1.Button_DF"></td><td><button value="Schuttgart" action="bypass _bbstop;gatekeeper/schuttgart.html" width=150 height=27 back="L2UI_CT1.Button_DF_Down" fore="L2UI_CT1.Button_DF"></td></tr>
</table></td></tr>
<tr><td height=12></td></tr>
<tr><td align=center><table><tr>
<td><button value="All Cities" action="bypass _bbstop;services/cities.html" width=100 height=27 back="L2UI_CT1.Button_DF_Down" fore="L2UI_CT1.Button_DF"></td>
<td><button value="Hunting Regions" action="bypass _bbstop;services/regions.html" width=100 height=27 back="L2UI_CT1.Button_DF_Down" fore="L2UI_CT1.Button_DF"></td>
<td><button value="Instances / Epics" action="bypass _bbstop;services/instances.html" width=100 height=27 back="L2UI_CT1.Button_DF_Down" fore="L2UI_CT1.Button_DF"></td>
</tr></table></td></tr>
<tr><td align=center><button value="Leveling" action="bypass _bbstop;gatekeeper/leveling.html" width=150 height=27 back="L2UI_CT1.Button_DF_Down" fore="L2UI_CT1.Button_DF"></td></tr>
<tr><td height=18></td></tr>
</table></center></td></tr></table></body></html>
'@
    Write-Page $root 'gatekeeper\main.html' $gatekeeper
    $leveling = @'
<html noscrollbar><body><table width=700><tr><td width=205>%navigation%</td><td width=480><center>
<table width=455 background="L2UI_CT1.Windows_DF_TooltipBG">
<tr><td height=22></td></tr>
<tr><td align=center><font name="hs12" color="CDB67F">Gatekeeper - Leveling</font></td></tr>
<tr><td align=center><font color="AAAAAA">Rotas rapidas para upar com spots reforcados.</font></td></tr>
<tr><td height=14></td></tr>
<tr><td align=center><table>
<tr><td><button value="0 - 20" action="bypass _bbsteleport;Leveling0_20" width=145 height=27 back="L2UI_CT1.Button_DF_Down" fore="L2UI_CT1.Button_DF"></td><td><font color="AAAAAA">Ruins / starting mobs</font></td></tr>
<tr><td><button value="20 - 40" action="bypass _bbsteleport;Leveling20_40" width=145 height=27 back="L2UI_CT1.Button_DF_Down" fore="L2UI_CT1.Button_DF"></td><td><font color="AAAAAA">Cruma Marshlands</font></td></tr>
<tr><td><button value="40 - 60" action="bypass _bbsteleport;Leveling40_60" width=145 height=27 back="L2UI_CT1.Button_DF_Down" fore="L2UI_CT1.Button_DF"></td><td><font color="AAAAAA">Fields of Massacre</font></td></tr>
<tr><td><button value="60 - 80" action="bypass _bbsteleport;Leveling60_80" width=145 height=27 back="L2UI_CT1.Button_DF_Down" fore="L2UI_CT1.Button_DF"></td><td><font color="AAAAAA">Varka Silenos</font></td></tr>
<tr><td><button value="80 +" action="bypass _bbsteleport;Leveling80Plus" width=145 height=27 back="L2UI_CT1.Button_DF_Down" fore="L2UI_CT1.Button_DF"></td><td><font color="AAAAAA">Primeval Isle</font></td></tr>
</table></td></tr>
<tr><td height=14></td></tr>
<tr><td align=center><button value="Back" action="bypass _bbstop;gatekeeper/main.html" width=100 height=25 back="L2UI_CT1.Button_DF_Down" fore="L2UI_CT1.Button_DF"></td></tr>
<tr><td height=18></td></tr>
</table></center></td></tr></table></body></html>
'@
    Write-Page $root 'gatekeeper\leveling.html' $leveling
    $cityAreas = [ordered]@{
        giran = @('Giran|Giran','Dragon Valley|DragonValley','Antharas Lair|AntharasLair','Breka Stronghold|BrekaStronghold','Giran Harbor|GiranHarbor','Hardin Academy|HardinsAcademy')
        aden = @('Aden|Aden','Forsaken Plains|ForsakenPlains','Blazing Swamp|BlazingSwamp','Fields of Massacre|FieldsMassacre','Tower of Insolence|TowerInsolence','Giants Cave|GiantsCave')
        oren = @('Oren|Oren','Ivory Tower|IvoryTower','Sea of Spores|SeaSpores','Lizardmen Plains|LizardmenPlains')
        goddard = @('Goddard|Goddard','Ketra Outpost|Ketra','Varka Stronghold|Varka','Hot Springs|HotSprings','Forge of Gods|ForgeGods')
        rune = @('Rune|Rune','Valley of Saints|ValleySaints','Forest of Dead|ForestDead','Swamp of Screams|SwampScreams','Monastery|Monastery','Stakato Nest|StakatoNest')
        dion = @('Dion|Dion','Cruma Tower|CrumaTower','Dragon Valley|DragonValley')
        heine = @('Heine|Heine','Field of Silence|FieldSilence','Field of Whispers|FieldWhispers','Alligator Island|AlligatorIsland','Garden of Eva|GardenEva','Isle of Prayer|IslePrayer')
        schuttgart = @('Schuttgart|Schuttgart','Den of Evil|DenEvil','Pavel Ruins|PavelRuins','Icemans Hut|IcemansHut','Crypts of Disgrace|CryptsDisgrace','Mithril Mines|MithrilMines')
    }
    foreach ($city in $cityAreas.Keys) {
        $body = [Text.StringBuilder]::new()
        [void]$body.Append("<html noscrollbar><body><table width=700><tr><td width=205>%navigation%</td><td width=480><center><table width=455 background=`"L2UI_CT1.Windows_DF_TooltipBG`"><tr><td height=22></td></tr><tr><td align=center><font name=hs12 color=CDB67F>$($city.ToUpper()) Region</font></td></tr><tr><td height=14></td></tr><tr><td align=center><table>")
        $areaIndex = 0
        foreach ($destination in $cityAreas[$city]) {
            $pair = $destination.Split('|')
            if (($areaIndex % 2) -eq 0) { [void]$body.Append('<tr>') }
            [void]$body.Append("<td><button value=`"$($pair[0])`" action=`"bypass _bbsteleport;$($pair[1])`" width=155 height=27 back=`"L2UI_CT1.Button_DF_Down`" fore=`"L2UI_CT1.Button_DF`"></td>")
            if (($areaIndex % 2) -eq 1) { [void]$body.Append('</tr>') }
            $areaIndex++
        }
        if (($areaIndex % 2) -eq 1) { [void]$body.Append('<td></td></tr>') }
        [void]$body.Append("</table></td></tr><tr><td height=14></td></tr><tr><td align=center><button value=`"Back`" action=`"bypass _bbstop;gatekeeper/main.html`" width=100 height=25 back=`"L2UI_CT1.Button_DF_Down`" fore=`"L2UI_CT1.Button_DF`"></td></tr><tr><td height=18></td></tr></table></center></td></tr></table></body></html>")
        Write-Page $root "gatekeeper\$city.html" $body.ToString()
    }
    $bufferMain = @'
<html noscrollbar><body><table width=700><tr><td width=205>%navigation%</td><td width=480><center>
<table width=455 background="L2UI_CT1.Windows_DF_TooltipBG">
<tr><td height=22></td></tr>
<tr><td align=center><font name="hs12" color="CDB67F">Community Buffer</font></td></tr>
<tr><td align=center><font color="AAAAAA">Buffs last 3 hours and persist after relog.</font></td></tr>
<tr><td height=12></td></tr>
<tr><td align=center><table>
<tr><td><button value="Prophet" action="bypass _bbsbufflist;Prophet;1" width=150 height=27 back="L2UI_CT1.Button_DF_Down" fore="L2UI_CT1.Button_DF"></td><td><button value="Elven Elder" action="bypass _bbsbufflist;ElvenElder;1" width=150 height=27 back="L2UI_CT1.Button_DF_Down" fore="L2UI_CT1.Button_DF"></td></tr>
<tr><td><button value="Warcryer" action="bypass _bbsbufflist;Warcryer;1" width=150 height=27 back="L2UI_CT1.Button_DF_Down" fore="L2UI_CT1.Button_DF"></td><td><button value="Shillien Elder" action="bypass _bbsbufflist;ShillienElder;1" width=150 height=27 back="L2UI_CT1.Button_DF_Down" fore="L2UI_CT1.Button_DF"></td></tr>
<tr><td><button value="Overlord" action="bypass _bbsbufflist;Overlord;1" width=150 height=27 back="L2UI_CT1.Button_DF_Down" fore="L2UI_CT1.Button_DF"></td><td><button value="Special" action="bypass _bbsbufflist;Special;1" width=150 height=27 back="L2UI_CT1.Button_DF_Down" fore="L2UI_CT1.Button_DF"></td></tr>
<tr><td><button value="Bladedancer" action="bypass _bbsbufflist;Bladedancer;1" width=150 height=27 back="L2UI_CT1.Button_DF_Down" fore="L2UI_CT1.Button_DF"></td><td><button value="Hero" action="bypass _bbsbufflist;Hero;1" width=150 height=27 back="L2UI_CT1.Button_DF_Down" fore="L2UI_CT1.Button_DF"></td></tr>
<tr><td><button value="Swordsinger" action="bypass _bbsbufflist;Swordsinger;1" width=150 height=27 back="L2UI_CT1.Button_DF_Down" fore="L2UI_CT1.Button_DF"></td><td><button value="Dwarf" action="bypass _bbsbufflist;Dwarf;1" width=150 height=27 back="L2UI_CT1.Button_DF_Down" fore="L2UI_CT1.Button_DF"></td></tr>
</table></td></tr>
<tr><td height=8></td></tr>
<tr><td align=center><table><tr>
<td><button value="Noblesse" action="bypass _bbsbufflist;Noblesse;1" width=150 height=27 back="L2UI_CT1.Button_DF_Down" fore="L2UI_CT1.Button_DF"></td>
<td><button value="Improved" action="bypass _bbsbufflist;Improved;1" width=150 height=27 back="L2UI_CT1.Button_DF_Down" fore="L2UI_CT1.Button_DF"></td>
</tr></table></td></tr>
<tr><td align=center><button value="VIP Buffs +30" action="bypass _bbstop;buffer/vip.html" width=150 height=27 back="L2UI_CT1.Button_DF_Down" fore="L2UI_CT1.Button_DF"></td></tr>
<tr><td height=12></td></tr>
<tr><td align=center><font color="LEVEL">Pacotes Prontos</font></td></tr>
<tr><td align=center><table><tr>
<td><button value="Fighter" action="bypass _bbspreset;fighter" width=100 height=27 back="L2UI_CT1.Button_DF_Down" fore="L2UI_CT1.Button_DF"></td>
<td><button value="Tanker" action="bypass _bbspreset;tanker" width=100 height=27 back="L2UI_CT1.Button_DF_Down" fore="L2UI_CT1.Button_DF"></td>
<td><button value="Sorcerer" action="bypass _bbspreset;sorcerer" width=100 height=27 back="L2UI_CT1.Button_DF_Down" fore="L2UI_CT1.Button_DF"></td>
</tr></table></td></tr>
<tr><td height=12></td></tr>
<tr><td align=center><table><tr>
<td><button value="Heal" action="bypass _bbsheal;buffer/main" width=100 height=25 back="L2UI_CT1.Button_DF_Down" fore="L2UI_CT1.Button_DF"></td>
<td><button value="Remove Buffs" action="bypass _bbscleanup" width=100 height=25 back="L2UI_CT1.Button_DF_Down" fore="L2UI_CT1.Button_DF"></td>
<td><button value="My Schemes" action="bypass _bbsscheme" width=100 height=25 back="L2UI_CT1.Button_DF_Down" fore="L2UI_CT1.Button_DF"></td>
</tr></table></td></tr>
<tr><td height=18></td></tr>
</table></center></td></tr></table></body></html>
'@
    Write-Page $root 'buffer\main.html' $bufferMain
    $bufferVip = @'
<html noscrollbar><body><table width=700><tr><td width=205>%navigation%</td><td width=480><center>
<table width=455 background="L2UI_CT1.Windows_DF_TooltipBG">
<tr><td height=22></td></tr>
<tr><td align=center><font name="hs12" color="CDB67F">VIP Buffer +30</font></td></tr>
<tr><td align=center><font color="AAAAAA">Skills enchantadas +30 - exclusivo para contas VIP.</font></td></tr>
<tr><td height=12></td></tr>
<tr><td align=center><table>
<tr><td><button value="Prophet" action="bypass _bbsbufflist;VIP-Prophet;1" width=150 height=27 back="L2UI_CT1.Button_DF_Down" fore="L2UI_CT1.Button_DF"></td><td><button value="Elven Elder" action="bypass _bbsbufflist;VIP-ElvenElder;1" width=150 height=27 back="L2UI_CT1.Button_DF_Down" fore="L2UI_CT1.Button_DF"></td></tr>
<tr><td><button value="Warcryer" action="bypass _bbsbufflist;VIP-Warcryer;1" width=150 height=27 back="L2UI_CT1.Button_DF_Down" fore="L2UI_CT1.Button_DF"></td><td><button value="Shillien Elder" action="bypass _bbsbufflist;VIP-ShillienElder;1" width=150 height=27 back="L2UI_CT1.Button_DF_Down" fore="L2UI_CT1.Button_DF"></td></tr>
<tr><td><button value="Overlord" action="bypass _bbsbufflist;VIP-Overlord;1" width=150 height=27 back="L2UI_CT1.Button_DF_Down" fore="L2UI_CT1.Button_DF"></td><td><button value="Special" action="bypass _bbsbufflist;VIP-Special;1" width=150 height=27 back="L2UI_CT1.Button_DF_Down" fore="L2UI_CT1.Button_DF"></td></tr>
<tr><td><button value="Bladedancer" action="bypass _bbsbufflist;VIP-Bladedancer;1" width=150 height=27 back="L2UI_CT1.Button_DF_Down" fore="L2UI_CT1.Button_DF"></td><td><button value="Hero" action="bypass _bbsbufflist;VIP-Hero;1" width=150 height=27 back="L2UI_CT1.Button_DF_Down" fore="L2UI_CT1.Button_DF"></td></tr>
<tr><td><button value="Swordsinger" action="bypass _bbsbufflist;VIP-Swordsinger;1" width=150 height=27 back="L2UI_CT1.Button_DF_Down" fore="L2UI_CT1.Button_DF"></td><td><button value="Dwarf" action="bypass _bbsbufflist;VIP-Dwarf;1" width=150 height=27 back="L2UI_CT1.Button_DF_Down" fore="L2UI_CT1.Button_DF"></td></tr>
</table></td></tr>
<tr><td height=8></td></tr>
<tr><td align=center><button value="Noblesse" action="bypass _bbsbufflist;VIP-Noblesse;1" width=150 height=27 back="L2UI_CT1.Button_DF_Down" fore="L2UI_CT1.Button_DF"></td></tr>
<tr><td height=12></td></tr>
<tr><td align=center><button value="Voltar" action="bypass _bbstop;buffer/main.html" width=100 height=25 back="L2UI_CT1.Button_DF_Down" fore="L2UI_CT1.Button_DF"></td></tr>
<tr><td height=18></td></tr>
</table></center></td></tr></table></body></html>
'@
    Write-Page $root 'buffer\vip.html' $bufferVip
    $classTransfer = @'
<html noscrollbar><body><table width=700><tr><td width=205>%navigation%</td><td width=480><center>
<table width=455 background="L2UI_CT1.Windows_DF_TooltipBG">
<tr><td height=22></td></tr>
<tr><td align=center><font name="hs12" color="CDB67F">Class Transfer</font></td></tr>
<tr><td align=center><font color="AAAAAA">Free profession advancement</font></td></tr>
<tr><td height=14></td></tr>
<tr><td align=center><button value="First Class Transfer" action="bypass Script ClassMaster firstclass" width=180 height=28 back="L2UI_CT1.Button_DF_Down" fore="L2UI_CT1.Button_DF"></td></tr>
<tr><td align=center><button value="Second Class Transfer" action="bypass Script ClassMaster secondclass" width=180 height=28 back="L2UI_CT1.Button_DF_Down" fore="L2UI_CT1.Button_DF"></td></tr>
<tr><td align=center><button value="Third Class Transfer" action="bypass Script ClassMaster thirdclass" width=180 height=28 back="L2UI_CT1.Button_DF_Down" fore="L2UI_CT1.Button_DF"></td></tr>
<tr><td height=10></td></tr>
<tr><td align=center><font color="LEVEL">Only advances the active class. The base class is never replaced.</font></td></tr>
<tr><td height=18></td></tr>
</table></center></td></tr></table></body></html>
'@
    Write-Page $root 'class\main.html' $classTransfer
}

Write-Output "Generated shop lists from $($items.Count) eligible item definitions."
