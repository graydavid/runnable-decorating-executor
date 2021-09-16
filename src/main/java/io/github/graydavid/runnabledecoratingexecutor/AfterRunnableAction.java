/*
 * Copyright 2021 David Gray
 * 
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.graydavid.runnabledecoratingexecutor;

/** Defines a way to run actions after running a decorated Runnable as part of submitting it to an executor. */
@FunctionalInterface
public interface AfterRunnableAction {
    /**
     * @param runnableThrowable the throwable thrown by the Runnable when running it. Will be null if the Runnable was
     *        successful.
     */
    void runAfterRunnable(Throwable runnableThrowable);

    /** Returns an AfterRunnableAction that does nothing. */
    static AfterRunnableAction doNothing() {
        return AfterRunnableActions.doNothing();
    }
}


class AfterRunnableActions {
    private AfterRunnableActions() {}

    public static AfterRunnableAction doNothing() {
        return DO_NOTHING;
    }

    private static final AfterRunnableAction DO_NOTHING = throwable -> {
    };
}
