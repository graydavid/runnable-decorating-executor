/*
 * Copyright 2021 David Gray
 * 
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.graydavid.runnabledecoratingexecutor;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/** Defines a way to create adornments for and thus decorate (in a limited way) a Runnable submitted to an Executor. */
@FunctionalInterface
public interface RunnableAdorner {
    /**
     * Simply creates an adornment for a runnable. The runnable (and its adornments) will be run later when submitted to
     * an Executor.
     */
    BeforeRunnableAction createAdornment(Runnable runnable);

    /**
     * Returns an adorner that does nothing: it returns a do-nothing BeforeRunnableAction that simply returns a
     * do-nothing AfterRunnableAction.
     */
    static RunnableAdorner doNothing() {
        return RunnableAdorners.doNothing();
    }

    /**
     * Creates a composite adorner that runs each adorner in the list in order. The response from the returned adorner
     * is a BeforeRunnableAction that runs each adorner's action in order. The response from the BeforeRunnableAction is
     * an AfterRunnableAction that runs each adorner's BeforeRunnableAction in reverse order. At any point iterating
     * through each adorner, before action, or after action; if any part of that iteration throws an exception, the
     * iteration is halted and the exception is propagated.
     */
    static RunnableAdorner composite(List<RunnableAdorner> adorners) {
        return RunnableAdorners.composite(adorners);
    }

    /**
     * Creates a (mostly) fault-tolerant adorner that decorates another adorner. <br>
     * * If any Throwable is thrown during the adornment-creation process, it's suppressed and passed to the
     * failureObserver, while a do-nothing BeforeRunnableAction is returned.<br>
     * * If the adornment-creation process succeeds, then the underlying BeforeRunnableAction it returns is run. If the
     * action throws an exception, it's suppressed and passed to the failureObserver, while a do-nothing
     * AfterRunnableAction is returned.<br>
     * * If the underlying BeforeRunnableAction succeeds, then the underlying AfterRunnableAction it returns is run. If
     * the action throws an exception, it's suppressed and passed to the failureObserver.<br>
     * * If the failureObserver itself throws an exception, then that exception is propagated as is. That's where the
     * "mostly" comes from in the name. This is intentional, as it gives you a way to re-throw exceptions that you
     * didn't want caught (e.g. Errors). That said, if you want something truly fault tolerant, then see
     * {@link AdornmentFailureObserver#faultSwallowing(AdornmentFailureObserver)}.
     */
    static RunnableAdorner mostlyFaultTolerant(RunnableAdorner decorated, AdornmentFailureObserver failureObserver) {
        return RunnableAdorners.mostlyFaultTolerant(decorated, failureObserver);
    }

    /**
     * A utility method to create a {@link #composite(List)} adorner that decorates a list of
     * {@link #mostlyFaultTolerant(RunnableAdorner, AdornmentFailureObserver)}s.
     */
    static RunnableAdorner compositeOfMostlyFaultTolerant(List<RunnableAdorner> adorners,
            AdornmentFailureObserver failureObserver) {
        Objects.requireNonNull(failureObserver);
        List<RunnableAdorner> tolerants = adorners.stream()
                .map(adorner -> mostlyFaultTolerant(adorner, failureObserver))
                .collect(Collectors.toList());
        return composite(tolerants);
    }
}


class RunnableAdorners {
    private RunnableAdorners() {}

    public static RunnableAdorner doNothing() {
        return DO_NOTHING;
    }

    private static final RunnableAdorner DO_NOTHING = runnable -> BeforeRunnableAction.doNothing();

    public static RunnableAdorner composite(List<RunnableAdorner> adorners) {
        return new CompositeRunnableAdorner(adorners);
    }

    private static class CompositeRunnableAdorner implements RunnableAdorner {
        private final List<RunnableAdorner> adorners;

        private CompositeRunnableAdorner(List<RunnableAdorner> adorners) {
            this.adorners = List.copyOf(adorners);
        }

        @Override
        public BeforeRunnableAction createAdornment(Runnable runnable) {
            List<BeforeRunnableAction> beforeActions = adorners.stream()
                    .map(adorner -> adorner.createAdornment(runnable))
                    .collect(Collectors.toList());
            return compositeBeforeAction(beforeActions);
        }

        private static BeforeRunnableAction compositeBeforeAction(List<BeforeRunnableAction> beforeActions) {
            return () -> {
                List<AfterRunnableAction> afterActions = beforeActions.stream()
                        .map(action -> action.runBeforeRunnable())
                        .collect(Collectors.toList());
                Collections.reverse(afterActions);
                return compositeAfterAction(afterActions);
            };
        }

        private static AfterRunnableAction compositeAfterAction(List<AfterRunnableAction> afterActions) {
            return throwable -> {
                afterActions.stream().forEach(action -> action.runAfterRunnable(throwable));
            };
        }
    }

    static RunnableAdorner mostlyFaultTolerant(RunnableAdorner decorated, AdornmentFailureObserver failureObserver) {
        return new MostlyFaultTolerantRunnableAdorner(decorated, failureObserver);
    }

    private static class MostlyFaultTolerantRunnableAdorner implements RunnableAdorner {
        private final RunnableAdorner decorated;
        private final AdornmentFailureObserver failureObserver;

        private MostlyFaultTolerantRunnableAdorner(RunnableAdorner decorated,
                AdornmentFailureObserver failureObserver) {
            this.decorated = Objects.requireNonNull(decorated);
            this.failureObserver = Objects.requireNonNull(failureObserver);
        }

        @Override
        public BeforeRunnableAction createAdornment(Runnable runnable) {
            try {
                BeforeRunnableAction beforeAction = decorated.createAdornment(runnable);
                return mostlyFaultTolerantBeforeAction(beforeAction);
            } catch (Throwable t) {
                failureObserver.observe(t);
                return BeforeRunnableAction.doNothing();
            }
        }

        private BeforeRunnableAction mostlyFaultTolerantBeforeAction(BeforeRunnableAction decoratedBeforeAction) {
            return () -> {
                try {
                    AfterRunnableAction afterAction = decoratedBeforeAction.runBeforeRunnable();
                    return mostlyFaultTolerantAfterAction(afterAction);
                } catch (Throwable t) {
                    failureObserver.observe(t);
                    return AfterRunnableAction.doNothing();
                }
            };
        }

        private AfterRunnableAction mostlyFaultTolerantAfterAction(AfterRunnableAction decoratedAfterAction) {
            return throwable -> {
                try {
                    decoratedAfterAction.runAfterRunnable(throwable);
                } catch (Throwable t) {
                    failureObserver.observe(t);
                }
            };
        }
    }
}
