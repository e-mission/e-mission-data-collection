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

    /**
      The value should be an object that is serializable using GSON.
      Most objects are, but it would be good to confirm, probably by
      adding a serialization/deserialization test to WrapperTest.
     */
    public abstract void putMessage(String key, Object value);

    public abstract void putReadWriteDocument(String key, Object value);

    // TODO: Should this return a JSON object or an actual object retrieved via gson?

    public abstract <T> T[] getMessagesForInterval(String key, TimeQuery tq, Class<T> classOfT);
    public abstract <T> T[] getLastMessages(String key, int nEntries, Class<T> classOfT);

        /**
         * Return the document that matches the specified key.
         * The class of T needs to be passed in, and an appropriate type will be reconstructed
         * and returned.
         */
    public abstract <T> T getDocument(String key, Class<T> classOfT);
    public abstract <T> T getUpdatedDocument(String key, Class<T> classOfT);

    /**
     * Delete documents that match the specified time query.
     * This allows us to support eventual consistency without locking.
     */
    public abstract void clearMessages(TimeQuery tq);
}
