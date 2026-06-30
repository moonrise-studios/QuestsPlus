# QuestsPlus

![QuestsPlus Cover Image](https://cdn.modrinth.com/data/cached_images/014af0d98c15b0dcf1277df6007f6d0b0db677c4.jpeg)
> See [LICENSE.MD](LICENSE.MD) before using, modifying, or redistributing this plugin.

[![Modrinth](https://img.shields.io/modrinth/v/questsplus?logo=modrinth&label=Modrinth&color=00AF5C)](https://modrinth.com/plugin/questsplus)
[![Modrinth downloads](https://img.shields.io/modrinth/dt/questsplus?logo=modrinth&label=Downloads&color=00AF5C)](https://modrinth.com/plugin/questsplus)

**QuestsPlus** combines randomly generated daily quests, shared global quests, progression systems, and flexible reset options in one plugin. It is designed for servers that want repeatable content, long-term player goals, and configurable reward loops without forcing players through the same fixed quest path every day.

## At a Glance

- Randomly generated daily quests from your configured quest pools
- Permission-based rerolls and Premium Quest slots
- Quest Reset purchases with PlayerPoints, Vault, or custom currencies
- Weekly global quests with percentile-based rewards
- Streaks, milestones, shields, and recovery systems
- Built-in survival quest types with SDK support for custom expansion

## Optional Dependencies

- **[PlayerPoints](https://www.spigotmc.org/resources/playerpoints.80745/)** - points-based quest resets
- **[Vault](https://dev.bukkit.org/projects/vault)** - economy-based quest resets
- **[RoseStacker](https://www.spigotmc.org/resources/rosestacker.82729/)** - stacked mob kill quest support

## Main Features

### Daily Quests

Daily quests are randomly generated from your defined configuration files. Each generated quest pulls from your configured quest definitions, values, and goals, which can include items, blocks, mobs, and more.

<details>
<summary>Randomly Generated Quests</summary>

All quests are randomly generated from the defined configuration files. The algorithm gets a random defined quest, a random number from the defined quest, and a random "goal" from the defined quest, which could be an item, block, mob, etc.

#### Example

![Example Quest](https://cdn.modrinth.com/data/cached_images/07af3056ab9f7ccf0237ebf336dbd741192cebd8.png)

</details>

<details>
<summary>Daily Quest Rerolls</summary>

Set up permission based reroll amounts, in case the player does not like their quest, they can choose to reroll.

#### `daily.yml`

```yml
# Permission-based daily reroll limits.
rerolls:
  # Maps each key to permission questsplus.reroll.<key>; the highest matching value is used.
  permission-limits:
    vip: 1
    vipplus: 2
    mvp: 3
```

</details>

<details>
<summary>Premium Quests</summary>

You can set up permission based **additional** slots that are additive from the **base amount** that are considered **Premium Quests** which adds bonus rewards that you define in the configurations.

#### `premium_quests.yml`

```yml
# Maps each key to permission questsplus.premium.<key>; the highest matching value is used.
permission-limits:
  vip: 1
  vipplus: 2
  mvp: 3

# Premium bonus rewards keyed by quest difficulty id. These run after difficulty rewards and before quest-specific rewards.
rewards:
  easy:
    commands:
    - eco give <player> 125
    - eco give <player> 500
```

</details>

<details>
<summary>Quest Reset Purchases</summary>

Players can purchase a Quest Reset after completing every quest slot they currently have access to. A reset clears the current generated quests for that reset window, but keeps lifetime completions, milestones, streaks, shields, recoveries, and reroll usage.

Supported reset currencies:

- **PlayerPoints** through `currencies.yml`
- **Vault** through `currencies.yml`
- **Custom currencies** registered through the QuestsPlus SDK

#### `currencies.yml`

```yml
enabled-currencies:
- playerpoints
- vault

playerpoints:
  display-name: Player Points
  quest-reset-cost: 250

vault:
  display-name: Money
  quest-reset-cost: 5000.0
```

</details>

### Global Quests

Global quests are weekly server-wide goals where one quest is generated for the active period and every matching player action contributes to the same shared target.

<details>
<summary>Global Quests Details</summary>

Global quests are weekly server-wide goals. One global quest is generated for the active period, and all matching player actions contribute toward the same shared goal.

Each player contribution is tracked separately, allowing percentile-based rewards at the end of the global quest period.

#### `global-quests.yml`

```yml
schedule:
  day-of-week: FRIDAY
  time: "05:00"

reduced-reward-minimum-percent: 50.0

reward-tiers:
- percentile: 1.0
  display-name: <gold>Top 1%
  commands:
  - eco give <player> 10000

reduced-reward-tiers:
- percentile: 25.0
  display-name: <yellow>Top 25%
  commands:
  - eco give <player> 1000
```

#### Admin Commands

```txt
/qa global add <quest-type> <amount> <player>
/qa global refresh
```

</details>

### Progression Systems

QuestsPlus includes multiple ways to keep players progressing over time, from visible quest progress indicators to streaks and automatic milestone rewards.

<details>
<summary>Quest Indicators</summary>

Quest indicators can show progress updates through boss bars, action bars, or chat summaries.

Players can choose how personal and global progress is displayed.

```txt
/quests indicator <main|global|both> <type|off|default>
```

</details>

<details>
<summary>Streaks</summary>

QuestsPlus can track streaks based on the configured reset window. You can require all selected quests, or a fixed amount of completed quests, before a player earns streak progress.

Streak Shields can automatically protect a streak from being broken. Streak Recovery can restore a recently lost streak from the streak menu.

#### `streaks.yml`

```yml
daily-required-completions: -1
recovery-window-days: 3

milestones:
- streak: 7
  display-name: <gold>One Week Streak
  rewards:
    commands:
    - eco give <player> 2500
```

#### Admin Commands

```txt
/qa shield give <amount> <player>
/qa shield take <amount> <player>
/qa recovery give <amount> <player>
/qa recovery take <amount> <player>
```

</details>

<details>
<summary>Milestones</summary>

Reward players for lifetime quest completions per difficulty. Milestones are claimed automatically, so players do not need to click a reward button.

#### `difficulty/easy/milestones.yml`

```yml
milestones:
- completed: 10
  display-name: <green>Easy Starter
  rewards:
    commands:
    - eco give <player> 1000

- completed: 25
  display-name: <green>Easy Regular
  rewards:
    commands:
    - give <player> diamond 3
```

</details>

### Quest Types

QuestsPlus includes common survival quest types across combat, gathering, farming, crafting, exploration, and trading.

<details>
<summary>View Included Quest Types</summary>

QuestsPlus includes common survival quest types such as:

- Kill mobs
- Kill mobs in a specific world
- Break blocks
- Place blocks
- Harvest crops
- Craft items
- Smelt items
- Brew items
- Fish
- Enchant items
- Shear sheep
- Dye sheep
- Milk mobs
- Eat cake slices
- Throw items
- Trade with villagers
- Travel distance

Use this command in-game to view the exact registered quest type keys:

```txt
/qa listtypes
```

</details>

## Commands

Players can track progress, milestones, streaks, and indicators through player commands, while admins can manage reloads, resets, completions, rerolls, and global quest progress.

<details>
<summary>Player and Admin Commands</summary>

### Player Commands

```txt
/quests
/q
/quests progress
/quests completed
/quests milestones
/quests milestones <difficulty>
/quests streaks
/quests indicator <main|global|both> <type|off|default>
```

### Admin Commands

```txt
/questsadmin
/qa
/qa reload
/qa listtypes
/qa reset <player>
/qa complete <player>
/qa dailyrerolls reset <player>
/qa add <amount> <quest-type> <player>
/qa global add <quest-type> <amount> <player>
/qa global refresh
```

</details>

## Compatibility

QuestsPlus supports SQLite out of the box, as well as MySQL, MariaDB, and PostgreSQL for servers that want an external database.

<details>
<summary>Database Support</summary>

QuestsPlus supports:

- **SQLite** - default database, no setup required
- **MySQL**
- **MariaDB**
- **PostgreSQL**

</details>

<details>
<summary>SDK Support</summary>

QuestsPlus includes an SDK for external plugins that want to register custom quest types or custom Quest Reset currencies.

External quest types can progress through the same completion, reward, milestone, streak, and global quest pipeline as built-in quests.

Custom currencies can be registered for Quest Reset purchases, allowing servers to charge custom points, tokens, or other plugin-managed balances.

</details>


## Full Reference

This README is the server-owner overview. For exhaustive examples, placeholder
tables, quest-type variable requirements, and GUI layout details, read
[wiki/questsplus.md](wiki/questsplus.md).
