package io.github.graydavid.runnabledecoratingexecutor;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;

public class RunnableAdornerTest {
    private final RunnableAdorner adorner = mock(RunnableAdorner.class);
    private final Runnable runnable = mock(Runnable.class);
    private final BeforeRunnableAction beforeAction = mock(BeforeRunnableAction.class);
    private final AfterRunnableAction afterAction = mock(AfterRunnableAction.class);
    private final AdornmentFailureObserver observer = mock(AdornmentFailureObserver.class);

    @BeforeEach
    public void setUp() {
        when(adorner.createAdornment(runnable)).thenReturn(beforeAction);
        when(beforeAction.runBeforeRunnable()).thenReturn(afterAction);
    }

    @Test
    public void doNothingDoesNothingAndReturnsActionThatDoesNothingAndReturnsActionThatDoesNothing() {
        RunnableAdorner.doNothing().createAdornment(runnable).runBeforeRunnable().runAfterRunnable(null);
        RunnableAdorner.doNothing().createAdornment(runnable).runBeforeRunnable().runAfterRunnable(new Throwable());

        verifyNoInteractions(runnable);
    }

    @Test
    public void compositeThrowsExceptionGivenNullList() {
        assertThrows(NullPointerException.class, () -> RunnableAdorner.composite(null));
    }

    @Test
    public void compositeThrowsExceptionGivenNullElementInList() {
        assertThrows(NullPointerException.class,
                () -> RunnableAdorner.composite(Arrays.asList((RunnableAdorner) null)));
    }

    @Test
    public void compositeDoesntThrowExceptionGivenNullRunnable() {
        RunnableAdorner.composite(List.of()).createAdornment(null);
    }

    @Test
    public void compositeAllowsEmptyListsAndDoesNothing() {
        RunnableAdorner.composite(List.of()).createAdornment(runnable).runBeforeRunnable().runAfterRunnable(null);
        RunnableAdorner.composite(List.of())
                .createAdornment(runnable)
                .runBeforeRunnable()
                .runAfterRunnable(new Throwable());

        verifyNoInteractions(runnable);
    }

    @Test
    public void compositeRunsThroughSingleAdornerBeforeAfterCycle() {
        RunnableAdorner composite = RunnableAdorner.composite(List.of(adorner));

        verifyNoInteractions(adorner, beforeAction, afterAction);
        BeforeRunnableAction compositeBeforeAction = composite.createAdornment(runnable);

        verify(adorner).createAdornment(runnable);
        verifyNoInteractions(beforeAction, afterAction);
        AfterRunnableAction compositeAfterAction = compositeBeforeAction.runBeforeRunnable();

        verify(beforeAction).runBeforeRunnable();
        verifyNoInteractions(afterAction);
        Throwable throwable = new Throwable();
        compositeAfterAction.runAfterRunnable(throwable);

        verify(afterAction).runAfterRunnable(throwable);
    }

    @Test
    public void compositeRunsThroughMultipleAdornerBeforeAfterCycleDoingAftersInReverse() {
        RunnableAdorner adorner2 = mock(RunnableAdorner.class);
        BeforeRunnableAction beforeAction2 = mock(BeforeRunnableAction.class);
        AfterRunnableAction afterAction2 = mock(AfterRunnableAction.class);
        when(adorner2.createAdornment(runnable)).thenReturn(beforeAction2);
        when(beforeAction2.runBeforeRunnable()).thenReturn(afterAction2);
        Throwable throwable = new Throwable();

        RunnableAdorner.composite(List.of(adorner, adorner2))
                .createAdornment(runnable)
                .runBeforeRunnable()
                .runAfterRunnable(throwable);

        InOrder inOrder = Mockito.inOrder(adorner, adorner2, beforeAction, beforeAction2, afterAction, afterAction2);
        inOrder.verify(adorner).createAdornment(runnable);
        inOrder.verify(adorner2).createAdornment(runnable);
        inOrder.verify(beforeAction).runBeforeRunnable();
        inOrder.verify(beforeAction2).runBeforeRunnable();
        inOrder.verify(afterAction2).runAfterRunnable(throwable);
        inOrder.verify(afterAction).runAfterRunnable(throwable);
    }

    @Test
    public void mostlyFaultTolerantThrowsExceptionGivenArguments() {
        assertThrows(NullPointerException.class, () -> RunnableAdorner.mostlyFaultTolerant(null, observer));
        assertThrows(NullPointerException.class, () -> RunnableAdorner.mostlyFaultTolerant(adorner, null));
    }

    @Test
    public void mostlyFaultTolerantDoesntThrowExceptionGivenNullRunnable() {
        RunnableAdorner.mostlyFaultTolerant(adorner, observer).createAdornment(null);
    }

    @Test
    public void mostlyFaultTolerantRunsThroughSingleAdornerBeforeAfterCycleOnSuccess() {
        RunnableAdorner tolerant = RunnableAdorner.mostlyFaultTolerant(adorner, observer);

        verifyNoInteractions(adorner, beforeAction, afterAction);
        BeforeRunnableAction compositeBeforeAction = tolerant.createAdornment(runnable);

        verify(adorner).createAdornment(runnable);
        verifyNoInteractions(beforeAction, afterAction);
        AfterRunnableAction compositeAfterAction = compositeBeforeAction.runBeforeRunnable();

        verify(beforeAction).runBeforeRunnable();
        verifyNoInteractions(afterAction);
        Throwable throwable = new Throwable();
        compositeAfterAction.runAfterRunnable(throwable);

        verify(afterAction).runAfterRunnable(throwable);
        verifyNoInteractions(observer);
    }

    @Test
    public void mostlyFaultTolerantObservesExceptionsThrownByDecoratedAfterAction() {
        Throwable runnableThrowable = new Throwable();
        Error afterActionError = new Error();
        doThrow(afterActionError).when(afterAction).runAfterRunnable(runnableThrowable);

        RunnableAdorner.mostlyFaultTolerant(adorner, observer)
                .createAdornment(runnable)
                .runBeforeRunnable()
                .runAfterRunnable(runnableThrowable);

        InOrder inOrder = Mockito.inOrder(adorner, beforeAction, observer);
        inOrder.verify(adorner).createAdornment(runnable);
        inOrder.verify(beforeAction).runBeforeRunnable();
        inOrder.verify(observer).observe(afterActionError);
    }

    @Test
    public void mostlyFaultTolerantObservesExceptionsThrownByDecoratedBeforeAction() {
        Throwable runnableThrowable = new Throwable();
        Error beforeActionError = new Error();
        when(beforeAction.runBeforeRunnable()).thenThrow(beforeActionError);

        RunnableAdorner.mostlyFaultTolerant(adorner, observer)
                .createAdornment(runnable)
                .runBeforeRunnable()
                .runAfterRunnable(runnableThrowable);

        InOrder inOrder = Mockito.inOrder(adorner, observer);
        inOrder.verify(adorner).createAdornment(runnable);
        inOrder.verify(observer).observe(beforeActionError);
        verifyNoInteractions(afterAction);
    }

    @Test
    public void mostlyFaultTolerantObservesExceptionsThrownByDecoratedAdorner() {
        Throwable runnableThrowable = new Throwable();
        Error adornerError = new Error();
        when(adorner.createAdornment(runnable)).thenThrow(adornerError);

        RunnableAdorner.mostlyFaultTolerant(adorner, observer)
                .createAdornment(runnable)
                .runBeforeRunnable()
                .runAfterRunnable(runnableThrowable);

        verify(observer).observe(adornerError);
        verifyNoInteractions(beforeAction, afterAction);
    }

    @Test
    public void compositeOfMostlyFaultTolerantThrowsExceptionGivenNullArguments() {
        assertThrows(NullPointerException.class, () -> RunnableAdorner.compositeOfMostlyFaultTolerant(null, observer));
        assertThrows(NullPointerException.class, () -> RunnableAdorner.compositeOfMostlyFaultTolerant(List.of(), null));
    }

    @Test
    public void compositeOfMostlyFaultTolerantThrowsExceptionGivenNullElementInList() {
        assertThrows(NullPointerException.class,
                () -> RunnableAdorner.compositeOfMostlyFaultTolerant(Arrays.asList((RunnableAdorner) null), observer));
    }

    @Test
    public void compositeOfMostlyFaultTolerantDoesntThrowExceptionGivenNullRunnable() {
        RunnableAdorner.compositeOfMostlyFaultTolerant(List.of(), observer).createAdornment(null);
    }

    @Test
    public void compositeOfMostlyFaultTolerantAllowsEmptyListsAndDoesNothing() {
        RunnableAdorner.compositeOfMostlyFaultTolerant(List.of(), observer)
                .createAdornment(runnable)
                .runBeforeRunnable()
                .runAfterRunnable(null);
        RunnableAdorner.compositeOfMostlyFaultTolerant(List.of(), observer)
                .createAdornment(runnable)
                .runBeforeRunnable()
                .runAfterRunnable(new Throwable());

        verifyNoInteractions(runnable);
    }

    @Test
    public void compositeOfMostlyFaultTolerantCreatesACompositeListOfMostlyFaultTolerantAdorners() {
        // One adorner throws...
        Throwable runnableThrowable = new Throwable();
        Error adornerError = new Error();
        when(adorner.createAdornment(runnable)).thenThrow(adornerError);
        // ... and the other doesn't
        RunnableAdorner adorner2 = mock(RunnableAdorner.class);
        BeforeRunnableAction beforeAction2 = mock(BeforeRunnableAction.class);
        AfterRunnableAction afterAction2 = mock(AfterRunnableAction.class);
        when(adorner2.createAdornment(runnable)).thenReturn(beforeAction2);
        when(beforeAction2.runBeforeRunnable()).thenReturn(afterAction2);

        RunnableAdorner.compositeOfMostlyFaultTolerant(List.of(adorner, adorner2), observer)
                .createAdornment(runnable)
                .runBeforeRunnable()
                .runAfterRunnable(runnableThrowable);

        InOrder inOrder = Mockito.inOrder(adorner, adorner2, beforeAction2, afterAction2, observer);
        inOrder.verify(adorner).createAdornment(runnable);
        inOrder.verify(observer).observe(adornerError);
        inOrder.verify(adorner2).createAdornment(runnable);
        inOrder.verify(beforeAction2).runBeforeRunnable();
        inOrder.verify(afterAction2).runAfterRunnable(runnableThrowable);
        verifyNoInteractions(beforeAction, afterAction);
    }
}
