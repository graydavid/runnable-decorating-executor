/*
 * Copyright 2021 David Gray
 * 
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.graydavid.runnabledecoratingexecutor;

/**
 * Defines a way to observe/suppress failures encountered during the adornment part of running a decorated Runnable.
 * That is, this class only observes failures in the adornments, not in the Runnable that they decorate.
 */
@FunctionalInterface
public interface AdornmentFailureObserver {
    /**
     * Observes a failure in an adornment. Because adornments throw only unchecked exceptions, the failure will only
     * ever be an unchecked exception (i.e. an Error or a RuntimeException)... unless you use "sneaky throws" in your
     * adornment.
     */
    void observe(Throwable throwable);

    /**
     * Creates an AdornmentFailureObserver that decorates another observer, swallowing any exceptions from it. Clients
     * should be wary of using this, as it's destroying useful information... but perhaps as a last resort, that makes
     * sense. It's your call.
     */
    static AdornmentFailureObserver faultSwallowing(AdornmentFailureObserver decorated) {
        return AdornmentFailureObservers.faultSwallowing(decorated);
    }
}


class AdornmentFailureObservers {
    private AdornmentFailureObservers() {}

    public static AdornmentFailureObserver faultSwallowing(AdornmentFailureObserver decorated) {
        return throwable -> {
            try {
                decorated.observe(throwable);
            } catch (Throwable t) {
                // Do nothing
            }
        };
    }
}
