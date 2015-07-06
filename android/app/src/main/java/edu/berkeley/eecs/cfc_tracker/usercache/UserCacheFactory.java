package edu.berkeley.eecs.cfc_tracker.usercache;

/**
 * Created by shankari on 7/5/15.
 */
public class UserCacheFactory {
    /**
     * This is the factory method that allows the designer to choose between
     * various backends. Currently, we have only one backend implemented,
     * so we return it.
     */
    public static UserCache getUserCache() {
        return new BuiltinUserCache();
    }
}
