package gg.moonrise.quests.loader;

import gg.moonrise.engine.paper.loader.PaperPluginLoader;
import io.papermc.paper.plugin.loader.library.impl.MavenLibraryResolver;

public class QuestsPlusPluginLoader extends PaperPluginLoader {

    @Override
    public void addLibraries(MavenLibraryResolver resolver) {
        resolver.addDependency(dependency("com.zaxxer:HikariCP:7.0.2"));
        resolver.addDependency(dependency("org.xerial:sqlite-jdbc:3.50.3.0"));
        resolver.addDependency(dependency("com.github.ben-manes.caffeine:caffeine:3.2.3"));
        resolver.addDependency(dependency("com.google.code.gson:gson:2.13.2"));
    }
}
