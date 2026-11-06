package gg.moonrise.quests.config;

import gg.moonrise.engine.message.Message;
import gg.moonrise.quests.QuestsPlusPlugin;
import gg.moonrise.quests.core.service.ConfigProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConfigMessagesTest {

    private static final List<Class<?>> MESSAGE_SOURCES = List.of(
            Config.SharedMessages.class,
            Config.DailyMessages.class,
            Config.MilestoneMessages.class,
            Config.StreakMessages.class
    );

    @TempDir
    private Path tempDir;

    @Test
    public void everyMessageGetterReturnsADefaultValue() throws Exception {
        List<Method> getters = messageGetters();
        assertFalse(getters.isEmpty());

        for (Config.Messages messages : List.of(new Config().getMessages(), Config.compose(null, null, null, null, null, null, null).getMessages())) {
            for (Method getter : getters) {
                assertNotNull(getter.invoke(messages), getter.getName() + " returned null");
            }
            assertNotNull(messages.getProgressIndicators());
        }
    }

    @Test
    public void everyMessageGetterIsBackedByExactlyOneSourceField() {
        Set<String> sourceGetters = new HashSet<>();
        for (Class<?> source : MESSAGE_SOURCES) {
            for (Field field : messageFields(source)) {
                assertTrue(sourceGetters.add(getterName(field)), "Duplicate message " + field.getName());
            }
        }

        Set<String> exposedGetters = new HashSet<>();
        for (Method getter : messageGetters()) {
            exposedGetters.add(getter.getName());
        }

        assertEquals(sourceGetters, exposedGetters);
    }

    @Test
    public void valuesFromEachSourceSectionShowUpThroughMessages() throws Exception {
        Config.SharedMessages shared = new Config.SharedMessages();
        Config.DailyMessages daily = new Config.DailyMessages();
        Config.MilestoneMessages milestone = new Config.MilestoneMessages();
        Config.StreakMessages streak = new Config.StreakMessages();
        List<Object> sources = List.of(shared, daily, milestone, streak);
        for (Object source : sources) {
            for (Field field : messageFields(source.getClass())) {
                field.setAccessible(true);
                String value = source.getClass().getSimpleName() + "." + field.getName();
                field.set(source, field.getType() == Message.class ? Message.of(value) : value);
            }
        }
        Config.SharedMessagesFile sharedFile = new Config.SharedMessagesFile();
        Config.DailyFile dailyFile = new Config.DailyFile();
        Config.QuestMilestonesFile milestonesFile = new Config.QuestMilestonesFile(milestone, null);
        Config.StreaksFile streaksFile = new Config.StreaksFile();
        Config.ProgressIndicators progressIndicators = new Config.ProgressIndicators();
        set(sharedFile, "messages", shared);
        set(sharedFile, "progressIndicators", progressIndicators);
        set(dailyFile, "messages", daily);
        set(streaksFile, "messages", streak);

        Config.Messages messages = Config.compose(null, dailyFile, null, streaksFile, sharedFile, null, null, milestonesFile, null, null).getMessages();

        for (Object source : sources) {
            for (Field field : messageFields(source.getClass())) {
                field.setAccessible(true);
                Object expected = field.get(source);
                Object actual = Config.Messages.class.getMethod(getterName(field)).invoke(messages);
                assertSame(expected, actual, field.getName());
            }
        }
        assertSame(progressIndicators, messages.getProgressIndicators());
    }

    @Test
    public void missingSourceSectionsFallBackToDefaults() throws Exception {
        Config.SharedMessagesFile sharedFile = new Config.SharedMessagesFile();
        Config.DailyFile dailyFile = new Config.DailyFile();
        Config.StreaksFile streaksFile = new Config.StreaksFile();
        set(sharedFile, "messages", null);
        set(sharedFile, "progressIndicators", null);
        set(dailyFile, "messages", null);
        set(streaksFile, "messages", null);
        Config.QuestMilestonesFile milestonesFile = new Config.QuestMilestonesFile();
        set(milestonesFile, "messages", null);

        Config.Messages messages = Config.compose(null, dailyFile, null, streaksFile, sharedFile, null, null, milestonesFile, null, null).getMessages();

        for (Method getter : messageGetters()) {
            assertNotNull(getter.invoke(messages), getter.getName() + " returned null");
        }
        assertEquals("<red>Only players can use this command.", messages.getPlayerOnly().content());
        assertEquals("<red>No daily quests are selected.", messages.getNoQuests().content());
        assertEquals("<green>You have completed <white><quests_completed></white> quests across all difficulties.", messages.getCompletedCount().content());
        assertEquals("<red>No recoverable streak is available.", messages.getStreakRecoveryUnavailable().content());
        assertNotNull(messages.getProgressIndicators());
    }

    @Test
    public void messagesEditedInEachYamlFileAreLoaded() throws Exception {
        QuestsPlusPlugin plugin = mock(QuestsPlusPlugin.class);
        when(plugin.getDataFolder()).thenReturn(tempDir.toFile());
        ConfigProvider provider = new ConfigProvider(plugin);
        provider.init();

        replace("messages.yml", "Only players can use this command.", "Custom player only.");
        replace("messages.yml", "<dark_gray>Locked", "<gray>Custom locked");
        replace("daily.yml", "No daily quests are selected.", "Custom no quests.");
        replace("quest-milestones.yml", "Milestone reward claimed:", "Custom claimed:");
        replace("streaks.yml", "No recoverable streak is available.", "Custom no recovery.");

        Config.Messages messages = provider.loadFromDisk().getMessages();

        assertEquals("<red>Custom player only.", messages.getPlayerOnly().content());
        assertEquals("<gray>Custom locked", messages.getStreakMilestoneLocked());
        assertEquals("<red>Custom no quests.", messages.getNoQuests().content());
        assertTrue(messages.getMilestoneClaimed().content().startsWith("<green>Custom claimed:"));
        assertEquals("<red>Custom no recovery.", messages.getStreakRecoveryUnavailable().content());
    }

    private void replace(String fileName, String target, String replacement) throws Exception {
        Path file = tempDir.resolve(fileName);
        String content = Files.readString(file);
        assertTrue(content.contains(target), fileName + " does not contain " + target);
        Files.writeString(file, content.replace(target, replacement));
    }

    private static List<Method> messageGetters() {
        List<Method> getters = new ArrayList<>();
        for (Method method : Config.Messages.class.getMethods()) {
            boolean messageType = method.getReturnType() == Message.class || method.getReturnType() == String.class;
            if (method.getName().startsWith("get") && method.getParameterCount() == 0 && messageType
                    && method.getDeclaringClass() == Config.Messages.class) {
                getters.add(method);
            }
        }
        return getters;
    }

    private static List<Field> messageFields(Class<?> source) {
        return Arrays.stream(source.getDeclaredFields())
                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                .filter(field -> field.getType() == Message.class || field.getType() == String.class)
                .toList();
    }

    private static String getterName(Field field) {
        String name = field.getName();
        return "get" + Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    private static void set(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
