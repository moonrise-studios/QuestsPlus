package gg.moonrise.quests.core.yml;

import de.exlll.configlib.Serializer;
import gg.moonrise.engine.message.Message;

public class MessageSerializer implements Serializer<Message, String> {

    @Override
    public String serialize(Message message) {
        return message.content();
    }

    @Override
    public Message deserialize(String input) {
        return Message.of(input);
    }
}
