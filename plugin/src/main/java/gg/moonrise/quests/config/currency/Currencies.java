package gg.moonrise.quests.config.currency;

import de.exlll.configlib.Comment;
import de.exlll.configlib.Configuration;
import lombok.Getter;

import java.util.List;

@Getter
@Configuration
public class Currencies {
    @Comment({
            "",
            "Currency keys QuestsPlus may show for purchases. Loaded from currencies.yml."
    })
    private List<String> enabledCurrencies = List.of("vault", "playerpoints");
    @Comment({
            "",
            "PlayerPoints currency settings."
    })
    private PlayerPointsCurrencyConfig playerPoints = new PlayerPointsCurrencyConfig();
    @Comment({
            "",
            "Vault currency settings."
    })
    private VaultCurrencyConfig vault = new VaultCurrencyConfig();

    public Currencies() {
    }

    public Currencies(PlayerPointsCurrencyConfig playerPoints, VaultCurrencyConfig vault) {
        this(null, playerPoints, vault);
    }

    public Currencies(List<String> enabledCurrencies, PlayerPointsCurrencyConfig playerPoints, VaultCurrencyConfig vault) {
        this.enabledCurrencies = enabledCurrencies == null ? List.of("vault", "playerpoints") : List.copyOf(enabledCurrencies);
        this.playerPoints = playerPoints == null ? new PlayerPointsCurrencyConfig() : playerPoints;
        this.vault = vault == null ? new VaultCurrencyConfig() : vault;
    }
}
