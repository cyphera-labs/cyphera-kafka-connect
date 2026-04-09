package io.cyphera.kafka.connect;

import io.cyphera.Cyphera;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Thread-safe singleton loader for the Cyphera SDK.
 */
public final class CypheraLoader {

    private static final Logger LOG = Logger.getLogger(CypheraLoader.class.getName());
    private static volatile Cyphera instance;

    private CypheraLoader() {}

    public static Cyphera getInstance() {
        if (instance == null) {
            synchronized (CypheraLoader.class) {
                if (instance == null) {
                    instance = load();
                }
            }
        }
        return instance;
    }

    private static Cyphera load() {
        String path = System.getProperty("cyphera.policy.file");
        if (path == null || path.isEmpty()) {
            path = System.getenv("CYPHERA_POLICY_FILE");
        }
        if (path == null || path.isEmpty()) {
            path = "/etc/cyphera/cyphera.json";
        }

        try {
            Cyphera c = Cyphera.fromFile(path);
            LOG.info("Cyphera loaded from " + path);
            return c;
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Failed to load Cyphera from " + path, e);
            throw new RuntimeException("Failed to initialize Cyphera: " + e.getMessage(), e);
        }
    }
}
