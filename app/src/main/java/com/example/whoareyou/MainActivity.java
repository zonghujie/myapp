package com.example.whoareyou;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import android.Manifest;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;

import android.os.Environment;
import android.os.FileUtils;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Base64;

import okhttp3.FormBody;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    public static final int TAKE_PHOTO = 1;     //拍照请求码
    public static final int CHOOSE_PHOTO = 2;   //相册请求码

    private Button PhotoBtn;
    private Button UploadBtn;
    private Button AlbumBtn;

    private Uri imageUri; //记录拍照后的照片文件的地址(临时文件)
    private ImageView ShowPhoto;//显示选中的照片
    private String uploadFileName;
    private String uploadUrl = "http://10.0.2.2:8000/upload";

//    private TextView responseText; //okhttp使用测试

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
//        responseText = findViewById(R.id.response_text);    //okhttp使用测试

        PhotoBtn.setOnClickListener(this);
        AlbumBtn.setOnClickListener(this);
        UploadBtn.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            // 1.1 【拍照】按钮
            case R.id.take_photo:
                takePhoto();
                break;
            // 1.2【相册】按钮
            case R.id.open_album:
                selectPhoto();
                break;
            // 3、【上传】按钮
            case R.id.upload_photo:
                //判断是否已选择图片
                if(ShowPhoto.getDrawable() == null){
                    Toast.makeText(MainActivity.this, "请先选择图片", Toast.LENGTH_SHORT).show();
                }else{//使用okhttp上传至阿里云
                    upload();
                }
                break;
            default:
                break;
        }
    }

    // 1.1调用摄像头拍照
    private void takePhoto() {
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
            Log.i("imageUri,SDK>=24", String.valueOf(imageUri));
        } else {
            imageUri = Uri.fromFile(outputImage);
            Log.i("imageUri,SDK<24", String.valueOf(imageUri));
        }
        // 启动相机
        // 构建 Intent 对象
        //      此处使用的是一个隐式 Intent，系统会找到能够响应这个 Intent 的活动区启动
        //      这样照相程序会被打开，拍下的照片将会输出到 output_image.jpg 中
        // 调用 putExtra() 方法指定图片的输出地址，此处填入刚得到的 Uri 对象
        // 调用 startActivityForResult() 来启动活动
        Intent intent = new Intent("android.media.action.IMAGE_CAPTURE");
        //MediaStore.EXTRA_OUTPUT，使得拍照后的图片输出到对应路径下。
        intent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri);
        startActivityForResult(intent, TAKE_PHOTO);
    }

    //1.2打开相册
    // 点击事件里先进行一个运行时权限处理，动态申请WRITE_EXTERNAL_STORAGE这个危险权限
    // 因为相册中的照片都是存储在SD卡上的，要从SD卡中读取照片就需要申请这个权限
    // WRITE_EXTERNAL_STORAGE:表示同时授予程序对SD卡读和写的能力
    private void selectPhoto() {
        //进行sdcard的读写请求
        if (ContextCompat.checkSelfPermission(MainActivity.this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(MainActivity.this, new String[]{
                    Manifest.permission.WRITE_EXTERNAL_STORAGE}, CHOOSE_PHOTO);
        } else {
            openAlbum();
        }
    }

    //权限
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case CHOOSE_PHOTO:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    //得到了用户的允许
                    openAlbum();
                } else {
                    //用户拒绝
                    Toast.makeText(this, "用户拒绝授权", Toast.LENGTH_SHORT).show();
                }
                break;
            default:
                break;
        }
    }

    // 1.2 选择照片
    private void openAlbum() {
        Intent intent = new Intent("android.intent.action.GET_CONTENT");
        intent.setType("image/*");
        startActivityForResult(intent, CHOOSE_PHOTO);
    }

    // 2 显示照片
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case TAKE_PHOTO:
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
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                break;
            case CHOOSE_PHOTO:
                if (resultCode == RESULT_OK) {
                    handleSelect(data);
                }
                break;
            default:
                break;
        }
    }

    //1.2 读取显示所选的照片
    private void handleSelect(Intent intent) {
        Cursor cursor = null;
        Uri uri = intent.getData();
        cursor = getContentResolver().query(uri, null, null, null, null);
        if (cursor.moveToFirst()) {
            int columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME);
            uploadFileName = cursor.getString(columnIndex);
        }
        try {//使用Glide，对图片进行压缩，并显示在中心
            Glide.with(this).load(uri)
                    .thumbnail()
                    .fitCenter()
                    .into(ShowPhoto);
        } catch (Exception e) {
            e.printStackTrace();
        }
        cursor.close();
    }


    //okhttp使用测试
//    private void upload() {
//        new Thread() {
//            @Override
//            public void run() {
//                try {
//                    //发起GET请求
//                    OkHttpClient client = new OkHttpClient();//创建一个OkHttpClient的实例
//                    Request request = new Request.Builder()//发起HTTP请求，需要创建一个Request对象
//                            .url("http://www.baidu.com")
//                            .build();
//                    //调用newCall()方法创建一个Call对象，并调用execute()方法来发送请求并获取服务器返回的数据
//                    //Response对象就是服务器返回的数据
//                    Response response = client.newCall(request).execute();
//                    //得到返回的具体内容
//                    String responseData = response.body().string();
//                    showResponse(responseData);
//                } catch (IOException e) {
//                    e.printStackTrace();
//                }
//            }
//        }.start();
//    }

    // 3、上传照片
    public void upload()  {
        Toast.makeText(MainActivity.this, "上传照片功能未实现", Toast.LENGTH_SHORT).show();
    }


//    //okhttp使用测试
//    private void showResponse(final String response){
//        runOnUiThread(new Runnable() {
//            @Override
//            public void run() {
//                //在这里进行UI操作，将结果显示到页面上
//                responseText.setText(response);
//            }
//        });
//    }
}