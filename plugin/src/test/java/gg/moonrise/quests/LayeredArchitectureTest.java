package gg.moonrise.quests;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

/**
 * Enforces the plugin's layers: presentation on top of application, application on top of infrastructure, and
 * domain, config and util below them. The SDK and the plugin's root class sit outside the layers.
 */
@AnalyzeClasses(packages = "gg.moonrise.quests", importOptions = ImportOption.DoNotIncludeTests.class)
public class LayeredArchitectureTest {

    private static final String BOOTSTRAP = "Bootstrap";
    private static final String PRESENTATION = "Presentation";
    private static final String APPLICATION = "Application";
    private static final String INFRASTRUCTURE = "Infrastructure";
    private static final String DOMAIN = "Domain";
    private static final String CONFIG = "Config";
    private static final String UTIL = "Util";

    private static final String BOOTSTRAP_PACKAGE = "gg.moonrise.quests.bootstrap..";
    private static final String PRESENTATION_PACKAGE = "gg.moonrise.quests.presentation..";
    private static final String APPLICATION_PACKAGE = "gg.moonrise.quests.application..";
    private static final String INFRASTRUCTURE_PACKAGE = "gg.moonrise.quests.infrastructure..";
    private static final String DOMAIN_PACKAGE = "gg.moonrise.quests.domain..";
    private static final String CONFIG_PACKAGE = "gg.moonrise.quests.config..";
    private static final String UTIL_PACKAGE = "gg.moonrise.quests.util..";
    private static final String SDK_PACKAGE = "gg.moonrise.quests.sdk..";

    @ArchTest
    public static final ArchRule LAYERS_ONLY_DEPEND_DOWNWARDS = layeredArchitecture()
            .consideringOnlyDependenciesInLayers()
            .layer(BOOTSTRAP).definedBy(BOOTSTRAP_PACKAGE)
            .layer(PRESENTATION).definedBy(PRESENTATION_PACKAGE)
            .layer(APPLICATION).definedBy(APPLICATION_PACKAGE)
            .layer(INFRASTRUCTURE).definedBy(INFRASTRUCTURE_PACKAGE)
            .layer(DOMAIN).definedBy(DOMAIN_PACKAGE)
            .layer(CONFIG).definedBy(CONFIG_PACKAGE)
            .layer(UTIL).definedBy(UTIL_PACKAGE)
            .whereLayer(BOOTSTRAP).mayNotBeAccessedByAnyLayer()
            .whereLayer(PRESENTATION).mayOnlyBeAccessedByLayers(BOOTSTRAP)
            .whereLayer(APPLICATION).mayOnlyBeAccessedByLayers(BOOTSTRAP, PRESENTATION)
            .whereLayer(INFRASTRUCTURE).mayOnlyBeAccessedByLayers(BOOTSTRAP, APPLICATION)
            .whereLayer(DOMAIN).mayOnlyBeAccessedByLayers(BOOTSTRAP, PRESENTATION, APPLICATION, INFRASTRUCTURE)
            .whereLayer(CONFIG).mayOnlyBeAccessedByLayers(BOOTSTRAP, PRESENTATION, APPLICATION, INFRASTRUCTURE)
            .whereLayer(UTIL).mayOnlyBeAccessedByLayers(BOOTSTRAP, PRESENTATION, APPLICATION, INFRASTRUCTURE, DOMAIN, CONFIG);

    /**
     * The layer rule ignores classes outside every layer, so a new top-level package would escape it.
     */
    @ArchTest
    public static final ArchRule EVERY_PLUGIN_CLASS_BELONGS_TO_A_LAYER = classes()
            .that().resideInAPackage("gg.moonrise.quests..")
            .and().resideOutsideOfPackage(SDK_PACKAGE)
            .and().doNotHaveFullyQualifiedName(QuestsPlusPlugin.class.getName())
            .should().resideInAnyPackage(
                    BOOTSTRAP_PACKAGE,
                    PRESENTATION_PACKAGE,
                    APPLICATION_PACKAGE,
                    INFRASTRUCTURE_PACKAGE,
                    DOMAIN_PACKAGE,
                    CONFIG_PACKAGE,
                    UTIL_PACKAGE
            );
}
