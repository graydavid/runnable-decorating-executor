# Runnable-Decorating-Executor

Runnable-Decorating-Executor is a simple java library that allows the safe decoration of Runnables passed to Executors such that the original Runnable is guaranteed to be run. As far as I'm concerned, this is the only way to decorate a Runnable in this way such that the contract of "execute" is still maintained: specifically, "Executes the given command at some time in the future.".

In normal decoration, the decorating class is given the decorated class and is allowed to run it in any way that it wants. Runnable-Decorating-Executor instead models decoration as an "adornment" process. Specifically, the RunnableAdorner class is still given the decorated Runnable, but instead of decorating it, it simply provides adornments for it. These adornments are BeforeRunnableActions and AfterRunnableActions, which are actions run before and after the decorated Runnable. Meanwhile, the RunnableDecoratingExecutor class is an Executor that invokes the RunnableAdorner on Runnable submission. It creates a decorating Runnable that will run the BeforeRunnableAction class, then the decorated Runnable, and then the AfterRunnableAction. The decorating Runnable will, if BeforeRunnableAction fails, temporarily suppress the exception and still run the decorated Runnable, propagating any exceptions only after the decorated Runnable has finished. (Note: Runnable-Decorating-Executor also provides ways to create composite and fault-tolerant versions of these concepts as well.)

The motivation for this class comes from a service that I used to maintain. The code called CompletableFuture#supplyAsync with an Executor and then called CompletableFuture#join on the result. One time, the decorating Runnable once started throwing exceptions before the underlying, original Runnable could be run. The underlying Runnable in this case was what would have completed the CompletableFuture and allowed "join" to resume. Because this command never ran, the Threads that had called "join" were stuck forever. Eventually, the service died as all of the Threads were exhausted.

I was left wondering where I had gone wrong. Was "supplyAsync" somehow fundamentally broken by not handling this edge case? How could it possibly handle such an edge case? Wouldn't even normal/non-CompletableFuture-based Executors run into this same problem (perhaps not the exact same problem, but a similar problem if the decorated command itself were ignored)? Was it an anti-pattern to ever call "join" (or "get", which would have behaved similarly)? Should every call to "join" be proceeded by a call to CompletableFuture#completeOnTimeout (which was introduced in java 9, and the service was java 8, so this really didn't apply to it)? In the end, I decided that what was broken was that the Executor was not fulfilling its contract. That contract states that "run" "Executes the given command at some time in the future." Meanwhile the Executor never did, instead throwing an exception before that could happen. That's where this project's central RunnableDecoratingExecutor class comes into play: by guaranteeing that the command will always be run.

Now, there are some examples in the java library itself that don't follow this line of thinking. One example is ExecutorService#shutdownNow, which says it "halts the processing of waiting tasks". That means commands used in previously-run, successful calls to "execute" will never be run. Another example is DiscardOldestPolicy and DiscardPolicy, which are policies run during ThreadPoolExecutor#execute that discard either previously successfully-submitted commands or the current command silently. Yet another example is ThreadPoolExecutor#beforeExecute, a similar concept as the decorating Runnable, for which ThreadPoolExecutor has no protections in place in case of exception.

If these examples are allowed to exist without any warning on their usage, and if they break calls to "join"/"get", then what gives? One possibility is that these examples are fundamentally broken but just not documented yet and should be considered anti-patterns. Another possibility is that these examples are only supposed to be used as cudgels in extreme, program-ending, the-jvm-is-going-to-shutdown-anyway situations (which I can buy for the first example but not the others). The last possibility I can think of is that programmers are supposed to know when these kind of examples are in play and should not use "join" or "get" when that's true (which is suspicious and brittle, to me, for large programs where there's a large disconnect between how an Executor was created and where it's used vs. how easy it is to modify and break for these kinds of situations). Either way, these kind of examples exist, and programmers must be prepared to handle it.

As general best practices, I think code should protect itself in layers:
* Code should only call "join" or "get" when they know that the Completable/Future is already done. One easy way to do this is to set a timeout on the call. <br>
* Code should not use the examples that I argue above break the contract of the Executor's "execute" method.
* Code should set up Thread#setDefaultUncaughtExceptionHandler to log uncaught exceptions (which is what happened in the CompletableFuture case, although may not be applicable in the normal/non-CompletableFuture case).
* Code should only use this class, or similar concepts, to decorate a Runnable as a part of submitting it to an Executor.

## Adding this project to your build

This project follows [semantic versioning](https://semver.org/). It's currently available via the Maven snapshot repository only as version 0.0.1-SNAPSHOT. The "0.0.1" part is because it's still in development, waiting for feedback or sufficient usage until its first official release. The "SNAPSHOT" part is because no one has requested a stable, non-SNAPSHOT development version. If you need a non-SNAPSHOT development version, feel free to reach out, and I can build this project into the Maven central repository.

## Usage

This project requires JDK version 11 or higher.

The below example shows how to create a RunnableDecoratingExecutor to propagate thread state from the origin thread (where the Runnable is submitted to the Executor) to the destination thread (where the decorated runnable is actually run) using this library. This example is only illustrative. See the `thread-state-propagation-runnable-adorner` library, which provides much-more-robust, built-in support for this usecase.

```java
/** Defines a way to interact with the current thread's state. */
public interface ThreadStateManager<T> {
    /** Retrieves the state associated with the current thread. */
    T getCurrentThreadState();

    /** Sets the state associated with the current thread. */
    void setCurrentThreadState(T state);
}

ThreadStateManager<Integer> stateManager = ...; //How this is done is up to clients
RunnableAdorner adorner = runnable -> {
    //In RunnableAdorner: Read the origin thread's state
    Integer originThreadState = stateManager.getCurrentThreadState();
    return () -> {
        //In BeforeRunnableAction: Save the destination thread's original state...
        Integer destinationOriginalThreadState = stateManager.getCurrentThreadState();
        //... and Propagate the origin thread's state
        stateManager.setCurrentThreadState(originThreadState);
        return throwable -> {
            //In AfterRunnableAction: Restore the destination thread's state
            stateManager.setCurrentThreadState(destinationOriginalThreadState);
        };
    };
};
Executor threadExecutor = ...; //How this is done is up to clients
Executor safeDecoratingExecutor = RunnableDecoratingExecutor.from(threadExecutor, adorner);
```

Although this example is a simple adornment process, Runnable-Decorating-Executor also provides a way to define composite RunnableAdorners based on a list of RunnableAdorners (i.e. the composite design pattern) as well as fault-tolerant RunnableAdorners (which can suppress failures).

##Contributions

Contributions are welcome! See the [graydavid-parent](https://github.com/graydavid/graydavid-parent) project for details.