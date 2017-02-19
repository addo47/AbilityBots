package org.telegram.abilitybots.api.objects;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import org.telegram.telegrambots.logging.BotLogger;

import java.util.function.Consumer;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.containsWhitespace;
import static org.apache.commons.lang3.StringUtils.isAlphanumeric;

/**
 * Created by Addo on 2/5/2017.
 */
public final class Ability {
    private static final String TAG = Ability.class.getName();
    private final String name;
    private final Locality locality;
    private final Privacy privacy;
    private final int argNum;
    private final Consumer<MessageContext> consumer;
    private final Consumer<MessageContext> afterConsumer;

    private Ability(String name, Locality locality, Privacy privacy, int argNum, Consumer<MessageContext> consumer, Consumer<MessageContext> afterConsumer) {

        checkArgument(!name.isEmpty(), "Method name cannot be empty");
        checkArgument(!containsWhitespace(name), "Method name cannot contain spaces");
        checkArgument(isAlphanumeric(name), "Method name can only be alpha-numeric", name);
        this.name = checkNotNull(name, "Please specify a valid method name");

        this.locality = checkNotNull(locality, "Please specify a valid locality setting. Use the Locality enum class");
        this.privacy = checkNotNull(privacy, "Please specify a valid privacy setting. Use the Privacy enum class");

        checkArgument(argNum >= 0, "The number of arguments the method can handle CANNOT be negative. " +
                "Use the number 0 if the method ignores the arguments OR uses as many as appended");
        this.argNum = argNum;

        this.consumer = checkNotNull(consumer, "Method consumer can't be empty. Please assign a function by using .consumer() method");
        if (afterConsumer == null)
            BotLogger.info(TAG, format("No after consumer was detected for method with name [%s]", name));

        this.afterConsumer = afterConsumer;
    }

    public static AbilityBuilder builder() {
        return new AbilityBuilder();
    }

    public String name() {
        return name;
    }

    public Locality locality() {
        return locality;
    }

    public Privacy privacy() {
        return privacy;
    }

    public int tokens() {
        return argNum;
    }

    public Consumer<MessageContext> consumer() {
        return consumer;
    }

    public Consumer<MessageContext> afterConsumer() {
        return afterConsumer;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("name", name)
                .add("locality", locality)
                .add("privacy", privacy)
                .add("argNum", argNum)
                .toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        Ability ability = (Ability) o;
        return argNum == ability.argNum &&
                Objects.equal(name, ability.name) &&
                locality == ability.locality &&
                privacy == ability.privacy;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(name, locality, privacy, argNum);
    }


    public static class AbilityBuilder {
        private Consumer<MessageContext> consumer;
        private Consumer<MessageContext> afterConsumer;
        private String name;
        private Locality locality;
        private Privacy privacy;
        private int argNum;

        private AbilityBuilder() {
        }

        public AbilityBuilder consumer(Consumer<MessageContext> consumer) {
            this.consumer = consumer;
            return this;
        }

        public AbilityBuilder name(String name) {
            this.name = name;
            return this;
        }

        public AbilityBuilder locality(Locality type) {
            this.locality = type;
            return this;
        }

        public AbilityBuilder input(int argNum) {
            this.argNum = argNum;
            return this;
        }

        public AbilityBuilder privacy(Privacy privacy) {
            this.privacy = privacy;
            return this;
        }

        public AbilityBuilder after(Consumer<MessageContext> afterConsumer) {
            this.afterConsumer = afterConsumer;
            return this;
        }


        public Ability build() {
            return new Ability(name, locality, privacy, argNum, consumer, afterConsumer);
        }
    }
}
