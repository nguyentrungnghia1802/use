package org.tzi.use.plugins.jacamo.codegrounded;

import org.tzi.use.main.Session;

/** The only native activation boundary; callers invoke it after every build/validation/export gate succeeds. */
public final class NativeUseSessionActivator {
    public void activate(Session session, CodeGroundedNativePipeline.Result result) {
        java.util.Objects.requireNonNull(session, "session");
        java.util.Objects.requireNonNull(result, "result");
        if (!result.state().structureValid() || !result.state().invariantsValid())
            throw new IllegalStateException("NATIVE_SESSION_ACTIVATION_GATE_FAILED");
        if (result.state().system().model() != result.model().model())
            throw new IllegalStateException("NATIVE_SESSION_MODEL_IDENTITY_DIVERGED");
        if (javax.swing.SwingUtilities.isEventDispatchThread()) {
            session.setSystem(result.state().system());
        } else {
            try {
                javax.swing.SwingUtilities.invokeAndWait(() -> session.setSystem(result.state().system()));
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("NATIVE_SESSION_ACTIVATION_INTERRUPTED", error);
            } catch (java.lang.reflect.InvocationTargetException error) {
                throw new IllegalStateException("NATIVE_SESSION_ACTIVATION_FAILED", error.getCause());
            }
        }
        if (session.system() != result.state().system())
            throw new IllegalStateException("NATIVE_SESSION_SYSTEM_IDENTITY_DIVERGED");
    }
}
