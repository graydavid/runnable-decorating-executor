package io.github.graydavid.runnabledecoratingexecutor;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.arrayContaining;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.concurrent.Executor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;

public class RunnableDecoratingExecutorTest {
    private final RunnableAdorner adorner = mock(RunnableAdorner.class);
    private final Executor executor = RunnableDecoratingExecutor.from(Runnable::run, adorner);
    private final Runnable decoratedRunnable = mock(Runnable.class);
    private final BeforeRunnableAction beforeAction = mock(BeforeRunnableAction.class);
    private final AfterRunnableAction afterAction = mock(AfterRunnableAction.class);

    @BeforeEach
    public void setUp() {
        when(adorner.createAdornment(decoratedRunnable)).thenReturn(beforeAction);
        when(beforeAction.runBeforeRunnable()).thenReturn(afterAction);
    }

    @Test
    public void constructorThrowsExceptionGivenNullArguments() {
        assertThrows(NullPointerException.class, () -> RunnableDecoratingExecutor.from(null, adorner));
        assertThrows(NullPointerException.class, () -> RunnableDecoratingExecutor.from(Runnable::run, null));
    }

    @Test
    public void throwsExceptionGivenNullCommand() {
        assertThrows(NullPointerException.class, () -> executor.execute(null));
    }

    @Test
    public void throwsExceptionIfAdornerProducesNullBeforeAction() {
        when(adorner.createAdornment(decoratedRunnable)).thenReturn(null);

        assertThrows(NullPointerException.class, () -> executor.execute(decoratedRunnable));
    }

    @Test
    public void callsBeforeDecoratedAfterOnSuccess() {
        verifyNoInteractions(adorner);

        executor.execute(decoratedRunnable);

        InOrder inOrder = Mockito.inOrder(beforeAction, decoratedRunnable, afterAction);
        inOrder.verify(beforeAction).runBeforeRunnable();
        inOrder.verify(decoratedRunnable).run();
        inOrder.verify(afterAction).runAfterRunnable(null);
    }

    @Test
    public void callsBeforeDecoratedAfterOnEveryCall() {
        executor.execute(decoratedRunnable);
        executor.execute(decoratedRunnable);
        executor.execute(decoratedRunnable);

        verify(adorner, times(3)).createAdornment(decoratedRunnable);
        verify(beforeAction, times(3)).runBeforeRunnable();
        verify(decoratedRunnable, times(3)).run();
        verify(afterAction, times(3)).runAfterRunnable(null);
    }

    @Test
    public void callsAdornerBeforePassingOnToExecutor() {
        Executor decoratedExecutor = mock(Executor.class);
        Executor executor = RunnableDecoratingExecutor.from(decoratedExecutor, adorner);

        executor.execute(decoratedRunnable);

        InOrder inOrder = Mockito.inOrder(adorner, decoratedExecutor);
        inOrder.verify(adorner).createAdornment(decoratedRunnable);
        inOrder.verify(decoratedExecutor).execute(any());
    }

    @Test
    public void propagatesUncheckedRunnableFailuresAfterRunningAfterAction() {
        Error error = new Error();
        doThrow(error).when(decoratedRunnable).run();

        Error thrown = assertThrows(Error.class, () -> executor.execute(decoratedRunnable));

        assertThat(thrown, is(error));
        verify(afterAction).runAfterRunnable(error);
    }

    @Test
    public void propagatesSneakyThrowRunnableFailuresAfterRunningAfterAction() {
        Throwable throwable = new Throwable();
        Runnable decoratedRunnable = () -> {
            sneakyThrow(throwable);
        };
        when(adorner.createAdornment(decoratedRunnable)).thenReturn(beforeAction);

        Throwable thrown = assertThrows(Throwable.class, () -> executor.execute(decoratedRunnable));

        assertThat(thrown, is(throwable));
        verify(afterAction).runAfterRunnable(throwable);
    }

    // Suppress justification: cast is not justified but doesn't matter given that throwable is just thrown
    @SuppressWarnings("unchecked")
    private <T extends Throwable> void sneakyThrow(Throwable throwable) throws T {
        throw (T) throwable;
    }

    @Test
    public void stillRunsRunnableOnUncheckedBeforeActionFailureButPropagatesFailure() {
        Error error = new Error();
        when(beforeAction.runBeforeRunnable()).thenThrow(error);

        Error thrown = assertThrows(Error.class, () -> executor.execute(decoratedRunnable));

        assertThat(thrown, is(error));
        verify(decoratedRunnable).run();
    }

    @Test
    public void treatsBeforeActionProvidingNullAfterActionAsNullPointerException() {
        when(beforeAction.runBeforeRunnable()).thenReturn(null);

        Throwable thrown = assertThrows(Throwable.class, () -> executor.execute(decoratedRunnable));

        assertThat(thrown, instanceOf(NullPointerException.class));
        assertThat(thrown.getMessage(), containsString("BeforeRunnableAction returned a null AfterRunnableAction"));
        verify(decoratedRunnable).run();
    }

    @Test
    public void stillRunsRunnableOnSneakyThrowBeforeActionFailureButPropagatesFailure() {
        Throwable throwable = new Throwable();
        BeforeRunnableAction beforeAction = () -> {
            sneakyThrow(throwable);
            return null;
        };
        when(adorner.createAdornment(decoratedRunnable)).thenReturn(beforeAction);

        Throwable thrown = assertThrows(Throwable.class, () -> executor.execute(decoratedRunnable));

        assertThat(thrown, is(throwable));
        verify(decoratedRunnable).run();
    }

    @Test
    public void propagatesUncheckedAfterActionFailures() {
        Error error = new Error();
        doThrow(error).when(afterAction).runAfterRunnable(null);

        Error thrown = assertThrows(Error.class, () -> executor.execute(decoratedRunnable));

        assertThat(thrown, is(error));
    }

    @Test
    public void propagatesSneakyThrowAfterActionFailures() {
        Throwable throwable = new Throwable();
        AfterRunnableAction afterAction = t -> {
            sneakyThrow(throwable);
        };
        when(beforeAction.runBeforeRunnable()).thenReturn(afterAction);

        Throwable thrown = assertThrows(Throwable.class, () -> executor.execute(decoratedRunnable));

        assertThat(thrown, is(throwable));
    }

    @Test
    public void addsAfterActionFailuresAsSuppressedToRunnableFailures() {
        Error runnableError = new Error("runnable");
        doThrow(runnableError).when(decoratedRunnable).run();
        Error afterActionError = new Error("after-action");
        doThrow(afterActionError).when(afterAction).runAfterRunnable(runnableError);

        Error thrown = assertThrows(Error.class, () -> executor.execute(decoratedRunnable));

        assertThat(thrown, is(runnableError));
        assertThat(thrown.getSuppressed(), arrayContaining(afterActionError));
    }

    @Test
    public void addsBeforeActionFailuresAsSuppressedToRunnableFailures() {
        Error beforeActionError = new Error("before-action");
        doThrow(beforeActionError).when(beforeAction).runBeforeRunnable();
        Error runnableError = new Error("runnable");
        doThrow(runnableError).when(decoratedRunnable).run();

        Error thrown = assertThrows(Error.class, () -> executor.execute(decoratedRunnable));

        assertThat(thrown, is(runnableError));
        assertThat(thrown.getSuppressed(), arrayContaining(beforeActionError));
    }

    @Test
    public void addsNullBeforeActionFailuresAsSuppressedToRunnableFailures() {
        when(beforeAction.runBeforeRunnable()).thenReturn(null);
        Error runnableError = new Error("runnable");
        doThrow(runnableError).when(decoratedRunnable).run();

        Error thrown = assertThrows(Error.class, () -> executor.execute(decoratedRunnable));

        assertThat(thrown, is(runnableError));
        assertThat(thrown.getSuppressed(), arrayContaining(instanceOf(NullPointerException.class)));
        assertThat(thrown.getSuppressed()[0].getMessage(),
                containsString("BeforeRunnableAction returned a null AfterRunnableAction"));
    }
}
