package cucumber.runtime.junit;

import cucumber.api.PendingException;
import cucumber.api.Result;
import cucumber.api.event.EventHandler;
import cucumber.api.event.TestStepFinished;
import cucumber.api.event.TestStepStarted;
import cucumber.runner.EventBus;
import cucumber.runtime.junit.PickleRunners.PickleRunner;
import gherkin.pickles.PickleStep;
import org.junit.internal.AssumptionViolatedException;
import org.junit.runner.Description;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.model.MultipleFailureException;

import static cucumber.runtime.Runtime.isPending;

public class JUnitReporter {

    private final boolean strict;
    private final JUnitOptions junitOptions;

    TestNotifier stepNotifier; // package-private for testing
    private PickleRunner executionUnitRunner;
    private RunNotifier runNotifier;
    TestNotifier executionUnitNotifier; // package-private for testing
    private boolean failedStep;
    private boolean ignoredStep;
    private final EventHandler<TestStepStarted> testStepStartedHandler = new EventHandler<TestStepStarted>() {

        @Override
        public void receive(TestStepStarted event) {
            if (!event.testStep.isHook()) {
                handleStepStarted(event.testStep.getPickleStep());
            }
        }

    };
    private final EventHandler<TestStepFinished> testStepFinishedHandler = new EventHandler<TestStepFinished>() {

        @Override
        public void receive(TestStepFinished event) {
            if (event.testStep.isHook()) {
                handleHookResult(event.result);
            } else {
                handleStepResult(event.result);
            }
        }

    };

    public JUnitReporter(EventBus bus, boolean strict, JUnitOptions junitOption) {
        this.strict = strict;
        this.junitOptions = junitOption;
        bus.registerHandlerFor(TestStepStarted.class, testStepStartedHandler);
        bus.registerHandlerFor(TestStepFinished.class, testStepFinishedHandler);
    }

    void startExecutionUnit(PickleRunner executionUnitRunner, RunNotifier runNotifier) {
        this.executionUnitRunner = executionUnitRunner;
        this.runNotifier = runNotifier;
        this.stepNotifier = null;
        this.failedStep = false;
        this.ignoredStep = false;

        executionUnitNotifier = new EachTestNotifier(runNotifier, executionUnitRunner.getDescription());
        executionUnitNotifier.fireTestStarted();
    }

    void finishExecutionUnit() {
        if (ignoredStep && !failedStep) {
            executionUnitNotifier.fireTestIgnored();
        }
        executionUnitNotifier.fireTestFinished();
    }

    void handleStepStarted(PickleStep step) {
        if (stepNotifications()) {
            Description description = executionUnitRunner.describeChild(step);
            stepNotifier = new EachTestNotifier(runNotifier, description);
        } else {
            stepNotifier = new NoTestNotifier();
        }
        if (junitOptions.allowStartedIgnored()) {
            stepNotifier.fireTestStarted();
        }
    }

    boolean stepNotifications() {
        return junitOptions.stepNotifications();
    }

    void handleStepResult(Result result) {
        Throwable error = result.getError();
        if (result.is(Result.Type.SKIPPED)) {
            stepNotifier.fireTestIgnored();
        } else if (isPendingOrUndefined(result)) {
            addFailureOrIgnoreStep(result);
        } else {
            if (stepNotifier != null) {
                //Should only fireTestStarted if not ignored
                if (!junitOptions.allowStartedIgnored()) {
                    stepNotifier.fireTestStarted();
                }
                if (error != null) {
                    stepNotifier.addFailure(error);
                }
                stepNotifier.fireTestFinished();
            }
            if (error != null) {
                failedStep = true;
                executionUnitNotifier.addFailure(error);
            }
        }
    }

    void handleHookResult(Result result) {
        if (result.is(Result.Type.FAILED) || (strict && isPending(result.getError()))) {
            executionUnitNotifier.addFailure(result.getError());
        } else if (isPending(result.getError())) {
            ignoredStep = true;
        }
    }

    boolean useFilenameCompatibleNames() {
        return junitOptions.filenameCompatibleNames();
    }

    private boolean isPendingOrUndefined(Result result) {
        Throwable error = result.getError();
        return result.is(Result.Type.UNDEFINED) || isPending(error);
    }

    private void addFailureOrIgnoreStep(Result result) {
        if (strict) {
            if (!junitOptions.allowStartedIgnored()) {
                stepNotifier.fireTestStarted();
            }
            addFailure(result);
            stepNotifier.fireTestFinished();
        } else {
            ignoredStep = true;
            stepNotifier.fireTestIgnored();
        }
    }

    private void addFailure(Result result) {
        Throwable error = result.getError();
        if (error == null) {
            error = new PendingException();
        }
        failedStep = true;
        stepNotifier.addFailure(error);
        executionUnitNotifier.addFailure(error);
    }

    private interface TestNotifier {

        void fireTestStarted();

        void addFailure(Throwable error);

        void fireTestIgnored();

        void fireTestFinished();
    }


    private static final class NoTestNotifier implements TestNotifier {

        @Override
        public void fireTestStarted() {
            // Does nothing
        }

        @Override
        public void addFailure(Throwable error) {
            // Does nothing
        }

        @Override
        public void fireTestIgnored() {
            // Does nothing
        }

        @Override
        public void fireTestFinished() {
            // Does nothing
        }
    }

    static class EachTestNotifier implements TestNotifier {
        private final RunNotifier notifier;

        private final Description description;

        EachTestNotifier(RunNotifier notifier, Description description) {
            this.notifier = notifier;
            this.description = description;
        }

        public void addFailure(Throwable targetException) {
            if (targetException instanceof MultipleFailureException) {
                addMultipleFailureException((MultipleFailureException) targetException);
            } else if (targetException instanceof AssumptionViolatedException) {
                addFailedAssumption((AssumptionViolatedException) targetException);
            } else {
                notifier.fireTestFailure(new Failure(description, targetException));
            }
        }

        private void addMultipleFailureException(MultipleFailureException mfe) {
            for (Throwable each : mfe.getFailures()) {
                addFailure(each);
            }
        }

        private void addFailedAssumption(AssumptionViolatedException e) {
            notifier.fireTestAssumptionFailed(new Failure(description, e));
        }

        public void fireTestFinished() {
            notifier.fireTestFinished(description);
        }

        public void fireTestStarted() {
            notifier.fireTestStarted(description);
        }

        public void fireTestIgnored() {
            notifier.fireTestIgnored(description);
        }
    }
}
