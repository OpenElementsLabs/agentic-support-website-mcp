package com.openelements.content;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Selects the {@link ContentSourceStrategy} for a {@link ContentSource} by its {@link SourceType}.
 *
 * <p>All strategy beans are injected and indexed by {@link ContentSourceStrategy#type()}, so a new
 * strategy is auto-discovered simply by being a bean. Two strategies claiming the same type is a
 * configuration error and fails fast at startup.
 */
@Component
public class SourceStrategyRegistry {

    private final Map<SourceType, ContentSourceStrategy> byType;

    public SourceStrategyRegistry(List<ContentSourceStrategy> strategies) {
        Map<SourceType, ContentSourceStrategy> map = new EnumMap<>(SourceType.class);
        for (ContentSourceStrategy strategy : strategies) {
            ContentSourceStrategy previous = map.putIfAbsent(strategy.type(), strategy);
            if (previous != null) {
                throw new IllegalStateException(
                    "Multiple content source strategies registered for type " + strategy.type());
            }
        }
        this.byType = map;
    }

    /**
     * @param source the source whose strategy is needed
     * @return the strategy handling the source's type
     * @throws IllegalArgumentException if no strategy is registered for the source's type
     */
    public ContentSourceStrategy forSource(ContentSource source) {
        ContentSourceStrategy strategy = byType.get(source.type());
        if (strategy == null) {
            throw new IllegalArgumentException(
                "No content source strategy registered for type " + source.type());
        }
        return strategy;
    }
}
