package io.github.graydavid.runnabledecoratingexecutor;

import org.junit.jupiter.api.Test;

public class BeforeRunnableActionTest {

    @Test
    public void doNothingDoesNothingAndReturnsActionThatDoesNothing() {
        BeforeRunnableAction.doNothing().runBeforeRunnable().runAfterRunnable(null);
        BeforeRunnableAction.doNothing().runBeforeRunnable().runAfterRunnable(new Throwable());
    }
}
