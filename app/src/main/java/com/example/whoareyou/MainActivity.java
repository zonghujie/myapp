package com.example.whoareyou;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import android.Manifest;
import android.annotation.TargetApi;
import android.content.ContentUris;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

public class MainActivity extends AppCompatActivity{

    private Button PhotoBtn;
    private Button UploadBtn;
    private Button AlbumBtn;

    private Uri imageUri; //记录拍照后的照片文件的地址(临时文件)
    private ImageView ShowPhoto;//显示选中的照片
    private String uploadFileName;
    private byte[] fileBuf;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_layout);


        //主要功能：
        // 1、获取照片（两种途径）：
        //    1.1 点击【拍照】按钮—>调用摄像头拍照
        //    1.2 点击【相册】按钮，打开相册选择照片
        // 2、显示照片
        // 3、点击【上传】按钮把选中的照片上传至阿里云—>
        //   人脸对比（涉及：建立人脸库，调用人脸检测API，调用人脸对比API）->返回结果至页面上
        PhotoBtn = findViewById(R.id.take_photo);
        AlbumBtn = findViewById(R.id.open_album);
        UploadBtn = findViewById(R.id.upload_photo);


        ShowPhoto = findViewById(R.id.show_photo);

        //【拍照】按钮
        //1.1 调用摄像头拍照
        PhotoBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Android 6.0 系统开始，读写SD卡被列为危险权限，
                //      如果将图片存在在SD卡的任何其他目录，都要进行运行时权限处理，而使用应用关联目录则可以跳过这步
                // 创建File对象，用于存储拍照后的图片,
                //      把图片命名为 output_image.jpg 并将它存放在手机SD卡的应用关联缓存目录下.
                // 应用关联缓存目录:指SD卡中专门用于存放当前应用缓存数据的位置，
                //      调用 getExternalCacheDir() 方法可以得到这个目录

                File outputImage = new File(getExternalCacheDir(), "output_image.jpg");
                try {
                    if (outputImage.exists()) {
                        outputImage.delete();
                    }
                    outputImage.createNewFile();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                // 从Android 7.0 系统开始，直接使用本地真实路径的Uri被认为是不安全的，
                // FileProvider 是一种特殊的内容提供器，使用了和内容提供器类似的机制来对数据进行保护，
                //     可以选择性地将封装过的Uri共享给外部，从而提高应用安全性
                //     需要配合 Provider,在 AndroidManifest.xml 中配置
                if (Build.VERSION.SDK_INT >= 24) {
                    imageUri = FileProvider.getUriForFile(MainActivity.this,
                            "com.example.whoareyou.fileprovider", outputImage);
                } else {
                    imageUri = Uri.fromFile(outputImage);
                }

                // 启动相机
                // 构建 Intent 对象
                //      此处使用的是一个隐式 Intent，系统会找到能够响应这个 Intent 的活动区启动
                //      这样照相程序会被打开，拍下的照片将会输出到 output_image.jpg 中
                // 调用 putExtra() 方法指定图片的输出地址，此处填入刚得到的 Uri 对象
                // 调用 startActivityForResult() 来启动活动
                Intent intent = new Intent("android.media.action.IMAGE_CAPTURE");
                intent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri);
                startActivityForResult(intent, 1);
            }
        });
        //【相册】按钮
        // 点击事件里先进行一个运行时权限处理，动态申请WRITE_EXTERNAL_STORAGE这个危险权限
        //因为相册中的照片都是存储在SD卡上的，要从SD卡中读取照片就需要申请这个权限
        //WRITE_EXTERNAL_STORAGE:表示同时授予程序对SD卡读和写的能力
        AlbumBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(ContextCompat.checkSelfPermission(MainActivity.this,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED){
                    ActivityCompat.requestPermissions(MainActivity.this, new String[]{
                            Manifest.permission.WRITE_EXTERNAL_STORAGE }, 1);
                }else {
                    openAlbum();
                }
            }
        });
        //【上传】按钮
        UploadBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode){
//            case 1:
//                if(grantResults.length>0 && grantResults[0]==PackageManager.PERMISSION_GRANTED){
//                    //得到了用户的允许
//                }
//                else{
//                    //用户拒绝
//                }
            case 2:
                if(grantResults.length>0 && grantResults[0]==PackageManager.PERMISSION_GRANTED){
                    //得到了用户的允许
                    openAlbum();
                }else {
                    //用户拒绝
                    Toast.makeText(this, "用户拒绝授权", Toast.LENGTH_SHORT).show();
                }
                break;
            default:
                break;
        }
    }

    // 1.2 打开相册选择照片
    private void openAlbum() {
        Intent intent = new Intent("android.intent.action.GET_CONTENT");
        intent.setType("image/*");
        startActivityForResult(intent, 2);
    }

    // 2 显示照片
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case 1:
                //此时，相机拍照完毕
                if (resultCode == RESULT_OK) {
                    try {
                        //将拍摄的照片显示出来
                        //利用ContentResolver,查询临时文件，并使用BitMapFactory,从输入流中创建BitMap
                        //同样需要配合Provider,在Manifest.xml中加以配置
                        Bitmap map = BitmapFactory.decodeStream(getContentResolver().openInputStream(imageUri));
                        ShowPhoto.setImageBitmap(map);
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    }
                }
                break;
            case 2:
                handleSelect(data);
                break;
            default:
                break;
        }
    }

    //选择后照片的读取工作
    private void handleSelect(Intent intent) {
        Cursor cursor = null;
        Uri uri = intent.getData();
        cursor = getContentResolver().query(uri, null, null, null, null);
        if (cursor.moveToFirst()) {
            int columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME);
            uploadFileName = cursor.getString(columnIndex);
        }
        try {
            InputStream inputStream = getContentResolver().openInputStream(uri);
            fileBuf= convertToBytes(inputStream);
            Bitmap bitmap = BitmapFactory.decodeByteArray(fileBuf, 0, fileBuf.length);
            ShowPhoto.setImageBitmap(bitmap);
        } catch (Exception e) {
            e.printStackTrace();
        }
        cursor.close();
    }

    private byte[] convertToBytes(InputStream inputStream) throws Exception{
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[1024];
        int len = 0;
        while ((len = inputStream.read(buf)) > 0) {
            out.write(buf, 0, len);
        }
        out.close();
        inputStream.close();
        return  out.toByteArray();
    }
}