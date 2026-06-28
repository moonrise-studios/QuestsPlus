# QuestsPlus SDK

`sdk` is the public API module for adding custom QuestsPlus goal types, variable selectors, and currencies from other plugins. It contains only API contracts and immutable records; the runtime implementation lives in the `QuestsPlus` plugin.

## Gradle

Consumers inside this standalone project can depend on the SDK module:

```kotlin
dependencies {
    compileOnly(project(":sdk"))
}
```

External plugins should compile against the published SDK jar and declare `QuestsPlus` as a runtime dependency or soft dependency in their plugin metadata.

## Getting the API

QuestsPlus exposes `QuestApi` through Bukkit services after the plugin enables:

```java
QuestApi api = Bukkit.getServicesManager().load(QuestApi.class);
if (api == null) {
    getLogger().warning("QuestsPlus is not available.");
    return;
}
```

## Registering a Goal

Quest types are data-driven keys. Built-in types are available in `QuestTypes`; custom plugins can use `QuestType.of("MY_CUSTOM_GOAL")`. Current built-ins include combat, block break/place, harvest, enchant, shear, fish, cake, craft, smelt, brew, travel, milking, thrown-item, and villager-trade keys.

```java
public final class MyCustomGoalHandler implements GoalHandler {
    private final QuestApi api;
    private final QuestType type = QuestType.of("MY_CUSTOM_GOAL");

    public MyCustomGoalHandler(QuestApi api) {
        this.api = api;
    }

    @Override
    public QuestType type() {
        return type;
    }

    @Override
    public void validateDefinition(QuestDefinition definition) {
        if (!definition.selectorTypes().containsKey("goal-amount")) {
            throw new IllegalArgumentException("MY_CUSTOM_GOAL requires goal-amount");
        }
    }

    @Override
    public GeneratedQuest createGeneratedQuest(QuestDefinition definition, UUID playerId, String resetKey, Map<String, String> variables) {
        int goalAmount = Integer.parseInt(variables.get("goal-amount"));
        return new GeneratedQuest(
                UUID.randomUUID(),
                playerId,
                resetKey,
                definition.id(),
                definition.type(),
                definition.difficultyId(),
                definition.difficultyDisplayName(),
                definition.displayName(),
                definition.description(),
                variables,
                goalAmount,
                0,
                false
        );
    }

    @EventHandler(ignoreCancelled = true)
    public void onCustomEvent(MyCustomEvent event) {
        api.progressMatching(event.player(), type, event.amount(), quest -> true);
    }
}
```

Register and unregister it from your plugin:

```java
QuestApi api = Bukkit.getServicesManager().load(QuestApi.class);
api.registerGoalHandler(this, new MyCustomGoalHandler(api));
```

`registerGoalHandler` also registers the handler as a Bukkit listener. Call `api.unregisterAll(this)` during disable if your plugin can disable while the server is running.

## Progress Events

QuestsPlus fires `QuestProgressEvent` before any matched personal or global quest receives progress. The event includes the `Player`, source `QuestDefinition`, current `GeneratedQuest`, progress `Cause`, original amount, and mutable amount.

```java
@EventHandler
public void onQuestProgress(QuestProgressEvent event) {
    if (event.quest().definitionId().equals("blocked_quest")) {
        event.setCancelled(true);
        return;
    }
    if (event.cause() == QuestProgressEvent.Cause.API) {
        event.setAmount(Math.max(1, event.getAmount() / 2));
    }
}
```

Cancelling the event prevents cache updates, persistence, completion rewards, streaks, milestones, and global contribution for that progress operation. Setting the amount to zero or less also skips that generated quest. Amount changes are capped by the quest's configured goal amount when QuestsPlus applies progress.

Causes are `EVENT` for built-in Bukkit listener progress, `API` for `QuestApi#progressMatching`, `COMMAND` for QuestsPlus admin command progress, and `UNKNOWN` for fallback internal uses.

## Admin Progress

Implement `GoalHandler` for every custom quest type. QuestsPlus admin progress commands target cached active personal or global quests by registered `QuestType`; no separate marker interface is required.

## Variable Selectors

Custom variable selectors can be registered with `api.registerVariableSelector(plugin, selector)`. Selector type keys are used in quest definition `variables.<key>.selector`.

## Currencies

External plugins can add Quest Reset purchase currencies by implementing `QuestCurrency` and registering it with `QuestApi`. QuestsPlus owns the reset limit and quest reset transaction; the currency plugin owns availability checks, display amount text, and the charge operation.

```java
public final class TokenCurrency implements QuestCurrency {
    private final QuestCurrencyKey key = QuestCurrencyKey.of("tokens");

    @Override
    public QuestCurrencyKey key() {
        return key;
    }

    @Override
    public String displayName() {
        return "Tokens";
    }

    @Override
    public String displayAmount(Player player) {
        return "5";
    }

    @Override
    public double questResetCost() {
        return 5.0D;
    }

    @Override
    public QuestCurrencyButton button() {
        return QuestCurrencyButton.of(
                13,
                "SUNFLOWER",
                "<gold>Tokens",
                List.of("<gray>Spend <yellow>5 tokens<gray> to reset quests.")
        );
    }

    @Override
    public boolean isAvailable() {
        return tokenService != null;
    }

    @Override
    public boolean charge(Player player, double amount) {
        return tokenService.take(player.getUniqueId(), amount);
    }
}
```

Register and unregister it from your plugin:

```java
QuestApi api = Bukkit.getServicesManager().load(QuestApi.class);
api.registerCurrency(this, new TokenCurrency());

// During disable, or before replacing the provider:
api.unregisterAll(this);
```

Built-in keys are available in `QuestCurrencies`; custom keys should use `QuestCurrencyKey.of("your-key")`.

## Artifacts

The module builds a normal jar, a sources jar, and a javadocs jar.
