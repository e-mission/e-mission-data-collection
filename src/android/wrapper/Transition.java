package edu.berkeley.eecs.emission.cordova.tracker.wrapper;

/**
 * Created by shankari on 7/12/15.
 */
public class Transition {
    private String currState;
    private String transition;
    // Should we put newState in here as well?
    // If so, we will need to change the location of the save

    public Transition() {}
    public Transition(String currState, String transition) {
        this.currState = currState;
        this.transition = transition;
    }
}
