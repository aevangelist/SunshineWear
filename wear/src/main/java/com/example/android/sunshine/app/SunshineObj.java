package com.example.android.sunshine.app;

import android.graphics.Bitmap;
import android.util.Log;

/**
 * Created by aevangelista on 16-03-28.
 */
public class SunshineObj {

    String minTemp;
    String maxTemp;
    Bitmap icon;

    // This is the constructor of the class
    public SunshineObj(String minTemp, String maxTemp, Bitmap icon){
        this.minTemp = minTemp;
        this.maxTemp = maxTemp;
        this.icon = icon;
    }

    public void setMinTemp(String t){
        minTemp = t;
        Log.d("", "Setting min temp to: " + t);
    }

    public String getMinTemp(){
        return minTemp;
    }

    public void setMaxTemp(String t){
        maxTemp = t;
        Log.d("", "Setting max temp to: " + t);

    }

    public String getMaxTemp(){
        return maxTemp;
    }

    public void setIcon(Bitmap b){
        icon = b;
        Log.d("", "Setting bitmap to: " + b);

    }

    public Bitmap getIcon(){
        return icon;
    }
}
