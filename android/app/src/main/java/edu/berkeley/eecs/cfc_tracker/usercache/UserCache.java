package edu.berkeley.eecs.cfc_tracker.usercache;

/**
 * Abstract superclass for the client side component of the user cache.
 */
public interface UserCache {

    class TimeQuery {
        int keyRes;
        double startTs;
        double endTs;

        public TimeQuery(int keyRes, double startTs, double endTs) {
            this.keyRes = keyRes;
            this.startTs = startTs;
            this.endTs = endTs;
        }

        public String toString() {
            return startTs + " < " + keyRes + " < " + endTs;
        }
    }

    /**
      The value should be an object that is serializable using GSON.
      Most objects are, but it would be good to confirm, probably by
      adding a serialization/deserialization test to WrapperTest.
     */
    public abstract void putSensorData(int keyRes, Object value);

    public abstract void putMessage(int keyRes, Object value);

    public abstract void putReadWriteDocument(int keyRes, Object value);

    // TODO: Should this return a JSON object or an actual object retrieved via gson?

    public abstract <T> T[] getMessagesForInterval(int keyRes, TimeQuery tq, Class<T> classOfT);
    public abstract <T> T[] getSensorDataForInterval(int keyRes, TimeQuery tq, Class<T> classOfT);

    public abstract <T> T[] getLastMessages(int keyRes, int nEntries, Class<T> classOfT);
    public abstract <T> T[] getLastSensorData(int keyRes, int nEntries, Class<T> classOfT);

        /**
         * Return the document that matches the specified key.
         * The class of T needs to be passed in, and an appropriate type will be reconstructed
         * and returned.
         */
    public abstract <T> T getDocument(int keyRes, Class<T> classOfT);
    public abstract <T> T getUpdatedDocument(int keyRes, Class<T> classOfT);

    /**
     * Delete documents that match the specified time query.
     * This allows us to support eventual consistency without locking.
     */
    public abstract void clearEntries(TimeQuery tq);
}
