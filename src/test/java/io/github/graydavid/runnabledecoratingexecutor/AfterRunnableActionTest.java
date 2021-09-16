package io.github.graydavid.runnabledecoratingexecutor;

import org.junit.jupiter.api.Test;

public class AfterRunnableActionTest {

    @Test
    public void doNothingDoesNothing() {
        AfterRunnableAction.doNothing().runAfterRunnable(null);
        AfterRunnableAction.doNothing().runAfterRunnable(new Throwable());
    }
}
