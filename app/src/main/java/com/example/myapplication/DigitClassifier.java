package com.example.myapplication;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.util.Log;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;

import org.tensorflow.lite.Interpreter;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class DigitClassifier {
    private static final String TAG = "DigitClassifier";
    private static final int FLOAT_TYPE_SIZE = 4;   // kích thước của 1 số float trong byte
    private static final int PIXEL_SIZE = 1;
    private static final int OUTPUT_CLASSES_COUNT = 10;

    private Interpreter interpreter;
    private Context context;
    boolean isInitialized = false;

//    Quản lý các luồng trong ứng dụng
    ExecutorService executorService = Executors.newCachedThreadPool();

    int inputImageWidth = 0;
    int inputImageHeight = 0;
    int modelInputSize = 0;

    public DigitClassifier(Context context) {
        this.interpreter = null;
        this.context = context;
    }

    //    Xử lý bất đồng bộ
    public Future<Void> initialize() {
//        Tạo 1 đối tượng để theo dõi kết quả
        CompletableFuture<Void> future = new CompletableFuture<>();

//        Xử lý ngoại lệ
        executorService.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    initializeInterpreter();
                    future.complete(null);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        });

//        trả về future để theo dõi kết quả
        return future;
    }

    private void initializeInterpreter() throws IOException {
        // Load tflite từ asset
        AssetManager assetManager = context.getAssets();
        String modelName = "digit_recognition.tflite";
        ByteBuffer model = loadModelFile(assetManager, modelName);
        Interpreter interpreter1 = new Interpreter(model);

        // Đọc vào shape input của model
        int[] inputShape = interpreter1.getInputTensor(0).shape();
        this.inputImageWidth = inputShape[1];
        this.inputImageHeight = inputShape[2];

        // Tính kích thước đầu vào theo byte, với ảnh có 1 channel
        modelInputSize = FLOAT_TYPE_SIZE * inputImageWidth * inputImageHeight * PIXEL_SIZE;

        this.interpreter = interpreter1;
        isInitialized = true;
        Log.d(TAG, "TFLite interpreter. được khởi tạo thành công");
    }

    private ByteBuffer loadModelFile(AssetManager assetManager, String filename) throws IOException{
        AssetFileDescriptor fileDescriptor = assetManager.openFd(filename);
        InputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = ((FileInputStream) inputStream).getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();

////        tạo ByteBuffer
//        ByteBuffer byteBuffer = ByteBuffer.allocateDirect((int) declaredLength).order(ByteOrder.nativeOrder());
////        đặt vị trí byteBuffer về đầu để chuẩn bị đọc dữ liệu
//        byteBuffer.rewind();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    int getMaxIndex(float[] arr){
        int max = 0;
        for(int i = 0; i < arr.length; i++){
            if(arr[i] > arr[max]){
                max = i;
            }
        }
        return max;
    }
    private String classify(Bitmap bitmap){
        if(!isInitialized){
            throw new IllegalStateException("TFLite Interpreter chưa được khởi tạo");
        }
        // Resize input giống với model
        Bitmap resizedImage = Bitmap.createScaledBitmap(bitmap, inputImageWidth, inputImageHeight, true);
        ByteBuffer byteBuffer = convertBitmapToByteBuffer(resizedImage);

        // Thực hiện predict cho input với tflite
        float[][] output = new float[1][OUTPUT_CLASSES_COUNT];
        interpreter.run(byteBuffer, output);

        // Tìm output có kết quả dự đoán cao nhất
        float[] result = output[0];
        int maxIndex = getMaxIndex(result);
        String resultString = String.format("Đây là số: %d\nTỷ lệ dự đoán là: %.2f", maxIndex, result[maxIndex]);
        return resultString;
    }

    public CompletableFuture<String> classifyAsync(Bitmap bitmap){
        CompletableFuture<String> future = new CompletableFuture<>();
        executorService.execute(()->{
            try{
                String result = classify(bitmap);
                future.complete(result);
            }catch (Exception e){
                Log.d(TAG, "Loiiiiiiiiii");
            }
        });

        return future;
    }

    public void close(){
        executorService.execute(()->{
            this.interpreter.close();
            Log.d("TAG", "TFLite interpreter. Đóng thành công");
        });
    }

    private ByteBuffer convertBitmapToByteBuffer(Bitmap bitmap) {
        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(modelInputSize);
        byteBuffer.order(ByteOrder.nativeOrder());

        int[] pixels = new int[inputImageWidth * inputImageHeight];
        bitmap.getPixels(pixels, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());

        for (int pixelValue : pixels) {
            int r = (pixelValue >> 16) & 0xFF;
            int g = (pixelValue >> 8) & 0xFF;
            int b = pixelValue & 0xFF;

            // Convert RGB to grayscale and normalize pixel value to [0..1].
            float normalizedPixelValue = (r + g + b) / 3.0f / 255.0f;
            byteBuffer.putFloat(normalizedPixelValue);
        }

        return byteBuffer;
    }

}
