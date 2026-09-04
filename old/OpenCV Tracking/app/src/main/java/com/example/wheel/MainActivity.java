package com.example.wheel;

import androidx.appcompat.app.AppCompatActivity;
import org.opencv.android.OpenCVLoader;
import android.util.Log;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.opencv.android.OpenCVLoader;

public class MainActivity extends AppCompatActivity {

    LinearLayout background=null;
    ImageView center=null;
    TextView info=null;
    Button toBT=null;
    Button OpenCV = null;
    Button VoiceControl = null;

    Intent intent=null;
    private double centerPoint=300;
    public static int[] wheelData=new int[]{0,0};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        //test the function of the opencv
        if (OpenCVLoader.initDebug()) {
            Log.d("LOADED", "successSSSSSSSSSSSS");
        } else {
            Log.d("LOADED", "ERRRRRRRRRRRRRRRRRR");
        }
        OpenCV = findViewById(R.id.OpenCV);
        VoiceControl = findViewById(R.id.VoiceControl);
        VoiceControl.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, VoiceControlActivity.class);
            startActivity(intent);
        });

        OpenCV.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, ShowimageActivity.class);
                startActivity(intent);
            }
        });
        {
            background=(LinearLayout) findViewById(R.id.background);
            center=(ImageView) findViewById(R.id.center);
            info=(TextView) findViewById(R.id.info);
            toBT=(Button) findViewById(R.id.toBT);
        }

        {
            background.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View view, MotionEvent motionEvent) {
                    switch (motionEvent.getAction()){
                        case MotionEvent.ACTION_DOWN:
                        case MotionEvent.ACTION_MOVE:
                            if((motionEvent.getX()-300)*(motionEvent.getX()-300)+(motionEvent.getY()-300)*(motionEvent.getY()-300)<=300*300){

                                center.setTranslationX(motionEvent.getX()-300);
                                center.setTranslationY(motionEvent.getY()-300);

                                info.setText("Angle:"+String.valueOf((int)getAngle(motionEvent.getX(),motionEvent.getY()))
                                +"+"+"Distance:"+String.valueOf((int)getDistance(motionEvent.getX(), motionEvent.getY())));
                                wheelData[0]=(int)getAngle(motionEvent.getY(),motionEvent.getX());
                                wheelData[1]=(int)getDistance(motionEvent.getY(),motionEvent.getX());
                            }
                            break;
                        case MotionEvent.ACTION_UP:

                            center.setTranslationX(0);
                            center.setTranslationY(0);
                            info.setText("Angle:0+Distance:0");
                            wheelData[0]=0;
                            wheelData[1]=0;
                            break;
                        default:
                            break;
                    }
                    return true;
                }
            });
            toBT.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    intent=new Intent(MainActivity.this,BluetoothActivity.class);
                    startActivity(intent);
                }
            });
        }

    }
    private double getAngle(double X,double Y){
        double angle=0;
        angle=Math.atan2(Y-centerPoint,X-centerPoint);
        angle=Math.toDegrees(angle);
        return angle;
    }
    private double getDistance(double X,double Y){
        double distance=0;
        distance=Math.sqrt(Math.pow(X-centerPoint,2)+Math.pow(Y-centerPoint,2));
        return distance;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        BluetoothActivity.connectThread.cancel();
        BluetoothActivity.connectedThread.cancel();
    }
}