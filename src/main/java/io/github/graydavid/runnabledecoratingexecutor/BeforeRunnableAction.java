/*
 * Copyright 2021 David Gray
 * 
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.graydavid.runnabledecoratingexecutor;

/** Defines a way to run actions before running a decorated Runnable as part of submitting it to an executor. */
@FunctionalInterface
public interface BeforeRunnableAction {
    AfterRunnableAction runBeforeRunnable();

    /** Returns a BeforeRunnableAction that does nothing: it only returns a do-nothing AfterRunnableAction. */
    static BeforeRunnableAction doNothing() {
        return BeforeRunnableActions.doNothing();
    }
}


class BeforeRunnableActions {
    private BeforeRunnableActions() {}

    public static BeforeRunnableAction doNothing() {
        return DO_NOTHING;
    }

    private static final BeforeRunnableAction DO_NOTHING = () -> AfterRunnableAction.doNothing();
}
