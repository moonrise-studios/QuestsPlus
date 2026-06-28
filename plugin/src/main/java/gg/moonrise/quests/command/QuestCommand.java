package gg.moonrise.quests.command;

import gg.moonrise.engine.paper.command.PaperCommand;
import gg.moonrise.engine.paper.scheduler.Scheduler;
import gg.moonrise.moss.spring.SpringComponent;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import gg.moonrise.quests.core.service.ConfigProvider;
import gg.moonrise.quests.core.service.GlobalQuestService;
import gg.moonrise.quests.core.service.QuestIndicatorPreferenceService;
import gg.moonrise.quests.core.service.QuestDefinitionService;
import gg.moonrise.quests.core.service.QuestMenuService;
import gg.moonrise.quests.core.service.QuestProgressIndicatorService;
import gg.moonrise.quests.core.service.QuestResetService;
import gg.moonrise.quests.core.service.QuestService;
import gg.moonrise.quests.core.service.QuestStreakService;
import gg.moonrise.quests.core.service.SqliteProvider;
import gg.moonrise.quests.model.QuestDifficulty;
import gg.moonrise.quests.sdk.model.QuestProgressResult;
import gg.moonrise.quests.sdk.model.QuestType;
import gg.moonrise.quests.util.QuestNumberFormatter;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.Permission;
import org.incendo.cloud.annotations.suggestion.Suggestions;
import org.incendo.cloud.annotation.specifier.Greedy;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.suggestion.SuggestionProvider;

import java.util.List;
import java.util.Map;

@Slf4j(topic = "QuestsPlus")
@SpringComponent
@RequiredArgsConstructor
public class QuestCommand implements PaperCommand {

    private final ConfigProvider configProvider;
    private final QuestDefinitionService definitionService;
    private final QuestService questService;
    private final QuestMenuService menuService;
    private final QuestResetService resetService;
    private final QuestStreakService streakService;
    private final SqliteProvider sqliteProvider;
    private final GlobalQuestService globalQuestService;
    private final QuestProgressIndicatorService progressIndicatorService;
    private final QuestIndicatorPreferenceService indicatorPreferenceService;

    @Override
    public void onRegister(CommandManager<CommandSourceStack> commandManager) {
        commandManager.parserRegistry().registerSuggestionProvider(
                "online-players",
                SuggestionProvider.blockingStrings((context, input) -> Bukkit.getOnlinePlayers().stream().map(Player::getName).toList())
        );
        commandManager.parserRegistry().registerSuggestionProvider(
                "quest-difficulties",
                SuggestionProvider.blockingStrings((context, input) -> definitionService.difficulties().stream().map(QuestDifficulty::id).toList())
        );
    }

    @Command("q|quests")
    public void quests(CommandSourceStack source) {
        if (!(source.getSender() instanceof Player player)) {
            configProvider.get().getMessages().getPlayerOnly().send(source.getSender());
            return;
        }
        menuService.openDailyQuests(player);
    }

    @Command("q|quests progress")
    public void progress(CommandSourceStack source) {
        if (!(source.getSender() instanceof Player player)) {
            configProvider.get().getMessages().getPlayerOnly().send(source.getSender());
            return;
        }
        String resetKey = resetService.currentResetKey();
        questService.ensurePlayerStateAsync(player, resetKey)
                .thenAccept(state -> Scheduler.entity(player).run(task -> {
                    if (player.isOnline()) {
                        if (questService.accessibleQuests(player, state).isEmpty()) {
                            configProvider.get().getMessages().getNoQuests().send(player);
                        } else {
                            questService.sendProgress(player, state);
                        }
                    }
                }))
                .exceptionally(throwable -> {
                    log.error("Failed to load QuestsPlus progress for {}.", player.getUniqueId(), throwable);
                    Scheduler.entity(player).run(task -> player.sendRichMessage("<red>Failed to load quest progress right now."));
                    return null;
                });
    }

    @Command("q|quests completed")
    public void completed(CommandSourceStack source) {
        if (!(source.getSender() instanceof Player player)) {
            configProvider.get().getMessages().getPlayerOnly().send(source.getSender());
            return;
        }
        String resetKey = resetService.currentResetKey();
        questService.ensurePlayerStateAsync(player, resetKey)
                .thenAccept(state -> Scheduler.entity(player).run(task -> {
                    if (player.isOnline()) {
                        questService.sendCompletedCount(player, state);
                    }
                }))
                .exceptionally(throwable -> {
                    log.error("Failed to load QuestsPlus completed count for {}.", player.getUniqueId(), throwable);
                    Scheduler.entity(player).run(task -> player.sendRichMessage("<red>Failed to load completed quest count right now."));
                    return null;
                });
    }

    @Command("q|quests milestones")
    public void milestones(CommandSourceStack source) {
        if (!(source.getSender() instanceof Player player)) {
            configProvider.get().getMessages().getPlayerOnly().send(source.getSender());
            return;
        }
        menuService.openMilestoneSelector(player);
    }

    @Command("q|quests milestones <difficulty>")
    public void milestonesDifficulty(CommandSourceStack source, @Argument(value = "difficulty", suggestions = "quest-difficulties") String difficulty) {
        if (!(source.getSender() instanceof Player player)) {
            configProvider.get().getMessages().getPlayerOnly().send(source.getSender());
            return;
        }
        menuService.openMilestones(player, difficulty);
    }

    @Command("q|quests streaks")
    public void streaks(CommandSourceStack source) {
        if (!(source.getSender() instanceof Player player)) {
            configProvider.get().getMessages().getPlayerOnly().send(source.getSender());
            return;
        }
        menuService.openStreaks(player);
    }

    @Command("q|quests indicator <scope> <type>")
    public void indicator(
            CommandSourceStack source,
            @Argument(value = "scope", suggestions = "quest-indicator-scopes") String scope,
            @Argument(value = "type", suggestions = "quest-indicators") String type
    ) {
        indicatorSelection(source, scope, type);
    }

    private void indicatorSelection(CommandSourceStack source, String scope, String type) {
        if (!(source.getSender() instanceof Player player)) {
            configProvider.get().getMessages().getPlayerOnly().send(source.getSender());
            return;
        }

        String normalizedType = indicatorPreferenceService.normalize(type);
        List<QuestIndicatorPreferenceService.Scope> scopes = indicatorScopes(scope);
        if (scopes.isEmpty()) {
            configProvider.get().getMessages().getIndicatorUnavailable().send(player, Placeholder.unparsed("indicator", scope));
            return;
        }
        if (QuestIndicatorPreferenceService.DEFAULT.equals(normalizedType)) {
            updateIndicatorPreferences(player, scopes, normalizedType)
                    .thenRun(() -> Scheduler.entity(player).run(task -> {
                        if (player.isOnline()) {
                            configProvider.get().getMessages().getIndicatorReset().send(player);
                        }
                    }))
                    .exceptionally(throwable -> {
                        log.error("Failed to clear QuestsPlus indicator preference for {}.", player.getUniqueId(), throwable);
                        Scheduler.entity(player).run(task -> player.sendRichMessage("<red>Failed to update quest indicator preference."));
                        return null;
                    });
            return;
        }

        if (!QuestIndicatorPreferenceService.OFF.equals(normalizedType) && !progressIndicatorService.availableIndicatorTypes().contains(normalizedType)) {
            configProvider.get().getMessages().getIndicatorUnavailable().send(player, Placeholder.unparsed("indicator", type));
            return;
        }

        updateIndicatorPreferences(player, scopes, normalizedType)
                .thenRun(() -> Scheduler.entity(player).run(task -> {
                    if (player.isOnline()) {
                        if (QuestIndicatorPreferenceService.OFF.equals(normalizedType)) {
                            configProvider.get().getMessages().getIndicatorDisabled().send(player, Placeholder.unparsed("scope", scopeLabel(scopes)));
                        } else {
                            configProvider.get().getMessages().getIndicatorSelected().send(player, Placeholder.unparsed("indicator", normalizedType));
                        }
                    }
                }))
                .exceptionally(throwable -> {
                    log.error("Failed to update QuestsPlus indicator preference for {}.", player.getUniqueId(), throwable);
                    Scheduler.entity(player).run(task -> player.sendRichMessage("<red>Failed to update quest indicator preference."));
                    return null;
                });
    }

    private java.util.concurrent.CompletableFuture<Void> updateIndicatorPreferences(Player player, List<QuestIndicatorPreferenceService.Scope> scopes, String normalizedType) {
        java.util.concurrent.CompletableFuture<Void> future = java.util.concurrent.CompletableFuture.completedFuture(null);
        for (QuestIndicatorPreferenceService.Scope scope : scopes) {
            future = future.thenCompose(unused -> QuestIndicatorPreferenceService.DEFAULT.equals(normalizedType)
                    ? indicatorPreferenceService.clearPreference(player.getUniqueId(), scope)
                    : indicatorPreferenceService.setPreference(player.getUniqueId(), scope, normalizedType));
        }
        return future;
    }

    private List<QuestIndicatorPreferenceService.Scope> indicatorScopes(String scope) {
        String normalized = indicatorPreferenceService.normalize(scope);
        if (normalized.isBlank() || "BOTH".equals(normalized) || "ALL".equals(normalized)) {
            return List.of(QuestIndicatorPreferenceService.Scope.PERSONAL, QuestIndicatorPreferenceService.Scope.GLOBAL);
        }
        if ("PERSONAL".equals(normalized) || "MAIN".equals(normalized) || "QUESTS".equals(normalized)) {
            return List.of(QuestIndicatorPreferenceService.Scope.PERSONAL);
        }
        if ("GLOBAL".equals(normalized) || "GLOBAL_QUESTS".equals(normalized)) {
            return List.of(QuestIndicatorPreferenceService.Scope.GLOBAL);
        }
        return List.of();
    }

    private String scopeLabel(List<QuestIndicatorPreferenceService.Scope> scopes) {
        if (scopes.size() > 1) {
            return "All";
        }
        return scopes.contains(QuestIndicatorPreferenceService.Scope.GLOBAL) ? "Global" : "Main";
    }

    @Command("qa|questsadmin")
    @Permission("questsplus.admin")
    public void admin(CommandSourceStack source) {
        configProvider.get().getMessages().getAdminUsage().send(source.getSender());
    }

    @Command("qa|questsadmin reload")
    @Permission("questsplus.admin")
    public void reload(CommandSourceStack source) {
        try {
            configProvider.reload();
            definitionService.reload();
            resetService.reload();
            sqliteProvider.reload();
            globalQuestService.reload();
            questService.claimRetroactiveMilestonesForOnlinePlayers();
            configProvider.get().getMessages().getReloadSuccess().send(source.getSender());
        } catch (RuntimeException exception) {
            log.error("Failed to reload QuestsPlus.", exception);
            configProvider.get().getMessages().getReloadFailed().send(source.getSender());
        }
    }

    @Command("qa|questsadmin listtypes")
    @Permission("questsplus.admin")
    public void listTypes(CommandSourceStack source) {
        for (Map.Entry<String, List<QuestType>> entry : definitionService.registeredTypesByOwner().entrySet()) {
            source.getSender().sendMessage(typeListLine(entry.getKey(), entry.getValue()));
        }
    }

    @Command("qa|questsadmin reset <player>")
    @Permission("questsplus.admin")
    public void reset(CommandSourceStack source, @Argument(value = "player", suggestions = "online-players") @Greedy String playerName) {
        Player target = Bukkit.getPlayerExact(playerName);
        if (target == null) {
            configProvider.get().getMessages().getPlayerNotFound().send(source.getSender(), Placeholder.unparsed("player", playerName));
            return;
        }
        questService.resetDaily(target.getUniqueId(), resetService.currentResetKey())
                .thenRun(() -> runForSender(source.getSender(), () -> configProvider.get().getMessages().getResetPlayer().send(
                        source.getSender(),
                        Placeholder.unparsed("player", target.getName())
                )))
                .exceptionally(throwable -> {
                    log.error("Failed to reset QuestsPlus daily quests for {}.", target.getUniqueId(), throwable);
                    runForSender(source.getSender(), () -> source.getSender().sendRichMessage("<red>Failed to reset that player's quests."));
                    return null;
                });
    }

    @Command("qa|questsadmin complete <player>")
    @Permission("questsplus.admin")
    public void completeDaily(CommandSourceStack source, @Argument(value = "player", suggestions = "online-players") @Greedy String playerName) {
        Player target = Bukkit.getPlayerExact(playerName);
        if (target == null) {
            configProvider.get().getMessages().getPlayerNotFound().send(source.getSender(), Placeholder.unparsed("player", playerName));
            return;
        }
        questService.completeDailyQuests(target, resetService.currentResetKey())
                .thenAccept(completed -> runForSender(source.getSender(), () -> configProvider.get().getMessages().getCompleteDailyPlayer().send(
                        source.getSender(),
                        Placeholder.unparsed("player", target.getName()),
                        Placeholder.unparsed("quests", QuestNumberFormatter.format(completed))
                )))
                .exceptionally(throwable -> {
                    log.error("Failed to complete QuestsPlus daily quests for {}.", target.getUniqueId(), throwable);
                    runForSender(source.getSender(), () -> source.getSender().sendRichMessage("<red>Failed to complete that player's daily quests."));
                    return null;
                });
    }

    @Command("qa|questsadmin add <amount> <quest-type> <player>")
    @Permission("questsplus.admin")
    public void add(
            CommandSourceStack source,
            @Argument("amount") int amount,
            @Argument(value = "quest-type", suggestions = "quest-types") String questType,
            @Argument(value = "player", suggestions = "online-players") @Greedy String playerName
    ) {
        if (amount <= 0) {
            configProvider.get().getMessages().getInvalidAdminProgressAmount().send(source.getSender());
            return;
        }

        QuestType type = parseRegisteredQuestType(questType);
        if (type == null) {
            configProvider.get().getMessages().getInvalidQuestType().send(source.getSender(), Placeholder.unparsed("quest_type", questType));
            return;
        }

        Player target = Bukkit.getPlayerExact(playerName);
        if (target == null) {
            configProvider.get().getMessages().getPlayerNotFound().send(source.getSender(), Placeholder.unparsed("player", playerName));
            return;
        }

        List<QuestProgressResult> results = questService.progressAdminGoal(target, resetService.currentResetKey(), amount, type);
        if (results.isEmpty()) {
            configProvider.get().getMessages().getAdminProgressNotActive().send(
                    source.getSender(),
                    Placeholder.unparsed("quest_id", type.key()),
                    Placeholder.unparsed("quest_type", type.key()),
                    Placeholder.unparsed("player", target.getName())
            );
            return;
        }

        configProvider.get().getMessages().getAdminProgress().send(
                source.getSender(),
                Placeholder.unparsed("amount", QuestNumberFormatter.format(amount)),
                Placeholder.unparsed("quest_id", type.key()),
                Placeholder.unparsed("quest_type", type.key()),
                Placeholder.unparsed("player", target.getName())
        );
    }

    @Command("qa|questsadmin global add <quest-type> <amount> <player>")
    @Permission("questsplus.admin")
    public void globalAdd(
            CommandSourceStack source,
            @Argument(value = "quest-type", suggestions = "quest-types") String questType,
            @Argument("amount") int amount,
            @Argument(value = "player", suggestions = "online-players") @Greedy String playerName
    ) {
        if (amount <= 0) {
            configProvider.get().getMessages().getInvalidAdminProgressAmount().send(source.getSender());
            return;
        }

        QuestType type = parseRegisteredQuestType(questType);
        if (type == null) {
            configProvider.get().getGlobalQuests().getMessages().getInvalidQuestType().send(source.getSender(), Placeholder.unparsed("quest_type", questType));
            return;
        }

        Player target = Bukkit.getPlayerExact(playerName);
        if (target == null) {
            configProvider.get().getMessages().getPlayerNotFound().send(source.getSender(), Placeholder.unparsed("player", playerName));
            return;
        }

        List<QuestProgressResult> results = globalQuestService.progressAdminGoal(target, type, amount);
        if (results.isEmpty()) {
            configProvider.get().getGlobalQuests().getMessages().getCommandNoActive().send(
                    source.getSender(),
                    Placeholder.unparsed("quest_type", type.key()),
                    Placeholder.unparsed("player", target.getName())
            );
            return;
        }

        configProvider.get().getGlobalQuests().getMessages().getCommandProgress().send(
                source.getSender(),
                Placeholder.unparsed("amount", QuestNumberFormatter.format(amount)),
                Placeholder.unparsed("quest_type", type.key()),
                Placeholder.unparsed("player", target.getName())
        );
    }

    @Command("qa|questsadmin global refresh")
    @Permission("questsplus.admin")
    public void globalRefresh(CommandSourceStack source) {
        globalQuestService.refreshActiveQuest()
                .thenRun(() -> runForSender(source.getSender(), () -> configProvider.get().getMessages().getGlobalQuestRefreshed().send(source.getSender())))
                .exceptionally(throwable -> {
                    log.error("Failed to refresh QuestsPlus global quest.", throwable);
                    runForSender(source.getSender(), () -> source.getSender().sendRichMessage("<red>Failed to refresh the active global quest."));
                    return null;
                });
    }

    @Command("qa|questsadmin dailyrerolls reset <player>")
    @Permission("questsplus.admin")
    public void resetRerolls(CommandSourceStack source, @Argument(value = "player", suggestions = "online-players") @Greedy String playerName) {
        Player target = Bukkit.getPlayerExact(playerName);
        if (target == null) {
            configProvider.get().getMessages().getPlayerNotFound().send(source.getSender(), Placeholder.unparsed("player", playerName));
            return;
        }
        questService.resetRerolls(target.getUniqueId(), resetService.currentResetKey())
                .thenRun(() -> runForSender(source.getSender(), () -> configProvider.get().getMessages().getRerollsReset().send(
                        source.getSender(),
                        Placeholder.unparsed("player", target.getName())
                )))
                .exceptionally(throwable -> {
                    log.error("Failed to reset QuestsPlus rerolls for {}.", target.getUniqueId(), throwable);
                    runForSender(source.getSender(), () -> source.getSender().sendRichMessage("<red>Failed to reset that player's rerolls."));
                    return null;
                });
    }

    @Command("qa|questsadmin shield give <amount> <player>")
    @Permission("questsplus.admin")
    public void shieldGive(CommandSourceStack source, @Argument("amount") int amount, @Argument(value = "player", suggestions = "online-players") @Greedy String playerName) {
        if (amount <= 0) {
            configProvider.get().getMessages().getInvalidAdminProgressAmount().send(source.getSender());
            return;
        }
        adjustCurrency(source, QuestStreakService.StreakCurrency.SHIELD, amount, playerName);
    }

    @Command("qa|questsadmin shield take <amount> <player>")
    @Permission("questsplus.admin")
    public void shieldTake(CommandSourceStack source, @Argument("amount") int amount, @Argument(value = "player", suggestions = "online-players") @Greedy String playerName) {
        if (amount <= 0) {
            configProvider.get().getMessages().getInvalidAdminProgressAmount().send(source.getSender());
            return;
        }
        adjustCurrency(source, QuestStreakService.StreakCurrency.SHIELD, -amount, playerName);
    }

    @Command("qa|questsadmin recovery give <amount> <player>")
    @Permission("questsplus.admin")
    public void recoveryGive(CommandSourceStack source, @Argument("amount") int amount, @Argument(value = "player", suggestions = "online-players") @Greedy String playerName) {
        if (amount <= 0) {
            configProvider.get().getMessages().getInvalidAdminProgressAmount().send(source.getSender());
            return;
        }
        adjustCurrency(source, QuestStreakService.StreakCurrency.RECOVERY, amount, playerName);
    }

    @Command("qa|questsadmin recovery take <amount> <player>")
    @Permission("questsplus.admin")
    public void recoveryTake(CommandSourceStack source, @Argument("amount") int amount, @Argument(value = "player", suggestions = "online-players") @Greedy String playerName) {
        if (amount <= 0) {
            configProvider.get().getMessages().getInvalidAdminProgressAmount().send(source.getSender());
            return;
        }
        adjustCurrency(source, QuestStreakService.StreakCurrency.RECOVERY, -amount, playerName);
    }

    private void adjustCurrency(CommandSourceStack source, QuestStreakService.StreakCurrency currency, int delta, String playerName) {
        if (!streakService.isEnabled()) {
            source.getSender().sendRichMessage("<red>Quest streaks are disabled.");
            return;
        }
        if (delta == 0) {
            configProvider.get().getMessages().getInvalidAdminProgressAmount().send(source.getSender());
            return;
        }
        Player target = Bukkit.getPlayerExact(playerName);
        if (target == null) {
            configProvider.get().getMessages().getPlayerNotFound().send(source.getSender(), Placeholder.unparsed("player", playerName));
            return;
        }
        if (delta < 0 && delta == Integer.MIN_VALUE) {
            configProvider.get().getMessages().getInvalidAdminProgressAmount().send(source.getSender());
            return;
        }
        int amount = Math.abs(delta);
        if (amount <= 0) {
            configProvider.get().getMessages().getInvalidAdminProgressAmount().send(source.getSender());
            return;
        }
        streakService.adjustCurrency(target.getUniqueId(), currency, delta)
                .thenAccept(balance -> runForSender(source.getSender(), () -> configProvider.get().getMessages().getStreakCurrencyUpdated().send(
                        source.getSender(),
                        Placeholder.unparsed("player", target.getName()),
                        Placeholder.unparsed("currency", currency == QuestStreakService.StreakCurrency.SHIELD ? "shield" : "recovery"),
                        Placeholder.unparsed("amount", QuestNumberFormatter.format(balance))
                )))
                .exceptionally(throwable -> {
                    log.error("Failed to adjust QuestsPlus streak currency for {}.", target.getUniqueId(), throwable);
                    runForSender(source.getSender(), () -> source.getSender().sendRichMessage("<red>Failed to update streak currency."));
                    return null;
                });
    }

    private void runForSender(CommandSender sender, Runnable action) {
        if (sender instanceof Player player) {
            Scheduler.entity(player).run(task -> {
                if (player.isOnline()) {
                    action.run();
                }
            });
            return;
        }

        Scheduler.sync().run(task -> action.run());
    }

    @Suggestions("online-players")
    public Iterable<String> onlinePlayerSuggestions(CommandContext<CommandSourceStack> context, String input) {
        return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
    }

    @Suggestions("quest-difficulties")
    public Iterable<String> questDifficultySuggestions(CommandContext<CommandSourceStack> context, String input) {
        return definitionService.difficulties().stream().map(QuestDifficulty::id).toList();
    }

    @Suggestions("quest-types")
    public Iterable<String> questTypeSuggestions(CommandContext<CommandSourceStack> context, String input) {
        String normalizedInput = normalizeSuggestionInput(input);
        return definitionService.registeredTypes().stream()
                .map(QuestType::key)
                .sorted()
                .filter(key -> normalizedInput.isBlank() || key.startsWith(normalizedInput))
                .toList();
    }

    @Suggestions("quest-indicator-scopes")
    public Iterable<String> questIndicatorScopeSuggestions(CommandContext<CommandSourceStack> context, String input) {
        String normalizedInput = normalizeSuggestionInput(input);
        return java.util.stream.Stream.of("Main", "Both", "Global")
                .filter(scope -> normalizedInput.isBlank() || normalizeSuggestionInput(scope).startsWith(normalizedInput))
                .toList();
    }

    @Suggestions("quest-indicators")
    public Iterable<String> questIndicatorSuggestions(CommandContext<CommandSourceStack> context, String input) {
        String normalizedInput = normalizeSuggestionInput(input);
        return java.util.stream.Stream.concat(
                        java.util.stream.Stream.of(QuestIndicatorPreferenceService.DEFAULT, QuestIndicatorPreferenceService.OFF),
                        progressIndicatorService.availableIndicatorTypes().stream()
                )
                .distinct()
                .filter(key -> normalizedInput.isBlank() || key.startsWith(normalizedInput))
                .toList();
    }

    private String normalizeSuggestionInput(String input) {
        return java.util.Objects.requireNonNullElse(input, "")
                .trim()
                .toUpperCase(java.util.Locale.ROOT)
                .replace('-', '_')
                .replace(' ', '_')
                .replaceAll("[^A-Z0-9_]", "");
    }

    private QuestType parseRegisteredQuestType(String questType) {
        try {
            QuestType type = QuestType.of(questType);
            return definitionService.handlerIfRegistered(type).isPresent() ? type : null;
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private Component typeListLine(String owner, List<QuestType> types) {
        Component line = Component.text(owner + ": ", NamedTextColor.WHITE);
        for (int index = 0; index < types.size(); index++) {
            if (index > 0) {
                line = line.append(Component.text(", ", NamedTextColor.WHITE));
            }
            String key = types.get(index).key();
            line = line.append(Component.text(key, NamedTextColor.GREEN)
                    .hoverEvent(Component.text("Click to copy", NamedTextColor.GRAY))
                    .clickEvent(ClickEvent.copyToClipboard(key)));
        }
        return line;
    }
}
