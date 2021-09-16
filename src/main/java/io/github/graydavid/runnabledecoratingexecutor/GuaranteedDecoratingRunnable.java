/*
 * Copyright 2021 David Gray
 * 
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.graydavid.runnabledecoratingexecutor;

import java.util.Objects;

/**
 * The decorating runnable mentioned in {@link RunnableDecoratingExecutor#execute(Runnable)}. Guarantees the Runnable
 * that it decorates will be run as a part of {@link #run()}, regardless of any exceptions thrown by
 * {@link BeforeRunnableAction}.
 */
class GuaranteedDecoratingRunnable implements Runnable {
    private final Runnable decorated;
    private final BeforeRunnableAction beforeAction;

    GuaranteedDecoratingRunnable(Runnable decorated, BeforeRunnableAction beforeAction) {
        this.decorated = Objects.requireNonNull(decorated);
        this.beforeAction = Objects.requireNonNull(beforeAction);
    }

    @Override
    public void run() {
        AfterRunnableAction afterAction = null;
        Throwable beforeActionThrowable = null;
        try {
            afterAction = beforeAction.runBeforeRunnable();
            if (afterAction == null) {
                beforeActionThrowable = new NullPointerException(
                        "BeforeRunnableAction returned a null AfterRunnableAction");
            }
        } catch (Throwable t) {
            beforeActionThrowable = t;
        }

        Throwable decoratedThrowable = runDecorated();
        Throwable beforeAndDecoratedThrowable = calculateThrowableToThrow(decoratedThrowable, beforeActionThrowable);

        runAfterAction(beforeAndDecoratedThrowable, afterAction);
    }

    private Throwable runDecorated() {
        try {
            decorated.run();
            return null;
        } catch (Throwable t) {
            return t;
        }
    }

    private Throwable calculateThrowableToThrow(Throwable primaryThrowable, Throwable secondaryThrowable) {
        if (primaryThrowable == null && secondaryThrowable == null) {
            return null;
        }
        if (primaryThrowable == null) {
            return secondaryThrowable;
        }
        if (secondaryThrowable == null) {
            return primaryThrowable;
        }
        primaryThrowable.addSuppressed(secondaryThrowable);
        return primaryThrowable;
    }

    private void runAfterAction(Throwable beforeAndDecoratedThrowable, AfterRunnableAction afterAction) {
        if (afterAction == null) {
            throwCaughtUnchecked(beforeAndDecoratedThrowable);
        }

        Throwable afterActionThrowable = null;
        try {
            afterAction.runAfterRunnable(beforeAndDecoratedThrowable);
        } catch (Throwable t) {
            afterActionThrowable = t;
        }

        Throwable throwableToThrow = calculateThrowableToThrow(beforeAndDecoratedThrowable, afterActionThrowable);
        if (throwableToThrow != null) {
            throwCaughtUnchecked(throwableToThrow);
        }
    }

    // Suppression justification: actually, the cast is likely *not* true. T is bound to RuntimeException at call sites
    // but may also be other unchecked exceptions (i.e. Errors)... or even checked exceptions if and of the Runnables or
    // Actions provided by the user sneakily-throw an exception. That said, the passed throwable is just thrown and is
    // guaranteed to be an unchecked exception... except for the sneaky-throw condition mentioned previously. So, the
    // casting doesn't actually matter, and this method is guaranteed to be as java-compliant as the Runnables or
    // Actions provided to it. Although we are using sneaky-throw ourselves to perform this logic, because of the known
    // call sites and conditions under which they're called, this usually-anti-pattern is justified because of the
    // convenience of factoring-out re-throw logic rather than confining it to a given catch block.
    @SuppressWarnings("unchecked")
    private <T extends Throwable> void throwCaughtUnchecked(Throwable throwable) throws T {
        throw (T) throwable;
    }
}
