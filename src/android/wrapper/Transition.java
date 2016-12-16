package edu.berkeley.eecs.emission.cordova.tracker.wrapper;

/**
 * Created by shankari on 7/12/15.
 */
public class Transition {
    public String getTransition() {
        return transition;
    }

    public String getCurrState() {
        return currState;
    }

    public double getTs() {
        return ts;
    }

    private String currState;
    private String transition;

    private double ts;
    // Should we put newState in here as well?
    // If so, we will need to change the location of the save

    public Transition() {}
    public Transition(String currState, String transition, double ts) {
        this.currState = currState;
        this.transition = transition;
        this.ts = ts;
    }
}
