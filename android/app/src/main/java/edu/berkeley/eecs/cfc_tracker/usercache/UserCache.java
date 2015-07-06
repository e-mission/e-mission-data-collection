package edu.berkeley.eecs.cfc_tracker.usercache;

import org.json.JSONObject;

/**
 * Abstract superclass for the client side component of the user cache.
 */
public interface UserCache {

    class TimeQuery {
        String key;
        long startTs;
        long endTs;

        public TimeQuery(String key, long startTs, long endTs) {
            this.key = key;
            this.startTs = startTs;
            this.endTs = endTs;
        }
    }

    public abstract void putMessage(String key, JSONObject value);

    public abstract void putReadWriteDocument(String key, JSONObject value);

    // TODO: Should this return a JSON object or an actual object retrieved via gson?
    /**
     * Return the document that matches the specified key.
     */
    public abstract JSONObject getDocument(String key);

    /**
     * Delete documents that match the specified time query.
     * This allows us to support eventual consistency without locking.
     */
    public abstract void clearMessages(TimeQuery tq);
}
