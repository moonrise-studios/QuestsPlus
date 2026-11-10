package gg.moonrise.quests;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import java.util.Arrays;
import java.util.stream.Stream;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

/**
 * Enforces the plugin's layers, top to bottom:
 * <ol>
 *     <li>player-facing code: commands, listeners, menus ({@code ui}), goal handlers ({@code type}), indicators and
 *     integrations;</li>
 *     <li>services ({@code core.service});</li>
 *     <li>backend: repositories, SQL, the clock and currency hooks ({@code core.repository}, {@code core.sql},
 *     {@code core.time}, {@code currency});</li>
 *     <li>entities, config and util at the bottom.</li>
 * </ol>
 * Each layer may only use the layers below it. The SDK and the plugin's root class sit outside the layers.
 */
@AnalyzeClasses(packages = "gg.moonrise.quests", importOptions = ImportOption.DoNotIncludeTests.class)
public class LayeredArchitectureTest {

    private static final String LOADER = "Loader";
    private static final String PLAYER_FACING = "Player-facing";
    private static final String SERVICE = "Service";
    private static final String BACKEND = "Backend";
    private static final String ENTITY = "Entity";
    private static final String CONFIG = "Config";
    private static final String UTIL = "Util";

    private static final String[] LOADER_PACKAGES = {"gg.moonrise.quests.loader.."};
    private static final String[] PLAYER_FACING_PACKAGES = {
            "gg.moonrise.quests.command..",
            "gg.moonrise.quests.listener..",
            "gg.moonrise.quests.ui..",
            "gg.moonrise.quests.type..",
            "gg.moonrise.quests.indicator..",
            "gg.moonrise.quests.integration.."
    };
    private static final String[] SERVICE_PACKAGES = {"gg.moonrise.quests.core.service.."};
    private static final String[] BACKEND_PACKAGES = {
            "gg.moonrise.quests.core.repository..",
            "gg.moonrise.quests.core.sql..",
            "gg.moonrise.quests.core.time..",
            "gg.moonrise.quests.currency.."
    };
    private static final String[] ENTITY_PACKAGES = {"gg.moonrise.quests.entity.."};
    private static final String[] CONFIG_PACKAGES = {"gg.moonrise.quests.config.."};
    private static final String[] UTIL_PACKAGES = {"gg.moonrise.quests.util.."};
    private static final String SDK_PACKAGE = "gg.moonrise.quests.sdk..";

    @ArchTest
    public static final ArchRule LAYERS_ONLY_DEPEND_DOWNWARDS = layeredArchitecture()
            .consideringOnlyDependenciesInLayers()
            .layer(LOADER).definedBy(LOADER_PACKAGES)
            .layer(PLAYER_FACING).definedBy(PLAYER_FACING_PACKAGES)
            .layer(SERVICE).definedBy(SERVICE_PACKAGES)
            .layer(BACKEND).definedBy(BACKEND_PACKAGES)
            .layer(ENTITY).definedBy(ENTITY_PACKAGES)
            .layer(CONFIG).definedBy(CONFIG_PACKAGES)
            .layer(UTIL).definedBy(UTIL_PACKAGES)
            .whereLayer(LOADER).mayNotBeAccessedByAnyLayer()
            .whereLayer(PLAYER_FACING).mayOnlyBeAccessedByLayers(LOADER)
            .whereLayer(SERVICE).mayOnlyBeAccessedByLayers(LOADER, PLAYER_FACING)
            .whereLayer(BACKEND).mayOnlyBeAccessedByLayers(LOADER, SERVICE)
            .whereLayer(ENTITY).mayOnlyBeAccessedByLayers(LOADER, PLAYER_FACING, SERVICE, BACKEND)
            .whereLayer(CONFIG).mayOnlyBeAccessedByLayers(LOADER, PLAYER_FACING, SERVICE, BACKEND)
            .whereLayer(UTIL).mayOnlyBeAccessedByLayers(LOADER, PLAYER_FACING, SERVICE, BACKEND, ENTITY, CONFIG);

    /**
     * The layer rule ignores classes outside every layer, so a new top-level package would escape it.
     */
    @ArchTest
    public static final ArchRule EVERY_PLUGIN_CLASS_BELONGS_TO_A_LAYER = classes()
            .that().resideInAPackage("gg.moonrise.quests..")
            .and().resideOutsideOfPackage(SDK_PACKAGE)
            .and().doNotHaveFullyQualifiedName(QuestsPlusPlugin.class.getName())
            .should().resideInAnyPackage(allLayerPackages());

    private static String[] allLayerPackages() {
        return Stream.of(
                        LOADER_PACKAGES,
                        PLAYER_FACING_PACKAGES,
                        SERVICE_PACKAGES,
                        BACKEND_PACKAGES,
                        ENTITY_PACKAGES,
                        CONFIG_PACKAGES,
                        UTIL_PACKAGES
                )
                .flatMap(Arrays::stream)
                .toArray(String[]::new);
    }
}
