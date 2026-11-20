package idle.miau.mcp.event;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class EventBus {
    private static final Logger LOGGER = LogManager.getLogger(EventBus.class);
    private final Map<Object, List<Listener>> listenersByOwner =
            Collections.synchronizedMap(new IdentityHashMap<Object, List<Listener>>());
    private final List<Object> ownerOrder = new ArrayList<Object>();
    private volatile DispatchState dispatchState = new DispatchState(Collections.<Listener>emptyList());
    private long nextSequence;

    public void register(Object owner) {
        if (owner == null) {
            return;
        }

        synchronized (listenersByOwner) {
            if (listenersByOwner.containsKey(owner)) {
                return;
            }

            List<Listener> discovered = new ArrayList<Listener>();
            for (Class<?> type = owner.getClass(); type != null; type = type.getSuperclass()) {
                for (Method method : type.getDeclaredMethods()) {
                    Subscribe annotation = method.getAnnotation(Subscribe.class);
                    if (annotation == null || method.getParameterTypes().length != 1
                            || !Event.class.isAssignableFrom(method.getParameterTypes()[0])) {
                        continue;
                    }
                    if (!Modifier.isPublic(method.getModifiers()) || !method.isAccessible()) {
                        method.setAccessible(true);
                    }
                    try {
                        discovered.add(new Listener(owner, method, method.getParameterTypes()[0],
                                annotation, nextSequence++));
                    } catch (IllegalAccessException exception) {
                        LOGGER.error("Cannot register event listener {}", method, exception);
                    }
                }
            }

            listenersByOwner.put(owner, discovered);
            ownerOrder.add(owner);
            rebuildSnapshot();
        }
    }

    public void unregister(Object owner) {
        synchronized (listenersByOwner) {
            if (listenersByOwner.remove(owner) != null) {
                removeOwnerFromOrder(owner);
                rebuildSnapshot();
            }
        }
    }

    public boolean post(Event event) {
        if (event == null) {
            return false;
        }
        DispatchState state = dispatchState;
        for (Listener listener : state.listenersFor(event.getClass())) {
            if (event.isCanceled() && !listener.receiveCanceled) {
                continue;
            }
            try {
                listener.handle.invokeExact(event);
            } catch (Throwable cause) {
                if (cause instanceof Error) {
                    throw (Error) cause;
                }
                LOGGER.error("Event listener {} failed for {}", listener.method,
                        event.getClass().getName(), cause);
            }
        }
        return event.isCanceled();
    }

    private void rebuildSnapshot() {
        List<Listener> all = new ArrayList<Listener>();
        for (Object owner : ownerOrder) {
            List<Listener> listeners = listenersByOwner.get(owner);
            if (listeners != null) {
                all.addAll(listeners);
            }
        }
        Collections.sort(all, new Comparator<Listener>() {
            @Override
            public int compare(Listener left, Listener right) {
                int priorityOrder = Integer.compare(left.priority.ordinal(), right.priority.ordinal());
                return priorityOrder != 0
                        ? priorityOrder
                        : Long.compare(left.sequence, right.sequence);
            }
        });
        dispatchState = new DispatchState(Collections.unmodifiableList(all));
    }

    private void removeOwnerFromOrder(Object owner) {
        for (int index = 0; index < ownerOrder.size(); index++) {
            if (ownerOrder.get(index) == owner) {
                ownerOrder.remove(index);
                return;
            }
        }
    }

    private static final class Listener {
        private final Method method;
        private final MethodHandle handle;
        private final Class<?> eventType;
        private final Priority priority;
        private final boolean receiveCanceled;
        private final long sequence;

        private Listener(Object owner, Method method, Class<?> eventType, Subscribe annotation,
                         long sequence) throws IllegalAccessException {
            this.method = method;
            this.handle = MethodHandles.lookup().unreflect(method).bindTo(owner)
                    .asType(MethodType.methodType(void.class, Event.class));
            this.eventType = eventType;
            this.priority = annotation.priority();
            this.receiveCanceled = annotation.receiveCanceled();
            this.sequence = sequence;
        }
    }

    private static final class DispatchState {
        private final List<Listener> listeners;
        private final ConcurrentMap<Class<?>, List<Listener>> listenersByEvent =
                new ConcurrentHashMap<Class<?>, List<Listener>>();

        private DispatchState(List<Listener> listeners) {
            this.listeners = listeners;
        }

        private List<Listener> listenersFor(Class<?> eventClass) {
            return listenersByEvent.computeIfAbsent(eventClass, this::resolveListeners);
        }

        private List<Listener> resolveListeners(Class<?> eventClass) {
            List<Listener> resolved = new ArrayList<Listener>();
            for (Listener listener : listeners) {
                if (listener.eventType.isAssignableFrom(eventClass)) {
                    resolved.add(listener);
                }
            }
            return resolved.isEmpty()
                    ? Collections.<Listener>emptyList()
                    : Collections.unmodifiableList(resolved);
        }
    }
}
