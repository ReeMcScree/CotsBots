package com.example.wheel;

import android.Manifest;
import android.content.pm.PackageManager;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.io.IOException;

public class ShowimageActivity extends AppCompatActivity {
    Button Select, Camera, Detect, Ball;
    ImageView imageView;
    Bitmap bitmap;
    Mat mat;
    int SELECT_CODE = 100, CAMERA_CODE = 101;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_show_image);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.showimage), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        Ball = findViewById(R.id.Ball);
        Detect = findViewById(R.id.Detect); // for the Detect button jump into next page
        Detect.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                // Create an Intent，FROM SHOWIMAGEActivity JUMP TO DETECT ACTIVITY
                Intent intent = new Intent(ShowimageActivity.this, DetectionActivity.class);
                startActivity(intent); // jump
            }
        });
        Ball.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Create an Intent，FROM SHOWIMAGEActivity JUMP TO DETECT ACTIVITY
                Intent intent = new Intent(ShowimageActivity.this, ColorBlobDetectionActivity.class);
                startActivity(intent); // jump
            }
        });

        // ADD MICROPHONE JUMP HERE
        Button Microphone = findViewById(R.id.Microphone);
        Microphone.setOnClickListener(v -> {
            Intent intent = new Intent(ShowimageActivity.this, MicroPhoneActivity.class);
            startActivity(intent);
        });



        getPermission();
        Camera = findViewById(R.id.Camera); //the function used to find item in xml layout
        Select = findViewById(R.id.Select);
        imageView = findViewById(R.id.imageView);

        Select.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("image/*");
                startActivityForResult(intent, SELECT_CODE);

            }
        });

        //capture images from camera
        Camera.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                startActivityForResult(intent, CAMERA_CODE);
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == SELECT_CODE && data != null) {
            try {
                bitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), data.getData());
                imageView.setImageBitmap(bitmap);
                mat = new Mat();
                Utils.bitmapToMat(bitmap, mat);
                Imgproc.cvtColor(mat, mat, Imgproc.COLOR_RGB2GRAY);
                Utils.matToBitmap(mat, bitmap);
                imageView.setImageBitmap(bitmap);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if(requestCode==CAMERA_CODE && data!=null){
            bitmap = (Bitmap) data.getExtras().get("data");
            imageView.setImageBitmap(bitmap);
            mat = new Mat();
            Utils.bitmapToMat(bitmap, mat);
            Imgproc.cvtColor(mat, mat, Imgproc.COLOR_RGB2GRAY);
            Utils.matToBitmap(mat, bitmap);
            imageView.setImageBitmap(bitmap);

        }
    }


    void getPermission() {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, 102);
        }
    }

    public void onRequestPermissionResult(int requestCode, @NonNull String[] permission, @NonNull int[] grantResult){
        super.onRequestPermissionsResult(requestCode, permission, grantResult);
        if(requestCode == 102 && grantResult.length>0){
            if(grantResult[0] == PackageManager.PERMISSION_GRANTED){
                getPermission(); //if user reject the permission, ask him again;
            }

        }
    }
}