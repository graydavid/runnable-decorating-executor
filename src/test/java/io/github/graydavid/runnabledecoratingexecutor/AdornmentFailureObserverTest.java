package io.github.graydavid.runnabledecoratingexecutor;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;

public class AdornmentFailureObserverTest {

    @Test
    public void faultSwallowingSwallowsExceptionsFromDecorated() {
        AdornmentFailureObserver decorated = throwable -> {
            throw new Error();
        };
        AdornmentFailureObserver observer = AdornmentFailureObserver.faultSwallowing(decorated);

        observer.observe(new Throwable());
    }

    @Test
    public void faultSwallowingAllowsSuccessfulDecorated() {
        AdornmentFailureObserver decorated = mock(AdornmentFailureObserver.class);
        AdornmentFailureObserver observer = AdornmentFailureObserver.faultSwallowing(decorated);
        Error error = new Error();

        observer.observe(error);

        verify(decorated).observe(error);
    }
}
