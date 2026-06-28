package gg.moonrise.quests.core.service;

import gg.moonrise.moss.spring.SpringComponent;
import gg.moonrise.quests.sdk.currency.QuestCurrencies;
import gg.moonrise.quests.sdk.currency.QuestCurrency;
import gg.moonrise.quests.sdk.currency.QuestCurrencyKey;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@SpringComponent
public class QuestResetPurchaseService {

    private final Set<UUID> processingPlayers = ConcurrentHashMap.newKeySet();
    private final Map<QuestCurrencyKey, CurrencyRegistration> currencies = new LinkedHashMap<>();

    public boolean begin(Player player) {
        return processingPlayers.add(player.getUniqueId());
    }

    public void finish(Player player) {
        processingPlayers.remove(player.getUniqueId());
    }

    public boolean isProcessing(Player player) {
        return processingPlayers.contains(player.getUniqueId());
    }

    public synchronized void registerCurrency(Plugin owner, QuestCurrency currency) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(currency, "currency");
        QuestCurrencyKey key = Objects.requireNonNull(currency.key(), "currency key");
        if (isReserved(key)) {
            throw new IllegalArgumentException("Currency key " + key.key() + " is reserved by QuestsPlus");
        }
        CurrencyRegistration existing = currencies.get(key);
        if (existing != null) {
            throw new IllegalArgumentException("Currency key " + key.key() + " is already registered by " + existing.owner().getName());
        }
        currencies.put(key, new CurrencyRegistration(owner, currency, false));
    }

    public synchronized void registerBuiltInCurrency(QuestCurrency currency) {
        Objects.requireNonNull(currency, "currency");
        QuestCurrencyKey key = Objects.requireNonNull(currency.key(), "currency key");
        if (!isReserved(key)) {
            throw new IllegalArgumentException("Currency key " + key.key() + " is not a QuestsPlus built-in currency");
        }
        currencies.put(key, new CurrencyRegistration(null, currency, true));
    }

    public synchronized void unregisterCurrency(Plugin owner, QuestCurrencyKey key) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(key, "key");
        CurrencyRegistration registration = currencies.get(key);
        if (registration != null && !registration.builtIn() && registration.owner().equals(owner)) {
            currencies.remove(key);
        }
    }

    public synchronized void unregisterCurrencies(Plugin owner) {
        Objects.requireNonNull(owner, "owner");
        Iterator<Map.Entry<QuestCurrencyKey, CurrencyRegistration>> iterator = currencies.entrySet().iterator();
        while (iterator.hasNext()) {
            CurrencyRegistration registration = iterator.next().getValue();
            if (!registration.builtIn() && registration.owner().equals(owner)) {
                iterator.remove();
            }
        }
    }

    public List<QuestCurrencyKey> registeredCurrencyKeys() {
        synchronized (this) {
            return List.copyOf(currencies.keySet());
        }
    }

    public List<QuestCurrency> registeredCurrencies() {
        synchronized (this) {
            return currencies.values().stream()
                    .filter(this::isEnabled)
                    .map(CurrencyRegistration::currency)
                    .toList();
        }
    }

    public QuestCurrency registeredCurrency(QuestCurrencyKey key) {
        CurrencyRegistration registration = currency(key);
        return isEnabled(registration) ? registration.currency() : null;
    }

    private boolean isEnabled(CurrencyRegistration registration) {
        return registration != null && (registration.builtIn() || registration.owner().isEnabled());
    }

    private boolean isReserved(QuestCurrencyKey key) {
        return QuestCurrencies.PLAYER_POINTS.equals(key) || QuestCurrencies.VAULT.equals(key);
    }

    private synchronized CurrencyRegistration currency(QuestCurrencyKey key) {
        return key == null ? null : currencies.get(key);
    }

    private record CurrencyRegistration(Plugin owner, QuestCurrency currency, boolean builtIn) {
    }
}
