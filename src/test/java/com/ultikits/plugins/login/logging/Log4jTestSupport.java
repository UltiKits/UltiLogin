package com.ultikits.plugins.login.logging;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.filter.CompositeFilter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Reads the real Log4j Core root logger configuration of this test JVM -- the same one {@link
 * Log4jPasswordFilter#install} finds -- and captures what its appenders receive.
 */
public final class Log4jTestSupport {

    private Log4jTestSupport() {
    }

    /**
     * The context {@link Log4jPasswordFilter#install} uses.
     *
     * @return the Log4j Core context of this JVM
     */
    public static LoggerContext context() {
        return (LoggerContext) LogManager.getContext(LogManager.class.getClassLoader(), false);
    }

    /**
     * The root logger configuration {@link Log4jPasswordFilter#install} uses.
     *
     * @return the root logger configuration
     */
    public static LoggerConfig root() {
        return context().getConfiguration().getRootLogger();
    }

    /**
     * Every filter on a logger configuration, a composite flattened.
     *
     * @param config the configuration
     * @return its filters, possibly empty
     */
    public static List<Filter> filters(LoggerConfig config) {
        Filter filter = config.getFilter();
        List<Filter> all = new ArrayList<>();
        if (filter instanceof CompositeFilter) {
            Collections.addAll(all, ((CompositeFilter) filter).getFiltersArray());
        } else if (filter != null) {
            all.add(filter);
        }
        return all;
    }

    /**
     * How many password filters the root logger configuration holds.
     *
     * @return the count, compared by class name as {@link Log4jPasswordFilter#removeFiltersOfThisClass} does
     */
    public static long passwordFiltersOnRoot() {
        return filters(root()).stream()
                .filter(f -> f.getClass().getName().equals(Log4jPasswordFilter.class.getName()))
                .count();
    }

    /** An appender that keeps every event it is given, as an immutable copy. */
    public static final class CapturingAppender extends AbstractAppender {

        private final List<LogEvent> events = Collections.synchronizedList(new ArrayList<>());

        /**
         * Creates and starts the appender.
         *
         * @param name the appender name
         */
        public CapturingAppender(String name) {
            super(name, null, null, true, Property.EMPTY_ARRAY);
            start();
        }

        @Override
        public void append(LogEvent event) {
            events.add(event.toImmutable());
        }

        /**
         * The formatted messages received so far.
         *
         * @return the messages, in order
         */
        public List<String> messages() {
            List<String> messages = new ArrayList<>();
            synchronized (events) {
                for (LogEvent event : events) {
                    messages.add(event.getMessage().getFormattedMessage());
                }
            }
            return messages;
        }

        /**
         * The events received so far.
         *
         * @return a copy of the events, in order
         */
        public List<LogEvent> events() {
            synchronized (events) {
                return new ArrayList<>(events);
            }
        }
    }
}
