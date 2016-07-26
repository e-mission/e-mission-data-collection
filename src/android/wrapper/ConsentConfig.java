package edu.berkeley.eecs.emission.cordova.tracker.wrapper;

/**
 * Created by shankari on 7/25/16.
 */
public class ConsentConfig {
    public ConsentConfig() {
        this.category = null;
        this.protocol_id = null;
        this.approval_date = null;
    }

    // We don't need any "set" fields because the entire document will be set as a whole
    // using the javascript interface
    public String getApproval_date() {
        return approval_date;
    }

    public String getCategory() {
        return category;
    }

    public String getProtocol_id() {
        return protocol_id;
    }

    private String category;
    private String protocol_id;
    private String approval_date;
}
