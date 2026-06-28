# QuestsPlus
> See [LICENSE.MD](LICENSE.MD) before using, modifying, or redistributing this plugin.

QuestsPlus is a Paper quest plugin for servers that want configurable daily
quests, premium quest slots, quest reset purchases, streak systems, milestones,
and weekly global goals without forcing every reward or menu into one file.

It is designed around operator-owned configuration. Server owners control the
reset schedule, quest pools, difficulty progression, GUI items, reward commands,
premium access, reroll allowances, progress indicators, global quest rewards,
and staff tools from modular files under `plugins/QuestsPlus/`.

## Server Requirements

- Paper `1.21.x`
- Java 21
- PlayerPoints, optional; enables points-based Quest Reset purchases
- Vault, optional; enables money-based Quest Reset purchases when an economy
  provider is registered
- RoseStacker, optional; stacked mob kills can count toward matching mob-kill
  quests when the plugin is installed

QuestsPlus declares PlayerPoints, Vault, and RoseStacker as optional plugin
dependencies. Currency-backed reset purchases only appear for the enabled
currency integrations that are present on the server. Other plugins can also
register Quest Reset currencies through the QuestsPlus SDK.

## What QuestsPlus Adds

- Player-selected daily quest slots with configurable difficulty pools.
- Daily or weekly personal quest reset schedules.
- Permission-based daily reroll limits.
- Permission-based premium quest slots with locked-slot display items.
- Quest Reset purchases using optional PlayerPoints, Vault, or SDK-registered
  currencies.
- Per-difficulty lifetime completion milestones.
- Quest streaks with shields, recovery, and streak milestone rewards.
- Weekly global quests with contribution tracking and percentile reward tiers.
- Boss bar, action bar, and chat progress indicators.
- Admin commands for reloads, testing, resets, progress grants, and streak
  currency management.

## First Setup

1. Install QuestsPlus on a Paper server running Java 21.
2. Install PlayerPoints, Vault, or a custom QuestsPlus currency plugin if quest
   reset purchases should use external currency. Add RoseStacker if stacked mob
   kills should count toward combat quests.
3. Start the server once so QuestsPlus can generate its default files.
4. Edit the files under `plugins/QuestsPlus/`.
5. Run `/qa reload` after config changes.
6. Use `/qa listtypes` to verify available quest type keys before writing new
   quest definitions.

Older flat files such as `config.yml`, `storage.yml`, `quests.yml`,
`difficulties.yml`, and `milestones.yml` are not used by this version. New
installs use the modular layout below.

## Configuration Layout

| File or folder | Owns |
|---|---|
| `daily.yml` | Daily/weekly reset mode, quest count, reroll limits, daily menu, difficulty picker, and daily messages |
| `currencies.yml` | Enabled currency keys plus PlayerPoints/Vault display, cost, and purchase button settings |
| `difficulty/<id>/settings.yml` | Difficulty display name, lore, menu placement, requirements, and baseline rewards |
| `difficulty/<id>/quests.yml` | Personal quest definitions for that difficulty |
| `difficulty/<id>/milestones.yml` | Lifetime completion milestones for that difficulty |
| `premium_quests.yml` | Premium slot permissions, locked-slot items, premium quest styling, and bonus rewards |
| `streaks.yml` | Streak rules, shields, recovery, streak milestones, and streak menus |
| `global-quests.yml` | Weekly global quest schedule, global definitions, contribution rewards, and preview menu |
| `messages.yml` | Shared messages, admin messages, milestone menus, and progress indicator settings |

Most menu text, messages, quest descriptions, and reward displays support
MiniMessage formatting. Reward and display text can use placeholders for player
name, quest name, difficulty, goal amount, progress, target mob, target block,
target item, reset timer, global contribution, and global rank.

For the full field-by-field configuration reference, see
[wiki/questsplus.md](wiki/questsplus.md).

## Daily Quest Operation

Daily quests are selected by the player from empty menu slots. The slot opens a
difficulty picker, and QuestsPlus generates one enabled quest from that
difficulty pool. The generated quest keeps its variables and progress until the
active reset window changes or an admin/reset action clears it.

Important operator rules:

- `daily.quest-count` controls normal personal quest slots.
- `daily.weekly: false` uses `daily.reset-time`.
- `daily.weekly: true` uses `daily.schedule.day-of-week` and
  `daily.schedule.time`.
- Reset windows use the server JVM timezone.
- Quest slots are empty until the player selects a difficulty.
- Generated quest variables do not reroll during the same reset window.
- Progress survives server restarts.
- Personal resets clear the current generated quests but do not clear lifetime
  completion totals, milestones, streaks, shields, recovery balances, or reroll
  usage.

## Difficulties And Quest Pools

Each folder in `difficulty/` is a difficulty id. For example:

```text
plugins/QuestsPlus/
  difficulty/
    easy/
      settings.yml
      quests.yml
      milestones.yml
    hard/
      settings.yml
      quests.yml
      milestones.yml
```

`settings.yml` controls the displayed difficulty name, menu lore, picker slot,
milestone selector slot, optional requirements, and baseline rewards.

`quests.yml` contains the personal quest definitions for that difficulty. A
quest definition chooses a type, display name, description, variables, schedule,
and optional extra rewards. Disabled definitions stay configured but are not
sampled.

`milestones.yml` contains completion thresholds for that difficulty. Milestone
commands run automatically when players reach the configured lifetime completed
count for that difficulty.

## Built-In Quest Types

QuestsPlus ships with quest types for common survival and economy loops:

| Category | Built-in goals |
|---|---|
| Combat | Kill a specific mob, kill a mob in a specific world, kill any mob |
| Blocks | Break a specific block, break any block, place a specific block, place any block |
| Items | Craft a specific item, craft any item, enchant items, harvest item drops |
| Smelting and brewing | Smelt a specific item, smelt any item, brew a specific item, brew any item |
| Animals | Shear sheep, dye sheep, milk cows/goats/mooshrooms |
| Movement | Travel a configured horizontal distance |
| Miscellaneous | Fish, eat cake slices, throw projectile items, trade with villagers |

Use `/qa listtypes` in-game to see the exact registered quest type keys. The
wiki includes the detailed variable requirements for each type.

## Rerolls

Reroll limits are configured in `daily.yml` under `rerolls.permission-limits`.
Each key maps to a permission using this format:

```text
questsplus.reroll.<key>
```

If a player has multiple matching reroll permissions, QuestsPlus uses the
highest configured value. Rerolls are per reset window, so weekly mode also
makes reroll usage weekly.

Rerolls are only consumed after the replacement quest is successfully saved.
Completed quests cannot be rerolled.

## Premium Quest Slots

Premium slots are configured in `premium_quests.yml`.

Each permission limit key maps to:

```text
questsplus.premium.<key>
```

Premium slots are appended after normal daily quest slots. Locked slots show the
configured locked item and do not open the difficulty picker. Unlocked premium
slots behave like normal slots but can have extra display text and premium bonus
rewards.

Locked premium slots do not count toward Quest Reset purchase requirements.
Unlocked premium slots do count.

## Quest Reset Purchases

Quest Reset purchase menu limits and shared status text are configured in
`daily.yml`. The `currencies.yml` `enabled-currencies` list controls which
currency keys may be offered; the default keys are `vault` and `playerpoints`.
Currency-specific costs and buttons are configured in `currencies.yml`. They let
a player clear their current reset-window quest slots after completing every slot
they can access.

Supported payment paths:

- Optional PlayerPoints via the `playerpoints` section in `currencies.yml`
- Optional Vault economy via the `vault` section in `currencies.yml`
- Custom currencies registered by other plugins through the QuestsPlus SDK

Each built-in currency must be listed in `currencies.yml` `enabled-currencies`
before it can appear. If a listed currency's backing plugin is missing,
QuestsPlus logs an error and hides that currency. SDK currencies own their cost,
button, display text, availability, and charge logic in the plugin that
registers them.

Reset purchase limits are per reset window. A successful purchase clears the
current generated quests but preserves lifetime completions, milestones, streaks,
shields, recoveries, and reroll usage.

## Milestones

Milestones reward lifetime completions per difficulty. They are configured in
`difficulty/<id>/milestones.yml`.

Milestone thresholds should be positive and unique within a difficulty.
QuestsPlus records claimed milestones so commands do not run twice for the same
player and threshold. If new milestones are added later, reloads can claim
eligible missed milestones for online players.

## Streaks

Streak behavior is configured in `streaks.yml`.

`daily-required-completions` controls how many selected/generated quests must be
completed in one reset window to award a streak point. `-1` means all selected
quests for that window.

Streak Shields and Streak Recoveries are virtual balances. Shields are consumed
automatically when a missed reset would break a streak. Recovery is used from
the streak menu to restore a recently lost streak inside the configured recovery
window.

Staff can manage balances with:

- `/qa shield give <amount> <player>`
- `/qa shield take <amount> <player>`
- `/qa recovery give <amount> <player>`
- `/qa recovery take <amount> <player>`

## Global Quests

Global quests are configured in `global-quests.yml`. They use a weekly schedule
and select one enabled global quest definition for the active period.

Global progress is shared by the server, while each player contribution is
tracked separately. At period end, QuestsPlus chooses reward behavior from
completion percentage:

- Below the configured reduced reward minimum: no global contribution rewards.
- From the reduced minimum through incomplete progress: reduced reward tiers.
- At full completion: full reward tiers.

Reward tiers are percentile-based. A player in a top tier can also receive
broader tiers they qualify for.

Admins can test global quests with:

- `/qa global add <quest-type> <amount> <player>`
- `/qa global refresh`

## Progress Indicators

Progress indicators are configured in `messages.yml`.

Supported indicator types:

- `BOSS_BAR`
- `ACTION_BAR`
- `CHAT`

Players can change personal and global indicator preferences with:

```text
/quests indicator <main|global|both> <type|off|default>
```

Operators can disable unavailable types in config and choose the default display
style.

## Permissions

| Permission | Purpose |
|---|---|
| `questsplus.admin` | Allows `/questsadmin` and `/qa` commands |
| `questsplus.reroll.<key>` | Grants the matching configured reroll limit |
| `questsplus.premium.<key>` | Grants the matching configured premium slot count |

The `<key>` values come from `daily.yml` reroll limits and
`premium_quests.yml` premium slot limits.

## Commands

### Player Commands

| Command | Purpose |
|---|---|
| `/quests`, `/q` | Open the daily quest menu |
| `/quests progress`, `/q progress` | Show current quest progress |
| `/quests completed`, `/q completed` | Show lifetime completed quest totals |
| `/quests milestones`, `/q milestones` | Open the milestone difficulty selector |
| `/quests milestones <difficulty>` | Open one difficulty's milestone menu |
| `/quests streaks`, `/q streaks` | Open the streak menu |
| `/quests indicator <main\|global\|both> <type\|off\|default>` | Set progress indicator preferences |

### Admin Commands

All admin commands require `questsplus.admin`.

| Command | Purpose |
|---|---|
| `/questsadmin`, `/qa` | Show admin usage |
| `/qa reload` | Reload config and validate quest definitions |
| `/qa listtypes` | List registered quest type keys |
| `/qa reset <player>` | Clear a player's current daily quest set |
| `/qa complete <player>` | Complete a player's current accessible quest set |
| `/qa dailyrerolls reset <player>` | Reset a player's used reroll count |
| `/qa add <amount> <quest-type> <player>` | Add progress to matching personal quests |
| `/qa global add <quest-type> <amount> <player>` | Add progress to the active global quest |
| `/qa global refresh` | Delete and regenerate the active global quest |
| `/qa shield give <amount> <player>` | Give Streak Shields |
| `/qa shield take <amount> <player>` | Remove Streak Shields |
| `/qa recovery give <amount> <player>` | Give Streak Recoveries |
| `/qa recovery take <amount> <player>` | Remove Streak Recoveries |

## Reward Behavior

Reward commands run from console.

Personal quest rewards run in this order:

1. One random reward from the completed quest's difficulty.
2. Premium bonus rewards when the completed quest is an accessible premium
   quest.
3. Quest-specific rewards from the quest definition.

Global reward tiers run at the end of the global period. Full reward tiers are
used only when the global quest reaches 100%. Reduced tiers are used only when
the quest reaches the configured reduced-reward minimum but does not complete.

## Operating Notes

- Run `/qa reload` after configuration changes.
- Use `/qa listtypes` before adding or editing quest definitions.
- Keep `easy` configured if you rely on default/fallback examples.
- Use unique quest ids inside configured quest pools.
- Locked premium slots are ignored for reset purchase readiness.
- Global quest completion does not count as personal quest completion.
- Streaks are based on the configured reset window, not necessarily calendar
  midnight.
- Reward commands receive raw command-safe placeholder values.
- Player-facing numbers in menus and messages are formatted with grouping, such
  as `15,000`.

## Full Reference

This README is the server-owner overview. For exhaustive examples, placeholder
tables, quest-type variable requirements, and GUI layout details, read
[wiki/questsplus.md](wiki/questsplus.md).
