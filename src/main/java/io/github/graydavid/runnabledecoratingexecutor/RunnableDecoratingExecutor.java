/*
 * Copyright 2021 David Gray
 * 
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.graydavid.runnabledecoratingexecutor;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor.DiscardOldestPolicy;
import java.util.concurrent.ThreadPoolExecutor.DiscardPolicy;

/**
 * Allows decoration of the Runnable passed to {@link #execute(Runnable)} such that the original Runnable is guaranteed
 * to be run as a part of {@link #execute(Runnable)}. As far as I'm concerned, this is the only way to decorate a
 * Runnable in this way such that the contract of "execute" is still maintained: "Executes the given command at some
 * time in the future.".
 * 
 * In normal decoration, the decorating class is given the decorated class and is allowed to run it in any way that it
 * wants. Runnable-Decorating-Executor instead models decoration as an "adornment" process. Specifically, the
 * RunnableAdorner class is still given the decorated Runnable, but instead of decorating it, it simply provides
 * adornments for it. These adornments are BeforeRunnableActions and AfterRunnableActions, which are actions run before
 * and after the decorated Runnable. Meanwhile, the RunnableDecoratingExecutor class is an Executor that invokes the
 * RunnableAdorner on Runnable submission. It creates a decorating Runnable that will run the BeforeRunnableAction
 * class, then the decorated Runnable, and then the AfterRunnableAction. The decorating Runnable will, if
 * BeforeRunnableAction fails, temporarily suppress the exception and still run the decorated Runnable, propagating any
 * exceptions only after the decorated Runnable has finished.
 * 
 * The motivation for this class comes from a service that I used to maintain. The code called
 * {@link CompletableFuture#supplyAsync(java.util.function.Supplier, Executor)} with an Executor and then called
 * {@link CompletableFuture#join()} on the result. One time, the decorating Runnable once started throwing exceptions
 * before the underlying, original Runnable could be run. The underlying Runnable in this case was what would have
 * completed the CompletableFuture and allowed "join" to resume. Because this command never ran, the Threads that had
 * called "join" were stuck forever. Eventually, the service died as all of the Threads were exhausted.
 * 
 * I was left wondering where I had gone wrong. Was "supplyAsync" somehow fundamentally broken by not handling this edge
 * case? How could it possibly handle such an edge case? Wouldn't even normal/non-CompletableFuture-based Executors run
 * into this same problem (perhaps not the exact same problem, but a similar problem if the decorated command itself
 * were ignored)? Was it an anti-pattern to ever call "join" (or "get", which would have behaved similarly)? Should
 * every call to "join" be proceeded by a call to
 * {@link CompletableFuture#completeOnTimeout(Object, long, java.util.concurrent.TimeUnit)} (which was introduced in
 * java 9, and the service was java 8, so this really didn't apply to it)? In the end, I decided that what was broken
 * was that the Executor was not fulfilling its contract. That contract states that "run" "Executes the given command at
 * some time in the future." Meanwhile the Executor never did, instead throwing an exception before that could happen.
 * That's where RunnableDecoratingExecutor comes into play: by guaranteeing that the command will always be run.
 * 
 * Now, there are some examples in the java library itself that don't follow this line of thinking. One example is
 * {@link ExecutorService#shutdownNow()}, which says it "halts the processing of waiting tasks". That means commands
 * used in previously-run, successful calls to "execute" will never be run. Another example is
 * {@link DiscardOldestPolicy} and {@link DiscardPolicy}, which are policies run during
 * {@link ThreadPoolExecutor#execute(Runnable)} that discard either previously successfully-submitted commands or the
 * current command silently. Yet another example is {@link ThreadPoolExecutor#beforeExecute}, a similar concept as the
 * decorating Runnable, for which ThreadPoolExecutor has no protections in place in case of exception.
 * 
 * If these examples are allowed to exist without any warning on their usage, and if they break calls to "join"/"get",
 * then what gives? One possibility is that these examples are fundamentally broken but just not documented yet and
 * should be considered anti-patterns. Another possibility is that these examples are only supposed to be used as
 * cudgels in extreme, program-ending, the-jvm-is-going-to-shutdown-anyway situations (which I can buy for the first
 * example but not the others). The last possibility I can think of is that programmers are supposed to know when these
 * kind of examples are in play and should not use "join" or "get" when that's true (which is suspicious and brittle, to
 * me, for large programs where there's a large disconnect between how an Executor was created and where it's used vs.
 * how easy it is to modify and break for these kinds of situations). Either way, these kind of examples exist, and
 * programmers must be prepared to handle it.
 * 
 * As general best practices, I think code should protect itself in layers:<br>
 * * Code should only call "join" or "get" when they know that the Completable/Future is already done. One easy way to
 * do this is to set a timeout on the call. <br>
 * * Code should not use the examples that I argue above break the contract of the Executor's "execute" method.<br>
 * * Code should set up {@link Thread#setDefaultUncaughtExceptionHandler(java.lang.Thread.UncaughtExceptionHandler)} to
 * log uncaught exceptions (which is what happened in the CompletableFutureCase, although may not be applicable in the
 * normal/non-CompletableFuture case).<br>
 * * Code should only use this class, or similar concepts, to decorate a Runnable as a part of submitting it to an
 * Executor.
 */
public class RunnableDecoratingExecutor implements Executor {
    private final Executor decorated;
    private final RunnableAdorner adorner;

    private RunnableDecoratingExecutor(Executor decorated, RunnableAdorner adorner) {
        this.decorated = Objects.requireNonNull(decorated);
        this.adorner = Objects.requireNonNull(adorner);
    }

    public static Executor from(Executor decorated, RunnableAdorner adorner) {
        return new RunnableDecoratingExecutor(decorated, adorner);
    }

    /**
     * Decorates the command with adornments and then passes that decorated command to the decorated Executor used to
     * construct this object.
     * 
     * When the decorated command is run, it will run the BeforeRunnableAction, the decorated Runnable, and then the
     * generated AfterRunnableAction. If BeforeRunnableAction throws an exception, the decorated Runnable will still be
     * run (but obviously not the generated AfterRunnableAction, since BeforeRunnableAction failed to generate that).
     * This is true even if the BeforeRunnableAction returns an disallowed null AfterRunnableAction, which will be
     * treated as if BeforeRunnableAction threw a NullPointerException.
     * 
     * @throws * if the decorated Runnable throws an exception, that exception will be thrown from here; any exception
     *         thrown from the BeforeRunnableAction or generated AfterRunnableAction will be added as suppressed
     *         exceptions.
     * 
     *         Otherwise, if BeforeRunnableAction throws an exception, that exception will be thrown from here; any
     *         exception thrown from the generated AfterRunnableAction will be added as a suppressed exception.
     * 
     *         Otherwise, if AfterRunnableAction throws an exception, that exception will be thrown from here.
     */
    @Override
    public void execute(Runnable command) {
        BeforeRunnableAction beforeAction = adorner.createAdornment(command);
        Runnable adorned = new GuaranteedDecoratingRunnable(command, beforeAction);
        decorated.execute(adorned);
    }
}
