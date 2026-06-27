---
title: QuestsPlus
---

`QuestsPlus` provides daily, data-driven player quests for Paper servers. The first release focuses on player-picked daily quest slots and built-in combat, block, enchanting, harvesting, shearing, fishing, cake, crafting, smelting, brewing, travel, milking, thrown-item, and villager-trade goal types, while keeping variables, difficulties, rewards, GUI text, and storage behavior configurable.

---

## Commands

| Command | Permission | Description |
|---------|-----------|-------------|
| `/quests`, `/q` | - | Open the daily quest GUI |
| `/quests progress`, `/q progress` | - | Show current daily quest progress in chat |
| `/quests completed`, `/q completed` | - | Show the player's permanent completed quest total and per-difficulty summary |
| `/quests milestones`, `/q milestones` | - | Open the quest milestone difficulty selector |
| `/quests milestones <difficulty>`, `/q milestones <difficulty>` | - | Open one difficulty's milestone GUI |
| `/quests streaks`, `/q streaks` | - | Open the quest streak GUI |
| `/quests indicator <main\|global\|both> <type\|off\|default>`, `/q indicator <main\|global\|both> <type\|off\|default>` | - | Set or disable progress indicators separately for personal and global quests |
| `/questsadmin`, `/qa` | `moonrise.quests.admin` | Show admin usage |
| `/questsadmin reload`, `/qa reload` | `moonrise.quests.admin` | Reload config and validate quest definitions |
| `/questsadmin listtypes`, `/qa listtypes` | `moonrise.quests.admin` | List registered quest types grouped by provider, with click-to-copy type keys |
| `/questsadmin reset <player>`, `/qa reset <player>` | `moonrise.quests.admin` | Clear the player's current daily quest set |
| `/questsadmin complete <player>`, `/qa complete <player>` | `moonrise.quests.admin` | Complete the player's current accessible daily quest set |
| `/questsadmin dailyrerolls reset <player>`, `/qa dailyrerolls reset <player>` | `moonrise.quests.admin` | Reset the player's used reroll count for the current reset window |
| `/questsadmin add <amount> <quest-type> <player>`, `/qa add <amount> <quest-type> <player>` | `moonrise.quests.admin` | Add admin-command progress to active quests of the selected quest type |
| `/questsadmin global add <quest-type> <amount> <player>`, `/qa global add <quest-type> <amount> <player>` | `moonrise.quests.admin` | Add admin progress to the active global quest when its type matches |
| `/questsadmin global refresh`, `/qa global refresh` | `moonrise.quests.admin` | Delete and regenerate the active global quest for testing |
| `/questsadmin shield give <amount> <player>`, `/qa shield give <amount> <player>` | `moonrise.quests.admin` | Add virtual Streak Shields |
| `/questsadmin shield take <amount> <player>`, `/qa shield take <amount> <player>` | `moonrise.quests.admin` | Remove virtual Streak Shields |
| `/questsadmin recovery give <amount> <player>`, `/qa recovery give <amount> <player>` | `moonrise.quests.admin` | Add virtual Streak Recoveries |
| `/questsadmin recovery take <amount> <player>`, `/qa recovery take <amount> <player>` | `moonrise.quests.admin` | Remove virtual Streak Recoveries |

Admin player arguments are trailing greedy strings for Bedrock-safe command entry.

---

## Daily Quest Lifecycle

- The active reset window is calculated from `daily.reset-time` using the server JVM timezone when `daily.weekly` is `false`.
- If the current time is before the configured reset time, the active reset key is yesterday's date; otherwise it is today's date.
- If `daily.weekly` is `true`, personal quests use `daily.schedule.day-of-week` and `daily.schedule.time` as a weekly boundary instead. The active reset key is the date of the most recent configured weekly boundary.
- On join or first quest access, the plugin loads the player's current reset-window quests from SQLite without generating missing quests.
- `daily.quest-count` controls normal selectable quest slots. `premium_quests.yml` can add visible premium personal slots after those normal slots.
- Empty daily quest slots show a configured placeholder item. Clicking one opens the difficulty picker for that slot.
- Locked premium quest slots show the configured premium locked item until the player has enough premium-slot permission.
- Selecting a difficulty generates one random enabled quest from that difficulty pool and stores it in the clicked slot.
- A difficulty can require permanent completed quest counts from one or more difficulties before it can be selected. Configure this with `requirements` in that difficulty's `settings.yml`, using difficulty ids as keys and required completion counts as values.
- Generation prefers quest definitions that are not already active for that player in the current reset window. If every definition for that difficulty is already active, repeats are allowed. If the difficulty has no enabled definitions, the slot stays empty and the configured no-available message is sent.
- Incomplete selected quests can be rerolled from the daily menu when the player has a configured reroll permission. Rerolls use the same difficulty picker, preserve the slot, replace the old generated quest, and consume one daily reroll only after the replacement persists.
- Reroll limits are per reset window, so weekly mode makes reroll usage weekly. If a player has multiple matching reroll permissions, the highest configured limit applies.
- Generated variables are fixed for the whole reset window; progress never rerolls variable values.
- The daily menu can show a reset button. It opens a purchase choice menu only after every quest slot the player can access is selected and complete; players with premium quest permissions must also complete those accessible premium slots.
- Purchased Quest Resets clear only the current reset-window quest rows so the player can choose more quests. Lifetime completion stats, milestones, streaks, shields, recoveries, and current-window reroll usage are not changed.
- Same-window progress persists through server restarts.
- Personal quest reset clears online players' cached state, broadcasts the configured reset message, and leaves new slots empty until players pick difficulties again.
- Permanent per-difficulty completion counts are never reset by the personal quest reset.
- The total shown by `/quests completed` or `/q completed` is calculated by adding all per-difficulty completion counts.
- Quest streaks are evaluated by reset window. Completing the configured number of daily quests in one reset window awards at most one streak point.
- Quest item templates can use `<quest_reset_timer>` to show a rounded-up countdown such as `Resets in 5d`, `Resets in 5h`, or `Resets in 1m`.
- Premium quest slots are personal-only. A premium generated quest is hidden behind the locked premium item and does not progress while the player no longer has enough premium-slot permission, but it remains stored and can reappear if permission returns before reset.

`/quests progress` and `/q progress` show only selected/generated quests. If a player has not selected any daily quests, it sends the no-quests message instead of generating a set.

`/questsadmin complete <player>` and `/qa complete <player>` force-complete the online player's current accessible generated daily quests for the active reset window. Already completed quests are skipped. Newly completed quests use the normal completion pipeline, including progress persistence, completion rewards, lifetime difficulty counts, eligible milestones, streak evaluation, and progress indicators.

---

## Config Files

QuestsPlus no longer reads `plugins/QuestsPlus/config.yml` or the old flat `storage.yml`, `quests.yml`, `difficulties.yml`, and `milestones.yml` files. New installs generate modular files in `plugins/QuestsPlus/`; existing old files are left untouched and ignored.

| File | Owns |
|------|------|
| `daily.yml` | Daily/weekly reset mode and schedule, quest count, reroll limits, daily quest menu, difficulty picker, and daily quest/reroll messages |
| `difficulty/<id>/settings.yml` | Difficulty display name, menu lore, and baseline difficulty rewards |
| `difficulty/<id>/quests.yml` | Data-driven personal quest definitions for that difficulty |
| `difficulty/<id>/milestones.yml` | Completion milestones for that difficulty |
| `streaks.yml` | Streak settings, streak milestones, streak menus, shield/recovery menus, and streak messages |
| `premium_quests.yml` | Premium personal slot permissions, premium bonus rewards, and premium quest item display styling |
| `global-quests.yml` | Weekly global quest schedule, definitions, menu item, contribution rewards, and messages |
| `messages.yml` | Shared command/admin/common messages, milestone menu/messages, and progress indicator settings |

Examples:

```yaml
# daily.yml
reset-time: "05:00"
weekly: false
schedule:
  day-of-week: FRIDAY
  time: "05:00"
quest-count: 3
rerolls:
  permission-limits:
    vip: 1
    vipplus: 2
    mvp: 3
menu:
  empty-quest:
    material: GRAY_DYE
    name: "<yellow>Select Quest Difficulty"
    lore:
      - "<gray>Slot <white><slot></white> is empty."
      - "<yellow>Click to pick a difficulty."
  difficulty-picker:
    title: "<dark_gray>Select Difficulty"
    rows: 3
    slots: [10, 11, 12, 13, 14, 15, 16]
    back-button:
      enabled: true
      slot: 22
      item:
        material: ARROW
        name: "<yellow>Back"
        lore:
          - "<gray>Return to quests."
messages:
  no-quests: "<red>No daily quests are selected."
```

```yaml
# premium_quests.yml
enabled: true
permission-limits:
  vip: 1
  vipplus: 2
  mvp: 3
rewards:
  easy:
    commands:
      - "eco give <player> 125"
menu:
  tooltip-style: "minecraft:premium_quest"
  display-name-suffix: " <gold><b>PREMIUM QUEST"
  locked-quest:
    material: BARRIER
    name: "<gold>Premium Quest <slot>"
    lore:
      - "<gray>Requires <rank></gray>"
      - "<yellow>Purchase <rank> to unlock this quest slot."
  locked-ranks:
    "4": "<green>VIP"
    "5": "<aqua>VIP+"
    "6": "<gold>MVP"
  lore:
    easy:
      - "<gold>Premium quest"
      - "<gray>Includes bonus premium rewards."
```

```yaml
# difficulty/easy/settings.yml
display-name: "<green><b>EASY"
lore:
  - "<gray>Possible reward: <green>$250"
picker-slot: -1
milestones-slot: -1
requirements: {}
rewards:
  commands:
    - "eco give <player> 250"
```

```yaml
# difficulty/easy/milestones.yml
milestones:
  - completed: 5
    display-name: "<green>Easy I"
    lore:
      - "<gray>Complete easy quests to unlock this reward."
    commands:
      - "eco give <player> 1000"
```

```yaml
# streaks.yml
daily-required-completions: -1
recovery-window-days: 3
milestones:
  - streak: 3
    display-name: "<green>3 Day Streak"
    lore:
      - "<gray>Reward for keeping your streak alive."
    commands:
      - "eco give <player> 500"
```

```yaml
# difficulty/easy/quests.yml
quest-definitions:
  - id: hostile-hunter
    type: KILL_MOB
    enabled: true
    schedule:
      begin:
        date: ""
        time: ""
        timezone: ""
      end:
        date: ""
        time: ""
        timezone: ""
    display-name: "<green>Hostile Hunter"
    description:
      - "<gray>Kill <white><goal-amount></white> <white><mob-type></white> mobs."
    variables:
      mob-type:
        selector: LIST
        values: [ZOMBIE, SKELETON, CREEPER]
      goal-amount:
        selector: LIST
        values: [25, 50, 75, 100]
    rewards:
      commands: []
```

```yaml
# global-quests.yml
schedule:
  day-of-week: FRIDAY
  time: "05:00"
menu:
  slot: 15
quest-definitions:
  - id: global-kill-all-mobs
    type: KILL_ALL_MOBS
    enabled: true
    difficulty: easy
    schedule:
      begin:
        date: ""
        time: ""
        timezone: ""
      end:
        date: ""
        time: ""
        timezone: ""
    display-name: "<green>Global Mob Slayer"
    description:
      - "<gray>Kill <white><goal-amount></white> mobs as a server."
    variables:
      goal-amount:
        selector: LIST
        values: [2500, 5000, 10000]
    rewards:
      commands: []
reward-tiers:
  - percentile: 1
    display-name: "<gold>Top 1%"
    item:
      material: EMERALD
      name: "<green>Full Reward: <reward_display_name>"
      lore:
        - "<gray>Quest completion: <white>100%</white>"
        - "<gray>Contributor tier: <white>Top <reward_percentile>%</white>"
        - "<yellow>Custom reward lore can go here."
    commands:
      - "eco give <player> 10000"
  - percentile: 10
    display-name: "<yellow>Top 10%"
    commands:
      - "eco give <player> 5000"
reduced-reward-minimum-percent: 50.0
reduced-reward-tiers:
  - percentile: 1
    display-name: "<gold>Reduced Top 1%"
    item:
      material: GOLD_INGOT
      name: "<yellow>Reduced Reward: <reward_display_name>"
      lore:
        - "<gray>Quest completion: <white>50% - 99.9%</white>"
        - "<gray>Contributor tier: <white>Top <reward_percentile>%</white>"
        - "<yellow>Custom reduced reward lore can go here."
    commands:
      - "eco give <player> 5000"
  - percentile: 10
    display-name: "<yellow>Reduced Top 10%"
    commands:
      - "eco give <player> 2500"
```

```yaml
# messages.yml
progress-indicators:
  enabled: true
  types: [BOSS_BAR]
  boss-bar:
    enabled: true
    duration-seconds: 10
    title: "<gold><quest_display_name> <gray><progress>/<goal_amount> (<percent>)"
    color: YELLOW
    overlay: PROGRESS
  action-bar:
    enabled: true
    title: "<gold><quest_display_name> <gray><progress>/<goal_amount> (<percent>)"
  chat:
    enabled: true
    interval-seconds: 30
    header: "<gold>Quest Progress"
    line: "<gray>- <yellow><quest_display_name></yellow>: <white><previous_progress></white> <gray>➜</gray> <white><progress></white> <gray>/</gray> <white><goal_amount></white> <gray>(<percent>)</gray>"
messages:
  usage: |-
    <yellow>/quests</yellow> <gray>or</gray> <yellow>/q</yellow> <gray>- Open daily quests
    <yellow>/quests progress</yellow> <gray>or</gray> <yellow>/q progress</yellow> <gray>- View progress
    <yellow>/quests indicator <main|global|both> <type></yellow> <gray>or</gray> <yellow>/q indicator <main|global|both> <type></yellow> <gray>- Set progress indicators
  reload-success: "<green>QuestsPlus config reloaded."
  player-only: "<red>Only players can use this command."
  indicator-selected: "<green>Quest progress indicator set to <white><indicator></white>."
  indicator-reset: "<green>Quest progress indicator reset to <white>BossBar</white>."
  indicator-unavailable: "<red>Unknown or unavailable quest progress indicator: <white><indicator></white>."
  indicator-disabled: "<green><scope> quest progress indicator disabled."
  streak-milestone-locked: "<dark_gray>Locked"
  streak-milestone-completed: "<green>Completed"
  streak-milestone-progress: "<gray>Progress: <white><streak></white>/<white><milestone_streak></white>"
milestone-messages:
  completed-count: "<green>You have completed <white><quests_completed></white> quests across all difficulties."
  completed-difficulty-line: "<gray>- <quest_difficulty><gray>: <white><quests_completed></white>"
  milestone-completed: "<green>Milestone complete: <white><milestone_display_name></white> <gray>(<quest_difficulty>, <milestone_completed> quests)."
  milestone-claimed: "<green>Milestone reward claimed: <white><milestone_display_name></white> <gray>(<quest_difficulty>, <milestone_completed> quests)."
```


### Quest Difficulties

Each folder under `difficulty/` is one config-only difficulty tag. `settings.yml` has a MiniMessage `display-name`, optional display-only `lore`, optional `picker-slot` and `milestones-slot` menu positions, and baseline `rewards.commands`. Difficulty `rewards.commands` is a random reward pool: one configured command is selected when a personal quest of that difficulty completes. Per-difficulty completion milestones live in that same folder's `milestones.yml`.

Every personal quest definition inherits its difficulty from the containing `difficulty/<id>/` folder. Global quest definitions still declare `difficulty` in `global-quests.yml` because they are not scoped by a difficulty folder. `easy` is the default sample difficulty and must remain configured for legacy generated quest fallback.

The daily difficulty picker lists configured difficulty folders. Choosing one for an empty slot samples from enabled quest definitions in that difficulty folder. Personal quest definitions are assigned to their containing folder's difficulty id during load. `picker-slot` can place a difficulty at a zero-based slot in the difficulty picker; `-1`, invalid slots, or duplicate slots fall back to `daily.yml` `menu.difficulty-picker.slots` order. Difficulty picker and quest item lore templates can use `<difficulty_lore>` to insert the configured lore lines at that exact position. Empty difficulty lore renders no lines.

### Daily Rerolls

`rerolls.permission-limits` in `daily.yml` maps config keys to `moonrise.quests.reroll.<key>` permissions. A player with `moonrise.quests.reroll.vipplus` and the sample config has `2` rerolls per reset window. If multiple configured permissions match, QuestsPlus uses the highest limit rather than summing them. Players without a matching permission have `0` rerolls.

Rerolling is menu-driven only. Clicking an incomplete selected quest opens the difficulty picker in reroll mode. Completed quests cannot be rerolled. A successful reroll deletes the old generated quest row, persists a new quest in the same `slot_index`, increments `player_quest_rerolls.used_count`, and returns to the daily menu. If the selected difficulty has no available enabled quest, the original quest remains and no reroll is consumed.

### Completed Quest Reset Purchases

The main daily quest menu reset button is configured under `daily.yml` `menu.reset-button`; the submenu layout, shared statuses, limit, filler, and back button are configured under `menu.reset-menu`. The reset button can use `<completed>`, `<required>`, `<status>`, `<resets_used>`, `<resets_limit>`, and `<resets_remaining>`. `<status>` is controlled by `menu.reset-menu.status-ready`, `menu.reset-menu.status-incomplete`, and `menu.reset-menu.status-limit-reached`. Purchase buttons are configured per provider in `currencies/playerpoints.yml` and `currencies/vault.yml`, and can use `<completed>`, `<required>`, `<status>`, `<payment>`, `<reward>`, `<amount>`, `<resets_used>`, `<resets_limit>`, and `<resets_remaining>`.

The reset purchase menu opens only when every slot the player can access is selected and complete. Normal slots come from `daily.quest-count`; premium slots come from the player's highest matching `moonrise.quests.premium.<key>` permission in `premium_quests.yml`. Locked premium slots do not count, but unlocked premium slots must be complete.

`menu.reset-menu.daily-limit` controls how many Quest Reset purchases each player can make in the active reset window. It defaults to `1`, is keyed by the same daily or weekly reset key as generated quests, and values below `0` are treated as `0`. The limit applies only to player purchases from the reset purchase menu. Admin commands such as `/qa reset`, `/qa complete`, and `/qa dailyrerolls reset` do not consume or check purchased reset usage. When a player has no resets remaining, the reset purchase menu does not open and QuestsPlus sends `quest-reset-limit-reached`.

PlayerPoints and Vault are optional server dependencies for Quest Reset purchases. Set `enabled` in `currencies/playerpoints.yml` or `currencies/vault.yml` to control which currencies QuestsPlus may offer. Each currency file also owns its `display-name`, `quest-reset-cost`, and purchase `button`. The PlayerPoints option appears only when it is enabled and PlayerPoints is installed; choosing it charges `currencies/playerpoints.yml` `quest-reset-cost`. The Vault option appears only when it is enabled and Vault has an economy provider; choosing it charges `currencies/vault.yml` `quest-reset-cost`. QuestsPlus checks the remaining reset limit before charging. A successful purchase increments `player_quest_resets.used_count` and clears the current `player_quests` rows in one SQLite transaction, then returns the player to empty slots for the same reset window. Lifetime difficulty completion stats, milestones, streak state, shield/recovery balances, and `player_quest_rerolls` usage are preserved.

### Premium Quest Slots

`premium_quests.yml` adds visible extra personal quest slots based on the highest configured value in `permission-limits`. `permission-limits.vip: 1` maps to `moonrise.quests.premium.vip`; if a player has multiple configured premium permissions, QuestsPlus uses the highest matching slot count rather than summing them.

Premium slots are appended after normal `daily.quest-count` slots. Locked premium slots render `menu.locked-quest` and do not open the difficulty picker or reroll picker. Once the player has enough premium-slot permission, empty premium slots use the same difficulty picker and reroll flow as normal slots. Generated premium quests persist a `premium` flag. Premium bonus rewards are configured per difficulty under `premium_quests.yml rewards`, and run only for accessible premium quests. If `menu.tooltip-style` is blank, QuestsPlus does not set a tooltip style; otherwise premium quest item stacks use that Paper tooltip style key. `menu.display-name-suffix` appends MiniMessage text to active and completed premium quest item names; blank disables the suffix. `menu.lore` maps difficulty ids to premium-only quest item lore lines, rendered where the active or completed quest item template includes `<premium_lore>`.

`menu.locked-ranks` is keyed by the one-based displayed quest slot number, not the zero-based internal slot index. With `daily.quest-count: 3`, key `"4"` controls the first premium slot's `<rank>` placeholder, key `"5"` controls the second, and so on. Locked premium items support `<slot>`, `<slot_index>`, `<premium_slot>`, `<premium_slot_index>`, and `<rank>`.

Difficulty milestones are lifetime per-difficulty quest completion thresholds. Milestone `completed` values must be positive and unique within that difficulty. Optional milestone `lore` lines are appended to that milestone's GUI item after the locked/claimed template lore. When a completion raises the player's count for that difficulty to a configured threshold, QuestsPlus records the milestone as executed, sends `milestone-messages.milestone-completed`, and runs its commands automatically. The GUI is status-only; milestones are not click-to-claim.

Milestones are also claimed retroactively from `player_quest_difficulty_stats`. When a player's quest state loads, and when `/questsadmin reload` or `/qa reload` refreshes milestones for online cached players, QuestsPlus records and rewards any configured milestones that are already below or equal to that player's per-difficulty count, sending `milestone-messages.milestone-claimed` for each newly recorded threshold. `player_quest_milestones` uses unique player/difficulty/threshold rows, so automated retroactive checks do not rerun already claimed milestone commands.

Legacy global `quests_completed` values are not used for totals, per-difficulty summaries, or milestone eligibility. Per-difficulty counts in `player_quest_difficulty_stats` are the source of truth.

### Quest Streaks

`streaks.daily-required-completions` controls how many selected/generated quests a player must complete in one reset window to earn one streak point. `-1` means all selected/generated quests for that window. Positive values are treated literally, so players who select fewer quests than the configured requirement cannot earn the streak until enough selected quests are completed. A reset window can award at most one streak point even if the player completes extra quests.

Streaks are based on QuestsPlus reset keys, not calendar midnight. When player state loads, QuestsPlus evaluates missed reset windows since the last evaluated key. Missing a window ends the current streak unless the player has a Streak Shield balance available. The highest streak is retained for documentation and menu display.

Streak Shields and Streak Recovery are virtual currencies. Admins adjust them with `/questsadmin shield give|take` or `/qa shield give|take`, and `/questsadmin recovery give|take` or `/qa recovery give|take`. Streak Shields are consumed automatically when a missed reset window would otherwise break the player's streak. Completing quests normally still awards the streak point and does not consume a shield.

Streak Recovery is applied manually from the streak menu confirmation screen. It consumes one recovery currency and restores the last lost streak if the loss is still within `streaks.recovery-window-days`. Recovery does not use legacy global quest totals and does not rerun already claimed streak milestone commands.

Streak milestones are separate from quest-completion milestones. They use `streaks.milestones`, persist claimed thresholds in `player_quest_streak_milestones`, and run automatically once when reached. Optional streak milestone `lore` lines are appended to that milestone's GUI item after the locked/claimed template lore. Streak milestone commands support `<player>`, `<uuid>`, `<streak>`, `<highest_streak>`, and `<milestone_streak>`.

### Quest Definitions

| Field | Description |
|-------|-------------|
| `id` | Unique quest definition id. Multiple definitions of the same type are allowed when ids differ. |
| `type` | Goal handler id. V1 supports `KILL_MOB`, `KILL_MOB_IN_WORLD`, `BLOCK_BREAK`, `ENCHANT_ITEM`, `PLACE_BLOCK`, `PLACE_ANY_BLOCK`, `HARVEST_ITEM`, `SHEAR_SHEEP`, `FISH`, `EAT_CAKE_SLICE`, `CRAFT_ITEM`, `CRAFT_ANY_ITEM`, `DYE_SHEEP`, `KILL_ALL_MOBS`, `BREAK_ALL_BLOCKS`, `SMELT_ITEM`, `SMELT_ANY_ITEM`, `BREW_ITEM`, `BREW_ANY_ITEM`, `TRAVEL_DISTANCE`, `MILK_MOB`, `THROW_ITEM`, and `VILLAGER_TRADE`. |
| `enabled` | Disabled definitions are not sampled for new daily quest sets. |
| `difficulty` | Global quest definitions only. Personal quest definitions inherit the containing `difficulty/<id>/` folder id at load time. |
| `schedule` | Optional begin/end window for limited-time definitions. Dates use `dd/MM/yyyy`, times use `HH:mm`, and timezones use Java ids such as `America/Halifax` or `UTC`. |
| `display-name` | MiniMessage display text used by chat and GUI surfaces. |
| `description` | MiniMessage lines. Resolved variables can be referenced as `<mob-type>` or `<mob_type>`. |
| `variables` | Data-driven selector definitions resolved once per generated quest. |
| `rewards.commands` | Optional bonus console commands run after difficulty rewards when the quest completes. |

Invalid individual quest definitions are skipped with a console error instead of preventing QuestsPlus from loading. Global config errors such as duplicate quest ids or missing required difficulties still fail reload/startup.

Quest schedules only control eligibility for new personal or global quest generation. Existing generated quests remain active and keep their progress even if the source definition's schedule expires. Begin timestamps are inclusive, end timestamps are exclusive, and blank begin or end fields create an open-ended window.

### Selectors

V1 includes the `LIST` selector. It picks one configured value uniformly from `values`.

### KILL_MOB Variables

| Variable | Required | Validation |
|----------|:--------:|------------|
| `mob-type` | Yes | Must be a valid living Bukkit `EntityType`, such as `ZOMBIE`. |
| `goal-amount` | Yes | Must be a positive integer. |

Only the direct killer receives progress. Non-player kills do not count.

When RoseStacker is installed, QuestsPlus also listens for RoseStacker multi-death
events. A stacked mob kill credits the visible Bukkit death normally and then credits
the remaining RoseStacker kill count toward matching `KILL_MOB`, `KILL_MOB_IN_WORLD`,
and `KILL_ALL_MOBS` quests. If RoseStacker is configured to fire one Bukkit death event
per stacked entity instead, the normal Bukkit death path handles those kills.

### KILL_MOB_IN_WORLD Variables

| Variable | Required | Validation |
|----------|:--------:|------------|
| `mob-type` | Yes | Must be a valid living Bukkit `EntityType`, such as `ZOMBIE`. |
| `world` | Yes | Must be a nonblank world name, such as `world`, `world_nether`, or `dungeons`. Worlds do not need to be loaded during plugin startup. |
| `goal-amount` | Yes | Must be a positive integer. |

Only the direct killer receives progress. The killed mob must match the generated `mob-type` and be in the generated `world`.

### KILL_ALL_MOBS Variables

| Variable | Required | Validation |
|----------|:--------:|------------|
| `goal-amount` | Yes | Must be a positive integer. |

Every non-player living entity killed by a player counts. This type does not use `mob-type`.

### BLOCK_BREAK Variables

| Variable | Required | Validation |
|----------|:--------:|------------|
| `block-type` | Yes | Must be a valid Paper `BlockType` registry key, such as `minecraft:stone`. Bare values like `stone` are normalized to `minecraft:stone`. |
| `goal-amount` | Yes | Must be a positive integer. |

Only uncancelled `BlockBreakEvent`s count. Protected or cancelled block breaks do not advance quest progress.

### ENCHANT_ITEM Variables

| Variable | Required | Validation |
|----------|:--------:|------------|
| `goal-amount` | Yes | Must be a positive integer. |

Every uncancelled enchantment table `EnchantItemEvent` counts as one enchanted item. This type does not use `item-type`.

### PLACE_BLOCK Variables

| Variable | Required | Validation |
|----------|:--------:|------------|
| `block-type` | Yes | Must be a valid Paper `BlockType` registry key, such as `minecraft:oak_planks`. Bare values like `oak_planks` are normalized to `minecraft:oak_planks`. |
| `goal-amount` | Yes | Must be a positive integer. |

Only uncancelled, build-allowed `BlockPlaceEvent`s count. The placed block must match the generated `block-type`.

### PLACE_ANY_BLOCK Variables

| Variable | Required | Validation |
|----------|:--------:|------------|
| `goal-amount` | Yes | Must be a positive integer. |

Every uncancelled, build-allowed player block placement counts. This type does not use `block-type`.

### HARVEST_ITEM Variables

| Variable | Required | Validation |
|----------|:--------:|------------|
| `item-type` | Yes | Must be a valid Paper `ItemType` registry key for the dropped item, such as `minecraft:wheat`. Bare values like `wheat` are normalized to `minecraft:wheat`. |
| `goal-amount` | Yes | Must be a positive integer. |

Progress is credited from `BlockDropItemEvent` after a player breaks a block and drops are computed. The dropped `ItemType` is matched, not the original block type, and stacked drops count by item amount.

### SHEAR_SHEEP Variables

| Variable | Required | Validation |
|----------|:--------:|------------|
| `goal-amount` | Yes | Must be a positive integer. |

Every uncancelled `PlayerShearEntityEvent` for a sheep counts as one sheared sheep. This type does not count dispenser shearing.

### FISH Variables

| Variable | Required | Validation |
|----------|:--------:|------------|
| `goal-amount` | Yes | Must be a positive integer. |

Every uncancelled `PlayerFishEvent` with state `CAUGHT_FISH` counts as one successful fishing catch.

### EAT_CAKE_SLICE Variables

| Variable | Required | Validation |
|----------|:--------:|------------|
| `goal-amount` | Yes | Must be a positive integer. |

Cake progress is credited after a right-click interaction changes a cake block's bite count or consumes the final slice. Cancelled or denied cake interactions do not count.

### CRAFT_ITEM Variables

| Variable | Required | Validation |
|----------|:--------:|------------|
| `item-type` | Yes | Must be a valid Paper `ItemType` registry key for the crafted output item, such as `minecraft:chest`. Bare values like `chest` are normalized to `minecraft:chest`. |
| `goal-amount` | Yes | Must be a positive integer. |

Progress is credited from uncancelled `CraftItemEvent`s. Normal result clicks count the crafted result amount, while shift-click crafting counts the produced amount that can fit in player storage.

### CRAFT_ANY_ITEM Variables

| Variable | Required | Validation |
|----------|:--------:|------------|
| `goal-amount` | Yes | Must be a positive integer. |

Every uncancelled player crafting result counts by crafted output amount. This type does not use `item-type`.

### DYE_SHEEP Variables

| Variable | Required | Validation |
|----------|:--------:|------------|
| `color` | Yes | Must be a valid Bukkit `DyeColor`, such as `RED`, `BLUE`, or `LIME`. |
| `goal-amount` | Yes | Must be a positive integer. |

Every uncancelled player-attributed `SheepDyeWoolEvent` counts when the dyed color matches the generated `color`.

### BREAK_ALL_BLOCKS Variables

| Variable | Required | Validation |
|----------|:--------:|------------|
| `goal-amount` | Yes | Must be a positive integer. |

Every uncancelled player `BlockBreakEvent` counts. This type does not use `block-type`.

### SMELT_ITEM Variables

| Variable | Required | Validation |
|----------|:--------:|------------|
| `item-type` | Yes | Must be a valid Paper `ItemType` registry key for the extracted output item, such as `minecraft:iron_ingot`. Bare values like `iron_ingot` are normalized to `minecraft:iron_ingot`. |
| `goal-amount` | Yes | Must be a positive integer. |

Progress is credited when a player extracts furnace output through `FurnaceExtractEvent`. The extracted output item is matched, not the anonymous input item or cook recipe. Extracting a stack counts the full extracted amount.

### SMELT_ANY_ITEM Variables

| Variable | Required | Validation |
|----------|:--------:|------------|
| `goal-amount` | Yes | Must be a positive integer. |

Every player-attributed furnace output extraction counts, and progress increases by the extracted amount. This type does not use `item-type`.

### BREW_ITEM Variables

| Variable | Required | Validation |
|----------|:--------:|------------|
| `item-type` | Yes | Must be a valid Paper `ItemType` registry key for the brewed output item, such as `minecraft:potion`. Bare values like `potion` are normalized to `minecraft:potion`. |
| `goal-amount` | Yes | Must be a positive integer. |

Progress is credited when a player takes brewed output from a brewing stand. Brew completion itself is not player-attributed by Bukkit, so the handler records completed stand output and credits the player that removes it. Taking a stack counts the removed brewed output amount.

### BREW_ANY_ITEM Variables

| Variable | Required | Validation |
|----------|:--------:|------------|
| `goal-amount` | Yes | Must be a positive integer. |

Every player-attributed removal of recorded brewed output from a brewing stand counts, and progress increases by the removed amount. This type does not use `item-type`.

### TRAVEL_DISTANCE Variables

| Variable | Required | Validation |
|----------|:--------:|------------|
| `goal-amount` | Yes | Must be a positive integer. |

Travel distance counts horizontal block-coordinate movement from loaded player quest state. Pure Y-axis movement does not count. Teleports and vehicle travel do not count. Movement in water is ignored unless the player is in the swimming animation.

### MILK_MOB Variables

| Variable | Required | Validation |
|----------|:--------:|------------|
| `mob-type` | Yes | Must be a milkable Bukkit `EntityType`. Supported built-ins are `COW`, `GOAT`, and `MOOSHROOM`. |
| `goal-amount` | Yes | Must be a positive integer. |

Milking progress counts non-cancelled right-clicks on adult milkable mobs while the player is holding an empty bucket. The resolved `<mob-type>` placeholder is display-friendly, and `<mob-type-raw>` / `<mob_type_raw>` expose the raw entity type key.

### THROW_ITEM Variables

| Variable | Required | Validation |
|----------|:--------:|------------|
| `item-type` | Yes | Must resolve to a Bukkit item type key, such as `minecraft:snowball`, `minecraft:egg`, `minecraft:ender_pearl`, or `minecraft:trident`. |
| `goal-amount` | Yes | Must be a positive integer. |

Thrown-item progress counts Paper `PlayerLaunchProjectileEvent` launches whose used item matches `item-type`. This covers direct projectile items such as snowballs, eggs, ender pearls, and tridents; bow and crossbow arrows are not emitted by that Paper event.

### VILLAGER_TRADE Variables

| Variable | Required | Validation |
|----------|:--------:|------------|
| `goal-amount` | Yes | Must be a positive integer. |

Villager-trade progress counts non-cancelled Paper `PlayerTradeEvent` purchases from villagers and wandering traders. This type does not use `item-type` or `mob-type`.

### Admin Progress

`/questsadmin add <amount> <quest-type> <player>` and `/qa add <amount> <quest-type> <player>` affect active generated quests whose registered quest type matches `<quest-type>`. The command is cache-first: it does not load, generate, or reroll daily quests. If the player has no cached current daily state, no active quest of that type, or the matching quest is already complete, no progress is applied.

### Quests SDK

`:sdk` is the public API library for external quest integrations. It provides the public model records, `GoalHandler`, `QuestVariableSelector`, the data-driven `QuestType` record, built-in `QuestTypes` constants, and the `QuestApi` Bukkit service contract. The module builds normal, sources, and javadocs jars, and its README contains registration examples.

External plugins compile against the SDK, depend or softdepend on `QuestsPlus`, then obtain `QuestApi` from Bukkit `ServicesManager`. Registered `GoalHandler`s are also Bukkit listeners, so custom event methods can live directly in the handler. Custom types use `QuestType.of("CUSTOM_TYPE")`; matching `type: CUSTOM_TYPE` definitions in `quests.yml` or `global-quests.yml` become eligible once the handler is registered.

External handlers should call `QuestApi#progressMatching(player, type, amount, matcher)` so personal and active global quest progress share the same cache, persistence, completion, and contribution pipeline. If an external handler is unregistered, existing generated quests of that type render raw variables and stop progressing instead of crashing menus.

Before matched progress is applied, QuestsPlus fires SDK `QuestProgressEvent`. Listeners can cancel the event or edit the progress amount before cache updates, SQLite persistence, completion rewards, milestones, streaks, or global contribution happen. Causes are `EVENT` for built-in Bukkit listener progress, `API` for `QuestApi#progressMatching`, `COMMAND` for QuestsPlus admin command progress, and `UNKNOWN` for fallback use.

`messages.yml progress-indicators` controls runtime quest progress indicators. Supported types are `BOSS_BAR`, `ACTION_BAR`, and `CHAT`; players choose `Main`, `Both`, or `Global` as the first `/quests indicator <scope> <type>` argument. `OFF` disables that scope for the player, while `DEFAULT` clears the saved preference. Players with no saved preference use `BOSS_BAR` by default. Non-default indicator choices are only available when enabled in config. BossBars show the latest personal or global quest progress for `duration-seconds` and refresh the timer whenever more progress is applied. ActionBars send the configured title once per accepted progress update and expire client-side. Chat summaries batch accepted progress for `chat.interval-seconds`, then send one message with every quest progressed during that window. Indicator templates support `<percent>`, rendered with two decimals and the percent sign such as `50.55%`. Chat lines support `<previous_progress>` plus normal quest placeholders and global placeholders such as `<global_progress>`, `<global_goal_amount>`, `<contribution>`, and `<global_time_remaining>`.

Admins can run `/questsadmin listtypes` or `/qa listtypes` to see registered quest type keys grouped by provider. Built-in handlers are listed under `QuestsPlus`, and SDK handlers are listed under the external plugin that registered them. Each displayed type key has a `Click to copy` hover and copies the exact key to the clipboard when clicked.

### Global Quests

`global-quests.yml` defines weekly server-wide quests separately from personal `quests.yml`. The default schedule starts on `FRIDAY` at `05:00` server-local time and ends the following Friday at the same time. One enabled global definition is randomly selected for the active period, with variables resolved once and persisted through restarts.

Global quests use the same goal types and variable schema as personal quests, but default global definitions use much higher `goal-amount` values. Operators should edit `global-quests.yml` when they want larger or smaller server-wide goals. Generated config comments list built-in quest type keys and note that external plugins can register additional data-driven types through `sdk`.

The daily quest GUI reserves fixed inventory slot `15` for the global quest item. Personal daily quest slots skip that inventory slot even if it appears in `daily.yml`.

Every matching event can progress both matching personal quests and the active global quest. Global progress is shared server-wide, while each player's contribution is tracked separately. Global quest completion does not award personal difficulty rewards, personal quest-completion milestones, streak points, or rerolls.

Clicking the global quest item opens a cosmetic reward preview menu. Reward tier items do not execute actions; only the preview back button returns players to the daily quest menu. Each full or reduced reward tier can optionally define an `item` with `material`, `name`, and `lore` for that preview button. Tier item text supports `<reward_display_name>` and `<reward_percentile>`. If a tier omits `item`, QuestsPlus falls back to `menu.reward-preview.full-reward-item` or `menu.reward-preview.reduced-reward-item`.

At period end, QuestsPlus evaluates global rewards by completion percentage. Below `reduced-reward-minimum-percent`, no contribution rewards are paid. From that configured minimum through `99.9%`, QuestsPlus runs `reduced-reward-tiers`. At `100%`, QuestsPlus runs full `reward-tiers`. Both reward lists use the same stacked percentile behavior: a player in the top `1%` also receives top `10%`, top `25%`, and any broader configured tiers they qualify for. The default reduced reward minimum is `50.0`.

Admins can manually credit the active global quest with `/questsadmin global add <quest-type> <amount> <player>` or `/qa global add <quest-type> <amount> <player>`, for example `/qa global add KILL_ALL_MOBS 25 Steve`. The command applies when the active global quest's data-driven quest type key matches `<quest-type>`. Operators can force a testing reroll with `/questsadmin global refresh` or `/qa global refresh`; this deletes the current period's active global quest, clears its contributions and reward execution rows, generates a new active quest, and broadcasts the configured started message with `<quest_display_name>` resolved.

---

## Reward Placeholders

Reward commands run as console and support:

| Placeholder | Value |
|-------------|-------|
| `<player>` | Player name |
| `<uuid>` | Player UUID |
| `<quest_id>` | Quest definition id |
| `<quest_display_name>` | Display name with MiniMessage tags stripped |
| `<quest_difficulty>` | Difficulty display name |
| `<quest_difficulty_id>` | Difficulty id |
| `<goal_amount>` / `<goal-amount>` | Resolved target amount |
| `<progress>` | Current progress at completion |
| `<quest_reset_timer>` | Menu-only reset countdown, such as `Resets in 5h` or `Resets in 1m` |
| `<mob_type>` / `<mob-type>` | Display mob type for `KILL_MOB`, such as `Zombie` |
| `<mob_type_raw>` / `<mob-type-raw>` | Raw mob type for `KILL_MOB` and `KILL_MOB_IN_WORLD`, such as `ZOMBIE` |
| `<world>` / `<world_name>` | World name for `KILL_MOB_IN_WORLD`, such as `world` |
| `<block_type>` / `<block-type>` | Display block type for `BLOCK_BREAK`, such as `Stone` |
| `<block_type_key>` / `<block-type-key>` | Raw Paper `BlockType` key for `BLOCK_BREAK`, such as `minecraft:stone` |
| `<item_type>` / `<item-type>` | Count-aware display output item type for `SMELT_ITEM` and `BREW_ITEM`, such as `Iron Ingot` for a goal of `1` or `Iron Ingots` for a goal of `16` |
| `<item_type_key>` / `<item-type-key>` | Raw Paper `ItemType` key for `SMELT_ITEM` and `BREW_ITEM`, such as `minecraft:iron_ingot` or `minecraft:potion` |

Reward order is one random difficulty reward first, then all premium difficulty bonus rewards for accessible premium quests, then all quest-specific bonus rewards. Quest-specific rewards do not replace difficulty or premium rewards.

Numbers shown in chat, menu items, and other player/operator-facing text are formatted with grouped `DecimalFormat` output, such as `15,000`. Console reward command placeholders intentionally keep raw numeric values, such as `15000`, so downstream plugin commands receive command-safe arguments.

`<quest_reset_timer>` is rounded up to avoid understating the reset time. For example, 30 seconds remaining displays `Resets in 1m`.

### Milestone Placeholders

Milestone commands run as console when the milestone is reached and support:

| Placeholder | Value |
|-------------|-------|
| `<player>` | Player name |
| `<uuid>` | Player UUID |
| `<difficulty>` | Difficulty display name |
| `<difficulty_id>` | Difficulty id |
| `<completed>` | Player's completed quest count for that difficulty after the completion |
| `<milestone_completed>` | Milestone threshold |

Milestone completion messages use `<quest_display_name>` when a quest completion unlocked the milestone, plus `<quest_difficulty>`, `<difficulty>`, `<difficulty_id>`, `<completed>`, `<milestone_completed>`, and `<milestone_display_name>`. Retroactive milestone-claim messages use the same placeholders except `<quest_display_name>`.

Milestone menus also use `<quest_difficulty>`, `<difficulty>`, `<difficulty_id>`, `<completed>`, `<milestone_count>`, `<milestone_completed>`, and `<milestone_display_name>` in configured item text.

### Milestone GUI

`/quests milestones` and `/q milestones` open a difficulty selector. The selector has its own configurable `selector-back-button`, defaulting to slot `22`, that returns to the main quest menu. `milestones-slot` in `difficulty/<id>/settings.yml` can place a difficulty at a zero-based slot in this selector; `-1`, invalid slots, duplicate slots, or the reserved selector back-button slot fall back to `messages.yml` `milestone-menu.selector-slots` order. Selecting a difficulty opens that difficulty's milestone GUI. Milestone page layout is configurable in `messages.yml` under `milestone-menu`: `milestone-slots` controls content positions, `previous-page-slot` and `next-page-slot` control pagination, and `back-button.slot` controls the milestone page back button that returns to the difficulty selector.

### Streak GUI

`/quests streaks` and `/q streaks` open the streak GUI. The daily quest GUI also has a separate streak button. Streak item templates support `<streak>`, `<highest_streak>`, `<daily_completed>`, `<daily_required>`, `<shield_balance>`, `<recovery_balance>`, `<last_lost_streak>`, `<recovery_days_remaining>`, `<milestone_streak>`, and `<milestone_display_name>`.

### Daily Quest GUI

The daily quest GUI renders `daily.quest-count` normal quest slots plus any currently entitled premium slots using `menu.content-slots` in order. Filled slots show the active or completed quest template. Empty slots show `menu.empty-quest`, and clicking one opens `menu.difficulty-picker` for that specific slot. Clicking an incomplete filled slot opens the same picker in reroll mode if the player has rerolls remaining. The difficulty picker has a configurable `back-button` with `enabled`, `slot`, and item material/name/lore settings. The empty-slot and difficulty-picker item templates support `<slot>` as a one-based player-facing slot number and `<slot_index>` as the zero-based persisted slot index. Difficulty-picker items also support `<quest_difficulty>`, `<difficulty>`, `<difficulty_id>`, and `<difficulty_lore>`.

Quest item templates can also use `<rerolls_used>`, `<rerolls_limit>`, `<rerolls_remaining>`, `<difficulty_lore>`, and `<premium_lore>`. Premium lore renders only for premium quest items and comes from `premium_quests.yml` `menu.lore.<difficulty-id>`.

Global quest item templates support existing quest placeholders plus `<global_progress>`, `<global_goal_amount>`, `<global_percent>`, `<contribution>`, `<contribution_percent>`, `<global_rank>`, `<global_participants>`, and `<global_time_remaining>`.

All numeric menu placeholders are formatted with grouped `DecimalFormat` output.

### Reset Broadcast

`messages.quests-reset-broadcast` is sent server-wide when the scheduled personal quest reset fires. Admin reset and reroll commands do not send this broadcast.

---

## Storage And Cache

`QuestsPlus` uses SQLite through HikariCP and writes on an async executor. Runtime event handlers update Caffeine-backed in-memory state first, then persist progress asynchronously.

SQLite tables:

- `global_quests` stores active and historical global generated quests by period.
- `global_quest_contributions` stores per-player global contribution totals.
- `global_quest_reward_executions` stores reward tiers already executed per player.

| Table | Purpose |
|-------|---------|
| `player_quests` | Current reset-window generated quest instances, premium flags, slot indexes, and progress |
| `player_quest_difficulty_stats` | Permanent per-player completed quest counts by difficulty |
| `player_quest_milestones` | Milestone thresholds already executed for each player and difficulty |
| `player_quest_streaks` | Current/highest streak, last evaluated reset key, last lost streak, and shield/recovery balances |
| `player_quest_streak_milestones` | Streak milestone thresholds already executed for each player |
| `player_quest_rerolls` | Daily reroll usage by player and reset key |
| `player_quest_resets` | Purchased Quest Reset usage by player and reset key |

Generated quests persist their resolved difficulty id, display name, `slot_index`, and premium flag. Fresh databases include the premium column; QuestsPlus does not migrate older personal quest rows for premium-slot support.

Older databases may still contain the legacy `player_quest_stats` table. QuestsPlus leaves that table untouched, but no longer reads or writes it.

Do not add synchronous SQLite reads to PlaceholderAPI, GUI rendering, mob-death handlers, block-break handlers, furnace handlers, brewing handlers, movement handlers, or admin/API progress integrations. GUI and command rendering should use loaded/cached player state.
