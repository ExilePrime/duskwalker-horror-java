package com.duskwalker.bedrock;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** world.afterEvents.xxx.subscribe(...) karsiligi. Dinleyici hatalari yutulur (Bedrock gibi diger dinleyicileri etkilemez). */
public final class Signal<T> {
    private final List<Consumer<T>> listeners = new ArrayList<>();

    public void subscribe(Consumer<T> listener) { listeners.add(listener); }

    /** Binding tarafindan cagrilir. */
    public void fire(T event) {
        for (Consumer<T> l : new ArrayList<>(listeners)) {
            try { l.accept(event); } catch (Throwable t) { Log.warn("event listener failed: " + t); }
        }
    }
}
