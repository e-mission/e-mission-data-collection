package edu.berkeley.eecs.cfc_tracker.wrapper;

/**
 * Created by shankari on 7/6/15.
 */
public class Entry {
    public Metadata getMetadata() {
        return metadata;
    }

    public void setMetadata(Metadata metadata) {
        this.metadata = metadata;
    }

    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = data;
    }

    private Metadata metadata;
    private Object data;

    public Entry() {
    }
}
