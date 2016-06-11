package pl.edu.uj.stolen_bike.guardapp;

import android.graphics.Bitmap;

/**
 * Created by shybovycha on 09/06/16.
 */
public class StolenBikeNotification {
    private StolenBikeRecord record;
    private Bitmap image;

    public StolenBikeNotification(StolenBikeRecord record, Bitmap image) {
        this.record = record;
        this.image = image;
    }

    public StolenBikeRecord getRecord() {
        return record;
    }

    public void setRecord(StolenBikeRecord record) {
        this.record = record;
    }

    public Bitmap getImage() {
        return image;
    }

    public void setImage(Bitmap image) {
        this.image = image;
    }
}
