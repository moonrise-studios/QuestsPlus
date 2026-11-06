package gg.moonrise.quests.loader;

import gg.moonrise.engine.paper.loader.PaperPluginLoader;
import io.papermc.paper.plugin.loader.library.impl.MavenLibraryResolver;

public class QuestsPlusPluginLoader extends PaperPluginLoader {

    @Override
    public void addLibraries(MavenLibraryResolver resolver) {
        for (String coordinates : RuntimeLibraries.load()) {
            resolver.addDependency(dependency(coordinates));
        }
    }
}
