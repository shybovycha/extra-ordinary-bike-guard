package pl.edu.uj.stolen_bike.guardapp;

import java.io.Serializable;

/**
 * Created by shybovycha on 09/06/16.
 */
public class StolenBikeRecord implements Serializable {
    private String docId;
    private String beaconId;
    private String image;

    public StolenBikeRecord(String docId, String beaconId) {
        this.docId = docId;
        this.beaconId = beaconId;
    }

    public String getDocId() {
        return docId;
    }

    public void setDocId(String docId) {
        this.docId = docId;
    }

    public String getBeaconId() {
        return beaconId;
    }

    public void setBeaconId(String beaconId) {
        this.beaconId = beaconId;
    }

    public String getImage() {
        return image;
    }

    public void setImage(String image) {
        this.image = image;
    }
}
